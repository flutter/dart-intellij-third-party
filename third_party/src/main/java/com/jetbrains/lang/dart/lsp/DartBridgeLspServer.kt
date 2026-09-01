/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.dart.server.ResponseListener
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.logging.PluginLogger
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.CompletionParams
import org.dartlang.analysis.server.protocol.AnalysisError
import org.dartlang.analysis.server.protocol.DiagnosticMessage
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileOperationOptions
import org.eclipse.lsp4j.FileOperationPattern
import org.eclipse.lsp4j.FileOperationsServerCapabilities
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.lang.reflect.Type
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * DartBridgeLspServer acts as a lightweight translation bridge between the JetBrains LSP client
 * (which expects a standard LSP server) and the legacy Dart Analysis Server (DAS) running in legacy mode.
 *
 * ## Request Flow
 * When the JetBrains client sends an LSP request (e.g., [hover]), the bridge intercepts it,
 * wraps it in a legacy DAS `lsp.handle` request, and forwards it to the existing DAS instance.
 * When DAS responds, the bridge extracts the nested LSP response and completes the future returned to the client.
 *
 * ## Migration Strategy: Shared vs LSP-Only Handlers
 * DAS contains two sets of LSP handlers:
 * 1. **Shared Handlers** (e.g., Hover, Formatting): These are registered in DAS even when running in legacy mode.
 *    We can forward these requests directly to DAS via `lsp.handle`.
 * 2. **LSP-Only Handlers** (e.g., Go to Definition, Completion): These are NOT registered in legacy DAS.
 *    If we forward them, DAS will return `MethodNotFound`. To support these, the bridge must manually
 *    intercept the LSP request, translate it to a legacy DAS request (e.g., `analysis.getNavigation` for Definition),
 *    and translate the legacy response back to LSP classes.
 *
 * ## Document Synchronization
 * Standard LSP document sync notifications ([didOpen], [didChange], [didClose]) are ignored in this bridge.
 * The legacy Dart plugin already handles robust file content synchronization via `analysis.updateContent`.
 * Since DAS shares the same in-memory overlay filesystem between legacy and LSP handlers, the shared LSP handlers
 * (like Hover) will automatically see the up-to-date content synced by the legacy plugin.
 * To ensure the server is fully up to date before processing a request, we also explicitly call
 * `das.updateFilesContent()` before forwarding any client request.
 */
class DartBridgeLspServer(private val project: Project) : DartLanguageServer, TextDocumentService, WorkspaceService,
    LanguageClientAware {
    companion object {
        private val logger = PluginLogger.createLogger(DartBridgeLspServer::class.java)
        private const val LSP_MESSAGE_KEY = "lspMessage"
        private const val LSP_NOTIFICATION_KEY = "lspNotification"
        private const val LSP_RESPONSE_KEY = "lspResponse"
        private const val JSONRPC_VERSION = "2.0"
        
        // We use a JSON handler with default lsp4j configuration to serialize/deserialize lsp4j objects.
        private val JSON_HANDLER = MessageJsonHandler(mapOf())
        private val GSON: Gson = JSON_HANDLER.gson
    }

    private var client: LanguageClient? = null
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest<*>>()
    private var dasMessageListener: ResponseListener? = null

    private val das: DartAnalysisServerService
        get() = DartAnalysisServerService.getInstance(project)

    init {
        setupDasMessageListener()
    }

    override fun connect(client: LanguageClient) {
        this.client = client
        logger.info("Connected to LanguageClient")
    }

    /**
     * Sets up a listener on the legacy Dart Analysis Server (DAS) to intercept messages
     * (responses, notifications, and reverse requests) containing LSP payloads.
     */
    private fun setupDasMessageListener() {
        val listener = ResponseListener { rawMessage ->
            // Intercept only those messages that contain LSP payload keys.
            if (!rawMessage.contains(LSP_MESSAGE_KEY) && !rawMessage.contains(LSP_RESPONSE_KEY) && !rawMessage.contains(LSP_NOTIFICATION_KEY)) {
                return@ResponseListener
            }

            try {
                val jsonObject = JsonParser.parseString(rawMessage).asJsonObject
                handleDasMessage(jsonObject)
            } catch (e: Exception) {
                logger.error("Error handling DAS message", e)
            }
        }
        this.dasMessageListener = listener
        das.addResponseListener(listener)
    }

    /**
     * Processes raw JSON messages from the Dart Analysis Server.
     * Depending on the payload, it either:
     * 1. Forwards server-initiated messages (e.g. publishDiagnostics, applyEdit) directly to the LSP client.
     * 2. Resolves matching pending request futures for client-initiated requests (e.g. hover, codeAction).
     */
    private fun handleDasMessage(jsonObject: JsonObject) {
        val idElement = jsonObject.get("id")
        val topLevelId = if (idElement != null && idElement.isJsonPrimitive) idElement.asString else null

        // Check if it's a server-initiated notification or request from DAS.
        if (jsonObject.has("params")) {
            val params = jsonObject.get("params").asJsonObject
            val msgObj = when {
                params.has(LSP_NOTIFICATION_KEY) -> params.get(LSP_NOTIFICATION_KEY).asJsonObject
                params.has(LSP_MESSAGE_KEY) -> params.get(LSP_MESSAGE_KEY).asJsonObject
                else -> null
            }
            if (msgObj != null) {
                val method = msgObj.getAsJsonPrimitive("method")?.asString
                if (method != null) {
                    // Forward server message to client.
                    forwardServerMessageToClient(topLevelId, method, msgObj)
                }
                return
            }
        }

        // Check if it's a response to a client-initiated request.
        if (topLevelId != null) {
            handlePendingRequestResponse(topLevelId, jsonObject)
        }
    }

    private fun handlePendingRequestResponse(topLevelId: String, jsonObject: JsonObject) {
        val pending = pendingRequests.remove(topLevelId) ?: return
        val topLevelError = jsonObject.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
        if (topLevelError != null) {
            pending.completeExceptionally(topLevelError)
            return
        }

        val lspResponse = jsonObject.get("result")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get(LSP_RESPONSE_KEY)?.takeIf { it.isJsonObject }?.asJsonObject

        val lspError = lspResponse?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
        if (lspError != null) {
            pending.completeExceptionally(lspError)
        } else {
            pending.complete(lspResponse?.get("result"))
        }
    }

    private fun forwardServerMessageToClient(dasRequestId: String?, method: String, msgObj: JsonObject) {
        val client = this.client ?: return
        try {
            // Parse and forward notifications/requests to the LSP client proxy using lsp4j.
            when (method) {
                "textDocument/publishDiagnostics" -> handlePublishDiagnostics(client, msgObj)
                "dart/textDocument/publishClosingLabels" -> handlePublishClosingLabels(msgObj)
                "workspace/applyEdit" -> handleApplyEdit(client, dasRequestId, msgObj)
                else -> logger.debug("Ignored notification/request from DAS: $method")
            }
        } catch (e: Exception) {
            logger.error("Failed to forward server message for method: $method", e)
        }
    }

    private fun handlePublishDiagnostics(client: LanguageClient, msgObj: JsonObject) {
        val paramsObj = msgObj.get("params")
        val params = GSON.fromJson(paramsObj, PublishDiagnosticsParams::class.java)
        client.publishDiagnostics(params)
        val errors = params.diagnostics?.map {
            DartLspDiagnosticConverter.convertDiagnosticToAnalysisError(project, das, params.uri, it)
        } ?: emptyList()
        das.onLspDiagnosticsUpdated(params.uri, errors)
    }

    private fun handlePublishClosingLabels(msgObj: JsonObject) {
        val paramsObj = msgObj.get("params")
        val params = GSON.fromJson(paramsObj, DartPublishClosingLabelsParams::class.java)
        if (params?.uri != null) {
            DartLspClosingLabelsService.getInstance(project).updateClosingLabels(params.uri, params.labels)
        }
    }

    private fun handleApplyEdit(client: LanguageClient, dasRequestId: String?, msgObj: JsonObject) {
        val paramsObj = msgObj.get("params")
        val params = GSON.fromJson(paramsObj, ApplyWorkspaceEditParams::class.java)
        val lspId = msgObj.get("id")
        client.applyEdit(params).whenComplete { response, error ->
            if (dasRequestId != null) {
                val lspResponse = JsonObject().apply {
                    addProperty("jsonrpc", JSONRPC_VERSION)
                    if (lspId != null) {
                        add("id", lspId)
                    }
                    if (error != null) {
                        val errorObj = JsonObject().apply {
                            addProperty("code", -32603)
                            addProperty("message", error.message ?: "Internal error")
                        }
                        add("error", errorObj)
                    } else {
                        add("result", GSON.toJsonTree(response))
                    }
                }
                val legacyResponse = JsonObject().apply {
                    addProperty("id", dasRequestId)
                    add("result", JsonObject().apply {
                        add(LSP_RESPONSE_KEY, lspResponse)
                    })
                }
                try {
                    das.sendResponse(legacyResponse)
                } catch (e: Exception) {
                    logger.error("Failed to send applyEdit response to DAS", e)
                }
            }
        }
    }

    fun stop() {
        dasMessageListener?.let { das.removeResponseListener(it) }
        pendingRequests.forEach { (_, pending) ->
            pending.future.cancel(true)
        }
        pendingRequests.clear()
    }

    // --- LanguageServer Implementation ---

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        logger.info("Initialize called")
        sendDefaultConfiguration()
        val capabilities = ServerCapabilities().apply {
            setHoverProvider(true)
            setDefinitionProvider(true)
            setTypeDefinitionProvider(true)
            setDocumentHighlightProvider(true)
            setInlayHintProvider(true)
            setTypeHierarchyProvider(true)
            setCallHierarchyProvider(true)
            setReferencesProvider(true)
            setCompletionProvider(CompletionOptions(true, listOf(".", "=", "'", "\"", "/", "@", ":")))
            val fileOperationsCaps = FileOperationsServerCapabilities().apply {
                willRename = FileOperationOptions(listOf(FileOperationFilter(FileOperationPattern("**/*"))))
            }
            workspace = WorkspaceServerCapabilities().apply {
                fileOperations = fileOperationsCaps
            }
            setCodeActionProvider(true)
            setExecuteCommandProvider(ExecuteCommandOptions())
            // Add other capabilities as we support them.
        }
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun initialized(params: InitializedParams) {
        logger.info("Initialized called")
        sendDefaultConfiguration()
    }

    override fun shutdown(): CompletableFuture<Any> {
        logger.info("Shutdown called")
        stop()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        logger.info("Exit called")
    }

    override fun getTextDocumentService(): TextDocumentService = this
    override fun getWorkspaceService(): WorkspaceService = this

    // --- TextDocumentService Implementation ---

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        return forwardRequest("textDocument/hover", params, Hover::class.java)
    }

    // Note: We advertise linkSupport: true in server.setClientCapabilities (see RequestUtilities.java)
    // so DAS is guaranteed to return List<LocationLink> for textDocument/definition.
    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        val type = object : TypeToken<List<LocationLink>>() {}.type
        return forwardRequest<List<LocationLink>>("textDocument/definition", params, type).thenApply { links ->
            Either.forRight(links ?: emptyList())
        }
    }

    override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        val type = object : TypeToken<List<LocationLink>>() {}.type
        return forwardRequest<List<LocationLink>>("textDocument/typeDefinition", params, type).thenApply { links ->
            Either.forRight(links ?: emptyList())
        }
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return forwardRequest<JsonElement>("textDocument/completion", params, JsonElement::class.java).thenApply { element ->
            val result = if (element == null || element.isJsonNull) {
                Either.forRight<List<CompletionItem>, CompletionList>(CompletionList(false, emptyList()))
            } else if (element.isJsonArray) {
                val type = object : TypeToken<List<CompletionItem>>() {}.type
                val items: List<CompletionItem> = GSON.fromJson(element, type) ?: emptyList()
                Either.forLeft<List<CompletionItem>, CompletionList>(items)
            } else {
                val list = GSON.fromJson(element, CompletionList::class.java) ?: CompletionList(false, emptyList())
                Either.forRight<List<CompletionItem>, CompletionList>(list)
            }
            enhanceCompletionItems(result)
        }.exceptionally { e ->
            logger.info("textDocument/completion failed: ${e.message}")
            Either.forRight<List<CompletionItem>, CompletionList>(CompletionList(false, emptyList()))
        }
    }

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        return forwardRequest("completionItem/resolve", unresolved, CompletionItem::class.java).thenApply { resolved ->
            val item = resolved ?: unresolved
            enhanceCompletionItem(item)
            item
        }.exceptionally { e ->
            logger.info("completionItem/resolve failed: ${e.message}")
            enhanceCompletionItem(unresolved)
            unresolved
        }
    }

    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> {
        val type = object : TypeToken<List<DocumentHighlight>>() {}.type
        return forwardRequest<List<DocumentHighlight>>("textDocument/documentHighlight", params, type)
    }

    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
        val type = object: TypeToken<List<Location>>() {}.type

        return forwardRequest("textDocument/references", params, type)
    }

    // Unlike the other overrides above, this one must never let its future complete
    // exceptionally: the LSP client's inlay-hint cache awaits this future inside a coroutine
    // without catching exceptions, so a DAS error (e.g. FileNotAnalyzed) would otherwise
    // surface as an uncaught IDE error instead of simply showing no hints.
    override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> {
        val type = object : TypeToken<List<InlayHint>>() {}.type
        return forwardRequest<List<InlayHint>>("textDocument/inlayHint", params, type)
            .handle { hints, error ->
                if (error != null) {
                    logger.info("textDocument/inlayHint failed: ${error.message}")
                    emptyList()
                } else {
                    hints ?: emptyList()
                }
            }
    }

    override fun diagnosticServer(): CompletableFuture<DiagnosticServerResult> {
        return forwardRequest("dart/diagnosticServer", null, DiagnosticServerResult::class.java)
    }

    // Note: We advertise codeActionLiteralSupport in server.setClientCapabilities (see DartAnalysisServerService.buildLspCapabilities)
    // so DAS is guaranteed to return List<CodeAction> for textDocument/codeAction.
    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
        val responseType = object : TypeToken<List<CodeAction>>() {}.type
        return forwardRequest<List<CodeAction>>("textDocument/codeAction", params, responseType).thenApply { actions ->
            actions?.map { Either.forRight<Command, CodeAction>(it) } ?: emptyList()
        }
    }

    // Implement other TextDocumentService methods as needed, returning unsupported or forwarding.
    
    override fun didOpen(params: DidOpenTextDocumentParams) {
        // Ignored. Document synchronization is handled by the legacy DartAnalysisServerService.
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        // Ignored. Document synchronization is handled by the legacy DartAnalysisServerService.
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        // Ignored. Document synchronization is handled by the legacy DartAnalysisServerService.
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // Ignored. Document synchronization is handled by the legacy DartAnalysisServerService.
    }

    // --- WorkspaceService Implementation ---

    override fun willRenameFiles(params: RenameFilesParams): CompletableFuture<WorkspaceEdit> {
        return forwardRequest("workspace/willRenameFiles", params, WorkspaceEdit::class.java)
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        return forwardRequest("workspace/executeCommand", params, Any::class.java)
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        forwardNotification("workspace/didChangeConfiguration", params)
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        // Ignored. File watching is handled by the legacy plugin.
    }

    // --- Type Hierarchy ---

    override fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): CompletableFuture<List<TypeHierarchyItem>> {
        val type = object : TypeToken<List<TypeHierarchyItem>>() {}.type
        return forwardRequest("textDocument/prepareTypeHierarchy", params, type)
    }

    override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        val type = object : TypeToken<List<TypeHierarchyItem>>() {}.type
        return forwardRequest("typeHierarchy/supertypes", params, type)
    }

    override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<List<TypeHierarchyItem>> {
        val type = object : TypeToken<List<TypeHierarchyItem>>() {}.type
        return forwardRequest("typeHierarchy/subtypes", params, type)
    }

    // --- Call Hierarchy ---

    override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<List<CallHierarchyItem>> {
        val type = object : TypeToken<List<CallHierarchyItem>>() {}.type
        return forwardRequest("textDocument/prepareCallHierarchy", params, type)
    }

    override fun callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture<List<CallHierarchyIncomingCall>> {
        val type = object : TypeToken<List<CallHierarchyIncomingCall>>() {}.type
        return forwardRequest("callHierarchy/incomingCalls", params, type)
    }

    override fun callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture<List<CallHierarchyOutgoingCall>> {
        val type = object : TypeToken<List<CallHierarchyOutgoingCall>>() {}.type
        return forwardRequest("callHierarchy/outgoingCalls", params, type)
    }


    // --- Helper Methods for Forwarding ---

    /**
     * Forwards an LSP request to the legacy DAS.
     *
     * It wraps the LSP request in a legacy DAS `lsp.handle` request.
     * We register a [PendingRequest] associated with the [legacyId] so that when DAS returns
     * the response, we can match it, extract the inner LSP response payload, and complete the future.
     *
     * Note: We use the same [legacyId] for both the outer legacy request and the inner LSP request
     * to simplify tracking and matching.
     */
    private fun <T> forwardRequest(method: String, params: Any?, responseClass: Class<T>): CompletableFuture<T> {
        return forwardRequest(method, params, responseClass as Type)
    }

    private fun <T> forwardRequest(method: String, params: Any?, responseType: Type): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        
        val ready = if (das.isServerProcessActive) {
            true
        } else {
            runCatching { runReadAction { das.serverReadyForRequest() } }.getOrDefault(false)
        }
        if (!ready) {
            future.completeExceptionally(ResponseErrorException(ResponseError(-32001, "Dart Analysis Server is not ready", null)))
            return future
        }

        if (com.intellij.openapi.application.ApplicationManager.getApplication().isReadAccessAllowed) {
            das.updateFilesContent()
        }

        val legacyId = das.generateUniqueId()
        if (legacyId == null) {
            future.completeExceptionally(ResponseErrorException(ResponseError(-32001, "Failed to generate request ID", null)))
            return future
        }
        
        val pending = PendingRequest(future, responseType)
        pendingRequests[legacyId] = pending

        val lspRequest = JsonObject().apply {
            addProperty("jsonrpc", JSONRPC_VERSION)
            addProperty("id", legacyId)
            addProperty("method", method)
            if (params != null && params != Unit) {
                add("params", GSON.toJsonTree(params))
            }
        }

        val legacyRequest = JsonObject().apply {
            addProperty("id", legacyId)
            addProperty("method", "lsp.handle")
            add("params", JsonObject().apply {
                add("lspMessage", lspRequest)
            })
        }

        try {
            das.sendRequest(legacyId, legacyRequest)
        } catch (e: Exception) {
            logger.error("Failed to send request to DAS for method: $method", e)
            pendingRequests.remove(legacyId)
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Forwards an LSP notification to the legacy DAS.
     *
     * It wraps the LSP notification in a legacy DAS `lsp.handle` request.
     * Since LSP notifications do not expect a response, we do not register a future to track it.
     * DAS will still return a dummy legacy response acknowledging the `lsp.handle` request,
     * which we will receive and safely ignore (as no pending request will match its ID).
     */
    private fun forwardNotification(method: String, params: Any) {
        val legacyId = das.generateUniqueId()
        val lspNotification = JsonObject().apply {
            addProperty("jsonrpc", JSONRPC_VERSION)
            addProperty("method", method)
            add("params", GSON.toJsonTree(params))
        }

        val legacyRequest = JsonObject().apply {
            addProperty("id", legacyId)
            addProperty("method", "lsp.handle")
            add("params", JsonObject().apply {
                add("lspMessage", lspNotification)
            })
        }

        try {
            das.sendRequest(legacyId, legacyRequest)
        } catch (e: Exception) {
            logger.error("Failed to send notification to DAS for method: $method", e)
        }
    }

    private fun sendDefaultConfiguration() {
        val settings = mapOf(
            "completeFunctionCalls" to true,
            "suggestFromUnimportedLibraries" to true,
            "dart" to mapOf(
                "completeFunctionCalls" to true,
                "suggestFromUnimportedLibraries" to true
            )
        )
        forwardNotification("workspace/didChangeConfiguration", DidChangeConfigurationParams(settings))
    }

    private fun extractSignature(item: CompletionItem): String? {
        val labelDetail = item.labelDetails?.detail
        if (!labelDetail.isNullOrBlank() && labelDetail.startsWith("(") && labelDetail.endsWith(")")) {
            return labelDetail
        }
        val detail = item.detail
        if (!detail.isNullOrBlank()) {
            val start = detail.indexOf('(')
            val end = detail.lastIndexOf(')')
            if (start != -1 && end != -1 && end > start) {
                return detail.substring(start, end + 1)
            }
        }
        return null
    }

    private fun extractParameters(signature: String): List<String> {
        val trimmed = signature.trim()
        if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) {
            return emptyList()
        }
        val content = trimmed.substring(1, trimmed.length - 1).trim()
        if (content.isEmpty()) {
            return emptyList()
        }

        val params = mutableListOf<String>()
        var depthParen = 0
        var depthAngle = 0
        var depthBrace = 0
        var depthBracket = 0
        val current = StringBuilder()

        for (ch in content) {
            when (ch) {
                '(' -> depthParen++
                ')' -> depthParen--
                '<' -> depthAngle++
                '>' -> depthAngle--
                '{' -> depthBrace++
                '}' -> depthBrace--
                '[' -> depthBracket++
                ']' -> depthBracket--
                ',' -> {
                    if (depthParen == 0 && depthAngle == 0 && depthBrace == 0 && depthBracket == 0) {
                        params.add(current.toString().trim())
                        current.clear()
                        continue
                    }
                }
            }
            current.append(ch)
        }
        if (current.isNotEmpty()) {
            params.add(current.toString().trim())
        }
        return params.filter { it.isNotEmpty() }
    }

    private fun extractParamName(rawParam: String): String? {
        val beforeEqual = rawParam.substringBefore('=').trim()
        val stripped = beforeEqual.trim('{', '}', '[', ']').trim()
        if (stripped.isEmpty()) return null

        val match = Regex("""([a-zA-Z_$][a-zA-Z0-9_$]*)\s*$""").find(stripped)
        return match?.groupValues?.get(1)
    }

    private fun isRequiredParam(rawParam: String): Boolean {
        val trimmed = rawParam.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return false
        }
        if (trimmed.startsWith("{") || trimmed.endsWith("}")) {
            return trimmed.contains("required ") || trimmed.startsWith("{required")
        }
        return true
    }

    private fun buildCallableSnippet(name: String, signature: String?): String {
        if (signature.isNullOrBlank()) {
            return "$name($0)"
        }
        val params = extractParameters(signature)
        if (params.isEmpty()) {
            return "$name()$0"
        }

        val requiredParams = params.filter { isRequiredParam(it) }.mapNotNull { extractParamName(it) }
        if (requiredParams.isEmpty()) {
            return "$name($0)"
        }

        val args = requiredParams.mapIndexed { index, paramName ->
            "\${${index + 1}:$paramName}"
        }.joinToString(", ")

        return "$name($args)"
    }

    private fun enhanceCompletionItem(item: CompletionItem) {
        if (item.kind == CompletionItemKind.Function ||
            item.kind == CompletionItemKind.Method ||
            item.kind == CompletionItemKind.Constructor
        ) {
            if (item.insertTextFormat != InsertTextFormat.Snippet) {
                val signature = extractSignature(item)
                val textEdit = item.textEdit
                if (textEdit != null) {
                    if (textEdit.isLeft) {
                        val edit = textEdit.left
                        if (edit != null && edit.newText != null && !edit.newText.contains("(")) {
                            edit.newText = buildCallableSnippet(edit.newText, signature)
                            item.insertTextFormat = InsertTextFormat.Snippet
                        }
                    } else if (textEdit.isRight) {
                        val edit = textEdit.right
                        if (edit != null && edit.newText != null && !edit.newText.contains("(")) {
                            edit.newText = buildCallableSnippet(edit.newText, signature)
                            item.insertTextFormat = InsertTextFormat.Snippet
                        }
                    }
                } else if (item.insertText != null) {
                    if (!item.insertText.contains("(")) {
                        item.insertText = buildCallableSnippet(item.insertText, signature)
                        item.insertTextFormat = InsertTextFormat.Snippet
                    }
                } else if (item.label != null && !item.label.contains("(")) {
                    item.insertText = buildCallableSnippet(item.label, signature)
                    item.insertTextFormat = InsertTextFormat.Snippet
                }
            }
        }
    }

    private fun enhanceCompletionItems(result: Either<List<CompletionItem>, CompletionList>): Either<List<CompletionItem>, CompletionList> {
        if (result.isLeft) {
            result.left?.forEach { enhanceCompletionItem(it) }
        } else if (result.isRight) {
            result.right?.items?.forEach { enhanceCompletionItem(it) }
        }
        return result
    }

    // Helper class to store pending request info.
    private inner class PendingRequest<T>(val future: CompletableFuture<T>, val responseType: Type) {
        fun complete(resultPayload: JsonElement?) {
            if (resultPayload == null || resultPayload.isJsonNull) {
                future.complete(null)
                return
            }
            try {
                val result: T = GSON.fromJson(resultPayload, responseType)
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        fun completeExceptionally(error: JsonObject) {
            val code = error.get("code")?.asInt ?: -32603
            val message = error.get("message")?.asString ?: "Unknown error"
            future.completeExceptionally(ResponseErrorException(ResponseError(code, message, null)))
        }
    }
}

