// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer

import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.DartLspWorkspaceConfigurationConsumer
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.logging.PluginLogger
import com.jetbrains.lang.dart.lsp.DartLspConfigurationSync
import com.jetbrains.lang.dart.lsp.DartLspInlayHintsConfiguration
import com.jetbrains.lang.dart.sdk.DartConfigurable
import kotlinx.coroutines.launch
import org.dartlang.analysis.server.protocol.*

private val LOG = PluginLogger.createLogger(DartAnalysisServerImpl::class.java)

/** The name of the configuration section of the Dart Analysis Server. */
private const val DART_CONFIGURATION_SECTION = "dart"

internal class DartAnalysisServerImpl(private val project: Project, socket: AnalysisServerSocket) : RemoteAnalysisServerImpl(socket) {

  override fun server_openUrlRequest(url: String) = BrowserUtil.browse(url)

  override fun server_showMessageRequest(
    messageType: String,
    message: @NlsSafe String,
    messageActions: List<MessageAction>,
    consumer: ShowMessageRequestConsumer,
  ) {
    val actionLabels: List<@NlsSafe String> = messageActions.map { it.label }
    showServerMessageRequest(project, messageType, message, actionLabels, answer = consumer::computedMessageActions)
  }

  override fun lsp_workspaceApplyEdit(params: DartLspApplyWorkspaceEditParams, consumer: DartLspWorkspaceApplyEditRequestConsumer) {
    if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
      // When experimental LSP features are enabled, workspace/applyEdit is handled by DartBridgeLspServer,
      // which forwards it to the LSP LanguageClient proxy and sends the legacy response to DAS when applied.
      return
    }

    DartAnalysisServerService.getInstance(project).serviceScope.launch {
      val label: @NlsSafe String? = params.label
      val commandName: String = label ?: DartBundle.message("code.changes.by.dart.analysis.server")

      val result = runCatching {
        WriteCommandAction.writeCommandAction(project)
          .withName(commandName)
          .compute<Boolean, Throwable> {
            applyWorkspaceEdit(params.workspaceEdit)
          }
      }
      consumer.workspaceEditApplied(DartLspApplyWorkspaceEditResult(result.getOrDefault(false)))
      result.getOrThrow()
    }
  }

  /**
   * Answers the `workspace/configuration` request of the server. The server blocks its
   * initialization until it gets an answer, so every requested section gets an entry: the settings
   * of the Dart plugin for the `dart` section, `null` for anything else.
   *
   * Called on the response reader thread of the server; reading the settings needs neither the EDT
   * nor a read action.
   */
  override fun lsp_workspaceConfiguration(sections: List<String?>, consumer: DartLspWorkspaceConfigurationConsumer) {
    val dartSection =
      if (sections.contains(DART_CONFIGURATION_SECTION)) DartLspInlayHintsConfiguration.buildDartSection() else null

    consumer.computedConfiguration(sections.map { section ->
      when (section) {
        DART_CONFIGURATION_SECTION -> dartSection
        else -> null
      }
    })

    // Only after the answer is out: what the server now knows is the yardstick for noticing that
    // the settings have changed, and answering must not depend on it.
    if (dartSection != null) {
      DartLspConfigurationSync.getInstance(project).configurationSentToServer(dartSection)
    }
  }

  @RequiresWriteLock
  private fun applyWorkspaceEdit(workspaceEdit: DartLspWorkspaceEdit): Boolean {
    val documentChanges = workspaceEdit.documentChanges ?: return false

    for (change in documentChanges) {
      when (change) {
        is DartLspTextDocumentEdit -> {
          val uri = change.textDocument.uri
          val virtualFile = getDartFileInfo(project, uri).findFile() ?: return false
          val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return false
          if (!applyTextEdits(document, change.edits)) return false
        }
        else -> {
          LOG.warn("Unsupported document change type: ${change::class.java.simpleName}")
          return false
        }
      }
    }
    return true
  }
}

/**
 * Shows a question of the server (`server.showMessageRequest`) and passes the chosen action label to [answer], or `null`
 * if the user closed it without choosing.
 *
 * While a modal dialog or progress is open, e.g. the progress of a rename that waits for the server's *Rename Anyway* /
 * *Cancel* question, the question is a modal dialog: a notification couldn't be clicked then. Otherwise it is a
 * notification with the actions as links.
 */
internal fun showServerMessageRequest(
  project: Project,
  messageType: String,
  message: @NlsSafe String,
  actionLabels: List<@NlsSafe String>,
  isModalContext: () -> Boolean = { ModalityState.current() != ModalityState.nonModal() },
  answer: (String?) -> Unit,
) {
  ApplicationManager.getApplication().invokeLater({
    if (isModalContext()) {
      val icon = when (messageType) {
        MessageType.ERROR -> Messages.getErrorIcon()
        MessageType.WARNING -> Messages.getWarningIcon()
        else -> Messages.getInformationIcon()
      }
      val choice = Messages.showDialog(project, message, DartBundle.message("dart.analysis.server.message.title"),
                                       actionLabels.toTypedArray(), 0, icon)
      answer(actionLabels.getOrNull(choice))
      return@invokeLater
    }

    val notificationType: NotificationType = when (messageType) {
      MessageType.ERROR -> NotificationType.ERROR
      MessageType.WARNING -> NotificationType.WARNING
      else -> NotificationType.INFORMATION
    }

    NotificationGroupManager.getInstance()
      .getNotificationGroup("Dart Analysis Server")
      .createNotification(message, notificationType)
      .also { notification ->
        for (actionLabel in actionLabels) {
          notification.addAction(object : AnAction(actionLabel) {
            override fun actionPerformed(e: AnActionEvent) {
              notification.expire()
              answer(actionLabel)
            }
          })
        }
      }
      .notify(project)
  }, ModalityState.any(), project.disposed)
}
