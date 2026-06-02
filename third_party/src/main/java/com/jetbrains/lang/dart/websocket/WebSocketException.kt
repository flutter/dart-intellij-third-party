package com.jetbrains.lang.dart.websocket

class WebSocketException : Exception {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)
}
