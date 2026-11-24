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
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.platform.dartlsp.api.customization.LspRenameDisabled
import com.intellij.platform.dartlsp.api.customization.LspRenameSupport
import com.intellij.openapi.util.TextRange
import com.intellij.platform.dartlsp.impl.features.rename.LspRenameHandler
import com.intellij.platform.dartlsp.impl.features.rename.startInlineRename
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.lsp.DartLspServerDescriptor

class DartLspRenameHandlersTest : DartCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        DartLspRename.enabledInTests = true
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

    private fun elementAtCaret(): PsiElement? =
        TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.getInstance().allAccepted)

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
        val local = requireNotNull(elementAtCaret())
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("greet"))
        val function = requireNotNull(elementAtCaret())

        // The platform's in-place handlers would rename without the server.
        assertFalse(provider.isInplaceRenameAvailable(local, null))
        assertFalse(provider.isMemberInplaceRenameAvailable(local, null))
        assertFalse(provider.isMemberInplaceRenameAvailable(function, null))
    }

    fun testInplaceFlagsWhenLspRenameDisabled() {
        DartLspRename.enabledInTests = false
        myFixture.configureByText("a.dart", "void f() {\n  var lo<caret>cal = 1;\n  print(local);\n}\nvoid greet() {}\n")
        val provider = DartRefactoringSupportProvider()
        val local = requireNotNull(elementAtCaret())
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("greet"))
        val function = requireNotNull(elementAtCaret())

        assertTrue(provider.isInplaceRenameAvailable(local, null))
        assertFalse(provider.isMemberInplaceRenameAvailable(local, null))
        assertFalse(provider.isMemberInplaceRenameAvailable(function, null))
    }

    fun testRenameCustomizerFollowsTheGate() {
        val descriptor = DartLspServerDescriptor(project)
        for (enabled in listOf(true, false, true)) {
            DartLspRename.enabledInTests = enabled
            val customizer = descriptor.lspCustomization.renameCustomizer
            if (enabled) assertInstanceOf(customizer, LspRenameSupport::class.java) else assertSame(LspRenameDisabled, customizer)
        }
    }

    fun testLspRenameRunsOnlyInDartFiles() {
        val support = DartLspServerDescriptor(project).lspCustomization.renameCustomizer as LspRenameSupport

        assertTrue(support.shouldRunRename(myFixture.configureByText("a.dart", "class A {}\n")))
        assertFalse(support.shouldRunRename(myFixture.configureByText("pubspec.yaml", "name: a\n")))
        assertFalse(support.shouldRunRename(myFixture.configureByText("analysis_options.yaml", "linter:\n")))
    }

    fun testLspRenameHandlerIsRegistered() {
        assertTrue(RenameHandler.EP_NAME.extensionList.any { it is LspRenameHandler })
    }

    // IntelliJ takes the platform's PSI-based rename only if no rename handler of the extension point is available, and
    // asks the user to choose if several are. LspRenameHandler (no server in this test) must therefore be the only one.
    fun testNoOtherRenameHandlerForAMethod() {
        myFixture.configureByText("a.dart", "class A {\n  void gr<caret>eet() {}\n}\n")
        assertNoOtherRenameHandler()
        withInplaceRename(false) { assertNoOtherRenameHandler() }

        DartLspRename.enabledInTests = false
        assertTrue(availableRenameHandlers().any { it is DartServerRenameHandler })
    }

    fun testNoOtherRenameHandlerForALocalVariable() {
        myFixture.configureByText("a.dart", "void f() {\n  var lo<caret>cal = 1;\n  print(local);\n}\n")
        assertNoOtherRenameHandler()

        DartLspRename.enabledInTests = false
        assertTrue(availableRenameHandlers().any { it is VariableInplaceRenameHandler })
    }

    fun testNoOtherRenameHandlerForANamedArgumentOfAFieldFormalParameter() {
        // DartFieldFormalParameter has no DartComponentName, so the PSI-based rename can't rename this argument.
        myFixture.configureByText("a.dart", """
            class Person {
              final String name;
              Person({required this.name});
            }
            var p = Person(na<caret>me: '');
        """.trimIndent())
        assertNoOtherRenameHandler()
    }

    fun testNoOtherRenameHandlerOnTheImportKeyword() {
        myFixture.configureByText("a.dart", "imp<caret>ort 'dart:math';\n")
        assertNoOtherRenameHandler()
    }

    fun testInplaceRenameEditsTheNameAtTheCaret() {
        myFixture.configureByText("a.dart", "class Gre<caret>eter {}\n")
        myFixture.editor.settings.isPreselectRename = false

        assertEquals("Grexeter", renameGreeterInPlace("x") {
            assertEquals(9, myFixture.editor.caretModel.offset)
            assertFalse(myFixture.editor.selectionModel.hasSelection())
        })
    }

    fun testInplaceRenameReplacesOnlyTheSelection() {
        myFixture.configureByText("a.dart", "class Gr<selection>eet<caret></selection>er {}\n")
        myFixture.editor.settings.isPreselectRename = false

        assertEquals("Grxer", renameGreeterInPlace("x") {
            assertEquals("eet", myFixture.editor.selectionModel.selectedText)
        })
    }

    fun testInplaceRenameReplacesTheNameWithPreselectOldName() {
        myFixture.configureByText("a.dart", "class Gre<caret>eter {}\n")
        myFixture.editor.settings.isPreselectRename = true

        assertEquals("Foo", renameGreeterInPlace("Foo") {
            assertEquals("Greeter", myFixture.editor.selectionModel.selectedText)
        })
    }

    /** Runs the patched `startInlineRename` of the platform's LspRenameHandler on `Greeter`, types [typed] and returns the new name. */
    private fun renameGreeterInPlace(typed: String, checkStart: () -> Unit): String? {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        val start = myFixture.editor.document.text.indexOf("Greeter")
        var newName: String? = null
        startInlineRename(project, myFixture.editor, TextRange(start, start + "Greeter".length), "Greeter") { newName = it }
        checkStart()
        myFixture.type(typed)
        val templateState = requireNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        // Enter finishes the template inside a command.
        CommandProcessor.getInstance().executeCommand(project, { templateState.gotoEnd(false) }, null, null)
        // The handler restores the old text and leaves the rename to the server.
        myFixture.checkResult("class Greeter {}\n")
        return newName
    }

    private fun availableRenameHandlers(): List<RenameHandler> =
        RenameHandler.EP_NAME.extensionList.filter { it.isRenaming(dataContext(elementAtCaret())) }

    private fun assertNoOtherRenameHandler() {
        assertEmpty(availableRenameHandlers().filter { it !is LspRenameHandler })
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
}
