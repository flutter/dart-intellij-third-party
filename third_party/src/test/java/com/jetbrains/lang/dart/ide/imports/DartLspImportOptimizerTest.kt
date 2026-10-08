/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.imports

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.lsp.DartLspApplyEditCapture
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either

class DartLspImportOptimizerTest : DartCodeInsightFixtureTestCase() {
    private val path = "/project/lib/a.dart"
    private val uri = "file:///project/lib/a.dart"
    private val sortedImports = TextEdit(Range(Position(0, 0), Position(2, 0)), "import 'dart:async';\nimport 'dart:math';\n")
    private val organizeImportsCommand =
        Command("Organize Imports", "dart.edit.organizeImports", listOf(mapOf("path" to path)))

    fun testLegacyOptimizerIsUsedWhenExperimentalLspIsOff() {
        val file = myFixture.addFileToProject("lib/off.dart", "")
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)

        assertTrue(DartImportOptimizer().supports(file))
        assertFalse(DartLspImportOptimizer().supports(file))
    }

    fun testLegacyOptimizerIsUsedWhenSdkIsTooOldForLspCodeActions() {
        // The test SDK (3.14.0-65.0.dev) is older than the code actions gate (3.14.0-137.0.dev).
        val file = myFixture.addFileToProject("lib/old_sdk.dart", "")
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        try {
            assertTrue(DartImportOptimizer().supports(file))
            assertFalse(DartLspImportOptimizer().supports(file))
        }
        finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        }
    }

    fun testEditSentDuringTheCommandIsReturned() {
        val capture = DartLspApplyEditCapture(project)
        val requests = mutableListOf<CodeActionParams>()
        val commands = mutableListOf<ExecuteCommandParams>()

        val edits = fetchOrganizeImportsEdits(
            path, uri, capture,
            requestCodeActions = { requests += it; listOf(organizeImportsAction()) },
            executeCommand = {
                commands += it
                // The server sends the edit back while it runs the command.
                assertEquals(true, capture.offer(applyEdit())?.isApplied)
                true
            },
        )

        assertEquals(listOf(sortedImports), edits)
        val request = requests.single()
        assertEquals(uri, request.textDocument.uri)
        assertEquals(listOf(CodeActionKind.SourceOrganizeImports), request.context.only)
        val command = commands.single()
        assertEquals("dart.edit.organizeImports", command.command)
        assertEquals(organizeImportsCommand.arguments, command.arguments)
        // The capture is gone once the command finished.
        assertNull(capture.offer(applyEdit()))
    }

    fun testNothingToOrganize() {
        val capture = DartLspApplyEditCapture(project)

        // The server sends no workspace/applyEdit if the imports are already organized.
        val edits = fetchOrganizeImportsEdits(path, uri, capture, { listOf(organizeImportsAction()) }, { true })

        assertNull(edits)
        assertNull(capture.offer(applyEdit()))
    }

    fun testNoOrganizeImportsAction() {
        var commandExecuted = false

        val edits = fetchOrganizeImportsEdits(
            path, uri, DartLspApplyEditCapture(project),
            requestCodeActions = { listOf(Either.forRight(CodeAction("Sort Members").apply { kind = "source.sortMembers" })) },
            executeCommand = { commandExecuted = true; true },
        )

        assertNull(edits)
        assertFalse(commandExecuted)
    }

    fun testNoResponseToTheCodeActionRequest() {
        val edits = fetchOrganizeImportsEdits(path, uri, DartLspApplyEditCapture(project), { null }, { fail(); true })

        assertNull(edits)
    }

    fun testActionWithoutCommandIsIgnored() {
        val action = CodeAction("Organize Imports").apply {
            kind = CodeActionKind.SourceOrganizeImports
            edit = WorkspaceEdit(mapOf(uri to listOf(sortedImports)))
        }

        val edits = fetchOrganizeImportsEdits(
            path, uri, DartLspApplyEditCapture(project), { listOf(Either.forRight(action)) }, { fail(); true })

        assertNull(edits)
    }

    fun testLateEditOfUnfinishedCommandIsDiscarded() {
        val capture = DartLspApplyEditCapture(project)

        // E.g. a timeout: waiting stopped before the server answered.
        val edits = fetchOrganizeImportsEdits(path, uri, capture, { listOf(organizeImportsAction()) }, { false })

        assertNull(edits)
        assertEquals(false, capture.offer(applyEdit())?.isApplied)
    }

    fun testLateEditAfterCancellationIsDiscarded() {
        val capture = DartLspApplyEditCapture(project)

        try {
            fetchOrganizeImportsEdits(
                path, uri, capture, { listOf(organizeImportsAction()) }, { throw ProcessCanceledException() })
            fail("ProcessCanceledException expected")
        }
        catch (_: ProcessCanceledException) {
        }

        assertEquals(false, capture.offer(applyEdit())?.isApplied)
    }

    fun testRunnableAppliesTheEdits() {
        val file = myFixture.addFileToProject("lib/apply.dart", UNSORTED)
        val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
        val runnable = DartLspOrganizeImportsRunnable(project, document, document.modificationStamp, listOf(sortedImports))

        WriteCommandAction.runWriteCommandAction(project, runnable)

        assertEquals(SORTED, document.text)
        assertEquals("Organized directives", (runnable as ImportOptimizer.CollectingInfoRunnable).userNotificationInfo)
    }

    fun testRunnableSkipsTheEditsIfTheDocumentChanged() {
        val file = myFixture.addFileToProject("lib/changed.dart", UNSORTED)
        val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
        val runnable = DartLspOrganizeImportsRunnable(project, document, document.modificationStamp, listOf(sortedImports))
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, "\n") }

        WriteCommandAction.runWriteCommandAction(project, runnable)

        assertEquals("$UNSORTED\n", document.text)
        assertNull((runnable as ImportOptimizer.CollectingInfoRunnable).userNotificationInfo)
    }

    private fun organizeImportsAction(): Either<Command, CodeAction> =
        Either.forRight(CodeAction("Organize Imports").apply {
            kind = CodeActionKind.SourceOrganizeImports
            command = organizeImportsCommand
        })

    private fun applyEdit() =
        ApplyWorkspaceEditParams(WorkspaceEdit(mapOf(uri to listOf(sortedImports))), "Organize Imports")

    companion object {
        private val UNSORTED = """
            import 'dart:math';
            import 'dart:async';

            Future<int> answer() => Future.value(max(41, 42));
        """.trimIndent()

        private val SORTED = """
            import 'dart:async';
            import 'dart:math';

            Future<int> answer() => Future.value(max(41, 42));
        """.trimIndent()
    }
}
