/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameFile
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.TimeUnit

class DartLspApplyEditCaptureTest : DartCodeInsightFixtureTestCase() {
    private val path = "/project/lib/a.dart"
    private val uri = "file:///project/lib/a.dart"
    private val otherUri = "file:///project/lib/b.dart"
    private val textEdit = TextEdit(Range(Position(0, 0), Position(2, 0)), "import 'dart:async';\n")

    fun testEditWithoutCaptureIsNotTaken() {
        val capture = DartLspApplyEditCapture(project)

        assertNull(capture.offer(documentChangesEdit(uri)))
    }

    fun testEditForCapturedFileIsTaken() {
        val capture = DartLspApplyEditCapture(project)
        val pending = capture.start(path)

        val response = capture.offer(documentChangesEdit(uri))

        assertEquals(true, response?.isApplied)
        assertEquals(listOf(textEdit), pending.edits)
    }

    fun testEditAsChangesMapIsTaken() {
        val capture = DartLspApplyEditCapture(project)
        val pending = capture.start(path)

        val response = capture.offer(ApplyWorkspaceEditParams(WorkspaceEdit(mapOf(uri to listOf(textEdit)))))

        assertEquals(true, response?.isApplied)
        assertEquals(listOf(textEdit), pending.edits)
    }

    fun testFileUriWithEncodedCharactersMatchesPath() {
        val capture = DartLspApplyEditCapture(project)
        val pending = capture.start("/project/lib/my file.dart")

        val response = capture.offer(documentChangesEdit("file:///project/lib/my%20file.dart"))

        assertEquals(true, response?.isApplied)
        assertEquals(listOf(textEdit), pending.edits)
    }

    fun testEditForOtherFileIsNotTaken() {
        val capture = DartLspApplyEditCapture(project)
        val pending = capture.start(path)

        assertNull(capture.offer(documentChangesEdit(otherUri)))
        assertNull(pending.edits)
    }

    fun testEditForSeveralFilesIsNotTaken() {
        val capture = DartLspApplyEditCapture(project)
        val pending = capture.start(path)
        val edit = WorkspaceEdit(
            listOf(
                Either.forLeft<TextDocumentEdit, ResourceOperation>(textDocumentEdit(uri)),
                Either.forLeft(textDocumentEdit(otherUri)),
            )
        )

        assertNull(capture.offer(ApplyWorkspaceEditParams(edit)))
        assertNull(pending.edits)
    }

    fun testEditWithResourceOperationIsNotTaken() {
        val capture = DartLspApplyEditCapture(project)
        val pending = capture.start(path)
        val edit = WorkspaceEdit(
            listOf(
                Either.forLeft<TextDocumentEdit, ResourceOperation>(textDocumentEdit(uri)),
                Either.forRight(RenameFile(uri, otherUri)),
            )
        )

        assertNull(capture.offer(ApplyWorkspaceEditParams(edit)))
        assertNull(pending.edits)
    }

    fun testOnlyTheFirstEditIsTaken() {
        val capture = DartLspApplyEditCapture(project)
        capture.start(path)

        assertNotNull(capture.offer(documentChangesEdit(uri)))
        assertNull(capture.offer(documentChangesEdit(uri)))
    }

    fun testFinishedCaptureTakesNothing() {
        val capture = DartLspApplyEditCapture(project)
        capture.start(path).finish(commandFinished = true)

        assertNull(capture.offer(documentChangesEdit(uri)))
    }

    fun testCaptureOfUnfinishedCommandDiscardsTheLateEdit() {
        val capture = DartLspApplyEditCapture(project)
        val pending = capture.start(path)
        pending.finish(commandFinished = false)

        val response = capture.offer(documentChangesEdit(uri))

        assertEquals(false, response?.isApplied)
        assertNull(pending.edits)
        // Only one late edit is discarded.
        assertNull(capture.offer(documentChangesEdit(uri)))
    }

    fun testCaptureOfUnfinishedCommandExpires() {
        var now = 0L
        val capture = DartLspApplyEditCapture(project)
        capture.nanoTime = { now }
        val lifetime = TimeUnit.MILLISECONDS.toNanos(DartLspApplyEditCapture.UNFINISHED_COMMAND_LIFETIME_MS)
        capture.start(path).finish(commandFinished = false)
        capture.start("/project/lib/b.dart").finish(commandFinished = false)

        now = lifetime - 1
        assertEquals(false, capture.offer(documentChangesEdit(otherUri))?.isApplied)

        now = lifetime
        assertNull(capture.offer(documentChangesEdit(uri)))
    }

    fun testCaptureThatAlreadyTookTheEditIsRemovedEvenIfUnfinished() {
        val capture = DartLspApplyEditCapture(project)
        val pending = capture.start(path)
        capture.offer(documentChangesEdit(uri))
        pending.finish(commandFinished = false)

        assertNull(capture.offer(documentChangesEdit(uri)))
    }

    fun testOlderCaptureGetsTheFirstEdit() {
        val capture = DartLspApplyEditCapture(project)
        capture.start(path).finish(commandFinished = false)
        val pending = capture.start(path)

        assertEquals(false, capture.offer(documentChangesEdit(uri))?.isApplied)
        assertNull(pending.edits)
        assertEquals(true, capture.offer(documentChangesEdit(uri))?.isApplied)
        assertEquals(listOf(textEdit), pending.edits)
    }

    private fun documentChangesEdit(fileUri: String): ApplyWorkspaceEditParams =
        ApplyWorkspaceEditParams(
            WorkspaceEdit(listOf(Either.forLeft<TextDocumentEdit, ResourceOperation>(textDocumentEdit(fileUri)))),
            "Organize Imports",
        )

    private fun textDocumentEdit(fileUri: String): TextDocumentEdit =
        TextDocumentEdit(VersionedTextDocumentIdentifier(fileUri, null), listOf(textEdit))
}
