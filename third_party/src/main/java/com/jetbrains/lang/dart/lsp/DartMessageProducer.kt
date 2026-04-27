/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.gson.JsonParseException
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.logging.PluginLogger
import org.eclipse.lsp4j.jsonrpc.JsonRpcException
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.MessageIssueException
import org.eclipse.lsp4j.jsonrpc.MessageProducer
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom MessageProducer that bridges messages from the Dart Analysis Server to the lsp4ij client.
 *
 * It uses a [LinkedBlockingQueue] to hold incoming JSON messages and supports a graceful
 * shutdown sequence using a Poison Pill pattern.
 */
class DartMessageProducer(val jsonHandler: MessageJsonHandler) : MessageProducer {
    companion object {
        private val logger = PluginLogger.createLogger(DartMessageProducer::class.java)
        private const val POISON_PILL = "SHUTDOWN_POISON_PILL"
    }

    // This is queue of responses from the DAS.
    val responseQueue = LinkedBlockingQueue<String>()

    @Volatile
    var keepRunning = true

    fun stop() {
        keepRunning = false
        responseQueue.offer(POISON_PILL)
    }

    // Enqueue an LSP message to be forwarded to lsp4ij (from the DAS or a virtual representation of the DAS).
    fun enqueueResponse(json: String) {
        responseQueue.offer(json)
    }

    override fun listen(messageConsumer: MessageConsumer?) {
        while (messageConsumer != null && keepRunning) {
            com.intellij.openapi.progress.ProgressManager.checkCanceled()
            val json = responseQueue.take()
            if (json == POISON_PILL) break

            try {
                val message = jsonHandler.parseMessage(json)
                if (message != null) {
                    messageConsumer.consume(message)
                } else {
                    logger.warn("Parsed message is null for JSON: $json")
                }
            } catch(ex: JsonParseException) {
                logger.warn("Error parsing JSON message to lsp4ij: $json", ex)
            } catch(ex: MessageIssueException) {
                logger.warn("Error sending message to lsp4ij: $json", ex)
            } catch(ex: JsonRpcException) {
                logger.warn("Error sending message to lsp4ij: $json", ex)
            }
        }
    }
}
