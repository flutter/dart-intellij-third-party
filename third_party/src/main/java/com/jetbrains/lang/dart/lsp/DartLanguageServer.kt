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
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
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

data class DiagnosticServerResult(
    val port: Int
)

object DartLspService {
    @JvmStatic
    fun getDiagnosticServerPort(project: Project): CompletableFuture<Int?> {
        return CompletableFuture.supplyAsync({
            if (project.isDisposed) return@supplyAsync null
            val server = LspServerManager.getInstance(project)
                .getServersForProvider(DartLspServerSupportProvider::class.java)
                .firstOrNull() ?: return@supplyAsync null
            var port: Int? = null
            ProgressManager.getInstance().executeNonCancelableSection {
                val result = server.sendRequestSync { (it as DartLanguageServer).diagnosticServer() }
                port = result?.port
            }
            port
        }, AppExecutorUtil.getAppExecutorService())
    }

    @JvmStatic
    fun willRenameFiles(project: Project, params: RenameFilesParams): CompletableFuture<WorkspaceEdit?> {
        return CompletableFuture.supplyAsync({
            if (project.isDisposed) return@supplyAsync null
            val server = LspServerManager.getInstance(project)
                .getServersForProvider(DartLspServerSupportProvider::class.java)
                .firstOrNull() ?: return@supplyAsync null
            var edit: WorkspaceEdit? = null
            ProgressManager.getInstance().executeNonCancelableSection {
                edit = server.sendRequestSync { it.workspaceService.willRenameFiles(params) }
            }
            edit
        }, AppExecutorUtil.getAppExecutorService())
    }

    @JvmStatic
    fun reanalyze(project: Project): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            if (project.isDisposed) return@supplyAsync false
            val server = LspServerManager.getInstance(project)
                .getServersForProvider(DartLspServerSupportProvider::class.java)
                .firstOrNull() ?: return@supplyAsync false
            var succeeded = false
            ProgressManager.getInstance().executeNonCancelableSection {
                succeeded = server.sendRequestSync {
                    (it as DartLanguageServer).reanalyze().thenApply { true }
                } == true
            }
            succeeded
        }, AppExecutorUtil.getAppExecutorService())
    }
}
