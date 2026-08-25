/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.dart.server.ResponseListener
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.logging.PluginLogger
import org.dartlang.analysis.server.protocol.AnalysisError
import org.dartlang.analysis.server.protocol.DiagnosticMessage
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
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
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.eclipse.lsp4j.TypeDefinitionParams
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
        
        @JvmField
        internal val GSON: Gson = run {
            val supportedMethods = LinkedHashMap<String, JsonRpcMethod>()
            supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(LanguageServer::class.java))
            supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(TextDocumentService::class.java))
            supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(WorkspaceService::class.java))
            supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(LanguageClient::class.java))
            MessageJsonHandler(supportedMethods).gson.newBuilder()
                .serializeNulls()
                .create()
        }
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
                    forwardServerMessageToClient(method, msgObj)
                }
                return
            }
        }

        // Check if it's a response to a client-initiated request.
        val idElement = jsonObject.get("id")
        val topLevelId = if (idElement != null && idElement.isJsonPrimitive) idElement.asString else null
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

    private fun forwardServerMessageToClient(method: String, msgObj: JsonObject) {
        val client = this.client ?: return
        try {
            // Parse and forward notifications/requests to the LSP client proxy using lsp4j.
            when (method) {
                "textDocument/publishDiagnostics" -> handlePublishDiagnostics(client, msgObj)
                "workspace/applyEdit" -> handleApplyEdit(client, msgObj)
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

    private fun handleApplyEdit(client: LanguageClient, msgObj: JsonObject) {
        val paramsObj = msgObj.get("params")
        val params = GSON.fromJson(paramsObj, ApplyWorkspaceEditParams::class.java)
        val id = msgObj.get("id")
        client.applyEdit(params).whenComplete { response, error ->
            if (id != null) {
                val legacyId = das.generateUniqueId() ?: return@whenComplete
                val lspResponse = JsonObject().apply {
                    addProperty("jsonrpc", JSONRPC_VERSION)
                    add("id", id)
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
                val legacyRequest = JsonObject().apply {
                    addProperty("id", legacyId)
                    addProperty("method", "lsp.handle")
                    add("params", JsonObject().apply {
                        add("lspMessage", lspResponse)
                    })
                }
                try {
                    das.sendRequest(legacyId, legacyRequest)
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
        val capabilities = ServerCapabilities().apply {
            setHoverProvider(true)
            setDefinitionProvider(true)
            setTypeDefinitionProvider(true)
            setDocumentHighlightProvider(true)
            setCodeActionProvider(true)
            setExecuteCommandProvider(ExecuteCommandOptions())
            // Add other capabilities as we support them.
        }
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
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

    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> {
        val type = object : TypeToken<List<DocumentHighlight>>() {}.type
        return forwardRequest<List<DocumentHighlight>>("textDocument/documentHighlight", params, type)
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

    override fun resolveCodeAction(unresolved: CodeAction): CompletableFuture<CodeAction> {
        return CompletableFuture.completedFuture(unresolved)
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

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        logger.debug("Client executeCommand called: command=${params.command}")

        val forwardedParams = normalizeExecuteCommandParams(params)

        return forwardRequest("workspace/executeCommand", forwardedParams, Any::class.java)
    }

    private fun normalizeExecuteCommandParams(params: ExecuteCommandParams): ExecuteCommandParams {
        if (params.command != "dart.edit.codeAction.apply" || params.arguments.isNullOrEmpty()) {
            return params
        }
        val arg0Tree = GSON.toJsonTree(params.arguments[0])
        if (!arg0Tree.isJsonObject) {
            return params
        }
        val obj = arg0Tree.asJsonObject
        if (!obj.has("textDocument") || !obj.has("range") || !obj.has("kind")) {
            return params
        }

        val td = obj.get("textDocument")
        val normalizedTd = if (td.isJsonObject) {
            val tdObj = td.asJsonObject
            val nTd = JsonObject()
            val uriElem = tdObj.get("uri")
            val uriStr = when {
                uriElem == null -> ""
                uriElem.isJsonPrimitive -> uriElem.asString
                uriElem.isJsonObject && uriElem.asJsonObject.has("path") -> {
                    "file://" + uriElem.asJsonObject.get("path").asString
                }
                else -> uriElem.toString()
            }
            nTd.addProperty("uri", uriStr)
            if (tdObj.has("version") && !tdObj.get("version").isJsonNull) {
                val ver = tdObj.get("version")
                if (ver.isJsonPrimitive && ver.asJsonPrimitive.isNumber) {
                    nTd.addProperty("version", ver.asInt)
                } else {
                    nTd.add("version", JsonNull.INSTANCE)
                }
            } else {
                nTd.add("version", JsonNull.INSTANCE)
            }
            nTd
        } else {
            td
        }
        val normalizedMap = JsonObject().apply {
            add("textDocument", normalizedTd)
            add("range", obj.get("range"))
            add("kind", obj.get("kind"))
        }
        return ExecuteCommandParams(
            params.command,
            listOf(normalizedMap)
        )
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        // Ignored. Configuration is managed by the legacy plugin settings.
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        // Ignored. File watching is handled by the legacy plugin.
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
        
        val ready = runReadAction { das.serverReadyForRequest() }
        if (!ready) {
            future.completeExceptionally(ResponseErrorException(ResponseError(-32001, "Dart Analysis Server is not ready", null)))
            return future
        }

        das.updateFilesContent()

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

