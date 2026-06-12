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
    INITIALIZE("initialize"),
    SHUTDOWN("shutdown"),
    HOVER("textDocument/hover", isExperimental = true, presentableName = "hover");

    companion object {
        fun fromMethod(method: String): LspMethod? = entries.find { it.method == method }

        fun getExperimentalFeatures(): List<LspMethod> = entries.filter { it.isExperimental }
    }
}
