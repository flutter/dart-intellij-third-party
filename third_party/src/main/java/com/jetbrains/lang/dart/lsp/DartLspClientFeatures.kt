/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageServer

/**
 * Custom LSP client features for Dart.
 *
 * This class extends [LSPClientFeatures] to customize the LSP connection lifecycle.
 * It provides a [DartLauncherBuilder] to override the default message production,
 * allowing messages to be bridged directly from the Dart Analysis Server (DAS)
 * instead of relying solely on standard input streams.
 */
class DartLspClientFeatures : LSPClientFeatures() {
    override fun <S : LanguageServer> createLauncherBuilder(): Launcher.Builder<S> {
        return DartLauncherBuilder<S>(this)
    }
}
