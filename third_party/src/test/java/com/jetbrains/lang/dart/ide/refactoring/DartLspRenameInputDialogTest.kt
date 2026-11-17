/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import java.io.IOException

class DartLspRenameInputDialogTest : DartCodeInsightFixtureTestCase() {

    private var renameResult: (VirtualFile, String) -> DartLspRenameResult<List<DartLspFileTextEdit>> =
        { _, _ -> DartLspRenameResult.Success(emptyList()) }

    override fun setUp() {
        super.setUp()
        project.replaceService(DartLspRenameRequests::class.java, object : DartLspRenameRequests {
            override fun prepareRename(file: VirtualFile, offset: Int) = throw AssertionError("No prepareRename expected")
            override fun rename(file: VirtualFile, offset: Int, newName: String) = renameResult(file, newName)
        }, testRootDisposable)
    }

    override fun tearDown() {
        try {
            TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
        }
        catch (e: Throwable) {
            addSuppressedException(e)
        }
        finally {
            super.tearDown()
        }
    }

    private val text = "class Greeter {\n  Greeter();\n}\n"

    private fun renameConstructorTo(name: String?) {
        myFixture.configureByText("greeter.dart", text)
        TestDialogManager.setTestInputDialog { name }
        renameResult = { file, newName ->
            DartLspRenameResult.Success(listOf(DartLspFileTextEdit(file, TextRange.from(text.indexOf("();"), 0), ".$newName")))
        }
    }

    fun testEditsApplied() {
        renameConstructorTo("named")

        renameWithInputDialog(project, myFixture.file.virtualFile, text.indexOf("Greeter()"), "")

        myFixture.checkResult("class Greeter {\n  Greeter.named();\n}\n")
    }

    fun testReadOnlyFileIsNotChanged() {
        renameConstructorTo("named")
        val file = myFixture.file.virtualFile
        WriteAction.runAndWait<IOException> { file.isWritable = false }
        try {
            renameWithInputDialog(project, file, text.indexOf("Greeter()"), "")
        }
        finally {
            WriteAction.runAndWait<IOException> { file.isWritable = true }
        }

        myFixture.checkResult(text)
    }

    fun testCancelledInputChangesNothing() {
        renameConstructorTo(null)
        renameResult = { _, _ -> fail("No rename expected"); DartLspRenameResult.Success(emptyList()) }

        renameWithInputDialog(project, myFixture.file.virtualFile, text.indexOf("Greeter()"), "")

        myFixture.checkResult(text)
    }
}
