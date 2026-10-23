/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.platform.dartlsp.api.LspServerNotificationsHandler
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import java.util.concurrent.CompletableFuture

/**
 * Handles the requests and notifications that the Dart server sends to the JetBrains LSP client like [delegate] does,
 * except for `workspace/applyEdit` edits that [capture] takes (see [DartLspApplyEditCapture]).
 */
internal class DartLspServerNotificationsHandler(
    private val delegate: LspServerNotificationsHandler,
    private val capture: DartLspApplyEditCapture,
) : LspServerNotificationsHandler by delegate {

    override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
        val response = capture.offer(params) ?: return delegate.applyEdit(params)
        return CompletableFuture.completedFuture(response)
    }
}
