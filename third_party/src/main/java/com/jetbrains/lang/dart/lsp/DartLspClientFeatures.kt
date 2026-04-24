package com.jetbrains.lang.dart.lsp

import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageServer

class DartLspClientFeatures : LSPClientFeatures() {
    override fun <S : LanguageServer> createLauncherBuilder(): Launcher.Builder<S> {
        return DartLauncherBuilder<S>(this)
    }
}