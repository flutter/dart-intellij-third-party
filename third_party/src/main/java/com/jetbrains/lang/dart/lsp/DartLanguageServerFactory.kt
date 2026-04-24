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
        logger.info("ConnectionProvider created logger")
        return DartVirtualStreamConnectionProvider(project)
    }

    @NotNull
    override fun createClientFeatures(): LSPClientFeatures {
        return DartLspClientFeatures()
    }
}