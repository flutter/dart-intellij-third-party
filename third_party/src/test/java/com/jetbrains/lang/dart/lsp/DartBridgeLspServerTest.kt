/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.Consumer
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.ResponseListener
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import com.google.gson.JsonObject
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InlayHintKind
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class DartBridgeLspServerTest : DartCodeInsightFixtureTestCase() {

    private lateinit var bridgeServer: DartBridgeLspServer
    private lateinit var capturedListener: ResponseListener
    private lateinit var mockServer: RemoteAnalysisServerImpl
    private val mockClient = MockLanguageClient()
    private val capturedRequests = CopyOnWriteArrayList<JsonObject>()

    override fun setUp() {
        super.setUp()

        val das = DartAnalysisServerService.getInstance(project)

        val sdk = requireNotNull(com.jetbrains.lang.dart.sdk.DartSdk.getDartSdk(project)) { "Dart SDK not found" }

        // Align mySdkHome and mySdkVersion in DartAnalysisServerService via reflection to bypass re-start check
        val serviceClass = DartAnalysisServerService::class.java
        
        val sdkHomeField = serviceClass.getDeclaredField("mySdkHome").apply { isAccessible = true }
        sdkHomeField.set(das, sdk.homePath)
        
        val dasSdkVersionField = serviceClass.getDeclaredField("mySdkVersion").apply { isAccessible = true }
        dasSdkVersionField.set(das, sdk.version)

        val stubSocket = createStubSocket()
        mockServer = object : RemoteAnalysisServerImpl(stubSocket) {
            override fun addResponseListener(listener: ResponseListener) {
                capturedListener = listener
                super.addResponseListener(listener)
            }
            
            override fun generateUniqueId(): String = "123"

            override fun isSocketOpen(): Boolean = true

            override fun sendRequestToServer(id: String, request: JsonObject) {
                capturedRequests.add(request)
            }

            override fun sendRequestToServer(id: String, request: JsonObject, consumer: Consumer) {
                capturedRequests.add(request)
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

        das.setServer(mockServer)

        bridgeServer = DartBridgeLspServer(project)
        bridgeServer.connect(mockClient)
    }

    override fun tearDown() {
        try {
            if (::bridgeServer.isInitialized) {
                bridgeServer.stop()
            }
            val das = DartAnalysisServerService.getInstance(project)
            das.setServer(null)
            
            val serviceClass = DartAnalysisServerService::class.java
            val sdkHomeField = serviceClass.getDeclaredField("mySdkHome").apply { isAccessible = true }
            sdkHomeField.set(das, null)
            val dasSdkVersionField = serviceClass.getDeclaredField("mySdkVersion").apply { isAccessible = true }
            dasSdkVersionField.set(das, null)
            
            capturedRequests.clear()
        } finally {
            super.tearDown()
        }
    }

    private fun createStubSocket(): AnalysisServerSocket {
        return object : AnalysisServerSocket {
            override fun getErrorStream(): ByteLineReaderStream? = null
            override fun getRequestSink(): RequestSink? = null
            override fun getResponseStream(): ResponseStream? = null
            override fun isOpen(): Boolean = true
            override fun start() {}
            override fun stop() {}
        }
    }

    fun testForwardRequest() {
        val params = HoverParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        bridgeServer.hover(params)

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        
        assertEquals("123", jsonObject.get("id")?.asString)

        val outerParams = jsonObject.getAsJsonObject("params")
        val lspMessage = outerParams.getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/hover", lspMessage.get("method").asString)
    }

    fun testHandleDasResponse() {
        val params = HoverParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.hover(params)

        // Simulate successful DAS response containing wrapped LSP response
        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "contents": {
                      "kind": "markdown",
                      "value": "Hover Content"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertTrue("Response contents should contain Hover Content", result.contents.toString().contains("Hover Content"))
    }

    fun testDiagnosticServerRequest() {
        val future = bridgeServer.diagnosticServer()

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val outerParams = jsonObject.getAsJsonObject("params")
        val lspMessage = outerParams.getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("dart/diagnosticServer", lspMessage.get("method").asString)
        assertFalse("lspMessage should not have params when null is passed", lspMessage.has("params"))

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "port": 9123
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(9123, result.port)
    }

    fun testForwardNotification() {
        // Simulate a diagnostics notification from DAS
        val notificationJson = """
            {
              "params": {
                "lspMessage": {
                  "jsonrpc": "2.0",
                  "method": "textDocument/publishDiagnostics",
                  "params": {
                    "uri": "file://test.dart",
                    "diagnostics": []
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(notificationJson)

        assertNotNull(mockClient.publishedDiagnostics)
        assertEquals("file://test.dart", mockClient.publishedDiagnostics?.uri)
        assertTrue(mockClient.publishedDiagnostics?.diagnostics?.isEmpty() == true)
    }

    fun testGetFileUriFormatting() {
        val descriptor = DartLspServerDescriptor(project)
        val file = myFixture.configureByText("foo.dart", "void main() {}").virtualFile
        val uri = descriptor.getFileUri(file)
        assertTrue(uri.startsWith("file:///"))
        val pathAfterPrefix = uri.substring("file:///".length)
        if (pathAfterPrefix.length >= 2 && (pathAfterPrefix[1] == ':' || pathAfterPrefix.substring(1).startsWith("%3A"))) {
            assertTrue("Drive letter must be uppercase in: $uri", pathAfterPrefix[0].isUpperCase())
        }
    }

    fun testDocumentHighlightRequest() {
        val params = DocumentHighlightParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.documentHighlight(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/documentHighlight", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": [
                    {"range": {"start": {"line": 0, "character": 4}, "end": {"line": 0, "character": 5}}, "kind": 3},
                    {"range": {"start": {"line": 2, "character": 2}, "end": {"line": 2, "character": 3}}, "kind": 2}
                  ]
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertEquals(2, result.size)
        assertEquals(DocumentHighlightKind.Write, result[0].kind)
        assertEquals(DocumentHighlightKind.Read, result[1].kind)
    }

    fun testClientCapabilities() {
        val lspCaps = JsonObject().apply {
            addProperty("testCap", true)
        }
        mockServer.server_setClientCapabilities(listOf("openUrlRequest"), true, lspCaps)

        val req = requireNotNull(capturedRequests.find { it.get("method")?.asString == "server.setClientCapabilities" }) {
            "A server.setClientCapabilities request should be generated"
        }
        val params = req.getAsJsonObject("params")
        assertEquals(true, params.get("supportsUris").asBoolean)
        val lspCapabilities = params.getAsJsonObject("lspCapabilities")
        assertEquals(true, lspCapabilities.get("testCap").asBoolean)
    }

    fun testInlayHintRequest() {
        val params = InlayHintParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            range = Range(Position(0, 0), Position(10, 0))
        }

        val future = bridgeServer.inlayHint(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/inlayHint", lspMessage.get("method").asString)

        val sentParams = lspMessage.getAsJsonObject("params")
        assertEquals("file://test.dart", sentParams.getAsJsonObject("textDocument").get("uri").asString)
        assertEquals(0, sentParams.getAsJsonObject("range").getAsJsonObject("start").get("line").asInt)
        assertEquals(10, sentParams.getAsJsonObject("range").getAsJsonObject("end").get("line").asInt)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": [
                    {"position": {"line": 0, "character": 5}, "label": "String", "kind": 1},
                    {"position": {"line": 2, "character": 8}, "label": [{"value": "name:"}], "kind": 2}
                  ]
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertEquals(2, result.size)
        assertEquals(InlayHintKind.Type, result[0].kind)
        assertEquals("String", result[0].label.left)
        assertEquals(InlayHintKind.Parameter, result[1].kind)
        assertEquals("name:", result[1].label.right[0].value)
    }

    fun testInlayHintRequestWithErrorResponse() {
        val params = InlayHintParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            range = Range(Position(0, 0), Position(10, 0))
        }

        val future = bridgeServer.inlayHint(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "error": {
                    "code": -32001,
                    "message": "File not analyzed"
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertTrue(result.isEmpty())
    }

    fun testInlayHintRequestWithNullResult() {
        val params = InlayHintParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            range = Range(Position(0, 0), Position(10, 0))
        }

        val future = bridgeServer.inlayHint(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": null
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertTrue(result.isEmpty())
    }

    private class MockLanguageClient : LanguageClient {
        var publishedDiagnostics: PublishDiagnosticsParams? = null

        override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
            publishedDiagnostics = diagnostics
        }

        override fun telemetryEvent(`object`: Any?) {}
        override fun showMessage(messageParams: MessageParams?) {}
        override fun showMessageRequest(requestMessageParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
            return CompletableFuture.completedFuture(null)
        }
        override fun logMessage(messageParams: MessageParams?) {}
    }
}
