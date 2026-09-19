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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction

/**
 * Tests the answer to the `workspace/configuration` request that the Dart Analysis Server sends
 * wrapped into a legacy `lsp.handle` request. The server blocks its initialization until it gets
 * that answer, so the shape of the response is what matters here.
 */
class DartLspWorkspaceConfigurationTest : DartCodeInsightFixtureTestCase() {

    private fun createStubSocket(): AnalysisServerSocket = object : AnalysisServerSocket {
        override fun getErrorStream(): ByteLineReaderStream? = null
        override fun getRequestSink(): RequestSink? = null
        override fun getResponseStream(): ResponseStream? = null
        override fun isOpen(): Boolean = true
        override fun start() {}
        override fun stop() {}
    }

    /** The configuration that the test server hands out for the `dart` section. */
    private val dartSection = JsonObject().apply { add("inlayHints", JsonObject()) }

    private inner class TestRemoteAnalysisServer(socket: AnalysisServerSocket) : RemoteAnalysisServerImpl(socket) {
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

    fun testDartAnalysisServerImplSuppliesTheInlayHintSettings() {
        val server = DartAnalysisServerImpl(project, createStubSocket())

        var configurations: List<JsonObject?>? = null
        server.lsp_workspaceConfiguration(listOf("dart", "flutter")) { configurations = it }

        val computed = requireNotNull(configurations) { "the consumer should have been called" }
        assertEquals("one entry per requested section", 2, computed.size)
        assertNull("an unknown section should be answered with null", computed[1])

        val inlayHints = requireNotNull(computed[0]?.getAsJsonObject("inlayHints")) {
            "the dart section should carry the inlay hint settings, was: ${computed[0]}"
        }
        assertEquals(
            setOf(
                "parameterNames",
                "variableTypes",
                "returnTypes",
                "parameterTypes",
                "typeArguments",
                "dotShorthandTypes",
            ),
            inlayHints.keySet(),
        )
    }
}
