/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.google.gson.JsonPrimitive
import com.intellij.CommonBundle
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.customization.LspIntentionAction
import com.intellij.platform.dartlsp.util.getLsp4jRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider
import com.jetbrains.lang.dart.sdk.DartSdk
import org.dartlang.analysis.server.protocol.RefactoringKind
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * Routes Dart refactoring actions (Extract Method, Extract Local Variable, Inline)
 * to LSP `textDocument/codeAction` requests when LSP code actions are enabled.
 */
object DartLspRefactoringHelper {
    const val EXTRACT_METHOD_TITLE: String = "Extract Method"
    const val EXTRACT_LOCAL_VARIABLE_TITLE: String = "Extract Local Variable"
    const val INLINE_LOCAL_VARIABLE_TITLE: String = "Inline Local Variable"
    const val INLINE_METHOD_TITLE: String = "Inline Method"
    const val PERFORM_REFACTOR_COMMAND: String = "refactor.perform"

    @JvmStatic
    fun performExtractMethod(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val selectionModel = editor.selectionModel
        val offset = selectionModel.selectionStart
        val length = selectionModel.selectionEnd - offset
        performRefactoring(
            project = project,
            editor = editor,
            file = file,
            offset = offset,
            length = length,
            codeActionKind = CodeActionKind.RefactorExtract,
            targetTitles = setOf(EXTRACT_METHOD_TITLE),
            targetRefactoringKinds = setOf(RefactoringKind.EXTRACT_METHOD),
            progressTitle = DartBundle.message("progress.title.extract.method"),
            errorTitle = DartBundle.message("dart.refactoring.extract.method.error"),
        )
    }

    @JvmStatic
    fun performExtractLocalVariable(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val selectionModel = editor.selectionModel
        val offset = selectionModel.selectionStart
        val length = selectionModel.selectionEnd - offset
        performRefactoring(
            project = project,
            editor = editor,
            file = file,
            offset = offset,
            length = length,
            codeActionKind = CodeActionKind.RefactorExtract,
            targetTitles = setOf(EXTRACT_LOCAL_VARIABLE_TITLE),
            targetRefactoringKinds = setOf(RefactoringKind.EXTRACT_LOCAL_VARIABLE),
            progressTitle = DartBundle.message("progress.title.extract.local.variable"),
            errorTitle = CommonBundle.getErrorTitle(),
        )
    }

    @JvmStatic
    fun performInline(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val offset = editor.caretModel.offset
        performRefactoring(
            project = project,
            editor = editor,
            file = file,
            offset = offset,
            length = 0,
            codeActionKind = CodeActionKind.RefactorInline,
            targetTitles = setOf(INLINE_LOCAL_VARIABLE_TITLE, INLINE_METHOD_TITLE),
            targetRefactoringKinds = setOf(
                RefactoringKind.INLINE_LOCAL_VARIABLE,
                RefactoringKind.INLINE_METHOD,
            ),
            progressTitle = RefactoringBundle.message("inline.command"),
            errorTitle = CommonBundle.getErrorTitle(),
        )
    }

    @JvmStatic
    @JvmOverloads
    fun performRefactoring(
        project: Project,
        editor: Editor,
        file: PsiFile,
        offset: Int,
        length: Int,
        codeActionKind: String,
        targetTitles: Set<String>,
        targetRefactoringKinds: Set<String>,
        @NlsContexts.DialogTitle progressTitle: String,
        @NlsContexts.DialogTitle errorTitle: String,
        lspServer: LspServer? = LspServerManager.getInstance(project)
            .getServersForProvider(DartLspServerSupportProvider::class.java)
            .firstOrNull(),
    ) {
        if (DartSdk.getDartSdk(project) == null) return
        if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) return
        val virtualFile = file.virtualFile ?: return

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        if (lspServer == null) {
            showCannotRefactorErrorHint(project, editor, errorTitle)
            return
        }

        val params = createCodeActionParams(lspServer, editor, file, offset, length, codeActionKind) ?: run {
            showCannotRefactorErrorHint(project, editor, errorTitle)
            return
        }

        var matchingIntention: LspIntentionAction? = null
        val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                val results = lspServer.sendRequestSync { it.textDocumentService.codeAction(params) }.orEmpty()
                val matchingAction = findMatchingCodeAction(results, targetTitles, targetRefactoringKinds)
                    ?: return@runProcessWithProgressSynchronously
                val intention = LspIntentionAction(lspServer, matchingAction)
                val available = runReadActionBlocking { intention.isAvailable() }
                if (available) {
                    matchingIntention = intention
                }
            },
            progressTitle,
            true,
            project,
        )

        if (!completed) return

        val intentionToInvoke = matchingIntention
        if (intentionToInvoke == null) {
            showCannotRefactorErrorHint(project, editor, errorTitle)
            return
        }

        intentionToInvoke.invoke(virtualFile)
    }

    @JvmStatic
    fun createCodeActionParams(
        lspServer: LspServer,
        editor: Editor,
        file: PsiFile,
        offset: Int,
        length: Int,
        codeActionKind: String,
    ): CodeActionParams? {
        val virtualFile = file.virtualFile ?: return null
        val document = editor.document
        val textLength = document.textLength
        val safeOffset = offset.coerceIn(0, textLength)
        val safeLength = length.coerceIn(0, textLength - safeOffset)
        val range = getLsp4jRange(document, safeOffset, safeLength)
        val context = CodeActionContext(emptyList(), listOf(codeActionKind)).apply {
            triggerKind = CodeActionTriggerKind.Invoked
        }
        return CodeActionParams(lspServer.getDocumentIdentifier(virtualFile), range, context)
    }

    @JvmStatic
    fun findMatchingCodeAction(
        results: List<Either<Command, CodeAction>>,
        targetTitles: Set<String>,
        targetRefactoringKinds: Set<String>,
    ): CodeAction? {
        for (either in results) {
            val codeAction = when {
                either.isRight -> either.right
                either.isLeft -> {
                    val cmd = either.left ?: continue
                    CodeAction().apply {
                        title = cmd.title
                        command = cmd
                        edit = WorkspaceEdit()
                    }
                }
                else -> null
            } ?: continue

            if (codeAction.disabled != null) continue

            if (matchesRefactoring(codeAction, targetTitles, targetRefactoringKinds)) {
                return codeAction
            }
        }
        return null
    }

    @JvmStatic
    fun matchesRefactoring(
        codeAction: CodeAction,
        targetTitles: Set<String>,
        targetRefactoringKinds: Set<String>,
    ): Boolean {
        if (codeAction.title in targetTitles) {
            return true
        }
        val command = codeAction.command ?: return false
        if (command.command == PERFORM_REFACTOR_COMMAND) {
            val firstArg = command.arguments?.firstOrNull()
            val refactorKind = (firstArg as? JsonPrimitive)?.asString
                ?: firstArg?.toString()?.removeSurrounding("\"")
            if (refactorKind != null && refactorKind in targetRefactoringKinds) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun showCannotRefactorErrorHint(
        project: Project,
        editor: Editor,
        @NlsContexts.DialogTitle errorTitle: String,
    ) {
        val message = RefactoringBundle.getCannotRefactorMessage(null)
        CommonRefactoringUtil.showErrorHint(project, editor, message, errorTitle, null)
    }
}
