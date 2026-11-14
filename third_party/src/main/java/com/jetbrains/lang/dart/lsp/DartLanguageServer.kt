/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartSdk
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import java.util.Collections
import java.util.WeakHashMap
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

    /**
     * Resolves an interactive command by returning form fields requiring user input or updated command arguments.
     */
    @JsonRequest("command/resolve")
    fun resolveCommand(params: InteractiveExecuteCommandParams): CompletableFuture<InteractiveExecuteCommandParams>
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

data class InteractiveExecuteCommandParams(
    val command: String,
    val arguments: List<Any?>? = null,
    val data: Any? = null,
    val formAnswers: List<FormAnswer>? = null,
    val formFields: List<FormField>? = null,
)

data class FormAnswer(
    val id: String,
    val value: Any,
)

data class FormField(
    val id: String,
    val description: String,
    val type: FormFieldType,
    val required: Boolean,
    @SerializedName("default")
    val defaultValue: Any? = null,
    val error: String? = null,
)

data class FormFieldType(
    val kind: String,
    val validators: List<RegexValidator>? = null,
    val existence: Int? = null,
    val filters: List<String>? = null,
    val type: Int? = null,
) {
    companion object {
        const val KIND_STRING = "string"
        const val KIND_FILE = "file"
        const val KIND_BOOL = "bool"
        const val KIND_NUMBER = "number"
        @JvmField
        val SUPPORTED_KINDS: List<String> = listOf(KIND_STRING, KIND_FILE, KIND_BOOL, KIND_NUMBER)

        const val FILE_EXISTENCE_NEW = 1
        const val FILE_EXISTENCE_EXISTING = 2

        const val FILE_TYPE_REGULAR = 1
        const val FILE_TYPE_DIRECTORY = 2
    }
}

data class RegexValidator(
    val kind: String = "regex",
    val pattern: String,
    val matchIsValid: Boolean,
    val message: String,
    val severity: Int,
) {
    companion object {
        const val SEVERITY_INFO = 1
        const val SEVERITY_WARNING = 2
        const val SEVERITY_ERROR = 3
    }
}

object DartLspService {
    private val connectedDtdUriByServer: MutableMap<LspServer, String> =
        Collections.synchronizedMap(WeakHashMap())

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
            val sdk = DartSdk.getDartSdk(project) ?: return@supplyAsync null
            if (!DartAnalysisServerService.isDartSdkVersionSufficientForWorkspaceApplyEdits(sdk.version)) {
                return@supplyAsync null
            }
            val server = LspServerManager.getInstance(project)
                .getServersForProvider(DartLspServerSupportProvider::class.java)
                .firstOrNull() ?: return@supplyAsync null
            if (server.state != LspServerState.Running) {
                return@supplyAsync null
            }
            synchronized(connectedDtdUriByServer) {
                if (connectedDtdUriByServer[server] == uri) {
                    return@supplyAsync null
                }
                connectedDtdUriByServer[server] = uri
            }
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
