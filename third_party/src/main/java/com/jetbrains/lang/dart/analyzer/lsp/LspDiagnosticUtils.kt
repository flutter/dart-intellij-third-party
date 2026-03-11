// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import org.eclipse.lsp4j.Diagnostic

internal fun Diagnostic.getCodeAsString(): String? {
    val diagnosticCode = code ?: return null
    return if (diagnosticCode.isLeft) diagnosticCode.left else diagnosticCode.right?.toString()
}
