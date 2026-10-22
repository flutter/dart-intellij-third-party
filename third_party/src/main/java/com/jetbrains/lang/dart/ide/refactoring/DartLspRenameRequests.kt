/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.platform.dartlsp.util.getLsp4jPosition
import com.intellij.platform.dartlsp.util.getOffsetInDocument
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

/** The outcome of a rename request to the Dart analysis server. */
sealed interface DartLspRenameResult<out T> {
    data class Success<T>(val value: T) : DartLspRenameResult<T>

    /** The server refused the request (e.g. an SDK element or an invalid name), or its answer can't be used. */
    data class Failure(val message: @NlsContexts.DialogMessage String) : DartLspRenameResult<Nothing>

    /** No running Dart LSP server. */
    data object NoServer : DartLspRenameResult<Nothing>
}

/** What `textDocument/prepareRename` found at an offset: the name to rename and its current text. */
data class DartLspRenameTarget(val range: TextRange, val placeholder: String)

/** One text edit of a rename, as offsets in the current content of [file]. */
data class DartLspFileTextEdit(val file: VirtualFile, val range: TextRange, val newText: String)

/** Sends the LSP rename requests to the Dart analysis server. A service, so tests can replace it. */
interface DartLspRenameRequests {
    @RequiresBackgroundThread
    fun prepareRename(file: VirtualFile, offset: Int): DartLspRenameResult<DartLspRenameTarget>

    /** An empty list means the rename changes nothing, e.g. when the user cancelled the server's *Rename Anyway* prompt. */
    @RequiresBackgroundThread
    fun rename(file: VirtualFile, offset: Int, newName: String): DartLspRenameResult<List<DartLspFileTextEdit>>

    companion object {
        @JvmStatic
        fun getInstance(project: Project): DartLspRenameRequests = project.service()
    }
}

class DartLspRenameRequestsImpl(private val project: Project) : DartLspRenameRequests {
    /**
     * Sends unsaved document changes to the analysis server before a request. The bridge only does that if it gets a read
     * action at once, but the rename edits are positions in the server's view of the files. Replaced in tests.
     */
    internal var syncFileContents: () -> Unit = { DartAnalysisServerService.getInstance(project).updateFilesContent() }

    companion object {
        private const val PREPARE_RENAME_TIMEOUT_MS: Int = 10_000

        /**
         * While the rename request runs, the server may ask the user to confirm the rename (*Rename Anyway* / *Cancel*)
         * and waits for the answer, so the request must not time out like other requests. The progress stays cancellable.
         */
        private val RENAME_REQUEST_TIMEOUT_MS: Int = TimeUnit.MINUTES.toMillis(10).toInt()
    }

    override fun prepareRename(file: VirtualFile, offset: Int): DartLspRenameResult<DartLspRenameTarget> {
        syncFileContents()
        val server = findServer() ?: return DartLspRenameResult.NoServer
        val document = findDocument(file) ?: return DartLspRenameResult.Failure(cannotRenameMessage())
        val params = runReadAction {
            PrepareRenameParams(TextDocumentIdentifier(server.descriptor.getFileUri(file)), getLsp4jPosition(document, offset))
        }

        val response = send(server, PREPARE_RENAME_TIMEOUT_MS) { it.textDocumentService.prepareRename(params) }
        val result = (response as? DartLspRenameResult.Success)?.value ?: return response.mapFailure()
        return runReadAction {
            when {
                result.isFirst -> rangeTarget(document, result.first)
                result.isSecond -> rangeTarget(document, result.second.range, result.second.placeholder)
                else -> TextRange(offset, offset).let { DartLspRenameResult.Success(DartLspRenameTarget(it, "")) }
            }
        }
    }

    override fun rename(file: VirtualFile, offset: Int, newName: String): DartLspRenameResult<List<DartLspFileTextEdit>> {
        syncFileContents()
        val server = findServer() ?: return DartLspRenameResult.NoServer
        val document = findDocument(file) ?: return DartLspRenameResult.Failure(cannotRenameMessage())
        val params = runReadAction {
            RenameParams(TextDocumentIdentifier(server.descriptor.getFileUri(file)), getLsp4jPosition(document, offset), newName)
        }

        val response = send(server, RENAME_REQUEST_TIMEOUT_MS) { it.textDocumentService.rename(params) }
        if (response !is DartLspRenameResult.Success) return response.mapFailure()
        val edit = response.value ?: return DartLspRenameResult.Success(emptyList())
        return runReadAction { toFileTextEdits(edit) { uri -> getDartFileInfo(project, uri).findFile() } }
    }

    /** The running Dart LSP server, if any. Replaced in tests. */
    internal var findServer: () -> LspServer? = {
        LspServerManager.getInstance(project).getServersForProvider(DartLspServerSupportProvider::class.java).firstOrNull()
    }

    private fun findDocument(file: VirtualFile): Document? = runReadAction { FileDocumentManager.getInstance().getDocument(file) }

    /** Sends a request and turns an error answer into [DartLspRenameResult.Failure] instead of `null`. */
    private fun <T> send(
        server: LspServer,
        timeoutMs: Int,
        request: (Lsp4jServer) -> CompletableFuture<T>,
    ): DartLspRenameResult<T?> {
        val response = server.sendRequestSync(timeoutMs) { lsp4jServer ->
            // The client runs this on its own executor: if it threw, the request would get no answer before the timeout.
            try {
                request(lsp4jServer).handle { value, error ->
                    if (error == null) DartLspRenameResult.Success(value) else DartLspRenameResult.Failure(messageOf(error))
                }
            }
            catch (e: Exception) {
                CompletableFuture.completedFuture(DartLspRenameResult.Failure(messageOf(e)))
            }
        }
        return when {
            response != null -> response
            server.state != LspServerState.Running -> DartLspRenameResult.NoServer
            else -> DartLspRenameResult.Failure(DartBundle.message("rename.lsp.no.response"))
        }
    }

    private fun rangeTarget(
        document: Document,
        range: org.eclipse.lsp4j.Range,
        placeholder: String? = null,
    ): DartLspRenameResult<DartLspRenameTarget> {
        val textRange = toTextRange(document, range) ?: return DartLspRenameResult.Failure(cannotRenameMessage())
        return DartLspRenameResult.Success(DartLspRenameTarget(textRange, placeholder ?: document.getText(textRange)))
    }
}

/**
 * Converts the [edit] of a rename into offsets of the current file contents. Only text edits are supported; an edit
 * that also creates, renames or deletes files is a [DartLspRenameResult.Failure].
 */
internal fun toFileTextEdits(
    edit: WorkspaceEdit,
    findFile: (String) -> VirtualFile?,
): DartLspRenameResult<List<DartLspFileTextEdit>> {
    val editsByUri = mutableListOf<Pair<String, List<*>>>()
    val documentChanges = edit.documentChanges
    if (documentChanges != null) {
        for (change in documentChanges) {
            val textDocumentEdit = change.left
                ?: return DartLspRenameResult.Failure(DartBundle.message("rename.lsp.unsupported.change"))
            editsByUri += textDocumentEdit.textDocument.uri to textDocumentEdit.edits
        }
    }
    else {
        edit.changes?.forEach { (uri, edits) -> editsByUri += uri to edits }
    }

    val result = mutableListOf<DartLspFileTextEdit>()
    for ((uri, edits) in editsByUri) {
        val file = findFile(uri) ?: return DartLspRenameResult.Failure(DartBundle.message("rename.lsp.unknown.file", uri))
        val document = FileDocumentManager.getInstance().getDocument(file)
            ?: return DartLspRenameResult.Failure(DartBundle.message("rename.lsp.unknown.file", uri))
        for (item in edits) {
            // lsp4j 1.0 (IntelliJ 2026.3) wraps text edits in Either<TextEdit, SnippetTextEdit>.
            val textEdit = item as? TextEdit ?: (item as? Either<*, *>)?.left as? TextEdit
                ?: return DartLspRenameResult.Failure(DartBundle.message("rename.lsp.unsupported.change"))
            val range = toTextRange(document, textEdit.range)
                ?: return DartLspRenameResult.Failure(DartBundle.message("rename.lsp.outdated", file.presentableUrl))
            result += DartLspFileTextEdit(file, range, textEdit.newText)
        }
    }
    return DartLspRenameResult.Success(result)
}

/** Applies the [edits] of a rename to the documents of their files. Call it inside a write command. */
internal fun applyFileTextEdits(edits: List<DartLspFileTextEdit>) {
    val fileDocumentManager = FileDocumentManager.getInstance()
    for ((file, fileEdits) in edits.groupBy { it.file }) {
        val document = fileDocumentManager.getDocument(file) ?: continue
        // From the end, so that earlier offsets stay valid.
        for (edit in fileEdits.sortedByDescending { it.range.startOffset }) {
            document.replaceString(edit.range.startOffset, edit.range.endOffset, edit.newText)
        }
    }
}

private fun toTextRange(document: Document, range: org.eclipse.lsp4j.Range): TextRange? {
    val start = getOffsetInDocument(document, range.start) ?: return null
    val end = getOffsetInDocument(document, range.end) ?: return null
    return if (start <= end) TextRange(start, end) else null
}

private fun messageOf(error: Throwable): @NlsContexts.DialogMessage String {
    val cause = if (error is CompletionException) error.cause ?: error else error
    return (cause as? ResponseErrorException)?.responseError?.message ?: cause.message ?: cannotRenameMessage()
}

private fun cannotRenameMessage(): @NlsContexts.DialogMessage String = DartBundle.message("rename.lsp.cannot.rename")

private fun <T> DartLspRenameResult<*>.mapFailure(): DartLspRenameResult<T> = when (this) {
    is DartLspRenameResult.Failure -> this
    DartLspRenameResult.NoServer -> DartLspRenameResult.NoServer
    // A successful response without a value: prepareRename answers null for a position that can't be renamed.
    is DartLspRenameResult.Success -> DartLspRenameResult.Failure(cannotRenameMessage())
}
