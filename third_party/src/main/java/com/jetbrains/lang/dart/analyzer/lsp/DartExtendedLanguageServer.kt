// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

internal interface DartExtendedLanguageServer : LanguageServer {
    @JsonRequest("dart/diagnosticServer")
    fun diagnosticServer(): CompletableFuture<DartDiagnosticServerResult>
}

internal data class DartDiagnosticServerResult(val port: Int = 0)
