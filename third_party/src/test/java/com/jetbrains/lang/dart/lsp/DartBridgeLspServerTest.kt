/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.Consumer
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.JsonConsumer
import com.google.dart.server.ResponseListener
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.platform.dartlsp.api.customization.LspCommandsDisabled
import com.intellij.platform.dartlsp.api.customization.LspSignatureHelpDisabled
import com.intellij.platform.dartlsp.api.customization.LspSignatureHelpSupport
import com.intellij.ui.components.JBTextField
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction
import org.dartlang.analysis.server.protocol.RequestError
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InlayHintKind
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.SemanticTokenModifiers
import org.eclipse.lsp4j.SemanticTokenTypes
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.WorkspaceService
import org.eclipse.lsp4j.jsonrpc.Launcher
import com.jetbrains.lang.dart.highlight.DartSyntaxHighlighterColors
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class DartBridgeLspServerTest : DartCodeInsightFixtureTestCase() {

    private lateinit var bridgeServer: DartBridgeLspServer
    private lateinit var capturedListener: ResponseListener
    private lateinit var mockServer: RemoteAnalysisServerImpl
    private val mockClient = MockLanguageClient()
    private val capturedRequests = CopyOnWriteArrayList<JsonObject>()
    private val capturedResponses = CopyOnWriteArrayList<JsonObject>()
    private val capturedNotifications = CopyOnWriteArrayList<JsonObject>()
    private var capturedConsumer: Consumer? = null

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
                capturedConsumer = consumer
            }

            override fun sendResponseToServer(response: JsonObject) {
                capturedResponses.add(response)
            }

            override fun sendNotificationToServer(notification: JsonObject) {
                capturedNotifications.add(notification)
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
            capturedNotifications.clear()
            capturedResponses.clear()
            capturedConsumer = null
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

    fun testCodeActionRequest() {
        val params = CodeActionParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            range = Range(Position(0, 0), Position(0, 5))
            context = CodeActionContext(emptyList())
        }

        val future = bridgeServer.codeAction(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/codeAction", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": [
                    {
                      "title": "Sort Members",
                      "kind": "source.sortMembers",
                      "command": {
                        "command": "dart.edit.sortMembers",
                        "title": "Sort Members"
                      }
                    },
                    {
                      "title": "Import library 'dart:io'",
                      "kind": "quickfix.import.librarySdk",
                      "command": {
                        "command": "dart.edit.codeAction.apply",
                        "title": "Import library 'dart:io'"
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertEquals(2, result.size)

        // First action should be wrapped as Right(CodeAction)
        assertTrue("First item should be Right (CodeAction)", result[0].isRight)
        assertEquals("Sort Members", result[0].right.title)
        assertEquals("source.sortMembers", result[0].right.kind)
        assertEquals("dart.edit.sortMembers", result[0].right.command.command)

        // Second action should be wrapped as Right(CodeAction)
        assertTrue("Second item should be Right (CodeAction)", result[1].isRight)
        assertEquals("Import library 'dart:io'", result[1].right.title)
        assertEquals("quickfix.import.librarySdk", result[1].right.kind)
        assertNotNull(result[1].right.command)
        assertEquals("dart.edit.codeAction.apply", result[1].right.command.command)
    }

    fun testExecuteCommandRequest() {
        val params = ExecuteCommandParams("dart.edit.sortMembers", listOf(JsonObject().apply {
            addProperty("path", "/path/to/main.dart")
        }))

        val future = bridgeServer.executeCommand(params)

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("workspace/executeCommand", lspMessage.get("method").asString)
        assertEquals("dart.edit.sortMembers", lspMessage.getAsJsonObject("params").get("command").asString)

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
        assertNull(future.get(5, TimeUnit.SECONDS))
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

    fun testConnectToDtdRequest() {
        val dtdUri = "ws://127.0.0.1:9124/secret"
        val future = bridgeServer.connectToDtd(ConnectToDtdParams(dtdUri))

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val outerParams = jsonObject.getAsJsonObject("params")
        val lspMessage = outerParams.getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("dart/connectToDtd", lspMessage.get("method").asString)
        val params = lspMessage.getAsJsonObject("params")
        assertNotNull("lspMessage should include params with uri", params)
        assertEquals(dtdUri, params.get("uri").asString)

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

        assertNull(future.get(5, TimeUnit.SECONDS))
    }

    fun testDescriptorLspServerListenerDoesNotEagerlyCreateDtdService() {
        var getServiceIfCreatedCalled = false
        var getServiceCalled = false
        val mockProject = Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java)
        ) { _, method, args ->
            if (args?.firstOrNull() == DartToolingDaemonService::class.java) {
                when (method.name) {
                    "getServiceIfCreated" -> {
                        getServiceIfCreatedCalled = true
                        return@newProxyInstance null
                    }
                    "getService" -> {
                        getServiceCalled = true
                        return@newProxyInstance null
                    }
                }
            }
            if (args == null) method.invoke(project) else method.invoke(project, *args)
        } as Project

        val descriptor = DartLspServerDescriptor(mockProject)
        val listener = descriptor.lspServerListener
        assertNotNull("DartLspServerDescriptor should provide an LspServerListener", listener)

        listener.serverInitialized(InitializeResult())

        assertTrue(
            "serverInitialized should query DartToolingDaemonService via getServiceIfCreated",
            getServiceIfCreatedCalled
        )
        assertFalse(
            "serverInitialized should not eagerly instantiate DartToolingDaemonService via getService",
            getServiceCalled
        )
    }

    fun testConnectToDtdIgnoresAlreadyConnectedStateError() {
        val dtdUri = "ws://127.0.0.1:9124/secret"
        val future = bridgeServer.connectToDtd(ConnectToDtdParams(dtdUri))

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "error": {
                    "code": -32015,
                    "message": "Server is already connected to DTD"
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        assertNull(future.get(5, TimeUnit.SECONDS))
    }

    fun testDescriptorLspServerListenerConnectsWhenDtdIsRunning() {
        val dtdUri = "ws://127.0.0.1:9125/secret"
        val dtdService = DartToolingDaemonService.getInstance(project)
        val uriField = DartToolingDaemonService::class.java.getDeclaredField("uri").apply { isAccessible = true }
        val sdk = DartSdk.getDartSdk(project)!!
        val versionField = DartSdk::class.java.getDeclaredField("myVersion").apply { isAccessible = true }
        val originalVersion = sdk.version

        val requestSentLatch = CountDownLatch(1)
        val mockLspServer = Proxy.newProxyInstance(
            LspServer::class.java.classLoader,
            arrayOf(LspServer::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getState" -> LspServerState.Running
                "sendRequestSync" -> {
                    @Suppress("UNCHECKED_CAST")
                    val sender = args.last() as (Lsp4jServer) -> CompletableFuture<Any?>
                    sender(bridgeServer)
                    requestSentLatch.countDown()
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        } as LspServer

        val mockLspServerManager = Proxy.newProxyInstance(
            LspServerManager::class.java.classLoader,
            arrayOf(LspServerManager::class.java)
        ) { _, method, _ ->
            if (method.name == "getServersForProvider") listOf(mockLspServer) else null
        } as LspServerManager

        val mockProject = Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java)
        ) { _, method, args ->
            when {
                args?.firstOrNull() == DartToolingDaemonService::class.java && method.name == "getServiceIfCreated" -> dtdService
                args?.firstOrNull() == LspServerManager::class.java && method.name == "getService" -> mockLspServerManager
                args == null -> method.invoke(project)
                else -> method.invoke(project, *args)
            }
        } as Project

        try {
            versionField.set(sdk, "3.8.0")
            uriField.set(dtdService, dtdUri)

            val descriptor = DartLspServerDescriptor(mockProject)
            descriptor.lspServerListener.serverInitialized(InitializeResult())

            assertTrue("Expected dart/connectToDtd request to be sent", requestSentLatch.await(5, TimeUnit.SECONDS))
            val connectRequests = capturedRequests.filter {
                it.get("method")?.asString == "lsp.handle" &&
                    it.getAsJsonObject("params")?.getAsJsonObject("lspMessage")?.get("method")?.asString == "dart/connectToDtd"
            }
            assertEquals(1, connectRequests.size)
            assertEquals(
                dtdUri,
                connectRequests[0].getAsJsonObject("params").getAsJsonObject("lspMessage").getAsJsonObject("params").get("uri").asString
            )

            // Calling connectToDtd again for the same running LspServer and URI must not send a duplicate request.
            DartLspService.connectToDtd(mockProject, dtdUri).get(5, TimeUnit.SECONDS)
            val connectRequestsAfterDuplicate = capturedRequests.filter {
                it.get("method")?.asString == "lsp.handle" &&
                    it.getAsJsonObject("params")?.getAsJsonObject("lspMessage")?.get("method")?.asString == "dart/connectToDtd"
            }
            assertEquals(1, connectRequestsAfterDuplicate.size)
        } finally {
            uriField.set(dtdService, null)
            versionField.set(sdk, originalVersion)
        }
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

    fun testForwardNotificationSendsALegacyNotification() {
        bridgeServer.forwardNotification("workspace/didChangeConfiguration", DidChangeConfigurationParams(JsonObject()))

        // The server never answers a notification, so wrapping it in an `lsp.handle` request would
        // leave that request unanswered; it travels as a legacy `lsp.notification` instead.
        assertEquals("a notification must not be sent as a request", 0, capturedRequests.size)
        assertEquals(1, capturedNotifications.size)

        val notification = capturedNotifications[0]
        assertEquals("lsp.notification", notification.get("event").asString)
        assertFalse("a legacy notification must not carry an id", notification.has("id"))

        val params = requireNotNull(notification.getAsJsonObject("params")) { "notification should carry params: $notification" }
        val lspNotification = requireNotNull(params.getAsJsonObject("lspNotification")) {
            "params should carry the LSP notification: $params"
        }
        assertEquals("2.0", lspNotification.get("jsonrpc").asString)
        assertEquals("workspace/didChangeConfiguration", lspNotification.get("method").asString)
        assertFalse("an LSP notification must not carry an id", lspNotification.has("id"))

        val settings = requireNotNull(lspNotification.getAsJsonObject("params")?.getAsJsonObject("settings")) {
            "the notification should carry empty settings: $lspNotification"
        }
        assertEquals("the server re-reads the settings itself", JsonObject(), settings)
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

    fun testCompletionRequest() {
        val params = CompletionParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.completion(params)

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id")?.asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/completion", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "isIncomplete": false,
                    "items": [
                      {
                        "label": "print",
                        "kind": 3,
                        "detail": "void print(Object? object)",
                        "insertText": "print(${'$'}{1:object})",
                        "insertTextFormat": 2
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertTrue(result.isRight)
        val completionList = result.right
        assertEquals(1, completionList.items.size)
        assertEquals("print", completionList.items[0].label)
        assertEquals(CompletionItemKind.Function, completionList.items[0].kind)
        assertEquals("print(\${1:object})", completionList.items[0].insertText)
        assertEquals(InsertTextFormat.Snippet, completionList.items[0].insertTextFormat)
    }

    fun testCompletionResolveRequest() {
        val unresolved = CompletionItem().apply {
            label = "unresolvedItem"
        }

        val future = bridgeServer.resolveCompletionItem(unresolved)

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id")?.asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("completionItem/resolve", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "label": "unresolvedItem",
                    "detail": "Resolved Detail",
                    "documentation": {
                      "kind": "markdown",
                      "value": "Resolved Docs"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals("unresolvedItem", result.label)
        assertEquals("Resolved Detail", result.detail)
        assertEquals("Resolved Docs", result.documentation.right.value)
    }

    fun testCompletionResolveRequestWithNullResult() {
        val unresolved = CompletionItem().apply {
            label = "unresolvedItem"
            detail = "Original Detail"
        }

        val future = bridgeServer.resolveCompletionItem(unresolved)

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
        assertNotNull(result)
        assertEquals("unresolvedItem", result.label)
        assertEquals("Original Detail", result.detail)
    }

    fun testCompletionResolveRequestWithErrorResponse() {
        val unresolved = CompletionItem().apply {
            label = "unresolvedItem"
            detail = "Original Detail"
        }

        val future = bridgeServer.resolveCompletionItem(unresolved)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "error": {
                    "code": -32603,
                    "message": "Internal error"
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals("unresolvedItem", result.label)
        assertEquals("Original Detail", result.detail)
    }

    fun testCompletionRequestWithErrorResponse() {
        val params = CompletionParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.completion(params)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "error": {
                    "code": -32601,
                    "message": "Method not found"
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertTrue(result.isRight)
        assertTrue(result.right.items.isEmpty())
    }

    fun testCompletionRequestWithNullResult() {
        val params = CompletionParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.completion(params)

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
        assertNotNull(result)
        assertTrue(result.isRight)
        assertTrue(result.right.items.isEmpty())
    }

    fun testCompletionSupportIconMapping() {
        val support = DartLspCompletionSupport
        val constructorItem = CompletionItem().apply { kind = CompletionItemKind.Constructor }
        assertEquals(AllIcons.Nodes.ClassInitializer, support.getIcon(constructorItem))

        val functionItem = CompletionItem().apply { kind = CompletionItemKind.Function }
        assertEquals(AllIcons.Nodes.Lambda, support.getIcon(functionItem))

        val methodItem = CompletionItem().apply { kind = CompletionItemKind.Method }
        assertEquals(AllIcons.Nodes.Method, support.getIcon(methodItem))
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

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id")?.asString)

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

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id")?.asString)

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

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id")?.asString)

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

    fun testBuildLspCapabilitiesWithCodeActions() {
        val enabledCaps = DartAnalysisServerService.buildLspCapabilities("3.14.0", true, false, true)
        val textDocEnabled = enabledCaps.getAsJsonObject("textDocument")
        assertNotNull(textDocEnabled)
        val codeAction = textDocEnabled.getAsJsonObject("codeAction")
        assertNotNull("codeAction capabilities must be present when enabled", codeAction)
        val literalSupport = codeAction.getAsJsonObject("codeActionLiteralSupport")
        assertNotNull("codeActionLiteralSupport must be present", literalSupport)
        val valueSet = literalSupport.getAsJsonObject("codeActionKind").getAsJsonArray("valueSet")
        val kinds = valueSet.map { it.asString }
        assertTrue(kinds.contains("quickfix"))
        assertTrue(kinds.contains("refactor"))
        assertTrue(kinds.contains("source.organizeImports"))
        assertEquals(true, codeAction.get("dataSupport").asBoolean)

        val disabledCaps = DartAnalysisServerService.buildLspCapabilities("3.14.0", true, false, false)
        val textDocDisabled = disabledCaps.getAsJsonObject("textDocument")
        assertFalse("codeAction capabilities should not be present when disabled", textDocDisabled.has("codeAction"))
    }

    fun testInitializeCapabilitiesIncludesCodeActionOptions() {
        val initResult = bridgeServer.initialize(org.eclipse.lsp4j.InitializeParams()).get(5, TimeUnit.SECONDS)
        val caProvider = initResult.capabilities.codeActionProvider
        assertNotNull("codeActionProvider capability must be set", caProvider)
        assertTrue("codeActionProvider should be Either.forLeft(true)", caProvider.isLeft && caProvider.left == true)
    }

    fun testLspMethodExperimentalFeatures() {
        val experimentalNames = LspMethod.getExperimentalFeatures().mapNotNull { it.presentableName }
        assertTrue("Experimental features list should contain 'code actions'", experimentalNames.contains("code actions"))
        assertTrue("Experimental features list should contain 'errors and warnings'", experimentalNames.contains("errors and warnings"))
        assertTrue("Experimental features list should contain 'signature help'", experimentalNames.contains("signature help"))
        assertFalse("DOCUMENT_HIGHLIGHT should not be experimental", LspMethod.getExperimentalFeatures().contains(LspMethod.DOCUMENT_HIGHLIGHT))
    }

    fun testBuildLspCapabilitiesIncludesCompletion() {
        val lspCapabilitiesOlder = DartAnalysisServerService.buildLspCapabilities("3.8.0")
        val textDocumentOlder = lspCapabilitiesOlder.getAsJsonObject("textDocument")
        assertNotNull(textDocumentOlder)
        assertNull(textDocumentOlder.getAsJsonObject("completion"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspCompletion("3.8.0"))

        val lspCapabilitiesSufficient = DartAnalysisServerService.buildLspCapabilities("3.14.0-226.0.dev")
        val textDocumentSufficient = lspCapabilitiesSufficient.getAsJsonObject("textDocument")
        assertNotNull(textDocumentSufficient)
        val completion = textDocumentSufficient.getAsJsonObject("completion")
        assertNotNull(completion)
        val completionItem = completion.getAsJsonObject("completionItem")
        assertNotNull(completionItem)
        assertTrue(completionItem.get("snippetSupport").asBoolean)
        assertTrue(completionItem.get("labelDetailsSupport").asBoolean)
        assertTrue(completionItem.get("deprecatedSupport").asBoolean)
        assertTrue(completionItem.get("insertReplaceSupport").asBoolean)
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspCompletion("3.14.0-226.0.dev"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspCompletion("3.15.0"))
    }

    fun testInitialize_semanticTokensProvider_notAdvertisedWhenLegendNotProvided() {
        val future = bridgeServer.initialize(InitializeParams())
        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        val capabilities = result.capabilities
        assertNotNull(capabilities)
        assertNull("Semantic tokens provider should not be advertised if DAS did not provide a legend", capabilities.semanticTokensProvider)
        assertFalse(bridgeServer.isSemanticTokensAdvertised)
        assertFalse(DartAnalysisServerService.getInstance(project).isSemanticTokensAdvertised)
    }

    fun testInitialize_semanticTokensDynamicLegendFromDas() {
        val dasLegendTypes = listOf(
            "annotation", "class", "comment", "method", "variable",
            "parameter", "enum", "enumMember", "type", "source",
            "property", "keyword", "label", "namespace", "boolean",
            "number", "string", "function", "typeParameter"
        )
        val dasLegendModifiers = listOf(
            "documentation", "constructor", "declaration", "importPrefix",
            "instance", "static", "escape", "annotation", "control",
            "label", "interpolation", "source", "void", "wildcard"
        )
        val das = DartAnalysisServerService.getInstance(project)
        das.semanticTokensLegend = SemanticTokensLegend(dasLegendTypes, dasLegendModifiers)
        bridgeServer.isLspHighlightingEnabled = { true }

        val future = bridgeServer.initialize(InitializeParams())
        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        val legend = result.capabilities?.semanticTokensProvider?.legend
        assertNotNull(legend)
        assertEquals(dasLegendTypes, legend!!.tokenTypes)
        assertEquals(dasLegendModifiers, legend.tokenModifiers)
        assertTrue("Legend should contain standard types like 'class'", legend.tokenTypes.contains(SemanticTokenTypes.Class))
        assertTrue("Legend should contain Dart custom type 'annotation'", legend.tokenTypes.contains("annotation"))
        assertTrue("Legend should contain modifier 'declaration'", legend.tokenModifiers.contains(SemanticTokenModifiers.Declaration))
        assertTrue("Legend should contain custom modifier 'constructor'", legend.tokenModifiers.contains("constructor"))
        assertTrue(bridgeServer.isSemanticTokensAdvertised)
        assertTrue(DartAnalysisServerService.getInstance(project).isSemanticTokensAdvertised)
    }

    fun testUpdateClientCapabilities_parsesSemanticTokensLegendFromDas() {
        val responseJson = """
            {
              "lspCapabilities": {
                "semanticTokensProvider": {
                  "legend": {
                    "tokenTypes": ["annotation", "class", "customType"],
                    "tokenModifiers": ["documentation", "customModifier"]
                  },
                  "full": { "delta": false },
                  "range": true
                }
              }
            }
        """.trimIndent()
        val resultObj = JsonParser.parseString(responseJson).asJsonObject
        val das = DartAnalysisServerService.getInstance(project)
        das.handleClientCapabilitiesResponse(resultObj, null)

        val legend = das.semanticTokensLegend
        assertNotNull("Dynamic legend should be parsed from DAS capabilities response", legend)
        assertEquals(listOf("annotation", "class", "customType"), legend!!.tokenTypes)
        assertEquals(listOf("documentation", "customModifier"), legend.tokenModifiers)
    }

    fun testUpdateClientCapabilities_endToEnd() {
        val das = DartAnalysisServerService.getInstance(project)
        das.semanticTokensLegend = null

        das.updateClientCapabilities()

        val setCapsReq = capturedRequests.find { it.get("method")?.asString == "server.setClientCapabilities" }
        assertNotNull("server.setClientCapabilities request should be sent", setCapsReq)
        assertNotNull("Consumer should be captured", capturedConsumer)

        val responseJson = """
            {
              "lspCapabilities": {
                "semanticTokensProvider": {
                  "legend": {
                    "tokenTypes": ["class", "method", "variable"],
                    "tokenModifiers": ["declaration", "static"]
                  },
                  "full": { "delta": false },
                  "range": true
                }
              }
            }
        """.trimIndent()
        val resultObj = JsonParser.parseString(responseJson).asJsonObject

        val jsonConsumer = capturedConsumer as JsonConsumer
        jsonConsumer.onResponse(resultObj, null)

        val legend = das.semanticTokensLegend
        assertNotNull("Dynamic legend should be set on DAS", legend)
        assertEquals(listOf("class", "method", "variable"), legend?.tokenTypes)
        assertEquals(listOf("declaration", "static"), legend?.tokenModifiers)

        val futureLegend = das.semanticTokensLegendFuture.get(5, TimeUnit.SECONDS)
        assertEquals(legend, futureLegend)
    }

    fun testUpdateClientCapabilities_handlesMissingLspCapabilitiesGracefully() {
        val das = DartAnalysisServerService.getInstance(project)
        das.updateClientCapabilities()

        val jsonConsumer = capturedConsumer as JsonConsumer
        jsonConsumer.onResponse(JsonObject(), null)

        assertNull("Legend should be null when lspCapabilities is missing", das.semanticTokensLegend)
        val futureLegend = das.semanticTokensLegendFuture.get(5, TimeUnit.SECONDS)
        assertNull("Future should resolve to null when lspCapabilities is missing", futureLegend)
    }

    fun testUpdateClientCapabilities_handlesRequestErrorGracefully() {
        val das = DartAnalysisServerService.getInstance(project)
        das.updateClientCapabilities()

        val jsonConsumer = capturedConsumer as JsonConsumer
        jsonConsumer.onResponse(null, RequestError("SERVER_ERROR", "Error message", null))

        assertNull("Legend should be null on request error", das.semanticTokensLegend)
        val futureLegend = das.semanticTokensLegendFuture.get(5, TimeUnit.SECONDS)
        assertNull("Future should resolve to null on request error", futureLegend)
    }

    fun testSemanticTokensFull_success() {
        val params = SemanticTokensParams(TextDocumentIdentifier("file:///test.dart"))
        val future = bridgeServer.semanticTokensFull(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/semanticTokens/full", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "data": [0, 4, 3, 0, 0, 1, 2, 5, 1, 2]
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(listOf(0, 4, 3, 0, 0, 1, 2, 5, 1, 2), result.data)
    }

    fun testSemanticTokensFull_errorReturnsNull() {
        val params = SemanticTokensParams(TextDocumentIdentifier("file:///test.dart"))
        val future = bridgeServer.semanticTokensFull(params)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "error": {
                    "code": -32601,
                    "message": "Method not found"
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNull("DAS error on semanticTokensFull should gracefully complete with null", result)
    }

    fun testDartLspSemanticTokensSupport_shouldAskServerForSemanticTokens() {
        val dartFile = myFixture.configureByText("test.dart", "class Foo {}")
        assertTrue("Should ask server for .dart files", DartLspSemanticTokensSupport.shouldAskServerForSemanticTokens(dartFile))

        val txtFile = myFixture.configureByText("test.txt", "some text")
        assertFalse("Should not ask server for .txt files", DartLspSemanticTokensSupport.shouldAskServerForSemanticTokens(txtFile))
    }

    fun testDartLspSemanticTokensSupport_getTextAttributesKey() {
        val cases = listOf(
            // Types & Classes
            Triple(SemanticTokenTypes.Class, emptyList<String>(), DartSyntaxHighlighterColors.CLASS),
            Triple(SemanticTokenTypes.Class, listOf("constructor"), DartSyntaxHighlighterColors.CONSTRUCTOR),
            Triple(SemanticTokenTypes.Class, listOf("annotation"), DartSyntaxHighlighterColors.ANNOTATION),
            Triple(SemanticTokenTypes.Enum, emptyList(), DartSyntaxHighlighterColors.ENUM),
            Triple(SemanticTokenTypes.EnumMember, emptyList(), DartSyntaxHighlighterColors.ENUM_CONSTANT),
            Triple(SemanticTokenTypes.TypeParameter, emptyList(), DartSyntaxHighlighterColors.TYPE_PARAMETER),
            Triple(SemanticTokenTypes.Type, emptyList(), DartSyntaxHighlighterColors.TYPE_NAME_DYNAMIC),
            Triple(SemanticTokenTypes.Type, listOf("typeAlias"), DartSyntaxHighlighterColors.TYPE_ALIAS),
            Triple(SemanticTokenTypes.Type, listOf("functionTypeAlias"), DartSyntaxHighlighterColors.FUNCTION_TYPE_ALIAS),
            Triple(SemanticTokenTypes.Class, listOf("mixin"), DartSyntaxHighlighterColors.MIXIN),
            Triple(SemanticTokenTypes.Class, listOf("extension"), DartSyntaxHighlighterColors.EXTENSION),
            Triple(SemanticTokenTypes.Class, listOf("extensionType"), DartSyntaxHighlighterColors.EXTENSION_TYPE),

            // Methods & Constructors
            Triple(SemanticTokenTypes.Method, listOf("constructor"), DartSyntaxHighlighterColors.CONSTRUCTOR),
            Triple(SemanticTokenTypes.Method, listOf(SemanticTokenModifiers.Static, SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.STATIC_METHOD_DECLARATION),
            Triple(SemanticTokenTypes.Method, listOf(SemanticTokenModifiers.Static), DartSyntaxHighlighterColors.STATIC_METHOD_REFERENCE),
            Triple(SemanticTokenTypes.Method, listOf("instance", SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.INSTANCE_METHOD_DECLARATION),
            Triple(SemanticTokenTypes.Method, listOf("instance"), DartSyntaxHighlighterColors.INSTANCE_METHOD_REFERENCE),
            Triple(SemanticTokenTypes.Method, listOf("constructor", "tearOff"), DartSyntaxHighlighterColors.CONSTRUCTOR_TEAR_OFF),
            Triple(SemanticTokenTypes.Method, listOf(SemanticTokenModifiers.Static, "tearOff"), DartSyntaxHighlighterColors.STATIC_METHOD_TEAR_OFF),
            Triple(SemanticTokenTypes.Method, listOf("instance", "tearOff"), DartSyntaxHighlighterColors.INSTANCE_METHOD_TEAR_OFF),

            // Functions
            Triple(SemanticTokenTypes.Function, listOf(SemanticTokenModifiers.Static, SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.TOP_LEVEL_FUNCTION_DECLARATION),
            Triple(SemanticTokenTypes.Function, listOf(SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.LOCAL_FUNCTION_DECLARATION),
            Triple(SemanticTokenTypes.Function, listOf(SemanticTokenModifiers.Static), DartSyntaxHighlighterColors.TOP_LEVEL_FUNCTION_REFERENCE),
            Triple(SemanticTokenTypes.Function, listOf(SemanticTokenModifiers.Static, "tearOff"), DartSyntaxHighlighterColors.TOP_LEVEL_FUNCTION_TEAR_OFF),
            Triple(SemanticTokenTypes.Function, emptyList(), DartSyntaxHighlighterColors.LOCAL_FUNCTION_REFERENCE),
            Triple(SemanticTokenTypes.Function, listOf("tearOff"), DartSyntaxHighlighterColors.LOCAL_FUNCTION_TEAR_OFF),

            // Properties (Getters / Setters)
            Triple(SemanticTokenTypes.Property, listOf(SemanticTokenModifiers.Static, SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.STATIC_GETTER_DECLARATION),
            Triple(SemanticTokenTypes.Property, listOf(SemanticTokenModifiers.Static), DartSyntaxHighlighterColors.STATIC_GETTER_REFERENCE),
            Triple(SemanticTokenTypes.Property, listOf("instance", SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.INSTANCE_GETTER_DECLARATION),
            Triple(SemanticTokenTypes.Property, listOf("instance"), DartSyntaxHighlighterColors.INSTANCE_GETTER_REFERENCE),
            Triple(SemanticTokenTypes.Property, listOf(SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.TOP_LEVEL_GETTER_DECLARATION),
            Triple(SemanticTokenTypes.Property, emptyList(), DartSyntaxHighlighterColors.TOP_LEVEL_GETTER_REFERENCE),

            // Variables & Fields
            Triple(SemanticTokenTypes.Variable, listOf("importPrefix"), DartSyntaxHighlighterColors.IMPORT_PREFIX),
            Triple(SemanticTokenTypes.Variable, listOf(SemanticTokenModifiers.Static, SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.STATIC_FIELD_DECLARATION),
            Triple(SemanticTokenTypes.Variable, listOf(SemanticTokenModifiers.Static), DartSyntaxHighlighterColors.STATIC_FIELD_DECLARATION),
            Triple(SemanticTokenTypes.Variable, listOf("instance", SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.INSTANCE_FIELD_DECLARATION),
            Triple(SemanticTokenTypes.Variable, listOf("instance"), DartSyntaxHighlighterColors.INSTANCE_FIELD_REFERENCE),
            Triple(SemanticTokenTypes.Variable, listOf(SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.LOCAL_VARIABLE_DECLARATION),
            Triple(SemanticTokenTypes.Variable, emptyList(), DartSyntaxHighlighterColors.LOCAL_VARIABLE_REFERENCE),

            // Parameters, Annotations, Labels, Namespaces, Keywords, Identifiers
            Triple(SemanticTokenTypes.Parameter, listOf(SemanticTokenModifiers.Declaration), DartSyntaxHighlighterColors.PARAMETER_DECLARATION),
            Triple(SemanticTokenTypes.Parameter, emptyList(), DartSyntaxHighlighterColors.PARAMETER_REFERENCE),
            Triple("annotation", emptyList(), DartSyntaxHighlighterColors.ANNOTATION),
            Triple("label", emptyList(), DartSyntaxHighlighterColors.LABEL),
            Triple(SemanticTokenTypes.Namespace, emptyList(), DartSyntaxHighlighterColors.LIBRARY_NAME),
            Triple(SemanticTokenTypes.Keyword, emptyList(), DartSyntaxHighlighterColors.KEYWORD),
            Triple("source", emptyList(), DartSyntaxHighlighterColors.IDENTIFIER),

            // Strings & Escapes
            Triple(SemanticTokenTypes.String, listOf("escape"), DartSyntaxHighlighterColors.VALID_STRING_ESCAPE),

            // Handled by local lexer (must return null)
            Triple(SemanticTokenTypes.String, emptyList(), null),
            Triple(SemanticTokenTypes.Comment, emptyList(), null),
            Triple(SemanticTokenTypes.Comment, listOf(SemanticTokenModifiers.Documentation), null),
            Triple(SemanticTokenTypes.Number, emptyList(), null),
            Triple(SemanticTokenTypes.Operator, emptyList(), null),
            Triple("boolean", emptyList(), null),
        )

        for ((type, modifiers, expected) in cases) {
            assertEquals("Failed for type=$type, modifiers=$modifiers", expected, DartLspSemanticTokensSupport.getTextAttributesKey(type, modifiers))
        }
    }

    fun testPublishDiagnosticsNotification() {
        val testFile = myFixture.addFileToProject(
            "lib/test.dart",
            """
            void main() {
              int x = "string";
            }
            """.trimIndent()
        )
        val fileUri = "file://${testFile.virtualFile.path}"

        val notificationJson = """
            {
              "params": {
                "lspNotification": {
                  "jsonrpc": "2.0",
                  "method": "textDocument/publishDiagnostics",
                  "params": {
                    "uri": "$fileUri",
                    "diagnostics": [
                      {
                        "range": {
                          "start": {"line": 1, "character": 10},
                          "end": {"line": 1, "character": 18}
                        },
                        "severity": 1,
                        "code": "invalid_assignment",
                        "message": "A value of type 'String' can't be assigned to a variable of type 'int'.",
                        "source": "dart"
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(notificationJson)

        assertNotNull(mockClient.publishedDiagnostics)
        assertEquals(fileUri, mockClient.publishedDiagnostics?.uri)
        assertEquals(1, mockClient.publishedDiagnostics?.diagnostics?.size)
        val publishedDiag = mockClient.publishedDiagnostics?.diagnostics?.first()
        assertEquals("invalid_assignment", publishedDiag?.code?.left)
        assertEquals("A value of type 'String' can't be assigned to a variable of type 'int'.", publishedDiag?.message)
    }

    fun testPublishClosingLabelsNotification() {
        val testFile = myFixture.addFileToProject(
            "lib/widget.dart",
            """
                void main() {
                  runApp(
                    MyWidget(
                      child: Text('Hello'),
                    ),
                  );
                }
                """.trimIndent()
        )
        val fileUri = "file://${testFile.virtualFile.path}"

        val notificationJson = """
                {
                  "params": {
                    "lspNotification": {
                      "jsonrpc": "2.0",
                      "method": "dart/textDocument/publishClosingLabels",
                      "params": {
                        "uri": "$fileUri",
                        "labels": [
                          {
                            "label": "MyWidget",
                            "range": {
                              "start": {"line": 2, "character": 4},
                              "end": {"line": 4, "character": 5}
                            }
                          }
                        ]
                      }
                    }
                  }
                }
            """.trimIndent()

        // 1. Simulate the reverse notification arriving from DAS over the bridge
        capturedListener.onResponse(notificationJson)

        // 2. Verify DartBridgeLspServer forwarded the notification to the LSP client proxy
        val published = mockClient.publishedClosingLabels
        assertNotNull("dart/textDocument/publishClosingLabels should be forwarded to client", published)
        assertEquals(fileUri, published?.uri)
        val closingLabels = published?.labels ?: emptyList()
        assertEquals(1, closingLabels.size)
        assertEquals("MyWidget", closingLabels[0].label)
        assertEquals(2, closingLabels[0].range?.start?.line)
        assertEquals(4, closingLabels[0].range?.end?.line)
    }


    fun testTypeDefinitionRequest() {
        val params = TypeDefinitionParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.typeDefinition(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/typeDefinition", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": [
                    {
                      "targetUri": "file://target.dart",
                      "targetRange": {"start": {"line": 0, "character": 0}, "end": {"line": 10, "character": 0}},
                      "targetSelectionRange": {"start": {"line": 0, "character": 6}, "end": {"line": 0, "character": 9}}
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertTrue(result.isRight)
        assertEquals(1, result.right.size)
        assertEquals("file://target.dart", result.right[0].targetUri)
    }

    // --- Hierarchy ---

    fun testPrepareTypeHierarchyRequest() {
        val params = TypeHierarchyPrepareParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.prepareTypeHierarchy(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/prepareTypeHierarchy", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "name": "Dog",
                          "kind": 5,
                          "uri": "file://test.dart",
                          "range": {"start": {"line": 1, "character": 0}, "end": {"line": 5, "character": 1}},
                          "selectionRange": {"start": {"line": 1, "character": 6}, "end": {"line": 1, "character": 9}}
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("Dog", result[0].name)
        assertEquals(SymbolKind.Class, result[0].kind)
        assertEquals("file://test.dart", result[0].uri)
    }

    fun testTypeHierarchySupertypesRequest() {
        val item = TypeHierarchyItem(
            "Dog",
            SymbolKind.Class,
            "file://test.dart",
            Range(Position(1, 0), Position(5, 1)),
            Range(Position(1, 6), Position(1, 9))
        )
        val params = TypeHierarchySupertypesParams().apply {
            this.item = item
        }

        val future = bridgeServer.typeHierarchySupertypes(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("typeHierarchy/supertypes", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "name": "Animal",
                          "kind": 5,
                          "uri": "file://test.dart",
                          "range": {"start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 24}},
                          "selectionRange": {"start": {"line": 0, "character": 15}, "end": {"line": 0, "character": 21}}
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("Animal", result[0].name)
    }

    fun testTypeHierarchySubtypesRequest() {
        val item = TypeHierarchyItem(
            "Dog",
            SymbolKind.Class,
            "file://test.dart",
            Range(Position(1, 0), Position(5, 1)),
            Range(Position(1, 6), Position(1, 9))
        )
        val params = TypeHierarchySubtypesParams().apply {
            this.item = item
        }

        val future = bridgeServer.typeHierarchySubtypes(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("typeHierarchy/subtypes", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "name": "Labrador",
                          "kind": 5,
                          "uri": "file://test.dart",
                          "range": {"start": {"line": 7, "character": 0}, "end": {"line": 7, "character": 27}},
                          "selectionRange": {"start": {"line": 7, "character": 6}, "end": {"line": 7, "character": 14}}
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("Labrador", result[0].name)
    }

    fun testPrepareCallHierarchyRequest() {
        val params = CallHierarchyPrepareParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
        }

        val future = bridgeServer.prepareCallHierarchy(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/prepareCallHierarchy", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "name": "bark",
                          "kind": 6,
                          "uri": "file://test.dart",
                          "range": {"start": {"line": 2, "character": 2}, "end": {"line": 4, "character": 3}},
                          "selectionRange": {"start": {"line": 2, "character": 7}, "end": {"line": 2, "character": 11}}
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("bark", result[0].name)
        assertEquals(SymbolKind.Method, result[0].kind)
    }

    fun testCallHierarchyIncomingCallsRequest() {
        val item = CallHierarchyItem().apply {
            name = "bark"
            kind = SymbolKind.Method
            uri = "file://test.dart"
            range = Range(Position(2, 2), Position(4, 3))
            selectionRange = Range(Position(2, 7), Position(2, 11))
        }
        val params = CallHierarchyIncomingCallsParams().apply {
            this.item = item
        }

        val future = bridgeServer.callHierarchyIncomingCalls(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("callHierarchy/incomingCalls", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "from": {
                            "name": "speak",
                            "kind": 6,
                            "uri": "file://test.dart",
                            "range": {"start": {"line": 0, "character": 2}, "end": {"line": 1, "character": 3}},
                            "selectionRange": {"start": {"line": 0, "character": 7}, "end": {"line": 0, "character": 12}}
                          },
                          "fromRanges": [
                            {"start": {"line": 1, "character": 4}, "end": {"line": 1, "character": 10}}
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("speak", result[0].from.name)
        assertEquals(1, result[0].fromRanges.size)
    }

    fun testCallHierarchyOutgoingCallsRequest() {
        val item = CallHierarchyItem().apply {
            name = "bark"
            kind = SymbolKind.Method
            uri = "file://test.dart"
            range = Range(Position(2, 2), Position(4, 3))
            selectionRange = Range(Position(2, 7), Position(2, 11))
        }
        val params = CallHierarchyOutgoingCallsParams().apply {
            this.item = item
        }

        val future = bridgeServer.callHierarchyOutgoingCalls(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)
        assertEquals("123", jsonObject!!.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("callHierarchy/outgoingCalls", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "to": {
                            "name": "print",
                            "kind": 12,
                            "uri": "file://core.dart",
                            "range": {"start": {"line": 10, "character": 0}, "end": {"line": 12, "character": 1}},
                            "selectionRange": {"start": {"line": 10, "character": 5}, "end": {"line": 10, "character": 10}}
                          },
                          "fromRanges": [
                            {"start": {"line": 3, "character": 4}, "end": {"line": 3, "character": 17}}
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("print", result[0].to.name)
        assertEquals(1, result[0].fromRanges.size)
    }

    fun testReferencesRequest() {
        val params = ReferenceParams().apply {
            textDocument = TextDocumentIdentifier("file://test.dart")
            position = Position(1, 2)
            context = ReferenceContext(true)
        }
        val future = bridgeServer.references(params)
        val jsonObject = requireNotNull(capturedRequests.find {
            it.get("method")?.asString == "lsp.handle"
        }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id").asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("textDocument/references", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": [
                    {
                      "uri": "file:///path/to/file.dart",
                      "range": {
                        "start": {"line": 0, "character": 4},
                        "end": {"line": 0, "character": 10}
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)
        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("file:///path/to/file.dart", result[0].uri)
        assertEquals(0, result[0].range.start.line)
        assertEquals(4, result[0].range.start.character)
        assertEquals(0, result[0].range.end.line)
        assertEquals(10, result[0].range.end.character)
    }

    fun testDocumentSymbolRequest() {
        val params = DocumentSymbolParams(TextDocumentIdentifier("file:///test.dart"))
        val future = bridgeServer.documentSymbol(params)

        val jsonObject = capturedRequests.find { it.get("method")?.asString == "lsp.handle" }
        assertNotNull("An lsp.handle request should be sent to DAS", jsonObject)

        val lspMessage = jsonObject!!.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("textDocument/documentSymbol", lspMessage.get("method").asString)

        val responseJson = """
                {
                  "id": "123",
                  "result": {
                    "lspResponse": {
                      "jsonrpc": "2.0",
                      "id": "123",
                      "result": [
                        {
                          "name": "MyClass",
                          "kind": 5,
                          "range": {
                            "start": { "line": 0, "character": 0 },
                            "end": { "line": 4, "character": 1 }
                          },
                          "selectionRange": {
                            "start": { "line": 0, "character": 6 },
                            "end": { "line": 0, "character": 13 }
                          },
                          "children": [
                            {
                              "name": "myMethod",
                              "detail": "(String name)",
                              "kind": 6,
                              "range": {
                                "start": { "line": 1, "character": 2 },
                                "end": { "line": 3, "character": 3 }
                              },
                              "selectionRange": {
                                "start": { "line": 1, "character": 7 },
                                "end": { "line": 1, "character": 15 }
                              }
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.size)
        val rootSymbol = result[0].right
        assertEquals("MyClass", rootSymbol.name)
        assertEquals(SymbolKind.Class, rootSymbol.kind)
        assertEquals(1, rootSymbol.children.size)
        assertEquals("myMethod", rootSymbol.children[0].name)
        assertEquals("(String name)", rootSymbol.children[0].detail)
    }

    fun testIsDartSdkVersionSufficientForLspReferences() {
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("3.14.0-65.0.dev"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("3.15.0"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("4.0.0"))

        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("3.13.0"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("3.0.0"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("2.19.0"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspReferences("2.14.0"))
    }

    fun testInitializeCapabilitiesIncludesFileOperations() {
        val initResult = bridgeServer.initialize(InitializeParams()).get(5, TimeUnit.SECONDS)
        assertNotNull("InitializeResult should not be null", initResult)
        val fileOperations = initResult.capabilities.workspace?.fileOperations
        assertNotNull("Workspace fileOperations should be advertised", fileOperations)
        assertNotNull("willRename file operations should be advertised", fileOperations?.willRename)
    }

    fun testWillRenameFilesRequest() {
        val oldUri = "file:///project/lib/old_folder"
        val newUri = "file:///project/lib/new_folder"
        val params = RenameFilesParams(listOf(FileRename(oldUri, newUri)))

        val future = bridgeServer.willRenameFiles(params)

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id")?.asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("123", lspMessage.get("id").asString)
        assertEquals("workspace/willRenameFiles", lspMessage.get("method").asString)

        val lspParams = lspMessage.getAsJsonObject("params")
        val filesArray = lspParams.getAsJsonArray("files")
        assertEquals(1, filesArray.size())
        val fileRenameObj = filesArray.get(0).asJsonObject
        assertEquals(oldUri, fileRenameObj.get("oldUri").asString)
        assertEquals(newUri, fileRenameObj.get("newUri").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "documentChanges": [
                      {
                        "textDocument": {
                          "uri": "file:///project/lib/main.dart",
                          "version": null
                        },
                        "edits": [
                          {
                            "range": {
                              "start": {"line": 0, "character": 7},
                              "end": {"line": 0, "character": 22}
                            },
                            "newText": "'package:project/new_folder/a.dart'"
                          }
                        ]
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val editResult = checkNotNull(future.get(5, TimeUnit.SECONDS)) { "WorkspaceEdit result should not be null" }
        val docChanges = checkNotNull(editResult.documentChanges) { "documentChanges should not be null" }
        assertEquals(1, docChanges.size)
        assertTrue("documentChanges item should be Left (TextDocumentEdit)", docChanges[0].isLeft)
        val textDocEdit = docChanges[0].left
        assertEquals("file:///project/lib/main.dart", textDocEdit.textDocument.uri)
        assertEquals(1, textDocEdit.edits.size)
        assertEquals("'package:project/new_folder/a.dart'", textDocEdit.edits[0].newText)
    }

    fun testBuildLspCapabilitiesIncludesFileOperations() {
        val caps = DartAnalysisServerService.buildLspCapabilities("3.8.0")
        val workspace = caps.getAsJsonObject("workspace")
        assertNotNull("workspace capability should not be null", workspace)
        val fileOperations = workspace.getAsJsonObject("fileOperations")
        assertNotNull("fileOperations capability should not be null", fileOperations)
        assertTrue("willRename should be true", fileOperations.get("willRename").asBoolean)
    }

    fun testLauncherCreationSucceedsWithoutDuplicateRpcMethodException() {
        val launcher = Launcher.createLauncher(
            bridgeServer,
            LanguageClient::class.java,
            ByteArrayInputStream(ByteArray(0)),
            ByteArrayOutputStream()
        )
        assertNotNull("Launcher should be created successfully without Duplicate RPC method exceptions", launcher)
    }

    fun testWorkspaceApplyEditRequestForwardedAndResponseSentBack() {
        val serverRequestJson = """
            {
              "id": "das_req_1",
              "method": "lsp.handle",
              "params": {
                "lspMessage": {
                  "jsonrpc": "2.0",
                  "id": 99,
                  "method": "workspace/applyEdit",
                  "params": {
                    "label": "Sort Members",
                    "edit": {
                      "changes": {
                        "file:///test.dart": [
                          {
                            "range": {
                              "start": { "line": 0, "character": 0 },
                              "end": { "line": 1, "character": 0 }
                            },
                            "newText": "// sorted\n"
                          }
                        ]
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(serverRequestJson)

        // Verify client received the applyEdit call
        assertNotNull("Client should have received applyEdit params", mockClient.lastApplyWorkspaceEditParams)
        assertEquals("Sort Members", mockClient.lastApplyWorkspaceEditParams?.label)

        // Verify response was sent back to DAS
        val responseJsonObject = capturedResponses.find { it.get("id")?.asString == "das_req_1" }
        assertNotNull("A response should be sent back to DAS with id das_req_1", responseJsonObject)

        val lspResponse = responseJsonObject!!.getAsJsonObject("result")?.getAsJsonObject("lspResponse")
        assertNotNull("response should contain lspResponse", lspResponse)
        assertEquals(99, lspResponse!!.get("id").asInt)
        assertNotNull("lspMessage should contain result", lspResponse.getAsJsonObject("result"))
        assertEquals(true, lspResponse.getAsJsonObject("result").get("applied").asBoolean)
    }

    fun testWorkspaceConfigurationRequestForwardedAndResponseSentBack() {
        val dartConfig = JsonObject().apply {
            add(DartLspInlayHintsConfiguration.INLAY_HINTS_KEY, JsonObject())
        }
        mockClient.configurationResult = listOf(dartConfig, null)

        val serverRequestJson = """
            {
              "id": "das_cfg_1",
              "method": "lsp.handle",
              "params": {
                "lspMessage": {
                  "jsonrpc": "2.0",
                  "id": 7,
                  "method": "workspace/configuration",
                  "params": {
                    "items": [
                      { "section": "dart" },
                      { "section": "flutter" }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(serverRequestJson)

        val receivedParams = requireNotNull(mockClient.lastConfigurationParams) {
            "Client should have received configuration params"
        }
        assertEquals(2, receivedParams.items.size)
        assertEquals("dart", receivedParams.items[0].section)
        assertEquals("flutter", receivedParams.items[1].section)

        val responseJsonObject = requireNotNull(capturedResponses.find { it.get("id")?.asString == "das_cfg_1" }) {
            "A response should be sent back to DAS with id das_cfg_1"
        }
        val lspResponse = requireNotNull(responseJsonObject.getAsJsonObject("result")?.getAsJsonObject("lspResponse")) {
            "response should contain lspResponse"
        }
        assertEquals(7, lspResponse.get("id").asInt)
        val resultArray = requireNotNull(lspResponse.getAsJsonArray("result")) {
            "lspResponse should contain a result array"
        }
        assertEquals(2, resultArray.size())
        assertEquals(dartConfig, resultArray[0].asJsonObject)
        assertTrue(resultArray[1].isJsonNull)
    }

    fun testWorkspaceConfigurationAnsweredWithNullsWhenNoClientConnected() {
        bridgeServer.stop()
        val unconnectedBridge = DartBridgeLspServer(project)
        try {
            val serverRequestJson = """
                {
                  "id": "das_cfg_no_client",
                  "method": "lsp.handle",
                  "params": {
                    "lspMessage": {
                      "jsonrpc": "2.0",
                      "id": 8,
                      "method": "workspace/configuration",
                      "params": {
                        "items": [
                          { "section": "dart" },
                          { "section": "flutter" }
                        ]
                      }
                    }
                  }
                }
            """.trimIndent()

            capturedListener.onResponse(serverRequestJson)

            val responseJsonObject = requireNotNull(capturedResponses.find { it.get("id")?.asString == "das_cfg_no_client" }) {
                "A response should be sent back to DAS even when no client is connected"
            }
            val lspResponse = requireNotNull(responseJsonObject.getAsJsonObject("result")?.getAsJsonObject("lspResponse"))
            assertEquals(8, lspResponse.get("id").asInt)
            val resultArray = requireNotNull(lspResponse.getAsJsonArray("result"))
            assertEquals(2, resultArray.size())
            assertTrue(resultArray[0].isJsonNull)
            assertTrue(resultArray[1].isJsonNull)
        } finally {
            unconnectedBridge.stop()
        }
    }

    fun testWorkspaceConfigurationAnsweredWithNullsWhenParamsMalformed() {
        val serverRequestJson = """
            {
              "id": "das_cfg_malformed",
              "method": "lsp.handle",
              "params": {
                "lspMessage": {
                  "jsonrpc": "2.0",
                  "id": 9,
                  "method": "workspace/configuration",
                  "params": {
                    "items": [
                      "not_an_object",
                      123
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(serverRequestJson)

        val responseJsonObject = requireNotNull(capturedResponses.find { it.get("id")?.asString == "das_cfg_malformed" }) {
            "A response should be sent back to DAS even when params fail to parse"
        }
        val lspResponse = requireNotNull(responseJsonObject.getAsJsonObject("result")?.getAsJsonObject("lspResponse"))
        assertEquals(9, lspResponse.get("id").asInt)
        val resultArray = requireNotNull(lspResponse.getAsJsonArray("result"))
        assertEquals(2, resultArray.size())
        assertTrue(resultArray[0].isJsonNull)
        assertTrue(resultArray[1].isJsonNull)
    }

    fun testStopWithdrawsWorkspaceConfigurationCapability() {
        capturedRequests.clear()
        bridgeServer.initialize(InitializeParams()).get(5, TimeUnit.SECONDS)

        val initCapReq = requireNotNull(capturedRequests.lastOrNull { it.get("method")?.asString == "server.setClientCapabilities" }) {
            "initialize() should send server.setClientCapabilities"
        }
        val initWorkspaceCap = initCapReq.getAsJsonObject("params")
            .getAsJsonObject("lspCapabilities")
            .getAsJsonObject("workspace")
        assertTrue(initWorkspaceCap.get("configuration").asBoolean)

        capturedRequests.clear()
        bridgeServer.stop()
        assertFalse("isClientConnected should be false after stop()", bridgeServer.isClientConnected)

        val stopCapReq = requireNotNull(capturedRequests.lastOrNull { it.get("method")?.asString == "server.setClientCapabilities" }) {
            "stop() should withdraw workspace.configuration via server.setClientCapabilities"
        }
        val stopWorkspaceCap = stopCapReq.getAsJsonObject("params")
            .getAsJsonObject("lspCapabilities")
            .getAsJsonObject("workspace")
        assertFalse(stopWorkspaceCap?.has("configuration") == true)
    }

    fun testDescriptorClientCapabilitiesAndInitializationOptions() {
        val descriptor = DartLspServerDescriptor(project)
        val capabilities = descriptor.clientCapabilities

        assertEquals(true, capabilities.workspace?.fileOperations?.willRename)
        assertEquals(true, capabilities.textDocument?.publishDiagnostics?.relatedInformation)
        assertEquals(true, capabilities.textDocument?.publishDiagnostics?.codeDescriptionSupport)

        val experimental = capabilities.experimental as? Map<*, *>
        assertNotNull("experimental capabilities should be a Map", experimental)
        assertTrue("experimental capabilities should include closingLabels", experimental!!.containsKey("closingLabels"))

        val initOptions = descriptor.createInitializationOptions() as? Map<*, *>
        assertNotNull("initializationOptions should be a Map", initOptions)
        assertEquals(true, initOptions!!["closingLabels"])
    }

    fun testDescriptorGetWorkspaceConfiguration() {
        val descriptor = DartLspServerDescriptor(project)

        val dartItem = ConfigurationItem().apply { section = "dart" }
        val dartConfig = descriptor.getWorkspaceConfiguration(dartItem) as? JsonObject
        assertNotNull("dart section should return a JsonObject", dartConfig)
        assertNotNull(
            "dart section should contain inlayHints",
            dartConfig!!.getAsJsonObject(DartLspInlayHintsConfiguration.INLAY_HINTS_KEY)
        )

        val unknownItem = ConfigurationItem().apply { section = "flutter" }
        assertNull("unknown section should return null", descriptor.getWorkspaceConfiguration(unknownItem))
    }

    fun testForwardRequestUpdatesFilesContentFromBackgroundThread() {
        val das = DartAnalysisServerService.getInstance(project)
        val changedDocsField = DartAnalysisServerService::class.java.getDeclaredField("myChangedDocuments").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val changedDocs = changedDocsField.get(das) as MutableSet<Document>
        val dummyDoc = EditorFactory.getInstance().createDocument("void main() {}")
        changedDocs.add(dummyDoc)

        val bgTask = ApplicationManager.getApplication().executeOnPooledThread {
            assertFalse(
                "Background thread should not start with read access",
                ApplicationManager.getApplication().isReadAccessAllowed
            )
            val params = HoverParams(TextDocumentIdentifier("file:///test.dart"), Position(1, 2))
            bridgeServer.hover(params)
        }
        bgTask.get(5, TimeUnit.SECONDS)

        assertTrue(
            "updateFilesContent() should have been called and cleared myChangedDocuments",
            changedDocs.isEmpty()
        )
        assertEquals(1, capturedRequests.size)
    }

    fun testForwardRequestDoesNotDeadlockDuringWriteAction() {
        WriteAction.run<Throwable> {
            val bgTask = ApplicationManager.getApplication().executeOnPooledThread {
                val params = RenameFilesParams(listOf(FileRename("file:///old.dart", "file:///new.dart")))
                bridgeServer.willRenameFiles(params)
            }
            // Should complete without deadlocking even while EDT holds the write lock
            bgTask.get(5, TimeUnit.SECONDS)
        }
        assertEquals(1, capturedRequests.size)
    }

    fun testIsDartSdkVersionSufficientForLspHighlighting() {
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("3.15.0-67.0.dev"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("3.15.0-68.0.dev"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("3.15.0"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("4.0.0"))

        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("3.15.0-66.0.dev"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("3.14.0-307.0.dev"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("3.14.0-217.0.dev"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("3.14.0-65.0.dev"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("3.13.0"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("3.0.0"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("2.19.0"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting("2.14.0"))
    }

    fun testIsLspHighlightingEnabled() {
        val properties = PropertiesComponent.getInstance(project)
        val sdk = DartSdk.getDartSdk(project)
        assertNotNull(sdk)
        val versionField = DartSdk::class.java.getDeclaredField("myVersion").apply { isAccessible = true }
        val originalVersion = sdk!!.version
        try {
            properties.setValue("dart.lsp.experimental.enabled", true, true)

            versionField.set(sdk, "3.15.0")
            assertTrue("Expected highlighting enabled on SDK 3.15.0", DartAnalysisServerService.isLspHighlightingEnabled(project))

            versionField.set(sdk, "3.13.0")
            assertFalse("Expected highlighting disabled on SDK 3.13.0", DartAnalysisServerService.isLspHighlightingEnabled(project))

            properties.setValue("dart.lsp.experimental.enabled", false, true)
            versionField.set(sdk, "3.15.0")
            assertFalse("Expected highlighting disabled when experimental setting is false", DartAnalysisServerService.isLspHighlightingEnabled(project))
        } finally {
            properties.unsetValue("dart.lsp.experimental.enabled")
            versionField.set(sdk, originalVersion)
        }
    }

    fun testIsLspHighlightingActive() {
        val properties = PropertiesComponent.getInstance(project)
        val das = DartAnalysisServerService.getInstance(project)
        try {
            properties.setValue("dart.lsp.experimental.enabled", true, true)
            // Initially before bridge initialization or legend advertisement:
            assertFalse(das.isSemanticTokensAdvertised)
            assertFalse(DartAnalysisServerService.isLspHighlightingActive(project))

            // Simulate advertisement:
            das.setSemanticTokensAdvertised(true)
            val sdk = DartSdk.getDartSdk(project)
            assertNotNull(sdk)
            val isSufficient = DartAnalysisServerService.isDartSdkVersionSufficientForLspHighlighting(sdk!!.version)
            assertEquals(isSufficient, DartAnalysisServerService.isLspHighlightingActive(project))

            // When experimental LSP is disabled, highlighting is not active even if advertised:
            properties.setValue("dart.lsp.experimental.enabled", false, true)
            assertFalse(DartAnalysisServerService.isLspHighlightingActive(project))

            // Reset back:
            das.setSemanticTokensAdvertised(false)
            assertFalse(DartAnalysisServerService.isLspHighlightingActive(project))
        } finally {
            properties.unsetValue("dart.lsp.experimental.enabled")
            das.setSemanticTokensAdvertised(false)
        }
    }

    fun testSemanticTokensLegendFuture_notMutatedOnTimeout() {
        val das = DartAnalysisServerService.getInstance(project)
        val legendFuture = das.semanticTokensLegendFuture
        assertFalse(legendFuture.isDone)

        // Simulating the timeout pattern used in DartBridgeLspServer.initialize:
        val timedOutCopy = legendFuture.copy().completeOnTimeout(null, 50, TimeUnit.MILLISECONDS)
        val copyResult = timedOutCopy.get(1, TimeUnit.SECONDS)
        assertNull("Copy should have completed with null on timeout", copyResult)

        // The original shared future in DAS must NOT be completed:
        assertFalse("Original future in DAS must remain uncompleted after copy timeout", legendFuture.isDone)

        // Now verify real DAS response can still complete the original future:
        val legend = SemanticTokensLegend(listOf("class"), listOf("declaration"))
        assertTrue("Original future should accept completion", legendFuture.complete(legend))
        assertEquals(legend, das.semanticTokensLegendFuture.get())
    }

    fun testInitializeCapabilitiesIncludesSignatureHelpOptions() {
        val initResult = bridgeServer.initialize(InitializeParams()).get(5, TimeUnit.SECONDS)
        val signatureHelpOptions = initResult.capabilities.signatureHelpProvider
        assertNotNull("signatureHelpProvider capability must be set", signatureHelpOptions)
        assertEquals(listOf("(", ","), signatureHelpOptions.triggerCharacters)
        assertEquals(listOf(","), signatureHelpOptions.retriggerCharacters)
    }

    fun testSignatureHelpRequest() {
        val params = SignatureHelpParams(TextDocumentIdentifier("file:///test.dart"), Position(1, 10))
        val future = bridgeServer.signatureHelp(params)

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id")?.asString)

        val lspMessage = jsonObject.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("textDocument/signatureHelp", lspMessage.get("method").asString)

        val responseJson = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "signatures": [
                      {
                        "label": "someFn(int a, String b)",
                        "parameters": [
                          { "label": "int a" },
                          { "label": "String b" }
                        ]
                      }
                    ],
                    "activeSignature": 0,
                    "activeParameter": 1
                  }
                }
              }
            }
        """.trimIndent()

        capturedListener.onResponse(responseJson)

        val result = future.get(5, TimeUnit.SECONDS)
        assertNotNull(result)
        assertEquals(1, result.signatures.size)
        assertEquals("someFn(int a, String b)", result.signatures[0].label)
        assertEquals(2, result.signatures[0].parameters.size)
        assertEquals("int a", result.signatures[0].parameters[0].label.left)
        assertEquals("String b", result.signatures[0].parameters[1].label.left)
        assertEquals(0, result.activeSignature)
        assertEquals(1, result.activeParameter)
    }

    fun testSignatureHelpRequestWithNullResult() {
        val params = SignatureHelpParams(TextDocumentIdentifier("file:///test.dart"), Position(0, 0))
        val future = bridgeServer.signatureHelp(params)

        val jsonObject = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" }) {
            "An lsp.handle request should be sent to DAS"
        }
        assertEquals("123", jsonObject.get("id")?.asString)

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
        assertNull(result)
    }

    fun testDescriptorSignatureHelpCustomizer() {
        val properties = PropertiesComponent.getInstance(project)
        val descriptor = DartLspServerDescriptor(project)
        try {
            properties.setValue("dart.lsp.experimental.enabled", true, true)
            assertTrue(descriptor.lspCustomization.signatureHelpCustomizer is LspSignatureHelpSupport)

            properties.setValue("dart.lsp.experimental.enabled", false, true)
            assertSame(LspSignatureHelpDisabled, descriptor.lspCustomization.signatureHelpCustomizer)
        } finally {
            properties.unsetValue("dart.lsp.experimental.enabled")
        }
    }

    fun testBuildLspCapabilitiesIncludesInteractiveFormsAndRegexAndResourceOperations() {
        val enabledCaps = DartAnalysisServerService.buildLspCapabilities("3.14.0", true, true, true)
        val workspaceEdit = enabledCaps.getAsJsonObject("workspace").getAsJsonObject("workspaceEdit")
        assertTrue(workspaceEdit.get("documentChanges").asBoolean)
        val resourceOperations = workspaceEdit.getAsJsonArray("resourceOperations").map { it.asString }
        assertEquals(listOf("create"), resourceOperations)

        val general = enabledCaps.getAsJsonObject("general")
        assertNotNull("general capabilities must be present", general)
        assertEquals("ECMAScript", general.getAsJsonObject("regularExpressions").get("engine").asString)

        val experimental = enabledCaps.getAsJsonObject("experimental")
        assertNotNull("experimental capabilities must be present", experimental)
        assertTrue("closingLabels should be preserved in experimental", experimental.has("closingLabels"))
        val interactiveResolve = experimental.getAsJsonObject("interactiveResolve")
        assertNotNull("interactiveResolve must be present when supportsLspCodeActions is true", interactiveResolve)
        val inputTypes = interactiveResolve.getAsJsonArray("inputTypes").map { it.asString }
        assertEquals(listOf("string", "file", "bool", "number"), inputTypes)

        val disabledCaps = DartAnalysisServerService.buildLspCapabilities("3.14.0", true, true, false)
        val disabledExperimental = disabledCaps.getAsJsonObject("experimental")
        assertNotNull(disabledExperimental)
        assertFalse("interactiveResolve must not be present when supportsLspCodeActions is false", disabledExperimental.has("interactiveResolve"))
    }

    fun testDescriptorClientCapabilitiesAndCommandsCustomizerForInteractiveForms() {
        val properties = PropertiesComponent.getInstance(project)
        val sdk = requireNotNull(DartSdk.getDartSdk(project))
        val versionField = DartSdk::class.java.getDeclaredField("myVersion").apply { isAccessible = true }
        val originalVersion = sdk.version
        val descriptor = DartLspServerDescriptor(project)
        try {
            properties.setValue("dart.lsp.experimental.enabled", true, true)
            versionField.set(sdk, "3.14.0")
            assertTrue(DartConfigurable.isLspCodeActionsEnabled(project))

            val capsEnabled = descriptor.clientCapabilities
            assertEquals("ECMAScript", capsEnabled.general?.regularExpressions?.engine)
            @Suppress("UNCHECKED_CAST")
            val expEnabled = capsEnabled.experimental as? Map<String, Any?>
            assertNotNull(expEnabled)
            @Suppress("UNCHECKED_CAST")
            val interactiveResolve = expEnabled?.get("interactiveResolve") as? Map<String, Any?>
            assertNotNull("interactiveResolve must be advertised on SDK 3.14.0+", interactiveResolve)
            assertEquals(listOf("string", "file", "bool", "number"), interactiveResolve?.get("inputTypes"))
            assertTrue(descriptor.lspCustomization.commandsCustomizer is DartLspCommandsSupport)

            // Older SDK (3.13.0 < 3.14.0-137.0.dev) -> disabled
            versionField.set(sdk, "3.13.0")
            assertFalse(DartConfigurable.isLspCodeActionsEnabled(project))
            @Suppress("UNCHECKED_CAST")
            val expOlderSdk = descriptor.clientCapabilities.experimental as? Map<String, Any?>
            assertNull(expOlderSdk?.get("interactiveResolve"))
            assertSame(LspCommandsDisabled, descriptor.lspCustomization.commandsCustomizer)

            // Experimental setting disabled on SDK 3.14.0 -> disabled
            versionField.set(sdk, "3.14.0")
            properties.setValue("dart.lsp.experimental.enabled", false, true)
            val capsDisabled = descriptor.clientCapabilities
            assertEquals("ECMAScript", capsDisabled.general?.regularExpressions?.engine)
            @Suppress("UNCHECKED_CAST")
            val expDisabled = capsDisabled.experimental as? Map<String, Any?>
            assertNotNull(expDisabled)
            assertNull(expDisabled?.get("interactiveResolve"))
            assertSame(
                LspCommandsDisabled,
                descriptor.lspCustomization.commandsCustomizer,
            )
        } finally {
            properties.unsetValue("dart.lsp.experimental.enabled")
            versionField.set(sdk, originalVersion)
        }
    }

    fun testResolveCommandSerializationAndMultiTurnResponse() {
        // Turn 1: initial command/resolve with null formAnswers/formFields
        val initialParams = InteractiveExecuteCommandParams(
            command = "refactor.perform",
            arguments = listOf(mapOf("kind" to "extract_method", "file" to "/test/a.dart")),
        )
        val future1 = bridgeServer.resolveCommand(initialParams)

        val req1 = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" })
        val lspMsg1 = req1.getAsJsonObject("params").getAsJsonObject("lspMessage")
        assertEquals("command/resolve", lspMsg1.get("method").asString)
        val wireParams1 = lspMsg1.getAsJsonObject("params")
        assertEquals("refactor.perform", wireParams1.get("command").asString)
        // Null optional fields must be omitted (not serialized as JSON null) because DAS uses allowsUndefined: true, allowsNull: false
        assertFalse("formAnswers must be omitted when null", wireParams1.has("formAnswers"))
        assertFalse("formFields must be omitted when null", wireParams1.has("formFields"))
        assertFalse("data must be omitted when null", wireParams1.has("data"))

        val response1Json = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "command": "refactor.perform",
                    "arguments": [
                      { "kind": "extract_method", "file": "/test/a.dart" }
                    ],
                    "formFields": [
                      {
                        "id": "name",
                        "description": "Method name",
                        "required": true,
                        "default": "newMethod",
                        "type": {
                          "kind": "string",
                          "validators": [
                            {
                              "kind": "regex",
                              "pattern": "^[a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*${'$'}",
                              "matchIsValid": true,
                              "message": "Method name must be a valid identifier.",
                              "severity": 3
                            },
                            {
                              "kind": "regex",
                              "pattern": "^_*[a-z][a-zA-Z0-9_${'$'}]*${'$'}",
                              "matchIsValid": true,
                              "message": "Name should start with a lowercase letter.",
                              "severity": 2
                            }
                          ]
                        }
                      },
                      {
                        "id": "destination",
                        "description": "Move to file",
                        "required": true,
                        "default": "file:///test/new_file.dart",
                        "type": {
                          "kind": "file",
                          "existence": 3,
                          "type": 1,
                          "filters": ["dart"]
                        }
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()
        capturedListener.onResponse(response1Json)

        val resolved1 = future1.get(5, TimeUnit.SECONDS)
        assertNotNull(resolved1)
        assertEquals("refactor.perform", resolved1.command)
        val fields = requireNotNull(resolved1.formFields)
        assertEquals(2, fields.size)
        val nameField = fields[0]
        assertEquals("name", nameField.id)
        assertEquals("Method name", nameField.description)
        assertTrue(nameField.required)
        assertEquals("newMethod", nameField.defaultValue)
        assertEquals("string", nameField.type.kind)
        val validators = requireNotNull(nameField.type.validators)
        assertEquals(2, validators.size)
        assertEquals(RegexValidator.SEVERITY_ERROR, validators[0].severity)
        assertEquals(RegexValidator.SEVERITY_WARNING, validators[1].severity)

        val fileField = fields[1]
        assertEquals("destination", fileField.id)
        assertEquals("file:///test/new_file.dart", fileField.defaultValue)
        assertEquals("file", fileField.type.kind)
        assertEquals(3, fileField.type.existence)
        assertEquals(FormFieldType.FILE_TYPE_REGULAR, fileField.type.type)
        assertEquals(listOf("dart"), fileField.type.filters)

        // Turn 2: submit formAnswers and receive resolved command with formFields omitted
        capturedRequests.clear()
        val submitParams = resolved1.copy(
            formAnswers = listOf(
                FormAnswer("name", "extractedHelper"),
                FormAnswer("destination", "file:///test/extracted.dart"),
            ),
        )
        val future2 = bridgeServer.resolveCommand(submitParams)
        val req2 = requireNotNull(capturedRequests.find { it.get("method")?.asString == "lsp.handle" })
        val wireParams2 = req2.getAsJsonObject("params").getAsJsonObject("lspMessage").getAsJsonObject("params")
        val wireAnswers = wireParams2.getAsJsonArray("formAnswers")
        assertEquals(2, wireAnswers.size())
        assertEquals("name", wireAnswers[0].asJsonObject.get("id").asString)
        assertEquals("extractedHelper", wireAnswers[0].asJsonObject.get("value").asString)

        val response2Json = """
            {
              "id": "123",
              "result": {
                "lspResponse": {
                  "jsonrpc": "2.0",
                  "id": "123",
                  "result": {
                    "command": "refactor.perform",
                    "arguments": [
                      {
                        "kind": "extract_method",
                        "file": "/test/a.dart",
                        "options": { "name": "extractedHelper" }
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()
        capturedListener.onResponse(response2Json)

        val resolved2 = future2.get(5, TimeUnit.SECONDS)
        assertNotNull(resolved2)
        assertTrue("formFields should be null/empty when fully resolved", resolved2.formFields.isNullOrEmpty())
    }

    fun testInteractiveFormValidationRulesAndConversions() {
        val stringField = FormField(
            id = "name",
            description = "Method name",
            required = true,
            defaultValue = "newMethod",
            type = FormFieldType(
                kind = FormFieldType.KIND_STRING,
                validators = listOf(
                    RegexValidator(
                        pattern = "^[a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*${'$'}",
                        matchIsValid = true,
                        message = "Method name must be a valid identifier.",
                        severity = RegexValidator.SEVERITY_ERROR,
                    ),
                    RegexValidator(
                        pattern = "^(await|yield|class)${'$'}",
                        matchIsValid = false,
                        message = "Method name must not be a reserved keyword.",
                        severity = RegexValidator.SEVERITY_ERROR,
                    ),
                    RegexValidator(
                        pattern = "^.{3,}",
                        matchIsValid = true,
                        message = "Name is very short.",
                        severity = RegexValidator.SEVERITY_INFO,
                    ),
                    RegexValidator(
                        pattern = "^_*[a-z][a-zA-Z0-9_${'$'}]*${'$'}",
                        matchIsValid = true,
                        message = "Name should start with a lowercase letter.",
                        severity = RegexValidator.SEVERITY_WARNING,
                    ),
                ),
            ),
        )

        // 1. Valid lowerCamelCase identifier -> no ValidationInfo
        assertNull(DartLspCommandsSupport.validateFieldInput(stringField, "myHelper"))

        // 2. Empty required field -> blocking error
        val emptyValidation = requireNotNull(DartLspCommandsSupport.validateFieldInput(stringField, ""))
        assertFalse(emptyValidation.warning)

        // 3. Invalid identifier (starts with digit) -> blocking error (SEVERITY_ERROR)
        val invalidIdValidation = requireNotNull(DartLspCommandsSupport.validateFieldInput(stringField, "123bad"))
        assertFalse("SEVERITY_ERROR must produce blocking ValidationInfo", invalidIdValidation.warning)
        assertEquals("Method name must be a valid identifier.", invalidIdValidation.message)

        // 4. Reserved keyword (matchIsValid = false) -> blocking error
        val keywordValidation = requireNotNull(DartLspCommandsSupport.validateFieldInput(stringField, "class"))
        assertFalse(keywordValidation.warning)
        assertEquals("Method name must not be a reserved keyword.", keywordValidation.message)

        // 5. UpperCamelCase 2-letter identifier ("Ab") fails both INFO (<3 chars) and WARNING (uppercase);
        // SEVERITY_WARNING (2) must take precedence over SEVERITY_INFO (1)
        val warningValidation = requireNotNull(DartLspCommandsSupport.validateFieldInput(stringField, "Ab"))
        assertTrue("SEVERITY_WARNING must produce warning ValidationInfo", warningValidation.warning)
        assertTrue("Warning ValidationInfo must keep OK enabled", warningValidation.okEnabled)
        assertEquals("Name should start with a lowercase letter.", warningValidation.message)

        // 6. Active server-side error takes precedence and blocks until cleared
        val serverErrValidation = requireNotNull(
            DartLspCommandsSupport.validateFieldInput(
                stringField,
                "myHelper",
                activeServerError = "A method with name 'myHelper' already exists.",
            ),
        )
        assertFalse(serverErrValidation.warning)
        assertEquals("A method with name 'myHelper' already exists.", serverErrValidation.message)

        // 7. Number field validation and parsing (including NaN / Infinity rejection)
        val numberField = FormField(
            id = "count",
            description = "Count",
            required = true,
            type = FormFieldType(kind = FormFieldType.KIND_NUMBER),
        )
        assertNull(DartLspCommandsSupport.validateFieldInput(numberField, "42"))
        assertNull(DartLspCommandsSupport.validateFieldInput(numberField, "3.14"))
        val badNumber = requireNotNull(DartLspCommandsSupport.validateFieldInput(numberField, "notANumber"))
        assertFalse(badNumber.warning)
        assertNotNull("NaN must be rejected", DartLspCommandsSupport.validateFieldInput(numberField, "NaN"))
        assertNotNull("Infinity must be rejected", DartLspCommandsSupport.validateFieldInput(numberField, "Infinity"))
        assertNotNull("-Infinity must be rejected", DartLspCommandsSupport.validateFieldInput(numberField, "-Infinity"))
        assertEquals(42L, DartLspCommandsSupport.parseNumberAnswer("42"))
        assertEquals(3.14, DartLspCommandsSupport.parseNumberAnswer("3.14"))
        assertNull(DartLspCommandsSupport.parseNumberAnswer("NaN"))
        assertNull(DartLspCommandsSupport.parseNumberAnswer("Infinity"))

        // 8. File field extension filter, relative path resolution, and URI <-> path conversion
        val fileField = FormField(
            id = "file",
            description = "Destination file",
            required = true,
            type = FormFieldType(
                kind = FormFieldType.KIND_FILE,
                filters = listOf("dart"),
            ),
        )
        assertNull(DartLspCommandsSupport.validateFieldInput(fileField, "/tmp/moved_widget.dart"))
        val badExt = requireNotNull(DartLspCommandsSupport.validateFieldInput(fileField, "/tmp/moved_widget.txt"))
        assertFalse(badExt.warning)
        assertTrue(badExt.message.contains(".dart"))

        val uriStr = DartLspCommandsSupport.filePathToUriString("lib/new_nonexistent_file.dart", "/workspace/my_app")
        assertEquals("file:///workspace/my_app/lib/new_nonexistent_file.dart", uriStr)
        val roundTripPath = DartLspCommandsSupport.uriStringToFilePath(uriStr)
        assertTrue(roundTripPath.endsWith("new_nonexistent_file.dart"))

        // 9. collectFormAnswers omits optional blank fields and converts types properly
        val optionalStringField = stringField.copy(id = "optionalPrefix", required = false, type = FormFieldType(FormFieldType.KIND_STRING))
        val boolField = FormField(id = "extractAll", description = "Extract all", required = true, defaultValue = true, type = FormFieldType(FormFieldType.KIND_BOOL))
        val answers = DartLspCommandsSupport.collectFormAnswers(
            listOf(stringField, optionalStringField, numberField, boolField, fileField),
            mapOf(
                "name" to "validName",
                "optionalPrefix" to "",
                "count" to "7",
                "extractAll" to false,
                "file" to "/tmp/target.dart",
            ),
        )
        assertEquals(4, answers.size)
        val answersById = answers.associate { it.id to it.value }
        assertEquals("validName", answersById["name"])
        assertFalse("Blank optional field must be omitted from formAnswers", answersById.containsKey("optionalPrefix"))
        assertEquals(7L, answersById["count"])
        assertEquals(false, answersById["extractAll"])
        assertTrue((answersById["file"] as String).startsWith("file:///"))
    }

    fun testDartLspCommandsSupportImmediateExecutionWhenNoFormFields() {
        var resolvedRequestParams: InteractiveExecuteCommandParams? = null
        var executedCommandParams: ExecuteCommandParams? = null

        val fakeWorkspaceService = object : WorkspaceService {
            override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
                executedCommandParams = params
                return CompletableFuture.completedFuture(null)
            }
            override fun didChangeConfiguration(params: DidChangeConfigurationParams) {}
            override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {}
        }

        val fakeDartServer = Proxy.newProxyInstance(
            DartLanguageServer::class.java.classLoader,
            arrayOf(DartLanguageServer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "resolveCommand" -> {
                    val req = args[0] as InteractiveExecuteCommandParams
                    resolvedRequestParams = req
                    // Simulate server returning resolved arguments and null formFields
                    CompletableFuture.completedFuture(
                        InteractiveExecuteCommandParams(
                            command = req.command,
                            arguments = listOf(mapOf("kind" to "inline_method", "resolved" to true)),
                            formFields = null,
                        ),
                    )
                }
                "getWorkspaceService" -> fakeWorkspaceService
                else -> null
            }
        } as DartLanguageServer

        val fakeLspServer = Proxy.newProxyInstance(
            LspServer::class.java.classLoader,
            arrayOf(LspServer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "sendRequestSync" -> {
                    @Suppress("UNCHECKED_CAST")
                    val fn = args.first { it is Function1<*, *> } as (Lsp4jServer) -> CompletableFuture<*>
                    fn(fakeDartServer).get(5, TimeUnit.SECONDS)
                }
                "sendNotification" -> {
                    @Suppress("UNCHECKED_CAST")
                    val fn = args[0] as (Lsp4jServer) -> Unit
                    fn(fakeDartServer)
                    Unit
                }
                else -> null
            }
        } as LspServer

        val dummyFile = myFixture.configureByText("test.dart", "void main() {}").virtualFile
        val support = DartLspCommandsSupport(project)
        support.executeCommand(fakeLspServer, dummyFile, Command("Inline Method", "refactor.perform", listOf("initial")))

        assertNotNull("command/resolve must be called first", resolvedRequestParams)
        assertEquals("refactor.perform", resolvedRequestParams?.command)
        assertEquals(listOf("initial"), resolvedRequestParams?.arguments)

        assertNotNull("workspace/executeCommand must be called with resolved arguments when formFields is null", executedCommandParams)
        assertEquals("refactor.perform", executedCommandParams?.command)
        assertEquals(listOf(mapOf("kind" to "inline_method", "resolved" to true)), executedCommandParams?.arguments)
    }

    fun testDartInteractiveFormDialogMultiTurnServerValidationAndSuccess() {
        var turnCount = 0
        val capturedResolveRequests = mutableListOf<InteractiveExecuteCommandParams>()

        val nameField = FormField(
            id = "name",
            description = "Method name",
            required = true,
            defaultValue = "existingMethod",
            type = FormFieldType(kind = FormFieldType.KIND_STRING),
        )
        val initialResolved = InteractiveExecuteCommandParams(
            command = "refactor.perform",
            arguments = listOf(mapOf("kind" to "extract_method")),
            formFields = listOf(nameField),
        )

        val fakeDartServer = Proxy.newProxyInstance(
            DartLanguageServer::class.java.classLoader,
            arrayOf(DartLanguageServer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "resolveCommand" -> {
                    turnCount++
                    val req = args[0] as InteractiveExecuteCommandParams
                    capturedResolveRequests.add(req)
                    if (turnCount == 1) {
                        // Turn 1: Server rejects "existingMethod" with a field-level server error
                        CompletableFuture.completedFuture(
                            req.copy(
                                formFields = listOf(
                                    nameField.copy(error = "A method with name 'existingMethod' already exists."),
                                ),
                            ),
                        )
                    } else {
                        // Turn 2: Server accepts "freshMethod" and returns null formFields
                        CompletableFuture.completedFuture(
                            InteractiveExecuteCommandParams(
                                command = req.command,
                                arguments = listOf(mapOf("kind" to "extract_method", "name" to "freshMethod")),
                                formFields = null,
                            ),
                        )
                    }
                }
                else -> null
            }
        } as DartLanguageServer

        val fakeLspServer = Proxy.newProxyInstance(
            LspServer::class.java.classLoader,
            arrayOf(LspServer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "sendRequestSync" -> {
                    @Suppress("UNCHECKED_CAST")
                    val fn = args.first { it is Function1<*, *> } as (Lsp4jServer) -> CompletableFuture<*>
                    fn(fakeDartServer).get(5, TimeUnit.SECONDS)
                }
                else -> null
            }
        } as LspServer

        val dialog = DartInteractiveFormDialog(project, fakeLspServer, "Extract Method", initialResolved)
        try {
            assertEquals(mapOf("name" to "existingMethod"), dialog.collectRawValues())
            assertTrue(dialog.doValidateAll().isEmpty())

            // Submit 1: server returns field error -> dialog stays open with activeServerErrors populated
            dialog.doOKAction()
            assertNull("Dialog must not finalize when server returns formFields with error", dialog.finalResolvedParams)
            assertEquals("A method with name 'existingMethod' already exists.", dialog.activeServerErrors["name"])
            val validationsAfterTurn1 = dialog.doValidateAll()
            assertEquals(1, validationsAfterTurn1.size)
            assertEquals("A method with name 'existingMethod' already exists.", validationsAfterTurn1[0].message)

            // User edits the text field -> activeServerErrors for "name" is cleared automatically
            val textField = dialog.fieldComponents["name"] as JBTextField
            textField.text = "freshMethod"
            assertNull("Editing the field must clear its activeServerError", dialog.activeServerErrors["name"])
            assertTrue(dialog.doValidateAll().isEmpty())

            // Submit 2: server returns null formFields -> dialog stores finalResolvedParams
            dialog.doOKAction()
            assertNotNull("Dialog must store finalResolvedParams when server returns null formFields", dialog.finalResolvedParams)
            assertEquals(2, capturedResolveRequests.size)
            assertEquals(listOf(FormAnswer("name", "freshMethod")), capturedResolveRequests[1].formAnswers)
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    private class MockLanguageClient : DartLanguageClient {
        var publishedDiagnostics: PublishDiagnosticsParams? = null
        var publishedClosingLabels: DartPublishClosingLabelsParams? = null
        var lastApplyWorkspaceEditParams: ApplyWorkspaceEditParams? = null
        var applyEditResult: ApplyWorkspaceEditResponse = ApplyWorkspaceEditResponse(true)
        var lastConfigurationParams: ConfigurationParams? = null
        var configurationResult: List<Any?> = emptyList()

        override fun publishClosingLabels(params: DartPublishClosingLabelsParams) {
            publishedClosingLabels = params
        }

        override fun applyEdit(params: ApplyWorkspaceEditParams?): CompletableFuture<ApplyWorkspaceEditResponse> {
            lastApplyWorkspaceEditParams = params
            return CompletableFuture.completedFuture(applyEditResult)
        }

        override fun configuration(params: ConfigurationParams?): CompletableFuture<List<Any?>> {
            lastConfigurationParams = params
            return CompletableFuture.completedFuture(configurationResult)
        }

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
