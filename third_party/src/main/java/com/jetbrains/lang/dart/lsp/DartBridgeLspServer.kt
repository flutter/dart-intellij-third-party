/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.google.dart.server.ResponseListener
import com.jetbrains.lang.dart.logging.PluginLogger
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class DartBridgeLspServer(private val project: Project) : LanguageServer, TextDocumentService, WorkspaceService, LanguageClientAware {
    companion object {
        private val logger = PluginLogger.createLogger(DartBridgeLspServer::class.java)
        private const val LSP_MESSAGE_KEY = "lspMessage"
        private const val LSP_RESPONSE_KEY = "lspResponse"
        private const val JSONRPC_VERSION = "2.0"
        
        // We use a JSON handler with default lsp4j configuration to serialize/deserialize lsp4j objects.
        private val JSON_HANDLER = MessageJsonHandler(mapOf())
        private val GSON: Gson = JSON_HANDLER.gson
    }

    private var client: LanguageClient? = null
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest<*>>()
    private var responseListener: ResponseListener? = null

    private val das: DartAnalysisServerService
        get() = DartAnalysisServerService.getInstance(project)

    init {
        setupDasResponseListener()
    }

    override fun connect(client: LanguageClient) {
        this.client = client
        logger.info("Connected to LanguageClient")
    }

    /**
     * Sets up a listener on the legacy Dart Analysis Server (DAS) to intercept responses 
     * and notifications sent back to requests originating from the client.
     */
    private fun setupDasResponseListener() {
        val listener = ResponseListener { response ->
            // Intercept only those responses that contain LSP payload keys.
            if (!response.contains(LSP_MESSAGE_KEY) && !response.contains(LSP_RESPONSE_KEY)) {
                return@ResponseListener
            }

            try {
                val jsonObject = JsonParser.parseString(response).asJsonObject
                handleDasResponse(jsonObject)
            } catch (e: Exception) {
                logger.error("Error handling DAS response: $response", e)
            }
        }
        this.responseListener = listener
        das.addResponseListener(listener)
    }

    /**
     * Processes raw JSON responses from the Dart Analysis Server.
     * Depending on the payload, it either:
     * 1. Forwards server notifications (e.g. publishDiagnostics) directly to the LSP client.
     * 2. Unwraps successful LSP responses (e.g. hover/completion results) or exceptional 
     *    LSP errors to resolve the matching pending request future.
     */
    private fun handleDasResponse(jsonObject: JsonObject) {
        logger.info("Received DAS response: $jsonObject")
        // Check if it's a notification from DAS.
        if (jsonObject.has("params")) {
            val params = jsonObject.get("params").asJsonObject
            if (params.has(LSP_MESSAGE_KEY)) {
                val msgObj = params.get(LSP_MESSAGE_KEY).asJsonObject
                val method = msgObj.getAsJsonPrimitive("method")?.asString
                if (method != null) {
                    // Forward notification to client.
                    forwardNotificationToClient(method, msgObj)
                }
                return
            }
        }

        // Check if it's a response to a request.
        val topLevelId = if (jsonObject.has("id")) jsonObject.get("id").asString else null
        if (topLevelId != null) {
            val pending = pendingRequests.remove(topLevelId)
            if (pending != null) {
                if (jsonObject.has("result")) {
                    val result = jsonObject.get("result").asJsonObject
                    if (result.has(LSP_RESPONSE_KEY)) {
                        val lspResponseElement = result.get(LSP_RESPONSE_KEY)
                        if (lspResponseElement != null && lspResponseElement.isJsonObject) {
                            val lspResponse = lspResponseElement.asJsonObject
                            if (lspResponse.has("error")) {
                                val error = lspResponse.getAsJsonObject("error")
                                pending.completeExceptionally(error)
                            } else if (lspResponse.has("result")) {
                                val lspResult = lspResponse.get("result")
                                if (lspResult != null && !lspResult.isJsonNull) {
                                    pending.complete(lspResult)
                                } else {
                                    pending.completeWithNull()
                                }
                            } else {
                                pending.completeWithNull()
                            }
                        } else {
                            pending.completeWithNull()
                        }
                    } else {
                        pending.completeWithNull()
                    }
                } else if (jsonObject.has("error")) {
                    val error = jsonObject.get("error").asJsonObject
                    pending.completeExceptionally(error)
                }
            }
        }
    }

    private fun forwardNotificationToClient(method: String, msgObj: JsonObject) {
        val client = this.client ?: return
        try {
            // Parse and forward notifications to the LSP client proxy using lsp4j.
            // Currently, only 'textDocument/publishDiagnostics' is supported and forwarded.
            if (method == "textDocument/publishDiagnostics") {
                val paramsObj = msgObj.get("params")
                val params = GSON.fromJson(paramsObj, PublishDiagnosticsParams::class.java)
                client.publishDiagnostics(params)
            } else {
                logger.info("Ignored notification from DAS: $method")
            }
        } catch (e: Exception) {
            logger.error("Failed to forward notification: $msgObj", e)
        }
    }

    fun stop() {
        responseListener?.let { das.removeResponseListener(it) }
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

    // Implement other TextDocumentService methods as needed, returning unsupported or forwarding.
    
    override fun didOpen(params: DidOpenTextDocumentParams) {
        forwardNotification("textDocument/didOpen", params)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        forwardNotification("textDocument/didChange", params)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        forwardNotification("textDocument/didClose", params)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        forwardNotification("textDocument/didSave", params)
    }

    // --- WorkspaceService Implementation ---

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        forwardNotification("workspace/didChangeConfiguration", params)
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        forwardNotification("workspace/didChangeWatchedFiles", params)
    }

    // --- Helper Methods for Forwarding ---

    private fun <T> forwardRequest(method: String, params: Any, responseClass: Class<T>): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        
        val ready = runReadAction { das.serverReadyForRequest() }
        if (!ready) {
            future.completeExceptionally(ResponseErrorException(ResponseError(-32001, "Dart Analysis Server is not ready", null)))
            return future
        }

        val legacyId = das.generateUniqueId()
        if (legacyId == null) {
            future.completeExceptionally(ResponseErrorException(ResponseError(-32001, "Failed to generate request ID", null)))
            return future
        }
        
        val pending = PendingRequest(future, responseClass)
        pendingRequests[legacyId] = pending

        val lspRequest = JsonObject().apply {
            addProperty("jsonrpc", JSONRPC_VERSION)
            addProperty("id", legacyId)
            addProperty("method", method)
            add("params", GSON.toJsonTree(params))
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
            logger.error("Failed to send request to DAS: $legacyRequest", e)
            pendingRequests.remove(legacyId)
            future.completeExceptionally(e)
        }

        return future
    }

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
            logger.error("Failed to send notification to DAS: $legacyRequest", e)
        }
    }

    // Helper class to store pending request info.
    private inner class PendingRequest<T>(val future: CompletableFuture<T>, val responseClass: Class<T>) {
        fun complete(resultPayload: JsonElement) {
            try {
                val result = GSON.fromJson(resultPayload, responseClass)
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        fun completeWithNull() {
            future.complete(null)
        }

        fun completeExceptionally(error: JsonObject) {
            val code = error.get("code")?.asInt ?: -32603
            val message = error.get("message")?.asString ?: "Unknown error"
            future.completeExceptionally(ResponseErrorException(ResponseError(code, message, null)))
        }
    }
}

