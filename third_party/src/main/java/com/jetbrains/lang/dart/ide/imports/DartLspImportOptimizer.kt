/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.imports

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.util.applyTextEdits
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.logging.PluginLogger
import com.jetbrains.lang.dart.lsp.DartLspApplyEditCapture
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.util.DartResolveUtil
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

private val LOG = PluginLogger.createLogger(DartLspImportOptimizer::class.java)

/**
 * Optimize Imports over LSP, used instead of [DartImportOptimizer] while LSP code actions are enabled.
 *
 * The Dart server's `source.organizeImports` code action only carries a command, and while the command runs, the
 * server sends the edit back with `workspace/applyEdit`. [processFile] runs on a background thread: it requests the
 * action, runs its command and takes the edit with [DartLspApplyEditCapture]. The returned [Runnable] applies the edit
 * in the write command of Optimize Imports, Reformat Code, save or commit, like [DartImportOptimizer] does with the
 * result of the legacy `edit.organizeDirectives`.
 */
class DartLspImportOptimizer : ImportOptimizer {
    override fun supports(file: PsiFile): Boolean =
        file is DartFile && DartConfigurable.isLspCodeActionsEnabled(file.project)

    override fun processFile(file: PsiFile): Runnable {
        // The platform calls processFile on a background thread, which the synchronous LSP requests below need.
        if (ApplicationManager.getApplication().isDispatchThread) return DartImportOptimizer().processFile(file)

        val project = file.project
        val virtualFile = DartResolveUtil.getRealVirtualFile(file) ?: return EmptyRunnable.getInstance()
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return EmptyRunnable.getInstance()
        val modificationStamp = document.modificationStamp
        val das = DartAnalysisServerService.getInstance(project)
        das.serverReadyForRequest()
        das.updateFilesContent()

        val server = LspServerManager.getInstance(project)
            .getServersForProvider(DartLspServerSupportProvider::class.java)
            .firstOrNull()
        if (server == null) {
            LOG.info("Optimize Imports: no running Dart LSP server for ${virtualFile.path}")
            return EmptyRunnable.getInstance()
        }

        val edits = fetchOrganizeImportsEdits(
            virtualFile.path,
            server.descriptor.getFileUri(virtualFile),
            DartLspApplyEditCapture.getInstance(project),
            requestCodeActions = { params -> server.sendRequestSync { it.textDocumentService.codeAction(params) } },
            executeCommand = { params ->
                val sent = AtomicReference<CompletableFuture<Any>>()
                server.sendRequestSync { it.workspaceService.executeCommand(params).also(sent::set) }
                // sendRequestSync also returns after a timeout, without the server having finished the command.
                sent.get()?.let { it.isDone && !it.isCancelled } == true
            },
        ) ?: return EmptyRunnable.getInstance()
        return DartLspOrganizeImportsRunnable(project, document, modificationStamp, edits)
    }
}

/**
 * Requests the `source.organizeImports` action for the file, runs its command and returns the text edits that the
 * server sends with `workspace/applyEdit` meanwhile, or `null` if there is nothing to change.
 *
 * [executeCommand] returns whether the server finished the command, also if it answered with an error (e.g. for a file
 * with syntax errors). If waiting stopped before (timeout, [com.intellij.openapi.progress.ProcessCanceledException]),
 * the capture drops the edit the command may still send, see [DartLspApplyEditCapture.Capture.finish].
 */
internal fun fetchOrganizeImportsEdits(
    filePath: String,
    fileUri: String,
    capture: DartLspApplyEditCapture,
    requestCodeActions: (CodeActionParams) -> List<Either<Command, CodeAction>>?,
    executeCommand: (ExecuteCommandParams) -> Boolean,
): List<TextEdit>? {
    val context = CodeActionContext(emptyList(), listOf(CodeActionKind.SourceOrganizeImports)).apply {
        triggerKind = CodeActionTriggerKind.Invoked
    }
    val params = CodeActionParams(TextDocumentIdentifier(fileUri), Range(Position(0, 0), Position(0, 0)), context)
    val action = requestCodeActions(params)
        ?.firstNotNullOfOrNull { either -> either.right?.takeIf { it.kind == CodeActionKind.SourceOrganizeImports } }
        ?: return null
    // The Dart server's action has no edit, only the command that sends it.
    val command = action.command ?: return null

    val pending = capture.start(filePath)
    var commandFinished = false
    try {
        commandFinished = executeCommand(ExecuteCommandParams(command.command, command.arguments))
    }
    finally {
        pending.finish(commandFinished)
    }
    return pending.edits
}

/**
 * Applies the organize-imports [edits] in the write command of Optimize Imports, unless [document] changed since the
 * server computed them.
 */
internal class DartLspOrganizeImportsRunnable(
    private val project: Project,
    private val document: Document,
    private val modificationStamp: Long,
    private val edits: List<TextEdit>,
) : ImportOptimizer.CollectingInfoRunnable {
    private var fileChanged = false

    override fun run() {
        if (document.modificationStamp != modificationStamp) {
            LOG.info("Optimize Imports: the document changed while the server organized the imports, edit skipped")
            return
        }
        applyTextEdits(document, edits)
        if (document.modificationStamp != modificationStamp) {
            fileChanged = true
            // Like in DartImportOptimizer: committing the document makes sure that DartPostFormatProcessor.processText()
            // is called afterwards.
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }

    override fun getUserNotificationInfo(): String? =
        if (fileChanged) DartBundle.message("organized.directives") else null
}
