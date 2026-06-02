package com.jetbrains.lang.dart.websocket

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

class WebSocket(private val uri: URI) {

  // This should be a singleton
  private val httpClient: HttpClient = HttpClient.newHttpClient()

  // @Volatile is needed because we need memory visibility
  @Volatile
  private var jdkWebSocket: WebSocket? = null

  // No concurrent read and write can happen because connect is happening after assigning
  // this value. So its prudent
  @Volatile
  var eventHandler: WebSocketEventHandler? = null

  // Building JDK's Websocket is async (unlike weberknecht). We get a future, and we need to block it to get the Websocket.
  @Throws(WebSocketException::class)
  fun connect() {
    val listener = JdkListener()
    val future: CompletionStage<WebSocket> = try {
      httpClient.newWebSocketBuilder().buildAsync(uri, listener)
    }
    catch (e: Exception) {
      throw WebSocketException("Failed to start WebSocket handshake to $uri", e)
    }

    jdkWebSocket = try {
      // onOpen fires before get
      future.toCompletableFuture().get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }
    catch (e: Exception) {
      throw WebSocketException("WebSocket handshake to $uri failed", e)
    }
  }

  @Throws(WebSocketException::class)
  fun send(text: String) {
    val ws = jdkWebSocket ?: throw WebSocketException("WebSocket is not connected")
    try {
      ws.sendText(text, true).toCompletableFuture().get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      // This is mimicking the behavior of Weberknecht, we don't really need to block it
    }
    catch (e: Exception) {
      throw WebSocketException("Failed to send WebSocket message", e)
    }
  }

  private inner class JdkListener : WebSocket.Listener {
    private val pendingText = StringBuilder()

    override fun onOpen(webSocket: WebSocket) {
      webSocket.request(Long.MAX_VALUE)
      eventHandler?.onOpen()
    }

    // In RFC 6455, it is stated that The fragments of one message MUST NOT be interleaved between the
    // fragments of another message unless an extension has been negotiated that can interpret the interleaving.
    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
      pendingText.append(data)
      if (last) {
        val complete = pendingText.toString()
        pendingText.setLength(0)
        eventHandler?.onMessage(WebSocketMessage(complete))
      }
      return null
    }

    // We could optimally use the statusCode and the reason for logging purposes
    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
      pendingText.clear()
      eventHandler?.onClose()
      return null
    }

    override fun onPing(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*>? {
      eventHandler?.onPing()
      return null
    }

    override fun onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*>? {
      eventHandler?.onPong()
      return null
    }
  }

  private companion object {
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val SEND_TIMEOUT_SECONDS = 10L
  }
}