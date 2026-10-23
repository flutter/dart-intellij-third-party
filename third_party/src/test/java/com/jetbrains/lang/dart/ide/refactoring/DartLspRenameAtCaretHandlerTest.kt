/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.replaceService
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import java.io.IOException

class DartLspRenameAtCaretHandlerTest : DartCodeInsightFixtureTestCase() {

    private var prepareResult: DartLspRenameResult<DartLspRenameTarget> =
        DartLspRenameResult.Success(DartLspRenameTarget(TextRange(0, 0), ""))
    private var renameResult: (VirtualFile, String) -> DartLspRenameResult<List<DartLspFileTextEdit>> =
        { _, _ -> DartLspRenameResult.Success(emptyList()) }

    override fun setUp() {
        super.setUp()
        DartLspRename.enabledInTests = true
        project.replaceService(DartLspRenameRequests::class.java, object : DartLspRenameRequests {
            override fun prepareRename(file: VirtualFile, offset: Int) = prepareResult
            override fun rename(file: VirtualFile, offset: Int, newName: String) = renameResult(file, newName)
        }, testRootDisposable)
    }

    override fun tearDown() {
        try {
            DartLspRename.enabledInTests = null
            TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
        }
        catch (e: Throwable) {
            addSuppressedException(e)
        }
        finally {
            super.tearDown()
        }
    }

    private fun dataContext(): DataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, myFixture.editor)
        .add(CommonDataKeys.PSI_FILE, myFixture.file)
        .add(CommonDataKeys.VIRTUAL_FILE, myFixture.file.virtualFile)
        .add(CommonDataKeys.PSI_ELEMENT,
             TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.getInstance().allAccepted))
        .build()

    fun testAvailableOnImportKeyword() {
        myFixture.configureByText("main.dart", "imp<caret>ort 'dart:math';\n\nvoid main() => print(max(1, 2));\n")

        assertTrue(DartLspRenameAtCaretHandler().isAvailableOnDataContext(dataContext()))
    }

    fun testUnavailableWhereIntelliJHasAnElement() {
        myFixture.configureByText("main.dart", "import 'dart:math';\n\nvoid ma<caret>in() => print(max(1, 2));\n")

        assertFalse(DartLspRenameAtCaretHandler().isAvailableOnDataContext(dataContext()))
    }

    fun testUnavailableWhenLspRenameDisabled() {
        DartLspRename.enabledInTests = false
        myFixture.configureByText("main.dart", "imp<caret>ort 'dart:math';\n\nvoid main() => print(max(1, 2));\n")

        assertFalse(DartLspRenameAtCaretHandler().isAvailableOnDataContext(dataContext()))
    }

    fun testImportPrefixAdded() {
        val text = "imp<caret>ort 'dart:math';\n\nvoid main() => print(max(1, 2));\n"
        myFixture.configureByText("main.dart", text)
        val plain = text.replace("<caret>", "")
        TestDialogManager.setTestInputDialog { "math" }
        renameResult = { file, newName ->
            DartLspRenameResult.Success(listOf(
                DartLspFileTextEdit(file, TextRange.from(plain.indexOf(";"), 0), " as $newName"),
                DartLspFileTextEdit(file, TextRange.from(plain.indexOf("max"), 0), "$newName."),
            ))
        }

        DartLspRenameAtCaretHandler().invoke(project, myFixture.editor, myFixture.file, dataContext())

        myFixture.checkResult("import 'dart:math' as math;\n\nvoid main() => print(math.max(1, 2));\n")
    }

    fun testReadOnlyFileIsNotChanged() {
        val text = "imp<caret>ort 'dart:math';\n"
        myFixture.configureByText("main.dart", text)
        val plain = text.replace("<caret>", "")
        TestDialogManager.setTestInputDialog { "math" }
        renameResult = { file, newName ->
            DartLspRenameResult.Success(listOf(DartLspFileTextEdit(file, TextRange.from(plain.indexOf(";"), 0), " as $newName")))
        }
        val file = myFixture.file.virtualFile
        WriteAction.runAndWait<IOException> { file.isWritable = false }
        try {
            DartLspRenameAtCaretHandler().invoke(project, myFixture.editor, myFixture.file, dataContext())
        }
        finally {
            WriteAction.runAndWait<IOException> { file.isWritable = true }
        }

        myFixture.checkResult(plain)
    }

    fun testCancelledInputChangesNothing() {
        myFixture.configureByText("main.dart", "imp<caret>ort 'dart:math';\n")
        TestDialogManager.setTestInputDialog { null }
        renameResult = { _, _ -> fail("No rename expected"); DartLspRenameResult.Success(emptyList()) }

        DartLspRenameAtCaretHandler().invoke(project, myFixture.editor, myFixture.file, dataContext())

        myFixture.checkResult("import 'dart:math';\n")
    }

    fun testServerRefusalShowsHint() {
        myFixture.configureByText("main.dart", "// a comm<caret>ent\n")
        prepareResult = DartLspRenameResult.Failure("This element can't be renamed.")

        try {
            DartLspRenameAtCaretHandler().invoke(project, myFixture.editor, myFixture.file, dataContext())
            fail("An error hint was expected")
        }
        catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
            assertEquals("This element can't be renamed.", e.message)
        }
    }
}
