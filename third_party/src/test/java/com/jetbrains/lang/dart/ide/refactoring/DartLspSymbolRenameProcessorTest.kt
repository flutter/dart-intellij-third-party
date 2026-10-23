/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.replaceService
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.psi.DartComponentName

class DartLspSymbolRenameProcessorTest : DartCodeInsightFixtureTestCase() {

    private val fake = FakeRenameRequests()

    override fun setUp() {
        super.setUp()
        DartLspRename.enabledInTests = true
        project.replaceService(DartLspRenameRequests::class.java, fake, testRootDisposable)
    }

    override fun tearDown() {
        try {
            DartLspRename.enabledInTests = null
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
        catch (e: Throwable) {
            addSuppressedException(e)
        }
        finally {
            super.tearDown()
        }
    }

    private fun addGreeterFiles(): Pair<PsiFile, PsiFile> {
        val a = myFixture.addFileToProject("lib/a.dart", "class Greeter {\n  String greet() => 'hi';\n}\n")
        val b = myFixture.addFileToProject("lib/b.dart", "import 'a.dart';\n\nvoid f() => Greeter().greet();\n")
        return a to b
    }

    private fun componentNameAt(file: PsiFile, text: String): DartComponentName {
        val offset = file.text.indexOf(text)
        assertTrue("'$text' not found", offset >= 0)
        return requireNotNull(PsiTreeUtil.getParentOfType(file.findElementAt(offset), DartComponentName::class.java, false))
    }

    private fun text(file: PsiFile): String = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file)).text

    fun testRenameAppliesServerEditsInAllFiles() {
        val (a, b) = addGreeterFiles()
        fake.renameResult = { _, _, newName -> DartLspRenameResult.Success(wordEdits("greet", newName, a.virtualFile, b.virtualFile)) }
        val declarationOffset = a.text.indexOf("greet")

        myFixture.renameElement(componentNameAt(a, "greet"), "welcome")

        assertEquals("class Greeter {\n  String welcome() => 'hi';\n}\n", text(a))
        assertEquals("import 'a.dart';\n\nvoid f() => Greeter().welcome();\n", text(b))
        val call = fake.renameCalls.single()
        assertEquals(a.virtualFile, call.file)
        assertEquals(declarationOffset, call.offset)
        assertEquals("welcome", call.newName)
    }

    fun testRenameIsOneUndoStep() {
        val (a, b) = addGreeterFiles()
        fake.renameResult = { _, _, newName -> DartLspRenameResult.Success(wordEdits("greet", newName, a.virtualFile, b.virtualFile)) }
        myFixture.configureFromExistingVirtualFile(a.virtualFile)
        TestDialogManager.setTestDialog(TestDialog.OK)

        myFixture.renameElement(componentNameAt(a, "greet"), "welcome")
        UndoManager.getInstance(project).undo(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))

        assertEquals("class Greeter {\n  String greet() => 'hi';\n}\n", text(a))
        assertEquals("import 'a.dart';\n\nvoid f() => Greeter().greet();\n", text(b))
    }

    fun testExcludedUsageIsNotApplied() {
        val (a, b) = addGreeterFiles()
        val element = componentNameAt(a, "greet")
        val usageInB = DartLspRenameUsageInfo(b, wordRange(b.text, "greet"), element, "welcome")

        WriteCommandAction.runWriteCommandAction(project) {
            DartLspSymbolRenameProcessor().renameElement(element, "welcome", arrayOf(usageInB), null)
        }

        assertEquals("class Greeter {\n  String greet() => 'hi';\n}\n", text(a))
        assertEquals("import 'a.dart';\n\nvoid f() => Greeter().welcome();\n", text(b))
    }

    fun testUsagesTrackDocumentChanges() {
        val (a, b) = addGreeterFiles()
        val element = componentNameAt(a, "greet")
        val usageInB = DartLspRenameUsageInfo(b, wordRange(b.text, "greet"), element, "welcome")
        val documentB = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(b))
        WriteCommandAction.runWriteCommandAction(project) {
            documentB.insertString(documentB.text.indexOf("void"), "// changed meanwhile\n")
            PsiDocumentManager.getInstance(project).commitDocument(documentB)
        }

        WriteCommandAction.runWriteCommandAction(project) {
            DartLspSymbolRenameProcessor().renameElement(element, "welcome", arrayOf(usageInB), null)
        }

        assertEquals("import 'a.dart';\n\n// changed meanwhile\nvoid f() => Greeter().welcome();\n", documentB.text)
    }

    fun testServerErrorBecomesConflict() {
        val (a, b) = addGreeterFiles()
        fake.renameResult = { _, _, _ -> DartLspRenameResult.Failure("Method name must not contain '-'.") }

        try {
            myFixture.renameElement(componentNameAt(a, "greet"), "gr-eet")
            fail("A conflict was expected")
        }
        catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
            assertTrue(e.messages.toString(), e.messages.any { it.contains("Method name must not contain '-'.") })
        }

        assertEquals("class Greeter {\n  String greet() => 'hi';\n}\n", text(a))
        assertEquals("import 'a.dart';\n\nvoid f() => Greeter().greet();\n", text(b))
    }

    fun testCancelledPromptRenamesNothing() {
        val (a, b) = addGreeterFiles()
        // The server answers with an empty edit when the user picks Cancel in its Rename Anyway prompt.
        fake.renameResult = { _, _, _ -> DartLspRenameResult.Success(emptyList()) }

        myFixture.renameElement(componentNameAt(a, "greet"), "hello")

        assertEquals("class Greeter {\n  String greet() => 'hi';\n}\n", text(a))
        assertEquals("import 'a.dart';\n\nvoid f() => Greeter().greet();\n", text(b))
    }

    fun testCancelledPromptLeavesNoUndoEntry() {
        val (a, _) = addGreeterFiles()
        myFixture.configureFromExistingVirtualFile(a.virtualFile)
        fake.renameResult = { _, _, _ -> DartLspRenameResult.Success(emptyList()) }

        val undoManager = UndoManager.getInstance(project)
        val textEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val undoBefore = undoManager.getUndoActionNameAndDescription(textEditor).first

        myFixture.renameElement(componentNameAt(a, "greet"), "hello")

        assertEquals(undoBefore, undoManager.getUndoActionNameAndDescription(textEditor).first)
    }

    fun testPrepareRenamingWithEmptyNameSendsNothing() {
        // MemberInplaceRenamer calls prepareRenaming with an empty name when the in-place template starts.
        val (a, _) = addGreeterFiles()
        val element = componentNameAt(a, "greet")

        DartLspSymbolRenameProcessor().prepareRenaming(element, "", mutableMapOf(), element.useScope)

        assertEmpty(fake.renameCalls)
    }

    fun testSubstituteForElementOutsideTheProjectAsksAtTheCaretAndShowsServerError() {
        val file = myFixture.configureByText("main.dart", "void main() {\n  pr<caret>int('hi');\n}\n")
        // Like an SDK declaration: a file outside the project content.
        val sdkFile = PsiFileFactory.getInstance(project).createFileFromText("core.dart", DartLanguage.INSTANCE, "void print(Object o) {}\n")
        fake.prepareResult = DartLspRenameResult.Failure("The function 'print' is defined in the SDK, so cannot be renamed.")

        try {
            DartLspSymbolRenameProcessor().substituteElementToRename(componentNameAt(sdkFile, "print"), myFixture.editor)
            fail("An error hint was expected")
        }
        catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
            assertEquals("The function 'print' is defined in the SDK, so cannot be renamed.", e.message)
        }
        val call = fake.prepareCalls.single()
        assertEquals(file.virtualFile, call.file)
        assertEquals(file.text.indexOf("print") + 2, call.offset)
    }

    fun testSubstituteKeepsElementWhenServerAgrees() {
        val (a, _) = addGreeterFiles()
        val element = componentNameAt(a, "greet")
        fake.prepareResult = DartLspRenameResult.Success(DartLspRenameTarget(wordRange(a.text, "greet"), "greet"))

        assertSame(element, DartLspSymbolRenameProcessor().substituteElementToRename(element, null))
        assertEquals(a.text.indexOf("greet"), fake.prepareCalls.single().offset)
    }

    fun testCanProcessElement() {
        val (a, _) = addGreeterFiles()
        val processor = DartLspSymbolRenameProcessor()
        val element = componentNameAt(a, "greet")

        assertTrue(processor.canProcessElement(element))
        assertFalse(processor.canProcessElement(a))
        assertFalse(processor.canProcessElement(requireNotNull(a.containingDirectory)))

        DartLspRename.enabledInTests = false
        assertFalse(processor.canProcessElement(element))
    }

    fun testNoCommentOrTextSearch() {
        val (a, _) = addGreeterFiles()
        val element = componentNameAt(a, "greet")
        val processor = DartLspSymbolRenameProcessor()

        assertFalse(processor.isToSearchInComments(element))
        assertFalse(processor.isToSearchForTextOccurrences(element))
    }

    fun testRenameDialogHidesCommentTextAndScopeOptions() {
        val (a, _) = addGreeterFiles()
        val element = componentNameAt(a, "greet")

        val dialog = DartLspSymbolRenameProcessor().createRenameDialog(project, element, null, null)
        try {
            assertFalse(dialog.isSearchInComments)
            assertFalse(dialog.isSearchInNonJavaFiles)
            assertNotNull(dialog.refactoringScope)
            assertFalse(dialog.cbSearchInComments.isShowing)
        }
        finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    private fun wordRange(text: String, word: String): TextRange {
        val start = Regex("\\b${Regex.escape(word)}\\b").find(text)?.range?.first ?: error("'$word' not found")
        return TextRange(start, start + word.length)
    }

    // Called by the fake on the background thread of the rename progress, like the real requests.
    private fun wordEdits(oldName: String, newName: String, vararg files: VirtualFile): List<DartLspFileTextEdit> = runReadAction {
        files.flatMap { file ->
            val text = requireNotNull(FileDocumentManager.getInstance().getDocument(file)).text
            Regex("\\b${Regex.escape(oldName)}\\b").findAll(text).map {
                DartLspFileTextEdit(file, TextRange(it.range.first, it.range.last + 1), newName)
            }.toList()
        }
    }

    private data class Call(val file: VirtualFile, val offset: Int, val newName: String?)

    private class FakeRenameRequests : DartLspRenameRequests {
        var prepareResult: DartLspRenameResult<DartLspRenameTarget> =
            DartLspRenameResult.Success(DartLspRenameTarget(TextRange.EMPTY_RANGE, ""))
        var renameResult: (VirtualFile, Int, String) -> DartLspRenameResult<List<DartLspFileTextEdit>> =
            { _, _, _ -> DartLspRenameResult.Success(emptyList()) }
        val prepareCalls = mutableListOf<Call>()
        val renameCalls = mutableListOf<Call>()

        override fun prepareRename(file: VirtualFile, offset: Int): DartLspRenameResult<DartLspRenameTarget> {
            prepareCalls += Call(file, offset, null)
            return prepareResult
        }

        override fun rename(file: VirtualFile, offset: Int, newName: String): DartLspRenameResult<List<DartLspFileTextEdit>> {
            renameCalls += Call(file, offset, newName)
            return renameResult(file, offset, newName)
        }
    }
}
