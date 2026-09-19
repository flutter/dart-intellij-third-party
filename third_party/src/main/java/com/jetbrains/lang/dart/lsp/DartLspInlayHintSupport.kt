/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.customization.LspInlayHintSupport
import com.jetbrains.lang.dart.hints.DartParameterNamesInlayHintsProvider
import com.jetbrains.lang.dart.hints.DartTypesInlayHintsProvider
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintKind

/**
 * Connects the inlay hints computed by the Dart Analysis Server with the Dart checkboxes in
 * Settings | Editor | Inlay Hints.
 *
 * The server marks every hint it returns with an [InlayHintKind]: parameter name hints use
 * [InlayHintKind.Parameter], and all type hints use [InlayHintKind.Type]. That allows filtering
 * the hints on the client without sending any configuration to the server:
 * [shouldDisplayInlayHint] drops hints whose checkbox is off, and [shouldAskServerForInlayHints]
 * skips the `textDocument/inlayHint` request entirely while both checkboxes are off.
 */
class DartLspInlayHintSupport : LspInlayHintSupport() {

    override fun shouldAskServerForInlayHints(file: VirtualFile): Boolean {
        return isProviderEnabled(DartParameterNamesInlayHintsProvider.PROVIDER_ID) ||
                isProviderEnabled(DartTypesInlayHintsProvider.PROVIDER_ID)
    }

    override fun shouldDisplayInlayHint(file: VirtualFile, inlayHint: InlayHint): Boolean {
        return when (inlayHint.kind) {
            InlayHintKind.Parameter -> isProviderEnabled(DartParameterNamesInlayHintsProvider.PROVIDER_ID)
            InlayHintKind.Type -> isProviderEnabled(DartTypesInlayHintsProvider.PROVIDER_ID)
            // The Dart Analysis Server sets a kind on every hint. A hint without a kind cannot be
            // attributed to either checkbox, so show it as long as hints were requested at all.
            null -> true
        }
    }

    // Shared with DartLspInlayHintsConfiguration, which reads the same checkboxes to build the
    // configuration section for the server.
    private fun isProviderEnabled(providerId: String): Boolean =
        DartLspInlayHintsConfiguration.isProviderEnabled(providerId)
}
