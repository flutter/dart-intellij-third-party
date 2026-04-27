/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.logging.PluginLogger
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.jetbrains.annotations.NotNull

class DartLanguageServerFactory : LanguageServerFactory {
    companion object {
        private val logger = PluginLogger.createLogger(DartLanguageServerFactory::class.java)
    }

    @NotNull
    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        if (com.intellij.openapi.application.ApplicationManager.getApplication().isUnitTestMode) {
            return object : StreamConnectionProvider {
                override fun start() {
                    throw java.io.IOException("LSP server is disabled in unit tests")
                }
                override fun getInputStream(): java.io.InputStream? = null
                override fun getOutputStream(): java.io.OutputStream? = null
                override fun stop() {}
            }
        }
        logger.info("ConnectionProvider created logger")
        return DartVirtualStreamConnectionProvider(project)
    }

    @NotNull
    override fun createClientFeatures(): LSPClientFeatures {
        return DartLspClientFeatures()
    }
}
