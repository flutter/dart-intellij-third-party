package com.jetbrains.lang.dart.lsp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.google.dart.server.ResponseListener
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.logging.PluginLogger
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

class DartVirtualStreamConnectionProvider(private val project: Project) : StreamConnectionProvider {
    companion object {
        private val logger = PluginLogger.createLogger(DartVirtualStreamConnectionProvider::class.java)
        private val JSON_HANDLER = MessageJsonHandler(mapOf())
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

    override fun start() {
        logger.info("Starting DartVirtualStreamConnectionProvider")
        val dartAnalysisService = DartAnalysisServerService.getInstance(project)

        ApplicationManager.getApplication().executeOnPooledThread {
            // Listen for analysis server responses.
            responseListener = ResponseListener { response ->
                logger.debug("Response received from DAS: $response")
                val jsonObject = JsonParser.parseString(response).asJsonObject

                var lspPayload: JsonObject? = null

                // Try to extract a message.
                if (jsonObject.has("params")) {
                    val params = jsonObject.get("params").asJsonObject
                    if (params.has("lspMessage")) {
                        lspPayload = params.get("lspMessage").asJsonObject
                    }
                }

                // Try to extract a response.
                if (lspPayload == null && jsonObject.has("result")) {
                    val topLevelId = if (jsonObject.has("id")) jsonObject.get("id").asString else null
                    if (topLevelId != null && pendingLegacyIds.remove(topLevelId)) {
                        val result = jsonObject.get("result").asJsonObject
                        if (result.has("lspResponse")) {
                            lspPayload = result.get("lspResponse").asJsonObject
                        }
                    }
                }

                if (lspPayload != null) {
                    val producer = DartMessageProducer.getProducer(project)
                    producer?.enqueueResponse(lspPayload.toString())
                }
            }
            dartAnalysisService.addResponseListener(responseListener!!)

            logger.info("Finished setting up DAS listening for lsp4ij")

            // Listen for lsp4ij messages.
            val requestReader = StreamMessageProducer(virtualServerLspInputStream, JSON_HANDLER)
            try {
                requestReader.listen { message ->
                    logger.debug("Message from lsp4ij: $message")

                    if (message is RequestMessage) {
                        when (val method = message.method) {
                        "initialize" -> {
                            // Write a response to lsp4ij pretending to initialize a server.
                            val capabilities = ServerCapabilities()

                            // TODO(helin24): Enable hover requests.
                            // capabilities.setHoverProvider(true)

                            val initResult = InitializeResult(capabilities)

                            val response = ResponseMessage()
                            response.jsonrpc = "2.0"
                            response.id = message.id
                            response.result = initResult

                            sendResponseToClient(response)
                        }
                        "shutdown" -> {
                            val response = ResponseMessage()
                            response.jsonrpc = "2.0"
                            response.id = message.id
                            response.result = null

                            sendResponseToClient(response)
                        }
//                        "textDocument/hover" -> {
//                            logger.info("Hover message received: $message")
//                            val legacyRequest = JsonObject()
//                            val legacyId = dartAnalysisService.generateUniqueId()
//                            pendingLegacyIds.add(legacyId)
//                            legacyRequest.addProperty("id", legacyId)
//                            legacyRequest.addProperty("method", "lsp.handle")
//
//                            val params = JsonObject()
//                            params.add("lspMessage", JSON_HANDLER.gson.toJsonTree(message).asJsonObject)
//                            legacyRequest.add("params", params)
//
//                            println("hover request: $legacyRequest")
//                            // Forward to DAS.
//                            dartAnalysisService.sendRequest(legacyId, legacyRequest)
//                        }
                        else -> {
                            logger.info("Ignored unimplemented method from lsp4ij request: $method")
                        }
                    }
                } else if (message is NotificationMessage) {
                    logger.info("Ignored unimplemented method from lsp4ij notification: ${message.method}")
                } else {
                    logger.info("Ignored unrecognized message type from lsp4ij request: $message")
                }
                }
            } catch (e: JsonRpcException) {
                if (e.cause is IOException && e.cause!!.message == "Pipe broken") {
                    logger.info("Pipe broken while listening for lsp4ij messages (normal during shutdown)")
                } else {
                    logger.error("Error listening for lsp4ij messages", e)
                }
            }
        }
    }

    private fun sendResponseToClient(response: ResponseMessage) {
        val jsonString = JSON_HANDLER.gson.toJson(response)
        enqueueResponse(jsonString)
    }

    private fun enqueueResponse(jsonString: String) {
        val producer = DartMessageProducer.getProducer(project)
        if (producer == null) {
            logger.warn("DartMessageProducer does not have a registered producer for project: ${project.name}")
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

        val dartAnalysisService = DartAnalysisServerService.getInstance(project)
        responseListener?.let { dartAnalysisService.removeResponseListener(it) }

        val producer = DartMessageProducer.getProducer(project)
        producer?.stop()
        DartMessageProducer.unregisterProducer(project)

        clientLspInputStream.close()
        virtualServerLspOutputStream.close()
        virtualServerLspInputStream.close()
        clientLspOutputStream.close()

        pendingLegacyIds.clear()
    }
}