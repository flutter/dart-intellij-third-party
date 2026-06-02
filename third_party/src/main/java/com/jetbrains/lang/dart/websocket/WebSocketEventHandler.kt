package com.jetbrains.lang.dart.websocket

interface WebSocketEventHandler {
  fun onOpen()
  fun onMessage(message: WebSocketMessage)
  fun onClose()
  fun onPing()
  fun onPong()
}
