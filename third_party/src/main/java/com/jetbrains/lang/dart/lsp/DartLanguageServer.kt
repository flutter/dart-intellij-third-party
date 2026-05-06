/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

interface DartLanguageServer : LanguageServer {
    @JsonRequest("dart/diagnosticServer")
    fun diagnosticServer(): CompletableFuture<DiagnosticServerResult>
}

data class DiagnosticServerResult(
    val port: Int
)
