/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.replaceService
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase

class DartLspRenameHandlersTest : DartCodeInsightFixtureTestCase() {

    private val renameCalls = mutableListOf<String>()

    override fun setUp() {
        super.setUp()
        DartLspRename.enabledInTests = true
        project.replaceService(DartLspRenameRequests::class.java, object : DartLspRenameRequests {
            override fun prepareRename(file: VirtualFile, offset: Int): DartLspRenameResult<DartLspRenameTarget> =
                DartLspRenameResult.Success(DartLspRenameTarget(TextRange(offset, offset), ""))

            override fun rename(file: VirtualFile, offset: Int, newName: String): DartLspRenameResult<List<DartLspFileTextEdit>> {
                renameCalls += newName
                // Renames every occurrence of the name at the offset in this file, like the server for a local variable.
                return DartLspRenameResult.Success(runReadAction { wordEdits(file, offset, newName) })
            }
        }, testRootDisposable)
    }

    override fun tearDown() {
        try {
            DartLspRename.enabledInTests = null
        }
        catch (e: Throwable) {
            addSuppressedException(e)
        }
        finally {
            super.tearDown()
        }
    }

    private fun dataContext(element: PsiElement?): DataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, myFixture.editor)
        .add(CommonDataKeys.CARET, myFixture.editor.caretModel.currentCaret)
        .add(CommonDataKeys.PSI_FILE, myFixture.file)
        .add(CommonDataKeys.VIRTUAL_FILE, myFixture.file.virtualFile)
        .add(CommonDataKeys.PSI_ELEMENT, element)
        .build()

    private fun elementAtCaret(): PsiElement = requireNotNull(
        TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.getInstance().allAccepted)
    )

    fun testLegacyRenameHandlerUnavailableWhenLspRenameEnabled() {
        myFixture.configureByText("a.dart", "class A {\n  void gr<caret>eet() {}\n}\n")
        val context = dataContext(elementAtCaret())

        assertFalse(DartServerRenameHandler().isAvailableOnDataContext(context))

        DartLspRename.enabledInTests = false
        assertTrue(DartServerRenameHandler().isAvailableOnDataContext(context))
    }

    fun testInplaceFlagsWhenLspRenameEnabled() {
        myFixture.configureByText("a.dart", "void f() {\n  var lo<caret>cal = 1;\n  print(local);\n}\nvoid greet() {}\n")
        val provider = DartRefactoringSupportProvider()
        val local = elementAtCaret()
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("greet"))
        val function = elementAtCaret()

        // DartLspInplaceRenameHandler renames Dart symbols in place instead of the platform's handlers.
        assertFalse(provider.isInplaceRenameAvailable(local, null))
        assertFalse(provider.isMemberInplaceRenameAvailable(local, null))
        assertFalse(provider.isMemberInplaceRenameAvailable(function, null))
    }

    fun testInplaceFlagsWhenLspRenameDisabled() {
        DartLspRename.enabledInTests = false
        myFixture.configureByText("a.dart", "void f() {\n  var lo<caret>cal = 1;\n  print(local);\n}\nvoid greet() {}\n")
        val provider = DartRefactoringSupportProvider()
        val local = elementAtCaret()
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("greet"))
        val function = elementAtCaret()

        assertTrue(provider.isInplaceRenameAvailable(local, null))
        assertFalse(provider.isMemberInplaceRenameAvailable(local, null))
        assertFalse(provider.isMemberInplaceRenameAvailable(function, null))
    }

    fun testDartInplaceRenameHandlerIsChosenForAMethod() {
        myFixture.configureByText("a.dart", "class A {\n  void gr<caret>eet() {}\n}\n")

        val handler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext(elementAtCaret()))

        assertInstanceOf(handler, DartLspInplaceRenameHandler::class.java)
    }

    fun testDialogIsUsedWhenInplaceRenameIsOff() {
        myFixture.configureByText("a.dart", "class A {\n  void gr<caret>eet() {}\n}\n")
        withInplaceRename(false) {
            val handler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext(elementAtCaret()))

            assertInstanceOf(handler, PsiElementRenameHandler::class.java)
        }
    }

    fun testDialogRenameGoesThroughTheServer() {
        myFixture.configureByText("a.dart", "void f() {\n  var lo<caret>cal = 1;\n  print(local);\n}\n")
        withInplaceRename(false) {
            val context = SimpleDataContext.builder()
                .setParent(dataContext(elementAtCaret()))
                .add(PsiElementRenameHandler.DEFAULT_NAME, "renamed")
                .build()

            // In tests, the rename dialog renames to DEFAULT_NAME right away.
            PsiElementRenameHandler().invoke(project, myFixture.editor, myFixture.file, context)
        }

        myFixture.checkResult("void f() {\n  var renamed = 1;\n  print(renamed);\n}\n")
        assertEquals(listOf("renamed"), renameCalls)
    }

    fun testInplaceRenameShowsNoOptionsInlay() {
        // The platform's options inlay offers "comments and strings", which the server-side rename can't honor.
        myFixture.configureByText("a.dart", "class A {\n  void gr<caret>eet() {}\n}\n")
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)

        DartLspInplaceRenameHandler().doRename(elementAtCaret(), myFixture.editor, dataContext(elementAtCaret()))
        val templateState = requireNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor)) { "in-place rename should run" }
        try {
            assertEmpty(myFixture.editor.inlayModel.getInlineElementsInRange(0, myFixture.editor.document.textLength))
        }
        finally {
            templateState.gotoEnd(true)
        }
    }

    private fun withInplaceRename(enabled: Boolean, action: () -> Unit) {
        val settings = EditorSettingsExternalizable.getInstance()
        val old = settings.isVariableInplaceRenameEnabled
        settings.isVariableInplaceRenameEnabled = enabled
        try {
            action()
        }
        finally {
            settings.isVariableInplaceRenameEnabled = old
        }
    }

    fun testInplaceRenameOfLocalVariableGoesThroughTheServer() {
        myFixture.configureByText("a.dart", "void f() {\n  var lo<caret>cal = 1;\n  print(local);\n}\n")

        CodeInsightTestUtil.doInlineRename(DartLspInplaceRenameHandler(), "renamed", myFixture)

        myFixture.checkResult("void f() {\n  var renamed = 1;\n  print(renamed);\n}\n")
        assertEquals(listOf("renamed"), renameCalls)
    }

    private fun wordEdits(file: VirtualFile, offset: Int, newName: String): List<DartLspFileTextEdit> {
        val text = requireNotNull(FileDocumentManager.getInstance().getDocument(file)).text
        val word = Regex("\\w+").findAll(text).first { offset in it.range.first..it.range.last + 1 }.value
        return Regex("\\b$word\\b").findAll(text).map {
            DartLspFileTextEdit(file, TextRange(it.range.first, it.range.last + 1), newName)
        }.toList()
    }
}
