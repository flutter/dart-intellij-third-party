/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.gson.JsonParser
import com.intellij.openapi.util.Disposer
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
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
