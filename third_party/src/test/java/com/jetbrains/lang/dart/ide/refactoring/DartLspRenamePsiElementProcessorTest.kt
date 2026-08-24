/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

class DartLspRenamePsiElementProcessorTest : DartCodeInsightFixtureTestCase() {

    fun testCanProcessElementGatedByExperimentalFlag() {
        val processor = DartLspRenamePsiElementProcessor()
        val file = myFixture.addFileToProject("lib/a.dart", "class A {}")
        val dir = file.containingDirectory

        // Disabled by default
        assertFalse(DartConfigurable.isExperimentalLspFeaturesEnabled(project))
        assertFalse(processor.canProcessElement(file))
        assertFalse(processor.canProcessElement(dir))

        // Enable experimental LSP
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        try {
            assertTrue(processor.canProcessElement(file))
            assertTrue(processor.canProcessElement(dir))
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        }
    }

    fun testApplyWorkspaceEdit() {
        val mainFile = myFixture.addFileToProject(
            "lib/main.dart",
            """
            import 'package:test_project/old_folder/foo.dart';

            void main() {
              print('hello');
            }
            """.trimIndent()
        )

        val mainVirtualFile = mainFile.virtualFile
        val fileUri = mainVirtualFile.url

        val workspaceEdit = WorkspaceEdit(
            listOf(
                Either.forLeft(
                    TextDocumentEdit(
                        VersionedTextDocumentIdentifier(fileUri, null),
                        listOf(
                            TextEdit(
                                Range(Position(0, 7), Position(0, 49)),
                                "'package:test_project/new_folder/foo.dart'"
                            )
                        )
                    )
                )
            )
        )

        DartLspRenamePsiElementProcessor.applyWorkspaceEdit(project, workspaceEdit)

        val doc = checkNotNull(FileDocumentManager.getInstance().getDocument(mainVirtualFile))
        assertEquals(
            """
            import 'package:test_project/new_folder/foo.dart';

            void main() {
              print('hello');
            }
            """.trimIndent(),
            doc.text
        )
    }

    fun testFindReferencesReturnsEmptyToPreventLegacyDoubleEdits() {
        val processor = DartLspRenamePsiElementProcessor()
        val file = myFixture.addFileToProject("lib/b.dart", "class B {}")
        val references = processor.findReferences(file, file.resolveScope, true)
        assertTrue("findReferences should return empty to prevent legacy PSI string double-edits", references.isEmpty())
    }

    fun testAwaitFutureCheckingCanceledSuccess() {
        val future = CompletableFuture<String>()
        future.complete("result")
        val result = DartLspRenamePsiElementProcessor.awaitFutureCheckingCanceled(future, 1)
        assertEquals("result", result)
    }

    fun testAwaitFutureCheckingCanceledTimeout() {
        val future = CompletableFuture<String>()
        try {
            DartLspRenamePsiElementProcessor.awaitFutureCheckingCanceled(future, 0)
            fail("Expected TimeoutException")
        } catch (_: TimeoutException) {
            // Expected
        }
    }
}
