/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.google.dart.server.ResponseListener
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.logging.PluginLogger
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import org.eclipse.lsp4j.jsonrpc.JsonRpcException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode

/**
 * This is essentially a virtual LSP language server.
 *
 * This provider intercepts LSP messages from the lsp4ij client, sends them to the DAS as LSP-over-legacy requests,
 * intercepts responses from the DAS, and sends LSP responses back to the client.
 */
class DartVirtualStreamConnectionProvider(private val project: Project) : StreamConnectionProvider {
    companion object {
        private val logger = PluginLogger.createLogger(DartVirtualStreamConnectionProvider::class.java)
        private val JSON_HANDLER = MessageJsonHandler(mapOf())

        private const val LSP_MESSAGE_KEY = "lspMessage"
        private const val LSP_RESPONSE_KEY = "lspResponse"
        private const val JSONRPC_VERSION = "2.0"
    }

    // Stream for writing LSP responses from the virtual server to the lsp4ij client.
    val responseStream = VirtualStream()

    // Stream for the lsp4ij client to write requests to the virtual server.
    val requestStream = VirtualStream()

    // This stores IDs that have been used by lsp4ij for LSP-over-legacy requests to DAS.
    // There are non-lsp4ij requests that are sent as LSP-over-legacy, but we don't need to forward responses for those
    // requests to lsp4ij since lsp4ij was not the originator.
    private val pendingLegacyIds = ConcurrentHashMap<String, JsonElement>()
    private var responseListener: ResponseListener? = null
    @Volatile private var isStopping = false
    private var clientMessageFuture: java.util.concurrent.Future<*>? = null

    override fun start() {
        logger.info("Starting DartVirtualStreamConnectionProvider")
        val dartAnalysisService = DartAnalysisServerService.getInstance(project)

        Disposer.register(project) {
            stop()
        }

        clientMessageFuture = ApplicationManager.getApplication().executeOnPooledThread {
            setupDasResponseListener(dartAnalysisService)
            processLspClientMessages()
        }
    }

    private fun setupDasResponseListener(dartAnalysisService: DartAnalysisServerService) {
        val listener = ResponseListener { response ->
            if (!response.contains(LSP_MESSAGE_KEY) && !response.contains(LSP_RESPONSE_KEY)) {
                return@ResponseListener
            }

            logger.debug("Response received from DAS: $response")
            val jsonObject = JsonParser.parseString(response).asJsonObject
            val lspPayload = extractLspPayload(jsonObject)

            if (lspPayload != null) {
                enqueueResponse(lspPayload.toString())
            }
        }
        this.responseListener = listener
        dartAnalysisService.addResponseListener(listener)
        logger.info("Finished setting up DAS listening for lsp4ij")
    }

    private fun extractLspPayload(jsonObject: JsonObject): JsonObject? {
        if (jsonObject.has("params")) {
            val params = jsonObject.get("params").asJsonObject
            if (params.has(LSP_MESSAGE_KEY)) {
                val msgObj = params.get(LSP_MESSAGE_KEY).asJsonObject
                if (msgObj.getAsJsonPrimitive("method")?.asString == "workspace/applyEdit") {
                    logger.debug("Ignored workspace/applyEdit message from DAS (handled by legacy bridge)")
                    return null
                }
                logger.debug("extractLspPayload: Extracted LSP notification/message from DAS: $msgObj")
                return msgObj
            }
        }

        val topLevelId = if (jsonObject.has("id")) jsonObject.get("id").asString else null
        if (topLevelId != null) {
            val lspIdElement = pendingLegacyIds[topLevelId]
            if (lspIdElement != null) {
                if (jsonObject.has("result")) {
                    pendingLegacyIds.remove(topLevelId)
                    val result = jsonObject.get("result").asJsonObject
                    if (result.has(LSP_RESPONSE_KEY)) {
                        val resultPayload = result.get(LSP_RESPONSE_KEY).asJsonObject
                        logger.debug("extractLspPayload: Extracted successful LSP response for legacyId=$topLevelId (lspId=$lspIdElement): $resultPayload")
                        return resultPayload
                    } else {
                        logger.debug("extractLspPayload: DAS response has result but is missing '$LSP_RESPONSE_KEY': $jsonObject")
                    }
                } else if (jsonObject.has("error")) {
                    pendingLegacyIds.remove(topLevelId)
                    val error = jsonObject.get("error").asJsonObject
                    logger.warn("Received legacy error from DAS for legacyId=$topLevelId (lspId=$lspIdElement): $error")
                    val lspErrorResponse = JsonObject()
                    lspErrorResponse.addProperty("jsonrpc", JSONRPC_VERSION)
                    lspErrorResponse.add("id", lspIdElement)
                    
                    val lspError = JsonObject()
                    lspError.addProperty("code", error.get("code")?.asInt ?: -32603)
                    lspError.addProperty("message", error.get("message")?.asString ?: "Unknown DAS error")
                    if (error.has("data")) {
                        lspError.add("data", error.get("data"))
                    }
                    lspErrorResponse.add("error", lspError)
                    logger.debug("extractLspPayload: Created LSP error response for legacyId=$topLevelId (lspId=$lspIdElement): $lspErrorResponse")
                    return lspErrorResponse
                }
            } else {
                logger.debug("extractLspPayload: Ignored DAS response for legacyId=$topLevelId because it is not in pendingLegacyIds")
            }
        } else {
            logger.debug("extractLspPayload: Ignored DAS message without top-level id: $jsonObject")
        }

        return null
    }

    private fun processLspClientMessages() {
        val requestReader = StreamMessageProducer(requestStream, JSON_HANDLER)
        try {
            requestReader.listen { message ->
                try {
                    logger.debug("LSP message from lsp4ij: $message")
                    when (message) {
                        is RequestMessage -> handleRequestMessage(message)
                        is NotificationMessage -> handleNotificationMessage(message)
                        else -> logger.debug("Ignored unrecognized message type from lsp4ij request: $message")
                    }
                } catch (e: Exception) {
                    logger.error("Error processing LSP message from lsp4ij: $message", e)
                }
            }
        } catch (e: JsonRpcException) {
            val cause = e.cause
            if (cause is IOException) {
                if (isStopping) {
                    logger.info("Connection closed during shutdown: ${cause.message}", e)
                } else {
                    logger.warn("Connection closed unexpectedly: ${cause.message}", e)
                }
            } else {
                logger.error("Error listening for lsp4ij messages", e)
            }
        } catch (e: Exception) {
            logger.error("Unexpected error in LSP message listener loop", e)
        }
    }

    private fun handleRequestMessage(message: RequestMessage) {
        val dartAnalysisService = DartAnalysisServerService.getInstance(project)
        val lspMethod = LspMethod.fromMethod(message.method)
        if (lspMethod == null) {
            logger.info("Ignored unimplemented method from lsp4ij request: ${message.method}")
            return
        }

        when (lspMethod) {
            LspMethod.INITIALIZE -> handleInitializeRequest(message)
            LspMethod.SHUTDOWN -> handleShutdownRequest(message)
            LspMethod.HOVER, LspMethod.DIAGNOSTIC_SERVER -> forwardLspRequestToDas(message, dartAnalysisService)
        }

    }

    private fun handleNotificationMessage(message: NotificationMessage) {
        if (message.method == "$/cancelRequest") {
            val params = message.params ?: return
            val jsonElement = JSON_HANDLER.gson.toJsonTree(params)
            if (!jsonElement.isJsonObject) return
            val cancelParamsObj = jsonElement.asJsonObject
            val lspIdToCancel = if (cancelParamsObj.has("id")) cancelParamsObj.get("id").asString else null
            if (lspIdToCancel == null) return

            val entry = pendingLegacyIds.entries.firstOrNull { it.value.asString == lspIdToCancel } ?: return
            val legacyIdToCancel = entry.key
            pendingLegacyIds.remove(legacyIdToCancel)

            logger.info("Forwarding $/cancelRequest for lspId=$lspIdToCancel as legacy server.cancelRequest for legacyId=$legacyIdToCancel")
            val cancelReqId = DartAnalysisServerService.getInstance(project).generateUniqueId()
            val cancelReq = JsonObject()
            cancelReq.addProperty("id", cancelReqId)
            cancelReq.addProperty("method", "server.cancelRequest")
            val paramsObj = JsonObject()
            paramsObj.addProperty("id", legacyIdToCancel)
            cancelReq.add("params", paramsObj)

            try {
                DartAnalysisServerService.getInstance(project).sendRequest(cancelReqId, cancelReq)
            } catch (e: Exception) {
                logger.error("Failed to send cancelRequest to DAS: $cancelReq", e)
            }
        } else if (message.method == "exit") {
            logger.info("Received exit notification from lsp4ij, stopping connection provider")
            isStopping = true
            ApplicationManager.getApplication().executeOnPooledThread { stop() }
        } else {
            logger.info("Ignored unimplemented method from lsp4ij notification: ${message.method}")
        }
    }

    private fun handleInitializeRequest(message: RequestMessage) {
        val capabilities = ServerCapabilities()
        capabilities.setHoverProvider(DartConfigurable.isExperimentalLspFeaturesEnabled(project))

        val initResult = InitializeResult(capabilities)
        sendSuccessResponse(message.id, initResult)
    }

    private fun handleShutdownRequest(message: RequestMessage) {
        logger.info("Received shutdown request from lsp4ij, transitioning to stopping state")
        isStopping = true
        sendSuccessResponse(message.id, null)
    }


    private fun forwardLspRequestToDas(message: RequestMessage, dartAnalysisService: DartAnalysisServerService) {
        val rawId = message.id ?: return
        val lspIdElement = JSON_HANDLER.gson.toJsonTree(rawId)

        if (!dartAnalysisService.isServerProcessActive) {
            logger.warn("Cannot forward LSP request to DAS because DAS process is not active: $message")
            sendErrorResponse(message.id, ResponseErrorCode.InternalError, "Dart Analysis Server is not active")
            return
        }

        val legacyRequest = JsonObject()
        val legacyId = dartAnalysisService.generateUniqueId()
        pendingLegacyIds[legacyId] = lspIdElement
        legacyRequest.addProperty("id", legacyId)
        legacyRequest.addProperty("method", "lsp.handle")

        val params = JsonObject()
        params.add("lspMessage", JSON_HANDLER.gson.toJsonTree(message).asJsonObject)
        legacyRequest.add("params", params)

        try {
            // Forward to DAS.
            dartAnalysisService.sendRequest(legacyId, legacyRequest)
        } catch (e: Exception) {
            logger.error("Failed to send request to DAS: $legacyRequest", e)
            pendingLegacyIds.remove(legacyId)
            sendErrorResponse(message.id, ResponseErrorCode.InternalError, "Failed to send request to Dart Analysis Server: ${e.message}")
        }
    }

    private fun sendResponseToClient(response: ResponseMessage) {
        val jsonString = JSON_HANDLER.gson.toJson(response)
        enqueueResponse(jsonString)
    }

    private fun sendSuccessResponse(messageId: String?, result: Any?) {
        val response = ResponseMessage()
        response.jsonrpc = JSONRPC_VERSION
        response.id = messageId
        response.result = result
        sendResponseToClient(response)
    }

    private fun sendErrorResponse(messageId: String?, errorCode: ResponseErrorCode, message: String) {
        val response = ResponseMessage()
        response.jsonrpc = JSONRPC_VERSION
        response.id = messageId
        response.error = ResponseError(errorCode, message, null)
        sendResponseToClient(response)
    }

    private fun enqueueResponse(jsonString: String) {
        val service = project.getService(DartLspProjectService::class.java)
        val producer = service.producer
        if (producer == null) {
            logger.warn("DartLspProjectService does not have a registered producer for project: ${project.name}")
            return
        }

        producer.enqueueResponse(jsonString)
    }

    override fun getInputStream(): InputStream? {
        return responseStream
    }

    override fun getOutputStream(): OutputStream? {
        return requestStream.outputStream
    }

    override fun stop() {
        logger.info("Stopping DartVirtualStreamConnectionProvider")
        isStopping = true

        val dartAnalysisService = DartAnalysisServerService.getInstance(project)
        responseListener?.let { dartAnalysisService.removeResponseListener(it) }

        val service = project.getService(DartLspProjectService::class.java)
        service.producer?.stop()
        service.producer = null

        responseStream.close()
        requestStream.close()

        // Wait for the client message thread to terminate.
        // This is primarily needed for legacy unit tests to prevent ThreadLeakTracker from failing tests
        // due to background threads that haven't fully terminated yet.
        if (ApplicationManager.getApplication().isUnitTestMode) {
            clientMessageFuture?.let {
                try {
                    it.get(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    logger.warn("Failed to wait for client message thread to terminate", e)
                }
            }
        }

        pendingLegacyIds.clear()
    }
}


