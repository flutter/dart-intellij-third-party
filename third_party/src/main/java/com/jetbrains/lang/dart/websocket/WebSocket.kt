/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.websocket

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket as JdkWebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * A thin wrapper around the JDK [java.net.http.WebSocket] that mirrors the API of the
 * Weberknecht library's WebSocket, so usages of Weberknecht can be replaced with this class.
 */
class WebSocket(private val uri: URI) {

    @Volatile
    private var jdkWebSocket: JdkWebSocket? = null

    @Volatile
    var eventHandler: WebSocketEventHandler? = null

    @Throws(WebSocketException::class)
    fun connect() {
        val listener = JdkListener()
        val future: CompletionStage<JdkWebSocket> = try {
            httpClient.newWebSocketBuilder().buildAsync(uri, listener)
        } catch (e: Exception) {
            throw WebSocketException("Failed to start WebSocket handshake to $uri", e)
        }
        try {
            future.toCompletableFuture().get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw WebSocketException("WebSocket handshake to $uri failed", e)
        }
    }

    @Synchronized
    @Throws(WebSocketException::class)
    fun send(text: String) {
        val webSocket = jdkWebSocket ?: throw WebSocketException("WebSocket is not connected")
        try {
            webSocket.sendText(text, true).toCompletableFuture().get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw WebSocketException("Failed to send WebSocket message", e)
        }
    }

    @Throws(WebSocketException::class)
    fun close() {
        val webSocket = jdkWebSocket ?: return
        try {
            webSocket.sendClose(JdkWebSocket.NORMAL_CLOSURE, "Normal Closure")
                .toCompletableFuture().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw WebSocketException("Failed to close WebSocket", e)
        }
    }

    private inner class JdkListener : JdkWebSocket.Listener {
        private val pendingText = StringBuilder()

        override fun onOpen(webSocket: JdkWebSocket) {
            jdkWebSocket = webSocket
            webSocket.request(Long.MAX_VALUE)
            eventHandler?.onOpen()
        }

        override fun onText(webSocket: JdkWebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            pendingText.append(data)
            if (last) {
                val complete = pendingText.toString()
                pendingText.setLength(0)
                eventHandler?.onMessage(WebSocketMessage(complete))
            }
            return null
        }

        // We could use the statusCode and the reason for logging purposes
        override fun onClose(webSocket: JdkWebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            pendingText.clear()
            eventHandler?.onClose()
            return null
        }

        override fun onPing(webSocket: JdkWebSocket, message: ByteBuffer): CompletionStage<*>? {
            eventHandler?.onPing()
            return null
        }

        override fun onPong(webSocket: JdkWebSocket, message: ByteBuffer): CompletionStage<*>? {
            eventHandler?.onPong()
            return null
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val SEND_TIMEOUT_SECONDS = 10L
        const val CLOSE_TIMEOUT_SECONDS = 10L

        private val httpClient: HttpClient = HttpClient.newHttpClient()
    }
}