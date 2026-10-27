/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.application.ApplicationManager
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
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind

/**
 * Routes Dart refactoring actions (Extract Method, Extract Local Variable, Inline)
 * to LSP `textDocument/codeAction` requests when LSP code actions are enabled.
 */
object DartLspRefactoringHelper {
    const val EXTRACT_METHOD_TITLE: String = "Extract Method"
    const val EXTRACT_LOCAL_VARIABLE_TITLE: String = "Extract Local Variable"
    const val INLINE_LOCAL_VARIABLE_TITLE: String = "Inline Local Variable"
    const val INLINE_METHOD_TITLE: String = "Inline Method"

    @JvmStatic
    fun performExtractMethod(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val selection = editor.selectionModel
        performRefactoring(
            project = project,
            editor = editor,
            file = file,
            offset = selection.selectionStart,
            length = selection.selectionEnd - selection.selectionStart,
            codeActionKind = CodeActionKind.RefactorExtract,
            targetTitles = setOf(EXTRACT_METHOD_TITLE),
            progressTitle = DartBundle.message("progress.title.extract.method"),
            errorTitle = DartBundle.message("dart.refactoring.extract.method.error"),
        )
    }

    @JvmStatic
    fun performExtractLocalVariable(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val selection = editor.selectionModel
        performRefactoring(
            project = project,
            editor = editor,
            file = file,
            offset = selection.selectionStart,
            length = selection.selectionEnd - selection.selectionStart,
            codeActionKind = CodeActionKind.RefactorExtract,
            targetTitles = setOf(EXTRACT_LOCAL_VARIABLE_TITLE),
            progressTitle = DartBundle.message("progress.title.extract.local.variable"),
            errorTitle = DartBundle.message("dart.refactoring.extract.local.variable.error"),
        )
    }

    @JvmStatic
    fun performInline(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        performRefactoring(
            project = project,
            editor = editor,
            file = file,
            offset = editor.caretModel.offset,
            length = 0,
            codeActionKind = CodeActionKind.RefactorInline,
            targetTitles = setOf(INLINE_LOCAL_VARIABLE_TITLE, INLINE_METHOD_TITLE),
            progressTitle = RefactoringBundle.message("inline.command"),
            errorTitle = DartBundle.message("dart.refactoring.inline.error"),
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
            showCannotRefactorErrorHint(
                project,
                editor,
                errorTitle,
                DartBundle.message("analysis.server.not.running"),
            )
            return
        }

        val document = editor.document
        val textLength = document.textLength
        val safeOffset = offset.coerceIn(0, textLength)
        val safeLength = length.coerceIn(0, textLength - safeOffset)
        val range = getLsp4jRange(document, safeOffset, safeLength)
        val context = CodeActionContext(emptyList(), listOf(codeActionKind)).apply {
            triggerKind = CodeActionTriggerKind.Invoked
        }
        val params = CodeActionParams(lspServer.getDocumentIdentifier(virtualFile), range, context)

        var serverResponded = false
        var matchingIntention: LspIntentionAction? = null
        val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                val results = lspServer.sendRequestSync { it.textDocumentService.codeAction(params) }
                    ?: return@runProcessWithProgressSynchronously
                serverResponded = true
                val matchingAction = results.asSequence()
                    .mapNotNull { it.right }
                    .firstOrNull { it.disabled == null && it.title in targetTitles }
                    ?: return@runProcessWithProgressSynchronously
                val intention = LspIntentionAction(lspServer, matchingAction)
                ApplicationManager.getApplication().runReadAction {
                    if (intention.isAvailable()) {
                        matchingIntention = intention
                    }
                }
            },
            progressTitle,
            true,
            project,
        )

        if (!completed) return

        if (!serverResponded) {
            showCannotRefactorErrorHint(
                project,
                editor,
                errorTitle,
                DartBundle.message("dart.refactoring.lsp.no.response"),
            )
            return
        }

        val intentionToInvoke = matchingIntention
        if (intentionToInvoke == null) {
            showCannotRefactorErrorHint(project, editor, errorTitle)
            return
        }

        intentionToInvoke.invoke(virtualFile)
    }

    private fun showCannotRefactorErrorHint(
        project: Project,
        editor: Editor,
        @NlsContexts.DialogTitle errorTitle: String,
        @NlsContexts.DialogMessage reason: String? = null,
    ) {
        val message = RefactoringBundle.getCannotRefactorMessage(reason)
        CommonRefactoringUtil.showErrorHint(project, editor, message, errorTitle, null)
    }
}
