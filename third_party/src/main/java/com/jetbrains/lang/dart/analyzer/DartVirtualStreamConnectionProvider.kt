package com.jetbrains.lang.dart.analyzer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.logging.PluginLogger
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets

class DartVirtualStreamConnectionProvider(private val project: Project) : StreamConnectionProvider {
    companion object {
        private val logger = PluginLogger.createLogger(DartVirtualStreamConnectionProvider::class.java)
        private val JSON_HANDLER = MessageJsonHandler(mapOf())
    }

    // Stream for writing LSP responses from the virtual server to the lsp4ij client.
    // I'm not even sure we need this at all.
    val clientLspInputStream = PipedInputStream(1024*1024)
    val virtualServerLspOutputStream = PipedOutputStream()

    // Stream for the lsp4ij client to write requests to the virtual server.
    val virtualServerLspInputStream = PipedInputStream(1024*1024)
    val clientLspOutputStream = PipedOutputStream()

    init {
        clientLspInputStream.connect(virtualServerLspOutputStream)
        virtualServerLspInputStream.connect(clientLspOutputStream)
    }

    override fun start() {
        logger.info("Starting DartVirtualStreamConnectionProvider")
        println("Starting DartVirtualStreamConnectionProvider")
        ApplicationManager.getApplication().executeOnPooledThread {
            // Listen for lsp4ij messages.
            val requestReader = StreamMessageProducer(virtualServerLspInputStream, JSON_HANDLER)
            requestReader.listen { message ->
                println("reader received: $message")

                if (message is RequestMessage) {
                    when (val method = message.method) {
                        "initialize" -> {
                            // Write a response to lsp4ij pretending to initialize a server.
                            val capabilities = ServerCapabilities()
                            capabilities.setHoverProvider(true)

                            val initResult = InitializeResult(capabilities)

                            val response = ResponseMessage()
                            response.jsonrpc = "2.0"
                            response.id = message.id
                            response.result = initResult

                            sendResponseToClient(response)
                        }
                        "shutdown" -> {
                            // Write a response to lsp4ij
                        }
                        "textDocument/hover" -> {
                            // Forward to DAS.
                        }
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

            // Listen for analysis server responses.
            DartAnalysisServerService.getInstance(project).addResponseListener { response ->
                println("DAS response: $response")

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
                if (lspPayload != null && jsonObject.has("payload")) {
                    val result = jsonObject.get("result").asJsonObject
                    if (result.has("lspResponse")) {
                        lspPayload = result.get("lspResponse").asJsonObject
                    }
                }

                // Send to stream
            }
        }
    }

    private fun sendResponseToClient(response: ResponseMessage) {
        val jsonString = JSON_HANDLER.gson.toJson(response)
        println("Sending response: $jsonString")
        val body: ByteArray = jsonString.encodeToByteArray()
        val header = "Content-Length: " + body.size + "\r\n\r\n";
        virtualServerLspOutputStream.write(header.encodeToByteArray())
        virtualServerLspOutputStream.write(body)
        virtualServerLspOutputStream.flush()
    }

    override fun getInputStream(): InputStream? {
        return clientLspInputStream
    }

    override fun getOutputStream(): OutputStream? {
        return clientLspOutputStream
    }

    override fun stop() {
        println("Stopping DartVirtualStreamConnectionProvider")

    }
}