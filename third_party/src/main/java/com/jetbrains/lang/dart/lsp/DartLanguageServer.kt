/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

/**
 * Custom Language Server interface for Dart to support custom LSP requests.
 */
interface DartLanguageServer : Lsp4jServer {
    /**
     * Returns the port of the diagnostic server.
     */
    @JsonRequest("dart/diagnosticServer")
    fun diagnosticServer(): CompletableFuture<DiagnosticServerResult>

    /**
     * Forces re-reading of all potentially changed files, clearing of caches, and re-analysis of everything.
     */
    @JsonRequest("dart/reanalyze")
    fun reanalyze(): CompletableFuture<Any>
}

/**
 * Custom Language Client interface for Dart to support custom LSP notifications from the server.
 */
interface DartLanguageClient : LanguageClient {
    @JsonNotification("dart/textDocument/publishClosingLabels")
    fun publishClosingLabels(params: DartPublishClosingLabelsParams)
}

data class DiagnosticServerResult(
    val port: Int
)

object DartLspService {
    @JvmStatic
    fun getDiagnosticServerPort(project: Project): CompletableFuture<Int?> {
        return sendRequestAsync(project) {
            (it as DartLanguageServer).diagnosticServer()
        }.thenApply { it?.port }
    }

    @JvmStatic
    fun willRenameFiles(project: Project, params: RenameFilesParams): CompletableFuture<WorkspaceEdit?> {
        return sendRequestAsync(project) {
            it.workspaceService.willRenameFiles(params)
        }
    }

    @JvmStatic
    fun reanalyze(project: Project): CompletableFuture<Boolean> {
        return sendRequestAsync(project) {
            (it as DartLanguageServer).reanalyze().thenApply { true }
        }.thenApply { it == true }
    }

    private fun <T> sendRequestAsync(
        project: Project,
        request: (Lsp4jServer) -> CompletableFuture<T>,
    ) : CompletableFuture<T?> {
        return CompletableFuture.supplyAsync ({
            if (project.isDisposed) return@supplyAsync null
            val server = LspServerManager.getInstance(project)
                .getServersForProvider(DartLspServerSupportProvider::class.java)
                .firstOrNull() ?: return@supplyAsync null
            var result: T? = null
            ProgressManager.getInstance().executeNonCancelableSection {
                result = server.sendRequestSync(lsp4jSender = request)
            }
            result
        }, AppExecutorUtil.getAppExecutorService())
    }
}
