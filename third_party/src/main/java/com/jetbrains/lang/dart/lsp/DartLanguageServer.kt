/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartSdk
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
     * Connects the analysis server to the Dart Tooling Daemon (DTD) at the given URI.
     */
    @JsonRequest("dart/connectToDtd")
    fun connectToDtd(params: ConnectToDtdParams): CompletableFuture<Any>
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

data class ConnectToDtdParams(
    val uri: String
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
    fun connectToDtd(project: Project, uri: String): CompletableFuture<Any?> {
        return CompletableFuture.supplyAsync({
            if (project.isDisposed) return@supplyAsync null
            // Connection to DTD is used for server-initiated edits (workspace/applyEdit), which require Dart 3.8+.
            val sdk = DartSdk.getDartSdk(project)
            if (sdk != null && !DartAnalysisServerService.isDartSdkVersionSufficientForWorkspaceApplyEdits(sdk.version)) {
                return@supplyAsync null
            }
            val server = LspServerManager.getInstance(project)
                .getServersForProvider(DartLspServerSupportProvider::class.java)
                .firstOrNull() ?: return@supplyAsync null
            var result: Any? = null
            ProgressManager.getInstance().executeNonCancelableSection {
                result = server.sendRequestSync { (it as DartLanguageServer).connectToDtd(ConnectToDtdParams(uri)) }
            }
            result
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
}
