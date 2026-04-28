/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

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
import java.io.PipedInputStream
import java.io.PipedOutputStream
import org.eclipse.lsp4j.jsonrpc.JsonRpcException

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
    // I'm not even sure we need this at all.
    val clientLspInputStream = PipedInputStream(1024 * 1024)
    val virtualServerLspOutputStream = PipedOutputStream()

    // Stream for the lsp4ij client to write requests to the virtual server.
    val virtualServerLspInputStream = PipedInputStream(1024 * 1024)
    val clientLspOutputStream = PipedOutputStream()

    init {
        clientLspInputStream.connect(virtualServerLspOutputStream)
        virtualServerLspInputStream.connect(clientLspOutputStream)
    }

    // This stores IDs that have been used by lsp4ij for LSP-over-legacy requests to DAS.
    // There are non-lsp4ij requests that are sent as LSP-over-legacy, but we don't need to forward responses for those
    // requests to lsp4ij since lsp4ij was not the originator.
    private val pendingLegacyIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var responseListener: ResponseListener? = null
    private var isStopping = false
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

            var lspPayload: JsonObject? = null

            if (jsonObject.has("params")) {
                val params = jsonObject.get("params").asJsonObject
                if (params.has(LSP_MESSAGE_KEY)) {
                    lspPayload = params.get(LSP_MESSAGE_KEY).asJsonObject
                }
            }

            if (lspPayload == null && jsonObject.has("result")) {
                val topLevelId = if (jsonObject.has("id")) jsonObject.get("id").asString else null
                if (topLevelId != null && pendingLegacyIds.remove(topLevelId)) {
                    val result = jsonObject.get("result").asJsonObject
                    if (result.has(LSP_RESPONSE_KEY)) {
                        lspPayload = result.get(LSP_RESPONSE_KEY).asJsonObject
                    }
                }
            }

            if (lspPayload != null) {
                enqueueResponse(lspPayload.toString())
            }
        }
        this.responseListener = listener
        dartAnalysisService.addResponseListener(listener)
        logger.info("Finished setting up DAS listening for lsp4ij")
    }

    private fun processLspClientMessages() {
        val requestReader = StreamMessageProducer(virtualServerLspInputStream, JSON_HANDLER)
        try {
            requestReader.listen { message ->
                logger.debug("Message from lsp4ij: $message")
                when (message) {
                    is RequestMessage -> handleRequestMessage(message)
                    is NotificationMessage -> handleNotificationMessage(message)
                    else -> logger.info("Ignored unrecognized message type from lsp4ij request: $message")
                }
            }
        } catch (e: JsonRpcException) {
            val cause = e.cause
            if (cause is IOException) {
                if (isStopping) {
                    logger.info("Connection closed during shutdown: ${cause.message}")
                } else {
                    logger.warn("Connection closed unexpectedly: ${cause.message}")
                }
            } else {
                logger.error("Error listening for lsp4ij messages", e)
            }
        }
    }

    private fun handleRequestMessage(message: RequestMessage) {
        val dartAnalysisService = DartAnalysisServerService.getInstance(project)
        when (val method = LspMethod.fromMethod(message.method)) {
            LspMethod.INITIALIZE -> handleInitializeRequest(message)
            LspMethod.SHUTDOWN -> handleShutdownRequest(message)
            LspMethod.HOVER -> handleHoverRequest(message, dartAnalysisService)
            null -> logger.info("Ignored unimplemented method from lsp4ij request: ${message.method}")
        }
    }

    private fun handleNotificationMessage(message: NotificationMessage) {
        logger.info("Ignored unimplemented method from lsp4ij notification: ${message.method}")
    }

    private fun handleInitializeRequest(message: RequestMessage) {
        val capabilities = ServerCapabilities()
        capabilities.setHoverProvider(DartConfigurable.isExperimentalLspFeaturesEnabled(project))

        val initResult = InitializeResult(capabilities)
        sendSuccessResponse(message.id, initResult)
    }

    private fun handleShutdownRequest(message: RequestMessage) {
        sendSuccessResponse(message.id, null)
    }

    private fun handleHoverRequest(message: RequestMessage, dartAnalysisService: DartAnalysisServerService) {
        logger.info("Hover message received: $message")
        val legacyRequest = JsonObject()
        val legacyId = dartAnalysisService.generateUniqueId()
        pendingLegacyIds.add(legacyId)
        legacyRequest.addProperty("id", legacyId)
        legacyRequest.addProperty("method", "lsp.handle")

        val params = JsonObject()
        params.add("lspMessage", JSON_HANDLER.gson.toJsonTree(message).asJsonObject)
        legacyRequest.add("params", params)

        // Forward to DAS.
        dartAnalysisService.sendRequest(legacyId, legacyRequest)
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
        return clientLspInputStream
    }

    override fun getOutputStream(): OutputStream? {
        return clientLspOutputStream
    }

    override fun stop() {
        logger.info("Stopping DartVirtualStreamConnectionProvider")
        isStopping = true

        val dartAnalysisService = DartAnalysisServerService.getInstance(project)
        responseListener?.let { dartAnalysisService.removeResponseListener(it) }

        val service = project.getService(DartLspProjectService::class.java)
        service.producer?.stop()
        service.producer = null

        clientLspInputStream.close()
        virtualServerLspOutputStream.close()
        virtualServerLspInputStream.close()
        clientLspOutputStream.close()

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
