/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.DefaultLauncherBuilder
import org.eclipse.lsp4j.jsonrpc.MessageIssueHandler
import org.eclipse.lsp4j.jsonrpc.MessageProducer
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.services.LanguageServer
import java.io.InputStream

/**
 * Custom Launcher Builder for the Dart LSP client.
 *
 * Extends [DefaultLauncherBuilder] to provide a custom [MessageProducer].
 * Instead of reading LSP messages from a standard input stream, it instantiates
 * [DartMessageProducer] and stores it in the project-level [DartLspProjectService], 
 * allowing other components to push messages directly to the client.
 */
class DartLauncherBuilder<S : LanguageServer>(clientFeatures: LSPClientFeatures) : DefaultLauncherBuilder<S>(
    clientFeatures
) {
    override fun createMessageProducer(
        input: InputStream,
        jsonHandler: MessageJsonHandler,
        issueHandler: MessageIssueHandler
    ): MessageProducer {
        val producer = DartMessageProducer(jsonHandler)
        val service = clientFeatures.project.getService(DartLspProjectService::class.java)
        service.producer = producer
        return producer
    }
}
