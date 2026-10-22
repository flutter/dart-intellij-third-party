/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.google.gson.JsonPrimitive
import com.intellij.CommonBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.LspServerDescriptor
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.platform.dartlsp.api.LspServerSupportProvider
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.ide.refactoring.extract.DartServerExtractMethodHandler
import com.jetbrains.lang.dart.ide.refactoring.introduce.DartServerExtractLocalVariableHandler
import com.jetbrains.lang.dart.lsp.DartLspServerDescriptor
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdkUtil
import com.jetbrains.lang.dart.util.DartTestUtils
import org.dartlang.analysis.server.protocol.RefactoringKind
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionDisabled
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture

class DartLspRefactoringHelperTest : DartCodeInsightFixtureTestCase() {

    private var previousCachedVersion: String? = null
    private var versionPairKey: Pair<File, Long>? = null

    @Suppress("UNCHECKED_CAST")
    private fun getOurVersionsMap(): MutableMap<Pair<File, Long>, String> {
        val field = DartSdkUtil::class.java.getDeclaredField("ourVersions")
        field.isAccessible = true
        return field.get(null) as MutableMap<Pair<File, Long>, String>
    }

    override fun setUp() {
        val versionFile = File("${DartTestUtils.SDK_HOME_PATH}/version")
        val key = Pair.create(versionFile, versionFile.lastModified())
        versionPairKey = key
        val map = getOurVersionsMap()
        previousCachedVersion = map[key]
        map[key] = "3.14.0-137.0.dev"
        super.setUp()
    }

    override fun tearDown() {
        try {
            PropertiesComponent.getInstance(project).unsetValue("dart.lsp.experimental.enabled")
            val key = versionPairKey
            if (key != null) {
                val map = getOurVersionsMap()
                val prev = previousCachedVersion
                if (prev != null) {
                    map[key] = prev
                } else {
                    map.remove(key)
                }
            }
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testMatchesRefactoringForExtractMethod() {
        val targetTitles = setOf(DartLspRefactoringHelper.EXTRACT_METHOD_TITLE)
        val targetKinds = setOf(RefactoringKind.EXTRACT_METHOD)

        val byTitle = CodeAction("Extract Method")
        assertTrue(DartLspRefactoringHelper.matchesRefactoring(byTitle, targetTitles, targetKinds))

        val byCommandString = CodeAction("Custom Title").apply {
            command = Command("Custom Title", "refactor.perform", listOf("EXTRACT_METHOD"))
        }
        assertTrue(DartLspRefactoringHelper.matchesRefactoring(byCommandString, targetTitles, targetKinds))

        val byCommandJsonPrimitive = CodeAction("Custom Title").apply {
            command = Command("Custom Title", "refactor.perform", listOf(JsonPrimitive("EXTRACT_METHOD")))
        }
        assertTrue(DartLspRefactoringHelper.matchesRefactoring(byCommandJsonPrimitive, targetTitles, targetKinds))

        val extractLocal = CodeAction("Extract Local Variable").apply {
            command = Command("Extract Local Variable", "refactor.perform", listOf("EXTRACT_LOCAL_VARIABLE"))
        }
        assertFalse(DartLspRefactoringHelper.matchesRefactoring(extractLocal, targetTitles, targetKinds))

        val extractWidget = CodeAction("Extract Widget").apply {
            command = Command("Extract Widget", "refactor.perform", listOf("EXTRACT_WIDGET"))
        }
        assertFalse(DartLspRefactoringHelper.matchesRefactoring(extractWidget, targetTitles, targetKinds))
    }

    fun testMatchesRefactoringForExtractLocalVariable() {
        val targetTitles = setOf(DartLspRefactoringHelper.EXTRACT_LOCAL_VARIABLE_TITLE)
        val targetKinds = setOf(RefactoringKind.EXTRACT_LOCAL_VARIABLE)

        val byTitle = CodeAction("Extract Local Variable")
        assertTrue(DartLspRefactoringHelper.matchesRefactoring(byTitle, targetTitles, targetKinds))

        val byCommand = CodeAction("Other Title").apply {
            command = Command("Other Title", "refactor.perform", listOf("EXTRACT_LOCAL_VARIABLE"))
        }
        assertTrue(DartLspRefactoringHelper.matchesRefactoring(byCommand, targetTitles, targetKinds))

        val extractMethod = CodeAction("Extract Method").apply {
            command = Command("Extract Method", "refactor.perform", listOf("EXTRACT_METHOD"))
        }
        assertFalse(DartLspRefactoringHelper.matchesRefactoring(extractMethod, targetTitles, targetKinds))
    }

    fun testMatchesRefactoringForInline() {
        val targetTitles = setOf(
            DartLspRefactoringHelper.INLINE_LOCAL_VARIABLE_TITLE,
            DartLspRefactoringHelper.INLINE_METHOD_TITLE,
        )
        val targetKinds = setOf(
            RefactoringKind.INLINE_LOCAL_VARIABLE,
            RefactoringKind.INLINE_METHOD,
        )

        val inlineLocal = CodeAction("Inline Local Variable")
        assertTrue(DartLspRefactoringHelper.matchesRefactoring(inlineLocal, targetTitles, targetKinds))

        val inlineMethod = CodeAction("Inline Method")
        assertTrue(DartLspRefactoringHelper.matchesRefactoring(inlineMethod, targetTitles, targetKinds))

        val inlineByCommand = CodeAction("Inline...").apply {
            command = Command("Inline...", "refactor.perform", listOf("INLINE_METHOD"))
        }
        assertTrue(DartLspRefactoringHelper.matchesRefactoring(inlineByCommand, targetTitles, targetKinds))

        val extractMethod = CodeAction("Extract Method")
        assertFalse(DartLspRefactoringHelper.matchesRefactoring(extractMethod, targetTitles, targetKinds))
    }

    fun testFindMatchingCodeActionSkipsDisabledActionsAndUnwrapsCommands() {
        val targetTitles = setOf(DartLspRefactoringHelper.EXTRACT_METHOD_TITLE)
        val targetKinds = setOf(RefactoringKind.EXTRACT_METHOD)

        val disabledAction = CodeAction("Extract Method").apply {
            disabled = CodeActionDisabled("Selection is not valid")
        }
        val validCommand = Command("Extract Method", "refactor.perform", listOf("EXTRACT_METHOD"))

        val matched = DartLspRefactoringHelper.findMatchingCodeAction(
            listOf(
                Either.forRight(disabledAction),
                Either.forLeft(validCommand),
            ),
            targetTitles,
            targetKinds,
        )
        assertNotNull(matched)
        assertEquals("Extract Method", matched?.title)
        assertEquals("refactor.perform", matched?.command?.command)
        assertNotNull("Unwrapped Command should have non-null edit to avoid resolveCodeAction", matched?.edit)
    }

    fun testDartInlineHandlerCanInlineElementInEditorGatedByLspCodeActions() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void main() {
              var foo = 42;
              print(fo<caret>o);
            }
            """.trimIndent(),
        )
        val element = file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)

        val handler = DartInlineHandler()

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        assertTrue(handler.canInlineElementInEditor(element, myFixture.editor))
        assertTrue(handler.canInlineElementInEditor(null, myFixture.editor))

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        // Without legacy navigation regions populated in DAS, legacy findContext returns null
        assertFalse(handler.canInlineElementInEditor(element, myFixture.editor))
        assertFalse(handler.canInlineElementInEditor(null, myFixture.editor))
    }

    fun testHandlersShowCannotRefactorErrorHintWhenLspServerNotRunning() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void main() {
              <selection>print('hello');</selection>
            }
            """.trimIndent(),
        )
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)

        val expectedMessage = RefactoringBundle.getCannotRefactorMessage(null)

        try {
            DartServerExtractMethodHandler().invoke(project, myFixture.editor, file, DataContext.EMPTY_CONTEXT)
            fail("Expected RefactoringErrorHintException for Extract Method")
        } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
            assertEquals(expectedMessage, e.message)
        }

        try {
            DartServerExtractLocalVariableHandler().invoke(project, myFixture.editor, file, DataContext.EMPTY_CONTEXT)
            fail("Expected RefactoringErrorHintException for Extract Local Variable")
        } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
            assertEquals(expectedMessage, e.message)
        }

        try {
            val element = file.findElementAt(myFixture.caretOffset)
            DartInlineHandler().inlineElement(project, myFixture.editor, element)
            fail("Expected RefactoringErrorHintException for Inline")
        } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
            assertEquals(expectedMessage, e.message)
        }
    }

    fun testPerformRefactoringExecutesMatchingCodeActionCommand() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void main() {
              <selection>print('hello');</selection>
            }
            """.trimIndent(),
        )
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)

        var capturedCodeActionParams: CodeActionParams? = null
        var capturedExecuteCommandParams: ExecuteCommandParams? = null

        val fakeServer = createFakeLspServer(
            onCodeAction = { params ->
                capturedCodeActionParams = params
                val action = CodeAction("Extract Method").apply {
                    kind = CodeActionKind.RefactorExtract
                    command = Command(
                        "Extract Method",
                        "refactor.perform",
                        listOf("EXTRACT_METHOD", file.virtualFile.path, 1, 16, 15),
                    )
                }
                listOf(Either.forRight(action))
            },
            onExecuteCommand = { params ->
                capturedExecuteCommandParams = params
            },
        )

        val selectionModel = myFixture.editor.selectionModel
        val offset = selectionModel.selectionStart
        val length = selectionModel.selectionEnd - offset

        DartLspRefactoringHelper.performRefactoring(
            project = project,
            editor = myFixture.editor,
            file = file,
            offset = offset,
            length = length,
            codeActionKind = CodeActionKind.RefactorExtract,
            targetTitles = setOf(DartLspRefactoringHelper.EXTRACT_METHOD_TITLE),
            targetRefactoringKinds = setOf(RefactoringKind.EXTRACT_METHOD),
            progressTitle = DartBundle.message("progress.title.extract.method"),
            errorTitle = DartBundle.message("dart.refactoring.extract.method.error"),
            lspServer = fakeServer,
        )

        val codeActionParams = checkNotNull(capturedCodeActionParams) { "codeAction should have been called" }
        assertEquals(listOf(CodeActionKind.RefactorExtract), codeActionParams.context.only)
        assertEquals(CodeActionTriggerKind.Invoked, codeActionParams.context.triggerKind)
        assertEquals(1, codeActionParams.range.start.line)
        assertEquals(2, codeActionParams.range.start.character)
        assertEquals(1, codeActionParams.range.end.line)
        assertEquals(17, codeActionParams.range.end.character)

        val executeCommandParams = checkNotNull(capturedExecuteCommandParams) { "executeCommand should have been called" }
        assertEquals("refactor.perform", executeCommandParams.command)
        assertEquals("EXTRACT_METHOD", executeCommandParams.arguments.first())
    }

    fun testPerformRefactoringShowsErrorHintWhenNoMatchingActionReturned() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void main() {
              <selection>var x = 1;</selection>
            }
            """.trimIndent(),
        )
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)

        val fakeServer = createFakeLspServer(
            onCodeAction = {
                // Only return Extract Method when Extract Local Variable is requested
                val action = CodeAction("Extract Method").apply {
                    kind = CodeActionKind.RefactorExtract
                    command = Command("Extract Method", "refactor.perform", listOf("EXTRACT_METHOD"))
                }
                listOf(Either.forRight(action))
            },
            onExecuteCommand = {},
        )

        val selectionModel = myFixture.editor.selectionModel
        val offset = selectionModel.selectionStart
        val length = selectionModel.selectionEnd - offset

        try {
            DartLspRefactoringHelper.performRefactoring(
                project = project,
                editor = myFixture.editor,
                file = file,
                offset = offset,
                length = length,
                codeActionKind = CodeActionKind.RefactorExtract,
                targetTitles = setOf(DartLspRefactoringHelper.EXTRACT_LOCAL_VARIABLE_TITLE),
                targetRefactoringKinds = setOf(RefactoringKind.EXTRACT_LOCAL_VARIABLE),
                progressTitle = DartBundle.message("progress.title.extract.local.variable"),
                errorTitle = CommonBundle.getErrorTitle(),
                lspServer = fakeServer,
            )
            fail("Expected RefactoringErrorHintException when no matching CodeAction is returned")
        } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
            assertEquals(RefactoringBundle.getCannotRefactorMessage(null), e.message)
        }
    }

    private fun createFakeLspServer(
        onCodeAction: (CodeActionParams) -> List<Either<Command, CodeAction>>,
        onExecuteCommand: (ExecuteCommandParams) -> Unit,
    ): LspServer {
        val textDocumentService = Proxy.newProxyInstance(
            TextDocumentService::class.java.classLoader,
            arrayOf(TextDocumentService::class.java),
        ) { _, method, args ->
            if (method.name == "codeAction") {
                val params = args[0] as CodeActionParams
                CompletableFuture.completedFuture(onCodeAction(params))
            } else {
                null
            }
        } as TextDocumentService

        val workspaceService = Proxy.newProxyInstance(
            WorkspaceService::class.java.classLoader,
            arrayOf(WorkspaceService::class.java),
        ) { _, method, args ->
            if (method.name == "executeCommand") {
                val params = args[0] as ExecuteCommandParams
                onExecuteCommand(params)
                CompletableFuture.completedFuture(null)
            } else {
                null
            }
        } as WorkspaceService

        val lsp4jServer = Proxy.newProxyInstance(
            Lsp4jServer::class.java.classLoader,
            arrayOf(Lsp4jServer::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getTextDocumentService" -> textDocumentService
                "getWorkspaceService" -> workspaceService
                else -> null
            }
        } as Lsp4jServer

        val serverDescriptor = DartLspServerDescriptor(project)

        return object : LspServer {
            override val providerClass: Class<out LspServerSupportProvider> = DartLspServerSupportProvider::class.java
            override val project = this@DartLspRefactoringHelperTest.project
            override val descriptor: LspServerDescriptor = serverDescriptor
            override val state: LspServerState = LspServerState.Running
            override val initializeResult: InitializeResult = InitializeResult(
                ServerCapabilities().apply {
                    setCodeActionProvider(true)
                },
            )

            override fun sendNotification(lsp4jSender: (Lsp4jServer) -> Unit) {
                lsp4jSender(lsp4jServer)
            }

            override suspend fun <Lsp4jResponse> sendRequest(
                lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
            ): Lsp4jResponse? {
                return lsp4jSender(lsp4jServer).get()
            }

            override fun <Lsp4jResponse> sendRequestSync(
                timeoutMs: Int,
                lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
            ): Lsp4jResponse? {
                return lsp4jSender(lsp4jServer).get()
            }

            override fun getDocumentIdentifier(file: VirtualFile): TextDocumentIdentifier {
                return TextDocumentIdentifier(descriptor.getFileUri(file))
            }

            override fun getDocumentVersion(document: Document): Int = 1
        }
    }
}
