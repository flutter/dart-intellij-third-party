/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

enum class LspMethod(
    val method: String,
    val isExperimental: Boolean = false,
    val presentableName: String? = null
) {
    DEFINITION("textDocument/definition", isExperimental = true, presentableName = "navigation"),
    DIAGNOSTIC_SERVER("dart/diagnosticServer", isExperimental = false),
    DOCUMENT_HIGHLIGHT("textDocument/documentHighlight", isExperimental = true, presentableName = "read/write highlighting"),
    HOVER("textDocument/hover", isExperimental = false),
    INITIALIZE("initialize"),
    // Not gated by the experimental LSP flag because there is no fallback in legacy mode.
    INLAY_HINT("textDocument/inlayHint", isExperimental = false),
    SEMANTIC_TOKENS_FULL("textDocument/semanticTokens/full", isExperimental = true, presentableName = "syntax highlighting"),
    SHUTDOWN("shutdown"),
    TYPE_DEFINITION("textDocument/typeDefinition", isExperimental = false),
    REFERENCES("textDocument/references", isExperimental = true, presentableName = "references");

    companion object {
        fun fromMethod(method: String): LspMethod? = entries.find { it.method == method }

        fun getExperimentalFeatures(): List<LspMethod> = entries.filter { it.isExperimental }
    }
}
