/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp


import com.google.gson.JsonParser
import com.intellij.openapi.util.Disposer
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.AnalysisServerSocket
import com.google.gson.JsonObject
import com.google.dart.server.Consumer
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import org.dartlang.analysis.server.protocol.MessageAction
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams

import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import java.util.concurrent.TimeUnit

class DartVirtualStreamConnectionProviderTest : DartCodeInsightFixtureTestCase() {

    private lateinit var provider: DartVirtualStreamConnectionProvider
    private lateinit var producer: DartMessageProducer
    private val jsonHandler = MessageJsonHandler(mapOf())

    override fun setUp() {
        super.setUp()

        provider = DartVirtualStreamConnectionProvider(project)
        Disposer.register(testRootDisposable) {
            provider.stop()
        }

        producer = DartMessageProducer(jsonHandler)

        val service = project.getService(DartLspProjectService::class.java)
        service.producer = producer

        provider.start()
    }

    private fun sendRequest(request: RequestMessage) {
        val json = jsonHandler.gson.toJson(request)
        val header = "Content-Length: ${json.toByteArray(Charsets.UTF_8).size}\r\n\r\n"

        val outputStream = provider.getOutputStream() ?: throw AssertionError("Provider output stream is null")
        outputStream.write(header.toByteArray(Charsets.UTF_8))
        outputStream.write(json.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    fun testInitializeRequest() {
        val request = RequestMessage().apply {
            jsonrpc = "2.0"
            id = "1"
            method = "initialize"
        }

        sendRequest(request)

        val responseJson = producer.responseQueue.poll(5, TimeUnit.SECONDS)
        assertNotNull("No response received from provider", responseJson)

        val jsonObject = JsonParser.parseString(responseJson).asJsonObject
        assertEquals("2.0", jsonObject.get("jsonrpc").asString)
        assertEquals("1", jsonObject.get("id").asString)
        assertTrue(jsonObject.has("result"))
        
        val result = jsonObject.get("result").asJsonObject
        assertTrue(result.has("capabilities"))
    }

    fun testShutdownRequest() {
        val request = RequestMessage().apply {
            jsonrpc = "2.0"
            id = "2"
            method = "shutdown"
        }

        sendRequest(request)

        val responseJson = producer.responseQueue.poll(5, TimeUnit.SECONDS)
        assertNotNull("No response received from provider", responseJson)

        val jsonObject = JsonParser.parseString(responseJson).asJsonObject
        assertEquals("2.0", jsonObject.get("jsonrpc").asString)
        assertEquals("2", jsonObject.get("id").asString)
        assertTrue(jsonObject.has("result"))
        assertTrue(jsonObject.get("result").isJsonNull)
    }

    fun testDiagnosticServerRequest() {
        val request = RequestMessage().apply {
            jsonrpc = "2.0"
            id = "3"
            method = "dart/diagnosticServer"
        }

        var capturedRequest: String? = null
        val mockServer = createMockServer { json -> capturedRequest = json }

        val dartAnalysisService = DartAnalysisServerService.getInstance(project)
        dartAnalysisService.setServer(mockServer)

        try {
            // Send initialize request first to put server in initialized state
            val initRequest = RequestMessage().apply {
                jsonrpc = "2.0"
                id = "1"
                method = "initialize"
            }
            sendRequest(initRequest)
            producer.responseQueue.poll(5, TimeUnit.SECONDS) // consume response

            sendRequest(request)

            // Wait for the request to be captured
            var attempts = 0
            while (capturedRequest == null && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }

            assertNotNull("No request captured by listener", capturedRequest)

            val jsonObject = JsonParser.parseString(capturedRequest).asJsonObject
            assertEquals("lsp.handle", jsonObject.get("method").asString)

            val params = jsonObject.get("params").asJsonObject
            assertTrue(params.has("lspMessage"))

            val lspMessage = params.get("lspMessage").asJsonObject
            assertEquals("2.0", lspMessage.get("jsonrpc").asString)
            assertEquals("3", lspMessage.get("id").asString)
            assertEquals("dart/diagnosticServer", lspMessage.get("method").asString)
        } finally {
            dartAnalysisService.setServer(null)
        }
    }

    private fun createMockServer(onRequestSent: (String) -> Unit): RemoteAnalysisServerImpl {
        val stubSocket = object : AnalysisServerSocket {
            override fun getErrorStream(): ByteLineReaderStream? = null
            override fun getRequestSink(): RequestSink? = null
            override fun getResponseStream(): ResponseStream? = null
            override fun isOpen(): Boolean = false
            override fun start() {}
            override fun stop() {}
        }

        return object : RemoteAnalysisServerImpl(stubSocket) {
            override fun generateUniqueId(): String = "123"

            override fun sendRequestToServer(id: String, request: JsonObject) {
                onRequestSent(request.toString())
            }

            override fun sendRequestToServer(id: String, request: JsonObject, consumer: Consumer) {
                onRequestSent(request.toString())
            }

            override fun server_openUrlRequest(url: String?) {}

            override fun server_showMessageRequest(
                messageType: String?,
                message: String?,
                messageActions: MutableList<MessageAction>?,
                consumer: ShowMessageRequestConsumer?
            ) {}

            override fun lsp_workspaceApplyEdit(
                params: DartLspApplyWorkspaceEditParams?,
                consumer: DartLspWorkspaceApplyEditRequestConsumer?
            ) {}
        }
    }

    fun testLanguageServerFactoryInUnitTestMode() {
        val factory = DartLanguageServerFactory()
        val connectionProvider = factory.createConnectionProvider(project)
        
        assertNotNull(connectionProvider)
        try {
            connectionProvider.start()
            fail("Expected IOException")
        } catch (e: java.io.IOException) {
            assertEquals("LSP server is disabled in unit tests", e.message)
        }
        assertNull(connectionProvider.getInputStream())
        assertNull(connectionProvider.getOutputStream())
    }
}
