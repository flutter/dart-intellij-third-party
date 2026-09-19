/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.analyzer

import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.DartLspWorkspaceConfigurationConsumer
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import com.google.dart.server.utilities.logging.Logger
import com.google.dart.server.utilities.logging.Logging
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.lsp.DartLspInlayHintsConfiguration
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction

/**
 * Tests the answer to the `workspace/configuration` request that the Dart Analysis Server sends
 * wrapped into a legacy `lsp.handle` request. The server blocks its initialization until it gets
 * that answer, so the shape of the response is what matters here.
 */
class DartLspWorkspaceConfigurationTest : DartCodeInsightFixtureTestCase() {

    private var previousLogger: Logger? = null

    override fun tearDown() {
        try {
            // Logging holds a global logger; make sure that a logger of this test does not leak,
            // whatever else fails while tearing down.
            try {
                previousLogger?.let { Logging.setLogger(it) }
            } catch (e: Throwable) {
                addSuppressedException(e)
            }
            setServerVersion("")
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    /** The protocol version is only known once the server is connected, so fake it here. */
    private fun setServerVersion(version: String) {
        val field = DartAnalysisServerService::class.java.getDeclaredField("myServerVersion")
            .apply { isAccessible = true }
        field.set(DartAnalysisServerService.getInstance(project), version)
    }

    /** Installs a logger that rethrows, the way the logger of IntelliJ rethrows control flow exceptions. */
    private fun installRethrowingLogger() {
        previousLogger = Logging.getLogger()
        Logging.setLogger(object : Logger {
            override fun logError(message: String?) {}
            override fun logError(message: String?, exception: Throwable) = throw exception
            override fun logInformation(message: String?) {}
            override fun logInformation(message: String?, exception: Throwable?) {}
        })
    }

    private fun createStubSocket(): AnalysisServerSocket = object : AnalysisServerSocket {
        override fun getErrorStream(): ByteLineReaderStream? = null
        override fun getRequestSink(): RequestSink? = null
        override fun getResponseStream(): ResponseStream? = null
        override fun isOpen(): Boolean = true
        override fun start() {}
        override fun stop() {}
    }

    /** The configuration that the test server hands out for the `dart` section. */
    private val dartSection =
        JsonObject().apply { add(DartLspInlayHintsConfiguration.INLAY_HINTS_KEY, JsonObject()) }

    private open inner class TestRemoteAnalysisServer(socket: AnalysisServerSocket) : RemoteAnalysisServerImpl(socket) {
        val requestedSections = mutableListOf<String?>()
        val sentResponses = mutableListOf<JsonObject>()

        override fun isSocketOpen(): Boolean = true
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

        override fun lsp_workspaceConfiguration(
            sections: List<String?>,
            consumer: DartLspWorkspaceConfigurationConsumer
        ) {
            requestedSections.addAll(sections)
            consumer.computedConfiguration(sections.map { if (it == "dart") dartSection else null })
        }

        override fun sendResponseToServer(response: JsonObject) {
            sentResponses.add(response)
        }

        fun testProcessResponse(response: JsonObject) {
            processResponse(response)
        }
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

    private fun answerTo(items: String): TestRemoteAnalysisServer {
        val server = TestRemoteAnalysisServer(createStubSocket())
        server.testProcessResponse(JsonParser.parseString(configurationRequest(items)).asJsonObject)
        assertEquals("the server must always get exactly one answer", 1, server.sentResponses.size)
        return server
    }

    private fun lspResponseOf(server: TestRemoteAnalysisServer): JsonObject {
        val response = server.sentResponses[0]
        val result = requireNotNull(response.getAsJsonObject("result")) { "response should carry a result: $response" }
        return requireNotNull(result.getAsJsonObject("lspResponse")) { "result should carry an lspResponse: $result" }
    }

    fun testAnswerUsesTheLspOverLegacyEnvelope() {
        val server = answerTo("""{ "section": "dart" }""")

        val response = server.sentResponses[0]
        assertEquals("the legacy request id must be echoed", "das_3", response.get("id").asString)

        val lspResponse = lspResponseOf(server)
        assertEquals("2.0", lspResponse.get("jsonrpc").asString)
        // The server sends the LSP id as a number, so it has to be echoed as a number.
        assertTrue("the LSP request id must keep its JSON type", lspResponse.get("id").asJsonPrimitive.isNumber)
        assertEquals(7, lspResponse.get("id").asInt)
    }

    fun testAnswerCarriesTheDartConfigurationSection() {
        val server = answerTo("""{ "section": "dart" }""")

        assertEquals(listOf("dart"), server.requestedSections)

        val result = requireNotNull(lspResponseOf(server).getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals("one entry per requested item", 1, result.size())
        assertEquals(dartSection, result[0].asJsonObject)
    }

    fun testAnswerHasOneEntryPerRequestedItemAndNullForUnknownSections() {
        val server = answerTo("""{ "section": "dart" }, { "section": "flutter" }, {}""")

        assertEquals(listOf("dart", "flutter", null), server.requestedSections)

        val result = requireNotNull(lspResponseOf(server).getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals("one entry per requested item", 3, result.size())
        assertEquals(dartSection, result[0].asJsonObject)
        assertTrue("an unknown section must be answered with null", result[1].isJsonNull)
        assertTrue("an item without a section must be answered with null", result[2].isJsonNull)
    }

    fun testAnswerToAnEmptyItemListIsAnEmptyArray() {
        val server = answerTo("")

        val result = requireNotNull(lspResponseOf(server).getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals(0, result.size())
    }

    fun testAnswerIsSentEvenIfTheConfigurationCannotBeComputed() {
        // An unanswered request blocks the initialization of the server, so a failure to compute the
        // configuration must still result in an answer that lets the server use its defaults.
        val server = object : TestRemoteAnalysisServer(createStubSocket()) {
            override fun lsp_workspaceConfiguration(
                sections: List<String?>,
                consumer: DartLspWorkspaceConfigurationConsumer
            ) {
                throw IllegalStateException("cannot read the settings")
            }
        }

        server.testProcessResponse(
            JsonParser.parseString(configurationRequest("""{ "section": "dart" }, { "section": "flutter" }""")).asJsonObject
        )

        assertEquals("the server must always get exactly one answer", 1, server.sentResponses.size)
        val result = requireNotNull(lspResponseOf(server).getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals("one entry per requested item", 2, result.size())
        assertTrue("a section that could not be computed must be answered with null", result[0].isJsonNull)
        assertTrue("a section that could not be computed must be answered with null", result[1].isJsonNull)
    }

    fun testAnswerIsSentEvenIfComputingTheConfigurationFailsWithAnError() {
        // The reader loop of the server swallows every Throwable, so an Error (a NoClassDefFoundError
        // while the plugin is being unloaded, an AssertionError) would leave the request unanswered
        // just like a RuntimeException does, and the server would never finish its initialization.
        val server = object : TestRemoteAnalysisServer(createStubSocket()) {
            override fun lsp_workspaceConfiguration(
                sections: List<String?>,
                consumer: DartLspWorkspaceConfigurationConsumer
            ) {
                throw NoClassDefFoundError("com/jetbrains/lang/dart/lsp/DartLspInlayHintsConfiguration")
            }
        }

        server.testProcessResponse(
            JsonParser.parseString(configurationRequest("""{ "section": "dart" }""")).asJsonObject
        )

        assertEquals("the server must always get exactly one answer", 1, server.sentResponses.size)
        val result = requireNotNull(lspResponseOf(server).getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals("one entry per requested item", 1, result.size())
        assertTrue("a section that could not be computed must be answered with null", result[0].isJsonNull)
    }

    fun testAnswerIsSentEvenIfTheLoggerRethrowsTheFailure() {
        // The logger of the IntelliJ client rethrows control flow exceptions, and an
        // AlreadyDisposedException during teardown is one of them, so logging the failure must not
        // be able to skip the answer.
        installRethrowingLogger()
        val server = object : TestRemoteAnalysisServer(createStubSocket()) {
            override fun lsp_workspaceConfiguration(
                sections: List<String?>,
                consumer: DartLspWorkspaceConfigurationConsumer
            ) {
                throw IllegalStateException("cannot read the settings")
            }
        }

        try {
            server.testProcessResponse(JsonParser.parseString(configurationRequest("""{ "section": "dart" }""")).asJsonObject)
            fail("the rethrown failure should reach the caller")
        } catch (expected: IllegalStateException) {
            // The reader loop of the server handles it; what matters is that the answer went out first.
        }

        assertEquals("the server must always get exactly one answer", 1, server.sentResponses.size)
        val result = requireNotNull(lspResponseOf(server).getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals("one entry per requested item", 1, result.size())
        assertTrue("a section that could not be computed must be answered with null", result[0].isJsonNull)
    }

    fun testAnswerIsSentOnlyOnceIfTheConfigurationIsSuppliedTwice() {
        val server = object : TestRemoteAnalysisServer(createStubSocket()) {
            override fun lsp_workspaceConfiguration(
                sections: List<String?>,
                consumer: DartLspWorkspaceConfigurationConsumer
            ) {
                consumer.computedConfiguration(sections.map { dartSection })
                consumer.computedConfiguration(sections.map { null })
            }
        }

        server.testProcessResponse(JsonParser.parseString(configurationRequest("""{ "section": "dart" }""")).asJsonObject)

        assertEquals("the server must always get exactly one answer", 1, server.sentResponses.size)
        val result = requireNotNull(lspResponseOf(server).getAsJsonArray("result")) { "lspResponse should carry a result array" }
        assertEquals(dartSection, result[0].asJsonObject)
    }

    fun testClientCapabilitiesAskTheServerToPullTheConfiguration() {
        // The server only sends workspace/configuration if the client advertises that it can answer
        // it, and that is the only way the settings ever reach the server.
        // Unconditional: a server that does not support workspace/configuration parses and ignores
        // the capability, and it must not depend on the SDK gate of the apply-edit capabilities.
        for (sdkVersion in listOf("3.14.0", "3.7.0")) {
            val capabilities = DartAnalysisServerService.buildLspCapabilities(sdkVersion)
            val workspace = requireNotNull(capabilities.getAsJsonObject("workspace")) {
                "the workspace capabilities should be present for $sdkVersion, was: $capabilities"
            }
            assertTrue("configuration should be advertised for $sdkVersion", workspace.get("configuration").asBoolean)
        }
    }

    fun testTheConfigurationNotificationIsGatedOnTheServerProtocolVersion() {
        // The protocol version of the server, not the version of the Dart SDK: an older server
        // cannot handle a notification from the client and logs it as an error.
        assertFalse(DartAnalysisServerService.isServerProtocolVersionSufficientForLspConfiguration(""))
        assertFalse(DartAnalysisServerService.isServerProtocolVersionSufficientForLspConfiguration("1.40.0"))
        // A bugfix release of the last protocol version before the floor is still below it.
        assertFalse(DartAnalysisServerService.isServerProtocolVersionSufficientForLspConfiguration("1.40.1"))
        assertTrue(DartAnalysisServerService.isServerProtocolVersionSufficientForLspConfiguration("1.41.0"))
        assertTrue(DartAnalysisServerService.isServerProtocolVersionSufficientForLspConfiguration("1.42.0"))
    }

    fun testDartAnalysisServerImplSuppliesTheInlayHintSettings() {
        val server = DartAnalysisServerImpl(project, createStubSocket())

        var configurations: List<JsonObject?>? = null
        server.lsp_workspaceConfiguration(listOf("dart", "flutter")) { configurations = it }

        val computed = requireNotNull(configurations) { "the consumer should have been called" }
        assertEquals("one entry per requested section", 2, computed.size)
        assertNull("an unknown section should be answered with null", computed[1])

        val inlayHints = requireNotNull(computed[0]?.getAsJsonObject(DartLspInlayHintsConfiguration.INLAY_HINTS_KEY)) {
            "the dart section should carry the inlay hint settings, was: ${computed[0]}"
        }
        assertEquals(DartLspInlayHintsConfiguration.SERVER_CATEGORY_KEYS, inlayHints.keySet())
    }

    fun testTheConfigurationIsAnsweredEvenByAServerThatCannotBeNotified() {
        // The gate of the notification must not reach the answer: a server below the floor still
        // pulls workspace/configuration while it starts up, and it blocks its initialization until
        // it gets an answer. An answer without the settings would make it compute the hints of
        // every category, so the settings the user made are honoured as far as the old server can.
        setServerVersion("1.40.1")
        val server = DartAnalysisServerImpl(project, createStubSocket())

        var configurations: List<JsonObject?>? = null
        server.lsp_workspaceConfiguration(listOf("dart")) { configurations = it }

        val computed = requireNotNull(configurations) { "the consumer should have been called" }
        assertNotNull(
            "the dart section should carry the inlay hint settings, was: ${computed[0]}",
            computed[0]?.getAsJsonObject(DartLspInlayHintsConfiguration.INLAY_HINTS_KEY),
        )
    }
}
