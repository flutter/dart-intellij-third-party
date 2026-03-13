// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

internal class DartLspLanguageClient(
    private val onPublishDiagnostics: (PublishDiagnosticsParams) -> Unit,
    private val onApplyEdit: (ApplyWorkspaceEditParams) -> ApplyWorkspaceEditResponse = { ApplyWorkspaceEditResponse(false) },
) : LanguageClient {
    override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
        return CompletableFuture.completedFuture(onApplyEdit(params))
    }

    override fun telemetryEvent(`object`: Any?) {}

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        onPublishDiagnostics(diagnostics)
    }

    override fun showMessage(messageParams: MessageParams) {}

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        return CompletableFuture.completedFuture(null)
    }

    override fun logMessage(message: MessageParams) {}
}
