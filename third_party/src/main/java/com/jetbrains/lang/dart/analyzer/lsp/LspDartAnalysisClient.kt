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
import com.google.dart.server.internal.BroadcastAnalysisServerListener
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiManager
import com.jetbrains.lang.dart.DartComponentType
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.DartClient
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import com.jetbrains.lang.dart.sdk.DartSdk
import org.dartlang.analysis.server.protocol.AddContentOverlay
import org.dartlang.analysis.server.protocol.AnalysisError
import org.dartlang.analysis.server.protocol.AnalysisErrorFixes
import org.dartlang.analysis.server.protocol.AnalysisOptions
import org.dartlang.analysis.server.protocol.ExtractMethodFeedback
import org.dartlang.analysis.server.protocol.ExtractMethodOptions
import org.dartlang.analysis.server.protocol.ExtractLocalVariableFeedback
import org.dartlang.analysis.server.protocol.ExtractLocalVariableOptions
import org.dartlang.analysis.server.protocol.ImportedElements
import org.dartlang.analysis.server.protocol.MoveFileOptions
import org.dartlang.analysis.server.protocol.RefactoringKind
import org.dartlang.analysis.server.protocol.RefactoringMethodParameter
import org.dartlang.analysis.server.protocol.RefactoringOptions
import org.dartlang.analysis.server.protocol.RefactoringProblem
import org.dartlang.analysis.server.protocol.RefactoringProblemSeverity
import org.dartlang.analysis.server.protocol.RemoveContentOverlay
import org.dartlang.analysis.server.protocol.RenameFeedback
import org.dartlang.analysis.server.protocol.RenameOptions
import org.dartlang.analysis.server.protocol.RequestError
import org.dartlang.analysis.server.protocol.RuntimeCompletionExpression
import org.dartlang.analysis.server.protocol.RuntimeCompletionVariable
import org.dartlang.analysis.server.protocol.SourceChange
import org.dartlang.analysis.server.protocol.SourceEdit
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
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
        private const val FIXES_RETRY_TIMEOUT_MS = 1_000L
        private const val FIXES_RETRY_INTERVAL_MS = 100L
        private const val RENAME_RETRY_TIMEOUT_MS = 1_000L
        private const val RENAME_RETRY_INTERVAL_MS = 100L
        private const val MOVE_FILE_RETRY_INTERVAL_MS = 100L
        private const val REFACTORING_COMMAND_TIMEOUT_MS = 5_000L
        private const val REFACTOR_VALIDATE_COMMAND = "refactor.validate"
        private const val REFACTOR_PERFORM_COMMAND = "refactor.perform"
        private val ASSIST_ACTION_KINDS = listOf(CodeActionKind.Refactor)
        private val FIX_ACTION_KINDS = listOf(CodeActionKind.QuickFix)
        private val SIMPLE_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }

    private data class CachedDiagnostic(
        val lspDiagnostic: Diagnostic,
        val analysisError: AnalysisError,
    )

    private data class DiagnosticKey(
        val startLine: Int?,
        val startCharacter: Int?,
        val endLine: Int?,
        val endCharacter: Int?,
        val severity: Int?,
        val code: String?,
        val source: String?,
        val message: String?,
    )

    private data class ValidateRefactorCommandResult(
        val valid: Boolean = false,
        val message: String? = null,
    )

    private data class PendingWorkspaceEditCapture(
        val future: java.util.concurrent.CompletableFuture<WorkspaceEdit>,
    )

    private data class RefactoringCommandContext(
        val content: String?,
        val filePath: String,
        val fileUri: String,
        val offset: Int,
        val length: Int,
    )

    private data class RenamePreparation(
        val feedback: RenameFeedback,
        val position: Position,
    )

    private data class RenameLocation(
        val originalOffset: Int,
        val originalLength: Int,
        val currentOffset: Int,
    )

    private data class RenameSyncState(
        val content: String?,
        val workspaceFolderUri: String?,
        val currentOffset: Int,
    )

    private val analysisServerListeners = BroadcastAnalysisServerListener()
    private val diagnosticConverter = LspDiagnosticConverter(project)
    private val lspClientConnectionManager =
        LspClientConnectionManager(project, sdk, suppressAnalytics, ::handlePublishDiagnostics, ::handleApplyEdit)
    private val sourceChangeConverter = LspSourceChangeConverter(project)
    private val documentLock = Any()
    private val diagnosticsByFileUri = mutableMapOf<String, List<CachedDiagnostic>>()
    private var pendingWorkspaceEditCapture: PendingWorkspaceEditCapture? = null
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

    override fun addAnalysisServerListener(listener: AnalysisServerListener) {
        analysisServerListeners.addListener(listener)
    }

    override fun removeAnalysisServerListener(listener: AnalysisServerListener) {
        analysisServerListeners.removeListener(listener)
    }

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
                files.forEach { (uri, overlay) ->
                    if (overlay is RemoveContentOverlay) {
                        diagnosticsByFileUri.remove(uri)
                    }
                    documentSync.applyOverlay(uri, overlay)
                }
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

    private fun handlePublishDiagnostics(params: PublishDiagnosticsParams) {
        val fileUri = params.uri ?: return
        val diagnostics =
            ReadAction.compute<List<CachedDiagnostic>, RuntimeException> {
                synchronized(documentLock) {
                    diagnosticConverter
                        .toConvertedDiagnostics(params)
                        .map { convertedDiagnostic ->
                            CachedDiagnostic(convertedDiagnostic.diagnostic, convertedDiagnostic.analysisError)
                        }.also { diagnosticsByFileUri[fileUri] = it }
                }
            }
        val errors = diagnostics.map(CachedDiagnostic::analysisError)
        analysisServerListeners.computedErrors(fileUri, errors)
    }

    private fun handleApplyEdit(params: ApplyWorkspaceEditParams): ApplyWorkspaceEditResponse {
        val capture =
            synchronized(documentLock) {
                pendingWorkspaceEditCapture
            }

        val edit = params.edit
        if (capture == null || edit == null) {
            return ApplyWorkspaceEditResponse(false).apply {
                failureReason = "No active LSP refactoring command is waiting for a workspace edit"
            }
        }

        capture.future.complete(edit)
        return ApplyWorkspaceEditResponse(true)
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
        return ASSIST_ACTION_KINDS.any { kind == it || kind.startsWith("$it.") }
    }

    private fun toAnalysisErrorFixes(
        matchingDiagnostics: List<CachedDiagnostic>,
        actionResults: List<Either<Command, CodeAction>>,
    ): List<AnalysisErrorFixes> {
        val fixesByDiagnostic = LinkedHashMap<CachedDiagnostic, MutableList<SourceChange>>()
        matchingDiagnostics.forEach { diagnostic ->
            fixesByDiagnostic[diagnostic] = mutableListOf()
        }

        actionResults.forEach { actionResult ->
            val sourceChange = toFixSourceChange(actionResult) ?: return@forEach
            val diagnostics = associatedDiagnostics(actionResult, matchingDiagnostics)
            diagnostics.forEach { diagnostic ->
                fixesByDiagnostic.getOrPut(diagnostic, ::mutableListOf).add(sourceChange)
            }
        }

        return fixesByDiagnostic.mapNotNull { (diagnostic, fixes) ->
            if (fixes.isEmpty()) {
                null
            } else {
                AnalysisErrorFixes(diagnostic.analysisError, fixes)
            }
        }
    }

    private fun toFixSourceChange(actionResult: Either<Command, CodeAction>): SourceChange? {
        if (actionResult.isLeft) {
            val command = actionResult.left ?: return null
            val workspaceEdit = extractWorkspaceEdit(command) ?: return null
            return sourceChangeConverter.toSourceChange(command.title, command.command, workspaceEdit)
        }

        val action = actionResult.right ?: return null
        if (!isFixAction(action)) {
            return null
        }

        val workspaceEdit = extractWorkspaceEdit(action) ?: return null
        if (!sourceChangeConverter.hasTranslatableChanges(workspaceEdit)) {
            return null
        }

        return sourceChangeConverter.toSourceChange(action, workspaceEdit)
    }

    private fun isFixAction(action: CodeAction): Boolean {
        if (action.disabled != null) {
            return false
        }
        val kind = action.kind ?: return true
        return FIX_ACTION_KINDS.any { kind == it || kind.startsWith("$it.") }
    }

    private fun associatedDiagnostics(
        actionResult: Either<Command, CodeAction>,
        matchingDiagnostics: List<CachedDiagnostic>,
    ): List<CachedDiagnostic> {
        if (matchingDiagnostics.size <= 1) {
            // If only one diagnostic matches the requested offset, attribute the returned fix to it even if
            // the server omits CodeAction.diagnostics. That preserves the existing one-error/one-fix UX.
            return matchingDiagnostics
        }

        val action = actionResult.takeIf(Either<Command, CodeAction>::isRight)?.right ?: return matchingDiagnostics
        val actionDiagnostics = action.diagnostics.orEmpty()
        if (actionDiagnostics.isEmpty()) {
            // Some servers omit the per-action diagnostic list. Fall back to all matching diagnostics so
            // fix actions are still surfaced instead of being dropped on the floor.
            return matchingDiagnostics
        }

        val matchingDiagnosticsByKey = matchingDiagnostics.associateBy { diagnostic -> diagnostic.key() }
        return actionDiagnostics.mapNotNull { diagnostic ->
            matchingDiagnosticsByKey[diagnostic.key()]
        }
    }

    private fun CachedDiagnostic.matchesOffset(offset: Int): Boolean {
        val startOffset = analysisError.location.offset
        val length = analysisError.location.length
        if (length <= 0) {
            return offset == startOffset
        }
        return offset in startOffset until (startOffset + length)
    }

    private fun CachedDiagnostic.key(): DiagnosticKey = lspDiagnostic.key()

    private fun Diagnostic.key(): DiagnosticKey =
        DiagnosticKey(
            range?.start?.line,
            range?.start?.character,
            range?.end?.line,
            range?.end?.character,
            severity?.value,
            getCodeAsString(),
            source,
            message,
        )

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
    ) {
        val matchingDiagnostics =
            try {
                synchronized(documentLock) {
                    ensureWorkspaceFolderForFile(file)
                    loadCurrentContent(file)?.let { content ->
                        documentSync.applyOverlay(file, AddContentOverlay(content))
                    }
                    diagnosticsByFileUri[file].orEmpty().filter { it.matchesOffset(offset) }
                }
            } catch (t: Throwable) {
                consumer.onError(toRequestError(t))
                return
            }

        if (matchingDiagnostics.isEmpty()) {
            consumer.computedFixes(emptyList())
            return
        }

        val params =
            try {
                CodeActionParams(
                    TextDocumentIdentifier(file),
                    sourceChangeConverter.toRange(file, offset, 0),
                    CodeActionContext(matchingDiagnostics.map(CachedDiagnostic::lspDiagnostic)).apply {
                        only = FIX_ACTION_KINDS
                        triggerKind = CodeActionTriggerKind.Invoked
                    },
                )
            } catch (t: Throwable) {
                consumer.onError(toRequestError(t))
                return
            }

        try {
            val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FIXES_RETRY_TIMEOUT_MS)
            while (true) {
                val actions = lspClientConnectionManager.codeAction(params).get(FIXES_RETRY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                val fixes = toAnalysisErrorFixes(matchingDiagnostics, actions.orEmpty())
                if (fixes.isNotEmpty() || System.nanoTime() >= deadlineNs) {
                    consumer.computedFixes(fixes)
                    return
                }
                Thread.sleep(FIXES_RETRY_INTERVAL_MS)
            }
        } catch (t: Throwable) {
            if (t is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            consumer.onError(toRequestError(t))
        }
    }

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
    ) {
        when (kind) {
            RefactoringKind.MOVE_FILE -> {
                edit_getMoveFileRefactoring(file, validateOnly, options, consumer)
            }
            RefactoringKind.RENAME -> {
                edit_getRenameRefactoring(file, offset, validateOnly, options, consumer)
            }
            RefactoringKind.EXTRACT_METHOD,
            RefactoringKind.EXTRACT_LOCAL_VARIABLE,
            RefactoringKind.EXTRACT_WIDGET,
            RefactoringKind.CONVERT_GETTER_TO_METHOD,
            RefactoringKind.CONVERT_METHOD_TO_GETTER,
            RefactoringKind.INLINE_LOCAL_VARIABLE,
            RefactoringKind.INLINE_METHOD -> {
                edit_getCommandBackedRefactoring(kind, file, offset, length, validateOnly, options, consumer)
            }
            else -> {
                reportUnsupportedRefactoring(kind, validateOnly, consumer)
            }
        }
    }

    private fun edit_getMoveFileRefactoring(
        file: String,
        validateOnly: Boolean,
        options: RefactoringOptions?,
        consumer: GetRefactoringConsumer,
    ) {
        val moveOptions = options as? MoveFileOptions
        val newFileUri = moveOptions?.newFile
        if (newFileUri.isNullOrBlank()) {
            reportRefactoringOptionsProblem(consumer, null, "A target file path must be provided for move file refactoring.")
            return
        }

        try {
            synchronized(documentLock) {
                ensureWorkspaceFolderForFile(file)
                workspaceFolderUriForTargetFile(newFileUri)?.let { workspaceFolderUri ->
                    lspClientConnectionManager.ensureWorkspaceFolderRegistered(workspaceFolderUri, ::toWorkspaceFolder)
                }
            }
            val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REFACTORING_COMMAND_TIMEOUT_MS)
            var workspaceEdit: WorkspaceEdit? = null
            while (true) {
                workspaceEdit =
                    lspClientConnectionManager
                        .willRenameFiles(listOf(FileRename(file, newFileUri)))
                        .get(REFACTORING_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                if (workspaceEdit != null || System.nanoTime() >= deadlineNs) {
                    break
                }

                Thread.sleep(MOVE_FILE_RETRY_INTERVAL_MS)
            }

            if (validateOnly) {
                consumer.computedRefactorings(emptyList(), emptyList(), emptyList(), null, null, emptyList())
                return
            }

            val sourceChange =
                workspaceEdit?.let { edit ->
                    ReadAction.compute<SourceChange?, RuntimeException> {
                        sourceChangeConverter.toSourceChange(
                            "Move File",
                            "workspace/willRenameFiles",
                            edit,
                        )
                    }
                } ?: emptySourceChange("Move File", "workspace/willRenameFiles")

            consumer.computedRefactorings(emptyList(), emptyList(), emptyList(), null, sourceChange, emptyList())
        } catch (t: Throwable) {
            if (t is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            reportRefactoringProblem(
                RefactoringKind.MOVE_FILE,
                validateOnly,
                consumer,
                unwrapCause(t).message ?: "Move file refactoring failed.",
            )
        }
    }

    private fun edit_getRenameRefactoring(
        file: String,
        offset: Int,
        validateOnly: Boolean,
        options: RefactoringOptions?,
        consumer: GetRefactoringConsumer,
    ) {
        val result = computeRenameRefactoring(file, offset, validateOnly, (options as? RenameOptions)?.getNewName())
        consumer.computedRefactorings(
            result.initialProblems,
            result.optionsProblems,
            result.finalProblems,
            result.feedback,
            result.change,
            result.potentialEdits,
        )
    }

    fun computeRenameRefactoring(
        file: String,
        offset: Int,
        validateOnly: Boolean,
        newName: String?,
    ): LspRenameRefactoringResult {
        val syncState: RenameSyncState
        try {
            syncState =
                ReadAction.compute<RenameSyncState, RuntimeException> {
                    val virtualFile = getDartFileInfo(project, file).findFile()
                    val service = DartAnalysisServerService.getInstance(project)
                    RenameSyncState(
                        loadCurrentContent(file),
                        workspaceFolderUriForFile(file),
                        if (virtualFile != null) service.getConvertedOffset(virtualFile, offset) else offset,
                    )
                }
            synchronized(documentLock) {
                syncState.workspaceFolderUri?.let { workspaceFolderUri ->
                    lspClientConnectionManager.ensureWorkspaceFolderRegistered(workspaceFolderUri, ::toWorkspaceFolder)
                }
                syncState.content?.let { content ->
                    documentSync.applyOverlay(file, AddContentOverlay(content))
                }
            }
        } catch (t: Throwable) {
            throw asRefactoringFailure(t)
        }

        try {
            val preparation = awaitRenamePreparation(file, offset, syncState.currentOffset, syncState.content)
            if (preparation == null) {
                return problemRenameResult(
                    validateOnly,
                    "LSP refactoring '${RefactoringKind.RENAME}' is not available at the current selection.",
                )
            }

            val feedback = preparation.feedback
            if (validateOnly && newName == null) {
                return LspRenameRefactoringResult(feedback = feedback)
            }

            if (newName.isNullOrBlank()) {
                return optionsProblemRenameResult(
                    feedback,
                    "A new name must be provided for rename refactoring.",
                )
            }

            val workspaceEdit =
                // LSP has no separate "validate this rename target" endpoint. A real rename request is the
                // only way to let the server reject invalid names while still preserving the legacy
                // checkInitialConditions/checkFinalConditions flow.
                lspClientConnectionManager
                    .rename(RenameParams(TextDocumentIdentifier(file), preparation.position, newName))
                    .get(REFACTORING_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            if (validateOnly) {
                return LspRenameRefactoringResult(feedback = feedback)
            }

            val sourceChange =
                workspaceEdit?.let { edit ->
                    ReadAction.compute<SourceChange?, RuntimeException> {
                        sourceChangeConverter.toSourceChange(
                            "Rename",
                            "textDocument/rename",
                            edit,
                        )
                    }
                }

            if (sourceChange == null) {
                return problemRenameResult(false, "LSP refactoring '${RefactoringKind.RENAME}' is not available at the current selection.")
            }

            return LspRenameRefactoringResult(
                feedback = feedback,
                change = sourceChange,
            )
        } catch (t: Throwable) {
            val message = unwrapCause(t).message ?: "Rename refactoring failed."
            return if (validateOnly) optionsProblemRenameResult(null, message) else problemRenameResult(false, message)
        }
    }

    private fun edit_getCommandBackedRefactoring(
        kind: String,
        file: String,
        offset: Int,
        length: Int,
        validateOnly: Boolean,
        options: RefactoringOptions?,
        consumer: GetRefactoringConsumer,
    ) {
        var filePath = VfsUtilCore.urlToPath(file)
        var docVersion: Int? = null
        var currentContent: String? = null
        var currentOffset = offset
        var currentLength = length
        try {
            val fileState =
                ReadAction.compute<RefactoringCommandContext, RuntimeException> {
                    val virtualFile = getDartFileInfo(project, file).findFile()
                    val resolvedPath =
                        virtualFile?.path
                            ?: VfsUtilCore.urlToPath(file)
                    val service = DartAnalysisServerService.getInstance(project)
                    val convertedOffset = if (virtualFile != null) service.getConvertedOffset(virtualFile, offset) else offset
                    val convertedEnd = if (virtualFile != null) service.getConvertedOffset(virtualFile, offset + length) else (offset + length)
                    RefactoringCommandContext(loadCurrentContent(file), resolvedPath, file, convertedOffset, convertedEnd - convertedOffset)
                }
            currentContent = fileState.content
            filePath = fileState.filePath
            currentOffset = fileState.offset
            currentLength = fileState.length

            synchronized(documentLock) {
                ensureWorkspaceFolderForFile(fileState.fileUri)
                currentContent?.let { content ->
                    documentSync.applyOverlay(file, AddContentOverlay(content))
                }
                docVersion = documentSync.documentVersion(file)
            }
        } catch (t: Throwable) {
            consumer.onError(toRequestError(t))
            return
        }

        try {
            if (
                kind == RefactoringKind.EXTRACT_METHOD &&
                options is ExtractMethodOptions &&
                (options.createGetter() || (options.extractAll() && hasMultipleSelectedOccurrences(currentContent, currentOffset, currentLength)))
            ) {
                reportRefactoringProblem(
                    kind,
                    validateOnly,
                    consumer,
                    "LSP refactoring '$kind' does not support extract-all or getter mode yet.",
                )
                return
            }
            val suggestedName =
                when (kind) {
                    RefactoringKind.EXTRACT_LOCAL_VARIABLE -> suggestExtractLocalName(currentContent, currentOffset, currentLength)
                    RefactoringKind.EXTRACT_METHOD -> suggestExtractMethodName(options as? ExtractMethodOptions)
                    else -> null
                }
            val commandOptions = toRefactoringCommandOptions(options, suggestedName)
            if (
                kind == RefactoringKind.EXTRACT_LOCAL_VARIABLE &&
                !validateOnly &&
                options is ExtractLocalVariableOptions &&
                options.extractAll() &&
                hasMultipleSelectedOccurrences(currentContent, currentOffset, currentLength)
            ) {
                // The current Dart LSP refactor.perform handler always performs single-occurrence
                // extraction for local variables, so fail explicitly rather than returning a
                // misleading partial refactoring.
                reportRefactoringProblem(
                    kind,
                    false,
                    consumer,
                    "LSP refactoring '$kind' does not support replacing all occurrences yet.",
                )
                return
            }
            val validationResult =
                executeValidateRefactorCommand(
                    kind,
                    filePath,
                    docVersion,
                    currentOffset,
                    currentLength,
                    commandOptions,
                )

            if (!validationResult.valid) {
                reportRefactoringProblem(
                    kind,
                    validateOnly,
                    consumer,
                    validationResult.message ?: "LSP refactoring '$kind' is not available at the current selection.",
                )
                return
            }

            val sourceChange =
                if (validateOnly) {
                    null
                } else {
                    executePerformRefactorCommand(
                        kind,
                        filePath,
                        docVersion,
                        currentOffset,
                        currentLength,
                        commandOptions,
                    )?.let { workspaceEdit ->
                        ReadAction.compute<SourceChange?, RuntimeException> {
                            sourceChangeConverter.toSourceChange(
                                "Perform Refactor",
                                REFACTOR_PERFORM_COMMAND,
                                workspaceEdit,
                            )
                        }
                    }
                }

            if (!validateOnly && sourceChange == null) {
                reportUnavailableRefactoring(kind, false, consumer)
                return
            }

            val feedback =
                when (kind) {
                    RefactoringKind.EXTRACT_LOCAL_VARIABLE ->
                        toExtractLocalVariableFeedback(offset, length, options as? ExtractLocalVariableOptions, sourceChange, suggestedName)
                    RefactoringKind.EXTRACT_METHOD ->
                        toExtractMethodFeedback(offset, length, options as? ExtractMethodOptions, sourceChange, suggestedName)
                    else -> null
                }

            consumer.computedRefactorings(emptyList(), emptyList(), emptyList(), feedback, sourceChange, emptyList())
        } catch (t: Throwable) {
            if (t is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            LOG.warn("LSP refactoring command failed for kind=$kind at $filePath:$currentOffset+$currentLength", t)
            reportRefactoringProblem(
                kind,
                validateOnly,
                consumer,
                unwrapCause(t).message ?: "Refactoring '$kind' failed.",
            )
        }
    }

    private fun awaitRenamePreparation(
        fileUri: String,
        originalOffset: Int,
        currentOffset: Int,
        currentContent: String?,
    ): RenamePreparation? {
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RENAME_RETRY_TIMEOUT_MS)
        var lastFailure: Throwable? = null
        while (true) {
            try {
                prepareRename(fileUri, originalOffset, currentOffset, currentContent)?.let { return it }
            } catch (t: Throwable) {
                lastFailure = t
            }
            if (System.nanoTime() >= deadlineNs) {
                if (lastFailure != null) {
                    throw lastFailure
                }
                return null
            }
            Thread.sleep(RENAME_RETRY_INTERVAL_MS)
        }
    }

    private fun executeValidateRefactorCommand(
        kind: String,
        filePath: String,
        docVersion: Int?,
        offset: Int,
        length: Int,
        options: Map<String, Any?>?,
    ): ValidateRefactorCommandResult {
        val result =
            lspClientConnectionManager
                .executeCommand(
                    REFACTOR_VALIDATE_COMMAND,
                    listOf(kind, filePath, docVersion, offset, length, options),
                ).get(REFACTORING_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        return when (result) {
            is ValidateRefactorCommandResult -> result
            else -> GSON.fromJson(GSON.toJsonTree(result), ValidateRefactorCommandResult::class.java)
        } ?: ValidateRefactorCommandResult(false, null)
    }

    private fun prepareRename(
        fileUri: String,
        originalOffset: Int,
        currentOffset: Int,
        currentContent: String?,
    ): RenamePreparation? {
        val position = toPosition(currentContent, currentOffset)
        val prepareResult =
            lspClientConnectionManager
                .prepareRename(PrepareRenameParams(TextDocumentIdentifier(fileUri), position))
                .get(REFACTORING_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        val rangeAndPlaceholder = toRenameRangeAndPlaceholder(prepareResult, position, currentContent, currentOffset) ?: return null
        val feedback = toRenameFeedback(fileUri, rangeAndPlaceholder.first, rangeAndPlaceholder.second, originalOffset, currentOffset)
        return RenamePreparation(feedback, position)
    }

    private fun toRenameRangeAndPlaceholder(
        prepareResult: Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>?,
        fallbackPosition: Position,
        currentContent: String?,
        currentOffset: Int,
    ): Pair<Range, String>? {
        prepareResult ?: return null
        return when {
            prepareResult.isFirst -> {
                val range = prepareResult.first ?: return null
                range to extractRenamePlaceholder(range, currentContent, currentOffset)
            }
            prepareResult.isSecond -> {
                val result = prepareResult.second ?: return null
                result.range to result.placeholder
            }
            prepareResult.isThird -> {
                val range = Range(fallbackPosition, fallbackPosition)
                range to extractRenamePlaceholder(range, currentContent, currentOffset)
            }
            else -> null
        }
    }

    private fun extractRenamePlaceholder(
        range: Range,
        currentContent: String?,
        currentOffset: Int,
    ): String {
        val text = currentContent ?: return "name"
        val currentRange = toCurrentOffsets(text, range)
        if (currentRange == null) {
            val fallbackText = text.substring(currentOffset.coerceAtMost(text.length))
            return SIMPLE_IDENTIFIER.find(fallbackText)?.value ?: "name"
        }
        return text.substring(currentRange.first, currentRange.second).takeIf(String::isNotEmpty) ?: "name"
    }

    private fun toRenameFeedback(
        fileUri: String,
        currentRange: Range,
        placeholder: String,
        originalOffsetFallback: Int,
        currentOffset: Int,
    ): RenameFeedback {
        val renameLocation =
            ReadAction.compute<RenameLocation, RuntimeException> {
                val virtualFile = getDartFileInfo(project, fileUri).findFile()
                val currentOffsets = toCurrentOffsets(loadCurrentContent(fileUri) ?: "", currentRange)
                if (virtualFile == null || currentOffsets == null) {
                    RenameLocation(originalOffsetFallback, placeholder.length, currentOffset)
                } else {
                    val service = DartAnalysisServerService.getInstance(project)
                    val originalStart = service.getOriginalOffset(virtualFile, currentOffsets.first)
                    val originalEnd = service.getOriginalOffset(virtualFile, currentOffsets.second)
                    RenameLocation(originalStart, originalEnd - originalStart, currentOffsets.first)
                }
            }
        val elementKindName = detectRenameElementKindName(fileUri, renameLocation.currentOffset)
        return RenameFeedback(renameLocation.originalOffset, renameLocation.originalLength, elementKindName, placeholder)
    }

    private fun detectRenameElementKindName(
        fileUri: String,
        currentOffset: Int,
    ): String {
        return ReadAction.compute<String, RuntimeException> {
            val virtualFile = getDartFileInfo(project, fileUri).findFile() ?: return@compute "element"
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@compute "element"
            val elementAtOffset =
                psiFile.findElementAt(currentOffset)
                    ?: currentOffset.takeIf { it > 0 }?.let { psiFile.findElementAt(it - 1) }
            DartComponentType.typeOf(elementAtOffset)?.usageType ?: "element"
        }
    }

    private fun toCurrentOffsets(
        content: String,
        range: Range,
    ): Pair<Int, Int>? {
        val start = getOffset(content, range.start) ?: return null
        val end = getOffset(content, range.end) ?: return null
        return start to end
    }

    private fun getOffset(
        content: String,
        position: Position?,
    ): Int? {
        if (position == null) return null
        if (position.line < 0 || position.character < 0) return null
        var currentLine = 0
        var lineStart = 0
        var index = 0
        while (currentLine < position.line && index < content.length) {
            when (content[index]) {
                '\n' -> {
                    currentLine++
                    lineStart = index + 1
                }
                '\r' -> {
                    currentLine++
                    lineStart = index + 1
                    if (index + 1 < content.length && content[index + 1] == '\n') {
                        index++
                        lineStart = index + 1
                    }
                }
            }
            index++
        }
        if (currentLine != position.line) return null
        var lineEnd = lineStart
        while (lineEnd < content.length && content[lineEnd] != '\n' && content[lineEnd] != '\r') {
            lineEnd++
        }
        return (lineStart + position.character).coerceAtMost(lineEnd)
    }

    private fun toPosition(
        content: String?,
        offset: Int,
    ): Position {
        val safeContent = content.orEmpty()
        val clampedOffset = offset.coerceIn(0, safeContent.length)
        var line = 0
        var lineStart = 0
        var index = 0
        while (index < clampedOffset) {
            when (safeContent[index]) {
                '\n' -> {
                    line++
                    lineStart = index + 1
                }
                '\r' -> {
                    line++
                    lineStart = index + 1
                    if (index + 1 < clampedOffset && safeContent[index + 1] == '\n') {
                        index++
                        lineStart = index + 1
                    }
                }
            }
            index++
        }
        return Position(line, clampedOffset - lineStart)
    }

    private fun executePerformRefactorCommand(
        kind: String,
        filePath: String,
        docVersion: Int?,
        offset: Int,
        length: Int,
        options: Map<String, Any?>?,
    ): WorkspaceEdit? {
        val capture = PendingWorkspaceEditCapture(java.util.concurrent.CompletableFuture())
        synchronized(documentLock) {
            pendingWorkspaceEditCapture = capture
        }

        try {
            lspClientConnectionManager
                .executeCommand(
                    REFACTOR_PERFORM_COMMAND,
                    listOf(kind, filePath, docVersion, offset, length, options),
                ).get(REFACTORING_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            return capture.future.get(REFACTORING_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            synchronized(documentLock) {
                if (pendingWorkspaceEditCapture === capture) {
                    pendingWorkspaceEditCapture = null
                }
            }
        }
    }

    private fun toRefactoringCommandOptions(
        options: RefactoringOptions?,
        suggestedName: String? = null,
    ): Map<String, Any?>? {
        if (options == null) {
            return null
        }

        return when (options) {
            is ExtractMethodOptions -> {
                val mappedOptions =
                    linkedMapOf<String, Any?>(
                        "extractAll" to options.extractAll(),
                        "createGetter" to options.createGetter(),
                        "parameters" to options.getParameters(),
                        "returnType" to options.getReturnType(),
                    )
                val requestedName = options.getName().takeUnless(StringUtil::isEmptyOrSpaces)
                val refactoringName =
                    if (requestedName != null && requestedName != "name") {
                        requestedName
                    } else {
                        suggestedName
                    }
                if (refactoringName != null) {
                    mappedOptions["name"] = refactoringName
                }
                mappedOptions
            }
            is ExtractLocalVariableOptions -> {
                val mappedOptions = linkedMapOf<String, Any?>("extractAll" to options.extractAll())
                val requestedName = options.getName().takeUnless(StringUtil::isEmptyOrSpaces)
                val refactoringName =
                    if (requestedName != null && requestedName != "name") {
                        requestedName
                    } else {
                        suggestedName
                    }
                if (refactoringName != null) {
                    mappedOptions["name"] = refactoringName
                }
                mappedOptions
            }
            else -> {
                @Suppress("UNCHECKED_CAST")
                GSON.fromJson(options.toJson(), LinkedHashMap::class.java) as? Map<String, Any?>
            }
        }
    }

    private fun extractLocalOccurrenceEdits(change: SourceChange): List<SourceEdit> {
        val candidateEdits =
            change.edits
                .flatMap { it.edits }
                .filter { edit ->
                    edit.length > 0 &&
                        !edit.replacement.isNullOrBlank() &&
                        SIMPLE_IDENTIFIER.matches(edit.replacement)
                }
        if (candidateEdits.isEmpty()) {
            return emptyList()
        }

        return candidateEdits
            .groupBy(SourceEdit::getReplacement)
            .maxByOrNull { (_, edits) -> edits.size }
            ?.value
            ?.sortedBy(SourceEdit::getOffset)
            .orEmpty()
    }

    private fun extractMethodInvocationEdits(change: SourceChange): List<SourceEdit> {
        val methodName = change.edits.flatMap { it.edits }.mapNotNull { edit -> SIMPLE_IDENTIFIER.find(edit.replacement)?.value }.firstOrNull()
        val candidateEdits =
            change.edits
                .flatMap { it.edits }
                .filter { edit ->
                    edit.length > 0 &&
                        !edit.replacement.isNullOrBlank() &&
                        methodName != null &&
                        edit.replacement.contains(methodName)
                }
        return candidateEdits.sortedBy(SourceEdit::getOffset)
    }

    private fun extractLocalSuggestedNames(
        change: SourceChange,
        occurrenceEdits: List<SourceEdit>,
    ): List<String> {
        val linkedNames =
            change.linkedEditGroups
                .asSequence()
                .flatMap { it.suggestions.asSequence() }
                .map { it.value }
                .filter(String::isNotBlank)
                .distinct()
                .toList()
        if (linkedNames.isNotEmpty()) {
            return linkedNames
        }

        val replacementNames = occurrenceEdits.map(SourceEdit::getReplacement).filter(String::isNotBlank).distinct()
        if (replacementNames.isNotEmpty()) {
            return replacementNames
        }

        return listOf("name")
    }

    private fun toExtractLocalVariableFeedback(
        offset: Int,
        length: Int,
        options: ExtractLocalVariableOptions?,
        change: SourceChange?,
        suggestedName: String?,
    ): ExtractLocalVariableFeedback {
        val occurrenceEdits = change?.let(::extractLocalOccurrenceEdits).orEmpty()
        val names =
            if (change != null && occurrenceEdits.isNotEmpty()) {
                extractLocalSuggestedNames(change, occurrenceEdits)
            } else {
                listOf(
                    options?.getName().takeUnless(StringUtil::isEmptyOrSpaces)?.takeUnless { it == "name" }
                        ?: suggestedName
                        ?: "newVariable",
                )
            }
        val offsets = occurrenceEdits.map(SourceEdit::getOffset).ifEmpty { listOf(offset) }
        val lengths = occurrenceEdits.map(SourceEdit::getLength).ifEmpty { listOf(length) }
        return ExtractLocalVariableFeedback(
            intArrayOf(offset),
            intArrayOf(length),
            names,
            offsets.toIntArray(),
            lengths.toIntArray(),
        )
    }

    private fun toExtractMethodFeedback(
        offset: Int,
        length: Int,
        options: ExtractMethodOptions?,
        change: SourceChange?,
        suggestedName: String?,
    ): ExtractMethodFeedback {
        val methodName =
            options?.getName().takeUnless(StringUtil::isEmptyOrSpaces)?.takeUnless { it == "name" }
                ?: suggestedName
                ?: "newMethod"
        val invocationEdits = change?.let(::extractMethodInvocationEdits).orEmpty()
        val offsets = invocationEdits.map(SourceEdit::getOffset).ifEmpty { listOf(offset) }
        val lengths = invocationEdits.map(SourceEdit::getLength).ifEmpty { listOf(length) }
        return ExtractMethodFeedback(
            offset,
            length,
            options?.getReturnType() ?: "",
            listOf(methodName),
            options?.createGetter() ?: false,
            options?.getParameters() ?: emptyList<RefactoringMethodParameter>(),
            offsets.toIntArray(),
            lengths.toIntArray(),
        )
    }

    private fun suggestExtractLocalName(
        currentContent: String?,
        offset: Int,
        length: Int,
    ): String? {
        if (currentContent == null || length <= 0 || offset < 0 || offset + length > currentContent.length) {
            return null
        }

        val selectedText = currentContent.substring(offset, offset + length).trim()
        val identifiers = SIMPLE_IDENTIFIER.findAll(selectedText).map { it.value }.toList()
        return identifiers.lastOrNull()
    }

    private fun suggestExtractMethodName(options: ExtractMethodOptions?): String? {
        val requestedName = options?.getName()?.takeUnless(StringUtil::isEmptyOrSpaces)
        return requestedName?.takeUnless { it == "name" } ?: "newMethod"
    }

    private fun hasMultipleSelectedOccurrences(
        currentContent: String?,
        offset: Int,
        length: Int,
    ): Boolean {
        if (currentContent == null || length <= 0 || offset < 0 || offset + length > currentContent.length) {
            return false
        }

        val selectedText = currentContent.substring(offset, offset + length)
        if (selectedText.isEmpty()) {
            return false
        }

        var occurrences = 0
        var searchFrom = 0
        while (true) {
            val matchOffset = currentContent.indexOf(selectedText, searchFrom)
            if (matchOffset < 0) {
                return false
            }

            occurrences++
            if (occurrences > 1) {
                return true
            }

            searchFrom = matchOffset + selectedText.length
        }
    }

    private fun reportUnsupportedRefactoring(
        kind: String,
        validateOnly: Boolean,
        consumer: GetRefactoringConsumer,
    ) {
        reportRefactoringProblem(
            kind,
            validateOnly,
            consumer,
            "LSP refactoring '$kind' is not implemented yet.",
        )
    }

    private fun reportUnavailableRefactoring(
        kind: String,
        validateOnly: Boolean,
        consumer: GetRefactoringConsumer,
    ) {
        reportRefactoringProblem(
            kind,
            validateOnly,
            consumer,
            "LSP refactoring '$kind' is not available at the current selection.",
        )
    }

    private fun reportRefactoringProblem(
        kind: String,
        validateOnly: Boolean,
        consumer: GetRefactoringConsumer,
        message: String,
    ) {
        val problem = RefactoringProblem(RefactoringProblemSeverity.FATAL, message, null)
        val initialProblems = if (validateOnly) listOf(problem) else emptyList()
        val finalProblems = if (validateOnly) emptyList() else listOf(problem)
        consumer.computedRefactorings(initialProblems, emptyList(), finalProblems, null, null, emptyList())
    }

    private fun reportRefactoringOptionsProblem(
        consumer: GetRefactoringConsumer,
        feedback: org.dartlang.analysis.server.protocol.RefactoringFeedback?,
        message: String,
    ) {
        val problem = RefactoringProblem(RefactoringProblemSeverity.FATAL, message, null)
        consumer.computedRefactorings(emptyList(), listOf(problem), emptyList(), feedback, null, emptyList())
    }

    private fun problemRenameResult(
        validateOnly: Boolean,
        message: String,
    ): LspRenameRefactoringResult {
        val problem = RefactoringProblem(RefactoringProblemSeverity.FATAL, message, null)
        val initialProblems = if (validateOnly) listOf(problem) else emptyList()
        val finalProblems = if (validateOnly) emptyList() else listOf(problem)
        return LspRenameRefactoringResult(
            initialProblems = initialProblems,
            finalProblems = finalProblems,
        )
    }

    private fun optionsProblemRenameResult(
        feedback: RenameFeedback?,
        message: String,
    ): LspRenameRefactoringResult {
        val problem = RefactoringProblem(RefactoringProblemSeverity.FATAL, message, null)
        return LspRenameRefactoringResult(
            optionsProblems = listOf(problem),
            feedback = feedback,
        )
    }

    private fun asRefactoringFailure(t: Throwable): RuntimeException {
        return when (t) {
            is RuntimeException -> t
            else -> RuntimeException(t)
        }
    }

    private fun emptySourceChange(
        message: String,
        id: String?,
    ): SourceChange = SourceChange(message, emptyList(), emptyList(), null, null, id)

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
            diagnosticsByFileUri.clear()
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
        val workspaceFolderUri = workspaceFolderUriForFile(fileUri) ?: return
        lspClientConnectionManager.ensureWorkspaceFolderRegistered(workspaceFolderUri, ::toWorkspaceFolder)
    }

    private fun workspaceFolderUriForFile(fileUri: String): String? {
        val virtualFile = getDartFileInfo(project, fileUri).findFile() ?: return null
        val parent = virtualFile.parent ?: return null
        return VfsUtilCore.pathToUrl(parent.path)
    }

    private fun workspaceFolderUriForTargetFile(fileUri: String): String? {
        val filePath = VfsUtilCore.urlToPath(fileUri)
        if (filePath.isBlank()) return null
        val parentPath = PathUtil.getParentPath(filePath)
        if (parentPath.isBlank()) return null
        return VfsUtilCore.pathToUrl(parentPath)
    }
}
