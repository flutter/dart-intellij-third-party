/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.lang.dart.analyzer.DartLocalFileInfo
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Lets plugin code take the edit of a server-initiated `workspace/applyEdit` instead of having the JetBrains LSP client
 * apply it.
 *
 * Some Dart server commands, such as `dart.edit.organizeImports`, don't return their edit but send it back with
 * `workspace/applyEdit` while they run. The client would apply it later in a write action of its own. Callers that
 * have to apply the edit themselves, such as [com.jetbrains.lang.dart.ide.imports.DartLspImportOptimizer] inside the
 * write command of Optimize Imports, [start] a capture for the file before they run the command and [Capture.finish]
 * it afterwards. [DartLspServerNotificationsHandler] offers every `workspace/applyEdit` to [offer] first.
 */
@Service(Service.Level.PROJECT)
class DartLspApplyEditCapture(private val project: Project) {
    companion object {
        /**
         * How long the capture of a command that was still running when its caller stopped waiting (cancellation,
         * timeout) discards a late edit for its file, so that the client doesn't apply it to a document that may have
         * changed in the meantime.
         */
        const val UNFINISHED_COMMAND_LIFETIME_MS: Long = 30_000
        private val UNFINISHED_COMMAND_LIFETIME_NANOS = TimeUnit.MILLISECONDS.toNanos(UNFINISHED_COMMAND_LIFETIME_MS)

        @JvmStatic
        fun getInstance(project: Project): DartLspApplyEditCapture = project.service()
    }

    /** Oldest first, so that a late edit of an unfinished command goes to its own capture. */
    private val captures = CopyOnWriteArrayList<Capture>()

    /** Time source in nanoseconds, replaced in tests. */
    internal var nanoTime: () -> Long = System::nanoTime

    /** Starts capturing the next `workspace/applyEdit` that only edits the file at [filePath]. */
    fun start(filePath: String): Capture = Capture(filePath).also { captures += it }

    /**
     * Returns the answer to [params] if they belong to a capture, or `null` if the client should handle them as usual.
     *
     * Only an edit with text edits for exactly one file and no resource operations is taken: `applied: true` for a
     * running capture (the caller applies it), `applied: false` for the late edit of an unfinished command (dropped).
     */
    fun offer(params: ApplyWorkspaceEditParams): ApplyWorkspaceEditResponse? {
        removeExpiredCaptures()
        if (captures.isEmpty()) return null
        val (fileUri, edits) = singleFileTextEdits(params.edit) ?: return null
        val filePath = (getDartFileInfo(project, fileUri) as? DartLocalFileInfo)?.filePath ?: return null
        for (capture in captures) {
            if (!FileUtil.pathsEqual(capture.filePath, filePath)) continue
            if (capture.discardsLateEdit) {
                captures.remove(capture)
                return ApplyWorkspaceEditResponse(false).apply {
                    failureReason = "The client stopped waiting for the command that sent this edit"
                }
            }
            if (capture.take(edits)) return ApplyWorkspaceEditResponse(true)
        }
        return null
    }

    private fun removeExpiredCaptures() {
        val now = nanoTime()
        captures.removeIf { capture ->
            capture.unfinishedSince?.let { now - it >= UNFINISHED_COMMAND_LIFETIME_NANOS } == true
        }
    }

    inner class Capture internal constructor(val filePath: String) {
        private val captured = AtomicReference<List<TextEdit>?>()

        /** Set by [finish] if the command was still running and nothing was captured; see [discardsLateEdit]. */
        @Volatile
        internal var unfinishedSince: Long? = null
            private set

        /** Whether the capture only waits to drop the late edit of its unfinished command. */
        internal val discardsLateEdit: Boolean
            get() = unfinishedSince != null

        /** The text edits of the captured `workspace/applyEdit`, or `null` if the server didn't send one. */
        val edits: List<TextEdit>?
            get() = captured.get()

        internal fun take(edits: List<TextEdit>): Boolean = !discardsLateEdit && captured.compareAndSet(null, edits)

        /**
         * Ends the capture. Pass `commandFinished = false` if waiting for the command stopped before the server
         * answered: without an edit so far, the capture then discards a late edit for the file for a while.
         */
        fun finish(commandFinished: Boolean) {
            if (commandFinished || edits != null) {
                captures.remove(this)
            }
            else {
                unfinishedSince = nanoTime()
            }
        }
    }
}

/** Returns the file URI and the text edits of [edit] if it only changes the text of a single file. */
private fun singleFileTextEdits(edit: WorkspaceEdit?): Pair<String, List<TextEdit>>? {
    val documentChanges = edit?.documentChanges
    if (documentChanges != null) {
        if (documentChanges.any { it.isRight }) return null
        val documentEdits = documentChanges.map { it.left }
        val fileUri = documentEdits.map { it.textDocument.uri }.distinct().singleOrNull() ?: return null
        return fileUri to documentEdits.flatMap { it.edits }
    }
    val (fileUri, edits) = edit?.changes?.entries?.singleOrNull() ?: return null
    return fileUri to edits
}
