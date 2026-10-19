/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.analyzer

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
import com.jetbrains.lang.dart.lsp.DartBridgeLspServer
import com.jetbrains.lang.dart.lsp.DartLspInlayHintsConfiguration
import com.jetbrains.lang.dart.lsp.DartLspServerDescriptor
import com.jetbrains.lang.dart.sdk.DartSdk
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests the answer to the `workspace/configuration` request that the Dart Analysis Server sends
 * wrapped into a legacy `lsp.handle` request, routed through [DartBridgeLspServer] and
 * [DartLspServerDescriptor]. The server blocks its initialization until it gets that answer, so
 * the shape of the response is what matters here.
 */
class DartLspWorkspaceConfigurationTest : DartCodeInsightFixtureTestCase() {

    private var previousSdkVersion: String? = null
    private lateinit var bridgeServer: DartBridgeLspServer
    private lateinit var capturedListener: ResponseListener
    private val capturedRequests = CopyOnWriteArrayList<JsonObject>()
    private val capturedResponses = CopyOnWriteArrayList<JsonObject>()

    override fun setUp() {
        super.setUp()

        val das = DartAnalysisServerService.getInstance(project)
        val sdk = requireNotNull(DartSdk.getDartSdk(project)) { "Dart SDK not found" }
        setSdkVersion(sdk.version)

        val stubServer = object : RemoteAnalysisServerImpl(createStubSocket()) {
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

            override fun sendResponseToServer(response: JsonObject) {
                capturedResponses.add(response)
            }

            override fun server_openUrlRequest(url: String?) {}
            override fun server_showMessageRequest(
                type: String?,
                message: String?,
                actions: MutableList<MessageAction>?,
                consumer: ShowMessageRequestConsumer?
            ) {}

            override fun lsp_workspaceApplyEdit(
                params: DartLspApplyWorkspaceEditParams?,
                consumer: DartLspWorkspaceApplyEditRequestConsumer?
            ) {}
        }
        das.setServer(stubServer)
        bridgeServer = DartBridgeLspServer(project)
    }

    override fun tearDown() {
        try {
            if (::bridgeServer.isInitialized) {
                bridgeServer.stop()
            }
            DartAnalysisServerService.getInstance(project).setServer(null)
            previousSdkVersion?.let { setSdkVersion(it) }
            capturedRequests.clear()
            capturedResponses.clear()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    /** Fakes the version of the SDK that the running server was started from. */
    private fun setSdkVersion(version: String) {
        val service = DartAnalysisServerService.getInstance(project)
        val field = DartAnalysisServerService::class.java.getDeclaredField("mySdkVersion")
            .apply { isAccessible = true }
        if (previousSdkVersion == null) previousSdkVersion = field.get(service) as String
        field.set(service, version)
    }

    private fun createStubSocket(): AnalysisServerSocket = object : AnalysisServerSocket {
        override fun getErrorStream(): ByteLineReaderStream? = null
        override fun getRequestSink(): RequestSink? = null
        override fun getResponseStream(): ResponseStream? = null
        override fun isOpen(): Boolean = true
        override fun start() {}
        override fun stop() {}
    }

    /** The configuration that the test client hands out for the `dart` section. */
    private val dartSection =
        JsonObject().apply { add(DartLspInlayHintsConfiguration.INLAY_HINTS_KEY, JsonObject()) }

    private open inner class StubLanguageClient(
        private val computeSection: (String?) -> Any? = { if (it == "dart") dartSection else null }
    ) : LanguageClient {
        val requestedSections = mutableListOf<String?>()

        override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any?>> {
            val sections = params.items.map { it.section }
            requestedSections.addAll(sections)
            return CompletableFuture.completedFuture(sections.map(computeSection))
        }

        override fun telemetryEvent(obj: Any?) {}
        override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {}
        override fun showMessage(messageParams: MessageParams?) {}
        override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> =
            CompletableFuture.completedFuture(null)
        override fun logMessage(message: MessageParams?) {}
    }

    private fun configurationRequest(items: String) = """
    {
      "id": "das_3",
      "method": "lsp.handle",
      "params": {
        "lspMessage": {
          "id": 7,
          "jsonrpc": "2.0",
          "method": "workspace/configuration",
          "params": {
            "items": [$items]
          }
        }
      }
    }
    """.trimIndent()

    private fun answerTo(items: String, client: StubLanguageClient = StubLanguageClient()): StubLanguageClient {
        bridgeServer.connect(client)
        capturedListener.onResponse(configurationRequest(items))
        assertEquals("the server must always get exactly one answer", 1, capturedResponses.size)
        return client
    }

    private fun lspResponse(): JsonObject {
        val response = capturedResponses[0]
        val result = requireNotNull(response.getAsJsonObject("result")) { "response should carry a result: $response" }
        return requireNotNull(result.getAsJsonObject("lspResponse")) { "result should carry an lspResponse: $result" }
    }

    fun testAnswerUsesTheLspOverLegacyEnvelope() {
        answerTo("""{ "section": "dart" }""")

        val response = capturedResponses[0]
        assertEquals("the legacy request id must be echoed", "das_3", response.get("id").asString)

        val lspResponse = lspResponse()
        assertEquals("2.0", lspResponse.get("jsonrpc").asString)
        // The server sends the LSP id as a number, so it has to be echoed as a number.
        assertTrue("the LSP request id must keep its JSON type", lspResponse.get("id").asJsonPrimitive.isNumber)
        assertEquals(7, lspResponse.get("id").asInt)
    }

    fun testAnswerCarriesTheDartConfigurationSection() {
        val client = answerTo("""{ "section": "dart" }""")

        assertEquals(listOf("dart"), client.requestedSections)

        val result = requireNotNull(lspResponse().getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals("one entry per requested item", 1, result.size())
        assertEquals(dartSection, result[0].asJsonObject)
    }

    fun testAnswerHasOneEntryPerRequestedItemAndNullForUnknownSections() {
        val client = answerTo("""{ "section": "dart" }, { "section": "flutter" }, {}""")

        assertEquals(listOf("dart", "flutter", null), client.requestedSections)

        val result = requireNotNull(lspResponse().getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals("one entry per requested item", 3, result.size())
        assertEquals(dartSection, result[0].asJsonObject)
        assertTrue("an unknown section must be answered with null", result[1].isJsonNull)
        assertTrue("an item without a section must be answered with null", result[2].isJsonNull)
    }

    fun testAnswerToAnEmptyItemListIsAnEmptyArray() {
        answerTo("")

        val result = requireNotNull(lspResponse().getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals(0, result.size())
    }

    fun testAnswerIsSentEvenIfTheConfigurationCannotBeComputed() {
        // An unanswered request blocks the initialization of the server, so a failure to compute the
        // configuration must still result in an answer that lets the server use its defaults.
        val failingClient = object : StubLanguageClient() {
            override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any?>> {
                return CompletableFuture.failedFuture(IllegalStateException("cannot read the settings"))
            }
        }

        answerTo("""{ "section": "dart" }, { "section": "flutter" }""", failingClient)

        val result = requireNotNull(lspResponse().getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals("one entry per requested item", 2, result.size())
        assertTrue("a section that could not be computed must be answered with null", result[0].isJsonNull)
        assertTrue("a section that could not be computed must be answered with null", result[1].isJsonNull)
    }

    fun testAnswerIsSentEvenIfComputingTheConfigurationFailsWithAnError() {
        val throwingClient = object : StubLanguageClient() {
            override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any?>> {
                throw NoClassDefFoundError("com/jetbrains/lang/dart/lsp/DartLspInlayHintsConfiguration")
            }
        }

        answerTo("""{ "section": "dart" }""", throwingClient)

        val result = requireNotNull(lspResponse().getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals("one entry per requested item", 1, result.size())
        assertTrue("a section that could not be computed must be answered with null", result[0].isJsonNull)
    }

    fun testClientCapabilitiesOnlyAskTheServerToPullConfigurationOnceBridgeIsInitialized() {
        // Before the bridge server is connected, configuration is not advertised so DAS does not
        // pull workspace/configuration before the bridge can handle it.
        for (sdkVersion in listOf("3.14.0", "3.7.0")) {
            val preBridgeCaps = DartAnalysisServerService.buildLspCapabilities(sdkVersion)
            assertNull(
                "configuration should not be advertised before the bridge is connected ($sdkVersion)",
                preBridgeCaps.getAsJsonObject("workspace")?.get("configuration")
            )

            val bridgeCaps = DartAnalysisServerService.buildLspCapabilities(sdkVersion, false, false, false, true)
            val workspace = requireNotNull(bridgeCaps.getAsJsonObject("workspace")) {
                "the workspace capabilities should be present when configuration is enabled ($sdkVersion), was: $bridgeCaps"
            }
            assertTrue("configuration should be advertised for $sdkVersion", workspace.get("configuration").asBoolean)
        }

        // Initializing the bridge server triggers server.setClientCapabilities with configuration = true.
        bridgeServer.connect(StubLanguageClient())
        bridgeServer.initialize(InitializeParams())

        val setCapsRequest = requireNotNull(capturedRequests.find { it.get("method")?.asString == "server.setClientCapabilities" }) {
            "DartBridgeLspServer.initialize should send server.setClientCapabilities"
        }
        val lspCaps = requireNotNull(setCapsRequest.getAsJsonObject("params")?.getAsJsonObject("lspCapabilities")) {
            "server.setClientCapabilities should include lspCapabilities"
        }
        assertTrue(
            "lspCapabilities.workspace.configuration should be true after bridge initialization",
            lspCaps.getAsJsonObject("workspace")?.get("configuration")?.asBoolean == true
        )
    }

    fun testTheConfigurationNotificationIsGatedOnTheSdkVersion() {
        // 3.14.0-258.0.dev is the first Dart SDK with dart-lang/sdk@6700ccc4316; an older server
        // cannot handle a notification from the client and logs it as an error.
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspInlayHintsConfiguration("3.14.0-258.0.dev"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspInlayHintsConfiguration("3.14.0"))
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspInlayHintsConfiguration("3.15.0-1.0.dev"))

        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspInlayHintsConfiguration(""))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspInlayHintsConfiguration("3.14.0-257.0.dev"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspInlayHintsConfiguration("3.13.0"))
    }

    fun testDartLspServerDescriptorSuppliesTheInlayHintSettings() {
        val descriptor = DartLspServerDescriptor(project)

        val dartConfig = descriptor.getWorkspaceConfiguration(ConfigurationItem().apply { section = "dart" }) as? JsonObject
        val inlayHints = requireNotNull(dartConfig?.getAsJsonObject(DartLspInlayHintsConfiguration.INLAY_HINTS_KEY)) {
            "the dart section should carry the inlay hint settings, was: $dartConfig"
        }
        assertEquals(DartLspInlayHintsConfiguration.SERVER_CATEGORY_KEYS, inlayHints.keySet())

        val flutterConfig = descriptor.getWorkspaceConfiguration(ConfigurationItem().apply { section = "flutter" })
        assertNull("an unknown section should be answered with null", flutterConfig)
    }

    fun testTheConfigurationIsAnsweredEvenByAServerThatCannotBeNotified() {
        setSdkVersion("3.14.0-257.0.dev")
        val descriptor = DartLspServerDescriptor(project)

        val dartConfig = descriptor.getWorkspaceConfiguration(ConfigurationItem().apply { section = "dart" }) as? JsonObject
        assertNotNull(
            "the dart section should carry the inlay hint settings, was: $dartConfig",
            dartConfig?.getAsJsonObject(DartLspInlayHintsConfiguration.INLAY_HINTS_KEY),
        )
    }
}
