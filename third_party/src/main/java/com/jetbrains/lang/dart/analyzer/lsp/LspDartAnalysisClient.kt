// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import com.google.dart.server.AnalysisServerListener
import com.google.dart.server.AnalysisServerStatusListener
import com.google.dart.server.Consumer
import com.google.dart.server.CreateContextConsumer
import com.google.dart.server.DartLspTextDocumentContentConsumer
import com.google.dart.server.ExtendedRequestErrorCode
import com.google.dart.server.FindElementReferencesConsumer
import com.google.dart.server.FormatConsumer
import com.google.dart.server.GetAssistsConsumer
import com.google.dart.server.GetFixesConsumer
import com.google.dart.server.GetHoverConsumer
import com.google.dart.server.GetImportedElementsConsumer
import com.google.dart.server.GetNavigationConsumer
import com.google.dart.server.GetPostfixCompletionConsumer
import com.google.dart.server.GetRefactoringConsumer
import com.google.dart.server.GetRuntimeCompletionConsumer
import com.google.dart.server.GetServerPortConsumer
import com.google.dart.server.GetStatementCompletionConsumer
import com.google.dart.server.GetSuggestionDetailsConsumer
import com.google.dart.server.GetSuggestionDetailsConsumer2
import com.google.dart.server.GetSuggestionsConsumer
import com.google.dart.server.GetSuggestionsConsumer2
import com.google.dart.server.GetTypeHierarchyConsumer
import com.google.dart.server.ImportElementsConsumer
import com.google.dart.server.IsPostfixCompletionApplicableConsumer
import com.google.dart.server.ListPostfixCompletionTemplatesConsumer
import com.google.dart.server.MapUriConsumer
import com.google.dart.server.OrganizeDirectivesConsumer
import com.google.dart.server.RequestListener
import com.google.dart.server.ResponseListener
import com.google.dart.server.SortMembersConsumer
import com.google.dart.server.UpdateContentConsumer
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.analyzer.DartClient
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import com.jetbrains.lang.dart.sdk.DartSdk
import org.dartlang.analysis.server.protocol.AddContentOverlay
import org.dartlang.analysis.server.protocol.AnalysisOptions
import org.dartlang.analysis.server.protocol.ImportedElements
import org.dartlang.analysis.server.protocol.RefactoringOptions
import org.dartlang.analysis.server.protocol.RequestError
import org.dartlang.analysis.server.protocol.RuntimeCompletionExpression
import org.dartlang.analysis.server.protocol.RuntimeCompletionVariable
import org.dartlang.analysis.server.protocol.SourceChange
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.UUID
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal class LspDartAnalysisClient(
    private val project: Project,
    sdk: DartSdk,
    suppressAnalytics: Boolean,
) : DartClient {
    private companion object {
        private val LOG = Logger.getInstance(LspDartAnalysisClient::class.java)
        private val GSON = Gson()
        private const val DIAGNOSTIC_SERVER_TIMEOUT_MS = 30_000L
        private const val ASSISTS_RETRY_TIMEOUT_MS = 1_000L
        private const val ASSISTS_RETRY_INTERVAL_MS = 100L
        private val ASSIST_ACTION_KINDS = listOf(CodeActionKind.Refactor)
    }

    private val lspClientConnectionManager = LspClientConnectionManager(project, sdk, suppressAnalytics)
    private val sourceChangeConverter = LspSourceChangeConverter(project)
    private val documentLock = Any()
    private val documentSync =
        LspDocumentSyncManager(
            onDidOpen = lspClientConnectionManager::didOpen,
            onDidChange = lspClientConnectionManager::didChange,
            onDidClose = lspClientConnectionManager::didClose,
            syncKindProvider = lspClientConnectionManager::textDocumentSyncKind,
        )

    override fun start() {
        lspClientConnectionManager.start()
    }

    override fun isSocketOpen(): Boolean = lspClientConnectionManager.isSocketOpen()

    override fun addAnalysisServerListener(listener: AnalysisServerListener) {}

    override fun removeAnalysisServerListener(listener: AnalysisServerListener) {}

    override fun addRequestListener(listener: RequestListener) {}

    override fun removeRequestListener(listener: RequestListener) {}

    override fun addResponseListener(listener: ResponseListener) {}

    override fun removeResponseListener(listener: ResponseListener) {}

    override fun addStatusListener(listener: AnalysisServerStatusListener) {
        lspClientConnectionManager.addStatusListener(listener)
    }

    override fun completion_setSubscriptions(subscriptions: List<String>) {}

    override fun analysis_updateContent(
        files: Map<String, Any>,
        consumer: UpdateContentConsumer,
    ) {
        try {
            synchronized(documentLock) {
                files.forEach { (uri, overlay) -> documentSync.applyOverlay(uri, overlay) }
            }
        } catch (t: Throwable) {
            LOG.warn("Failed to synchronize overlaid Dart document content over LSP", t)
        } finally {
            // DartAnalysisServerService currently uses this callback for overlay cleanup and cache refresh.
            // The real fix for partial-send failures is to defer its state commits until after a confirmed send.
            consumer.onResponse()
        }
    }

    override fun analysis_setAnalysisRoots(
        included: List<String>,
        excluded: List<String>,
        packageRoots: Map<String, String>?,
    ) {
        try {
            lspClientConnectionManager.synchronizeWorkspaceFolders(included.toSet(), ::toWorkspaceFolder)
        } catch (t: Throwable) {
            LOG.warn("Failed to synchronize Dart LSP analysis roots", t)
        }
    }

    override fun analysis_getHover(
        file: String,
        offset: Int,
        consumer: GetHoverConsumer,
    ) {}

    override fun analysis_getNavigation(
        file: String,
        offset: Int,
        length: Int,
        consumer: GetNavigationConsumer,
    ) {}

    override fun edit_getAssists(
        file: String,
        offset: Int,
        length: Int,
        consumer: GetAssistsConsumer,
    ) {
        try {
            synchronized(documentLock) {
                ensureWorkspaceFolderForFile(file)
                loadCurrentContent(file)?.let { content ->
                    documentSync.applyOverlay(file, AddContentOverlay(content))
                }
            }
        } catch (t: Throwable) {
            consumer.onError(toRequestError(t))
            return
        }

        val params =
            try {
                CodeActionParams(
                    TextDocumentIdentifier(file),
                    sourceChangeConverter.toRange(file, offset, length),
                    CodeActionContext(emptyList()).apply {
                        only = ASSIST_ACTION_KINDS
                        triggerKind = CodeActionTriggerKind.Invoked
                    },
                )
            } catch (t: Throwable) {
                consumer.onError(toRequestError(t))
                return
            }

        try {
            val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ASSISTS_RETRY_TIMEOUT_MS)
            while (true) {
                val actions = lspClientConnectionManager.codeAction(params).get(ASSISTS_RETRY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                val sourceChanges =
                    actions
                        .orEmpty()
                        .mapNotNull(::toAssistSourceChange)
                if (sourceChanges.isNotEmpty() || System.nanoTime() >= deadlineNs) {
                    consumer.computedSourceChanges(sourceChanges)
                    return
                }
                Thread.sleep(ASSISTS_RETRY_INTERVAL_MS)
            }
        } catch (t: Throwable) {
            if (t is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            consumer.onError(toRequestError(t))
        }
    }

    override fun edit_isPostfixCompletionApplicable(
        file: String,
        key: String,
        offset: Int,
        consumer: IsPostfixCompletionApplicableConsumer,
    ) {}

    override fun edit_listPostfixCompletionTemplates(consumer: ListPostfixCompletionTemplatesConsumer) {}

    override fun edit_getPostfixCompletion(
        file: String,
        key: String,
        offset: Int,
        consumer: GetPostfixCompletionConsumer,
    ) {}

    override fun edit_getStatementCompletion(
        file: String,
        offset: Int,
        consumer: GetStatementCompletionConsumer,
    ) {}

    override fun diagnostic_getServerPort(consumer: GetServerPortConsumer) {
        val future =
            try {
                lspClientConnectionManager.requestDiagnosticServer()
            } catch (t: Throwable) {
                consumer.onError(toRequestError(t))
                return
            }

        future.orTimeout(DIAGNOSTIC_SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS).whenComplete { result, throwable ->
            if (throwable != null) {
                consumer.onError(toRequestError(throwable))
                return@whenComplete
            }

            val port = result?.port
            if (port == null || port <= 0) {
                consumer.onError(
                    RequestError(
                        ExtendedRequestErrorCode.INVALID_SERVER_RESPONSE,
                        DartBundle.message("analysis.server.show.diagnostics.error"),
                        null,
                    ),
                )
                return@whenComplete
            }

            consumer.computedServerPort(port)
        }
    }

    private fun toRequestError(throwable: Throwable): RequestError {
        val rootCause = unwrapCause(throwable)
        val message =
            when {
                rootCause is IllegalStateException -> DartBundle.message("analysis.server.not.running")
                rootCause.message.isNullOrBlank() -> DartBundle.message("analysis.server.show.diagnostics.error")
                else -> rootCause.message!!
            }
        return RequestError(ExtendedRequestErrorCode.INVALID_SERVER_RESPONSE, message, null)
    }

    private fun unwrapCause(throwable: Throwable): Throwable {
        var current = throwable
        while ((current is CompletionException || current is ExecutionException) && current.cause != null) {
            current = current.cause!!
        }
        return current
    }

    private fun toAssistSourceChange(actionResult: Either<Command, CodeAction>): SourceChange? {
        if (actionResult.isLeft) {
            val command = actionResult.left ?: return null
            val workspaceEdit = extractWorkspaceEdit(command) ?: return null
            return sourceChangeConverter.toSourceChange(command.title, command.command, workspaceEdit)
        }

        val action = actionResult.right ?: return null
        if (!isAssistAction(action)) {
            return null
        }

        val workspaceEdit = extractWorkspaceEdit(action) ?: return null
        if (!sourceChangeConverter.hasTranslatableChanges(workspaceEdit)) {
            return null
        }

        return sourceChangeConverter.toSourceChange(action, workspaceEdit)
    }

    private fun isAssistAction(action: CodeAction): Boolean {
        if (action.disabled != null || !action.diagnostics.isNullOrEmpty()) {
            return false
        }
        val kind = action.kind ?: return true
        return ASSIST_ACTION_KINDS.any { kind == it || kind.startsWith("${it}.") }
    }

    private fun extractWorkspaceEdit(action: CodeAction): WorkspaceEdit? {
        action.edit?.let { return it }
        return extractWorkspaceEdit(action.command)
    }

    private fun extractWorkspaceEdit(command: Command?): WorkspaceEdit? {
        val safeCommand = command ?: return null
        safeCommand.arguments.orEmpty().forEach { argument ->
            val candidate =
                try {
                    when (argument) {
                        is WorkspaceEdit -> argument
                        else -> GSON.fromJson(GSON.toJsonTree(argument), WorkspaceEdit::class.java)
                    }
                } catch (_: Throwable) {
                    null
                }
            if (sourceChangeConverter.hasTranslatableChanges(candidate)) {
                return candidate
            }
        }
        return null
    }

    override fun edit_getFixes(
        file: String,
        offset: Int,
        consumer: GetFixesConsumer,
    ) {}

    override fun search_findElementReferences(
        file: String,
        offset: Int,
        includePotential: Boolean,
        consumer: FindElementReferencesConsumer,
    ) {}

    override fun search_getTypeHierarchy(
        file: String,
        offset: Int,
        superOnly: Boolean,
        consumer: GetTypeHierarchyConsumer,
    ) {}

    override fun completion_getSuggestionDetails(
        file: String,
        id: Int,
        label: String,
        offset: Int,
        consumer: GetSuggestionDetailsConsumer,
    ) {}

    override fun completion_getSuggestionDetails2(
        file: String,
        offset: Int,
        completion: String,
        libraryUri: String,
        consumer: GetSuggestionDetailsConsumer2,
    ) {}

    override fun completion_getSuggestions(
        file: String,
        offset: Int,
        consumer: GetSuggestionsConsumer,
    ) {}

    override fun completion_getSuggestions2(
        file: String,
        offset: Int,
        maxResults: Int,
        completionCaseMatchingMode: String,
        completionMode: String,
        invocationCount: Int,
        timeout: Int,
        consumer: GetSuggestionsConsumer2,
    ) {}

    override fun edit_format(
        file: String,
        selectionOffset: Int,
        selectionLength: Int,
        lineLength: Int,
        consumer: FormatConsumer,
    ) {}

    override fun analysis_getImportedElements(
        file: String,
        offset: Int,
        length: Int,
        consumer: GetImportedElementsConsumer,
    ) {}

    override fun edit_importElements(
        file: String,
        elements: List<ImportedElements>,
        offset: Int,
        consumer: ImportElementsConsumer,
    ) {}

    override fun edit_getRefactoring(
        kind: String,
        file: String,
        offset: Int,
        length: Int,
        validateOnly: Boolean,
        options: RefactoringOptions?,
        consumer: GetRefactoringConsumer,
    ) {}

    override fun edit_organizeDirectives(
        file: String,
        consumer: OrganizeDirectivesConsumer,
    ) {}

    override fun edit_sortMembers(
        file: String,
        consumer: SortMembersConsumer,
    ) {}

    override fun analysis_reanalyze() {}

    override fun analysis_setPriorityFiles(files: List<String>) {}

    override fun analysis_setSubscriptions(subscriptions: Map<String, List<String>>) {}

    override fun execution_createContext(
        contextRoot: String,
        consumer: CreateContextConsumer,
    ) {}

    override fun execution_deleteContext(contextId: String) {}

    override fun execution_getSuggestions(
        code: String,
        offset: Int,
        contextFile: String,
        contextOffset: Int,
        variables: List<RuntimeCompletionVariable>,
        expressions: List<RuntimeCompletionExpression>?,
        consumer: GetRuntimeCompletionConsumer,
    ) {}

    override fun execution_mapUri(
        id: String,
        file: String?,
        uri: String?,
        consumer: MapUriConsumer,
    ) {}

    override fun server_setSubscriptions(subscriptions: List<String>) {}

    override fun analysis_updateOptions(options: AnalysisOptions) {}

    override fun server_setClientCapabilities(
        requests: List<String>,
        supportsUris: Boolean,
        supportsWorkspaceApplyEdits: Boolean,
    ) {}

    override fun server_shutdown() {
        synchronized(documentLock) {
            documentSync.clear()
        }
        lspClientConnectionManager.shutdown()
    }

    override fun generateUniqueId(): String = UUID.randomUUID().toString()

    override fun sendRequestToServer(
        id: String,
        request: JsonObject,
    ) {}

    override fun sendRequestToServer(
        id: String,
        request: JsonObject,
        consumer: Consumer,
    ) {}

    override fun lspMessage_dart_textDocumentContent(
        uri: String,
        consumer: DartLspTextDocumentContentConsumer,
    ) {}

    override fun lsp_connectToDtd(uri: String) {}

    private fun loadCurrentContent(fileUri: String): String? {
        val virtualFile = getDartFileInfo(project, fileUri).findFile() ?: return null
        return FileDocumentManager.getInstance().getDocument(virtualFile)?.text ?: VfsUtilCore.loadText(virtualFile)
    }

    private fun toWorkspaceFolder(uri: String): WorkspaceFolder {
        val name =
            getDartFileInfo(project, uri).findFile()?.name
                ?: StringUtil.trimEnd(uri.substringAfterLast('/'), '/')
        return WorkspaceFolder(uri, name)
    }

    private fun ensureWorkspaceFolderForFile(fileUri: String) {
        val virtualFile = getDartFileInfo(project, fileUri).findFile() ?: return
        val parent = virtualFile.parent ?: return
        val workspaceFolderUri = VfsUtilCore.pathToUrl(parent.path)
        lspClientConnectionManager.ensureWorkspaceFolderRegistered(workspaceFolderUri, ::toWorkspaceFolder)
    }
}
