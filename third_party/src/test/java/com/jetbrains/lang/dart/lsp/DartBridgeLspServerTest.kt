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
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class DartBridgeLspServerTest : DartCodeInsightFixtureTestCase() {

    private lateinit var bridgeServer: DartBridgeLspServer
    private lateinit var capturedListener: ResponseListener
    private val mockClient = MockLanguageClient()
    private val capturedRequests = CopyOnWriteArrayList<JsonObject>()

    private var originalSdkVersion: String? = null

    override fun setUp() {
        super.setUp()

        val das = DartAnalysisServerService.getInstance(project)

        // Stub the version of the test SDK to a modern version (e.g. 3.0.0) to bypass version sufficiency check
        val sdk = requireNotNull(com.jetbrains.lang.dart.sdk.DartSdk.getDartSdk(project)) { "Dart SDK not found" }
        originalSdkVersion = sdk.version
        val sdkClass = com.jetbrains.lang.dart.sdk.DartSdk::class.java
        val sdkVersionField = sdkClass.getDeclaredField("myVersion").apply { isAccessible = true }
        sdkVersionField.set(sdk, "3.0.0")

        // Align mySdkHome and mySdkVersion in DartAnalysisServerService via reflection to bypass re-start check
        val serviceClass = DartAnalysisServerService::class.java
        
        val sdkHomeField = serviceClass.getDeclaredField("mySdkHome").apply { isAccessible = true }
        sdkHomeField.set(das, sdk.homePath)
        
        val dasSdkVersionField = serviceClass.getDeclaredField("mySdkVersion").apply { isAccessible = true }
        dasSdkVersionField.set(das, "3.0.0")

        val stubSocket = createStubSocket()
        val mockServer = object : RemoteAnalysisServerImpl(stubSocket) {
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
            
            val sdk = com.jetbrains.lang.dart.sdk.DartSdk.getDartSdk(project)
            if (sdk != null && originalSdkVersion != null) {
                val sdkClass = com.jetbrains.lang.dart.sdk.DartSdk::class.java
                val sdkVersionField = sdkClass.getDeclaredField("myVersion").apply { isAccessible = true }
                sdkVersionField.set(sdk, originalSdkVersion)
            }
            
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

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

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

    fun testEitherCommandAndCodeActionDeserialization() {
        val gson = DartBridgeLspServer.GSON
        val jsonInput = """
        [
          {
            "title": "Sort Members",
            "command": "dart.edit.sortMembers",
            "arguments": [
              {
                "path": "/Users/helinx/Documents/embeddings_playground/bin/main.dart",
                "autoTriggered": true
              }
            ]
          },
          {
            "title": "Import library 'dart:io'",
            "kind": "quickfix.import.librarySdk",
            "command": {
              "title": "Import library 'dart:io'",
              "command": "dart.edit.codeAction.apply",
              "arguments": [
                {
                  "textDocument": {
                    "uri": "file:///Users/helinx/Documents/embeddings_playground/bin/main.dart",
                    "version": null
                  },
                  "range": {
                    "start": { "character": 39, "line": 11 },
                    "end": { "character": 39, "line": 11 }
                  },
                  "kind": "quickfix.import.librarySdk"
                }
              ]
            }
          }
        ]
        """.trimIndent()

        val type = object : TypeToken<List<Either<Command, CodeAction>>>() {}.type
        val result: List<Either<Command, CodeAction>> = gson.fromJson(jsonInput, type)

        assertEquals(2, result.size)

        // First item should deserialize cleanly as a Left (Command)
        assertTrue("First item should be Left (Command)", result[0].isLeft)
        assertEquals("dart.edit.sortMembers", result[0].left.command)
        assertEquals("Sort Members", result[0].left.title)

        // Second item should deserialize cleanly as a Right (CodeAction)
        assertTrue("Second item should be Right (CodeAction)", result[1].isRight)
        assertEquals("Import library 'dart:io'", result[1].right.title)
        assertEquals("quickfix.import.librarySdk", result[1].right.kind)
        assertNotNull(result[1].right.command)
        assertEquals("dart.edit.codeAction.apply", result[1].right.command.command)
    }

    fun testExecuteCommandNormalizationPreservesVersionNull() {
        val gson = DartBridgeLspServer.GSON

        val tdObj = JsonObject().apply {
            addProperty("uri", "file:///path/to/main.dart")
        }
        val rangeObj = JsonObject().apply {
            add("start", JsonObject().apply { addProperty("line", 10); addProperty("character", 5) })
            add("end", JsonObject().apply { addProperty("line", 10); addProperty("character", 5) })
        }
        val rawMap = JsonObject().apply {
            add("textDocument", tdObj)
            add("range", rangeObj)
            addProperty("kind", "quickfix.import.librarySdk")
        }

        // Verify that normalizing textDocument ensures "version": null (JsonNull) is included
        // and serialized when serializeNulls is enabled
        val td = rawMap.get("textDocument").asJsonObject
        val normalizedTd = JsonObject().apply {
            addProperty("uri", td.get("uri").asString)
            add("version", JsonNull.INSTANCE)
        }
        val normalizedMap = JsonObject().apply {
            add("textDocument", normalizedTd)
            add("range", rawMap.get("range"))
            add("kind", rawMap.get("kind"))
        }

        val serialized = gson.toJson(listOf(normalizedMap))
        assertTrue("Serialized output must explicitly contain \"version\":null for OptionalVersionedTextDocumentIdentifier validation",
            serialized.contains("\"version\":null"))
        assertTrue("Serialized output must explicitly contain valid string uri",
            serialized.contains("\"uri\":\"file:///path/to/main.dart\""))
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
