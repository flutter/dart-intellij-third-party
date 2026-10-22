/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.CommonBundle
import com.intellij.ide.TitledHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.util.DartResolveUtil

/**
 * Renames over LSP at caret positions where IntelliJ has no element to rename, while [DartLspRename] is enabled:
 * - the `import` keyword: renaming it adds an import prefix (`import 'dart:math' as math;` and `math.max(...)`),
 * - references in comments, which have no PSI references with LSP navigation.
 *
 * Where IntelliJ finds an element, its standard rename UI and [DartLspSymbolRenameProcessor] handle the rename.
 */
class DartLspRenameAtCaretHandler : RenameHandler, TitledHandler {

    override fun getActionTitle(): String = DartBundle.message("rename.lsp.dialog.title")

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val project = editor.project ?: return false
        if (!DartLspRename.isEnabled(project)) return false
        if (CommonDataKeys.PSI_ELEMENT.getData(dataContext) != null) return false
        if (!DartAnalysisServerService.isLocalAnalyzableFile(CommonDataKeys.VIRTUAL_FILE.getData(dataContext))) return false
        val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        return psiFile.findElementAt(editor.caretModel.offset)?.language == DartLanguage.INSTANCE
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        val virtualFile = DartResolveUtil.getRealVirtualFile(file) ?: return
        val offset = editor.caretModel.offset
        val requests = DartLspRenameRequests.getInstance(project)

        val target = when (val result = runRenameRequestUnderProgress(project) { requests.prepareRename(virtualFile, offset) }) {
            is DartLspRenameResult.Success -> result.value
            is DartLspRenameResult.Failure -> return showErrorHint(project, editor, result.message)
            DartLspRenameResult.NoServer -> return showErrorHint(project, editor, DartBundle.message("rename.lsp.server.not.ready"))
            null -> return
        }

        val newName = Messages.showInputDialog(
            project, DartBundle.message("rename.lsp.new.name"), DartBundle.message("rename.lsp.dialog.title"), null,
            target.placeholder, null,
        ) ?: return

        val edits = when (val result = runRenameRequestUnderProgress(project) { requests.rename(virtualFile, offset, newName) }) {
            is DartLspRenameResult.Success -> result.value
            is DartLspRenameResult.Failure -> return showErrorDialog(project, result.message)
            DartLspRenameResult.NoServer -> return showErrorDialog(project, DartBundle.message("rename.lsp.server.not.ready"))
            null -> return
        }
        if (edits.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project, DartBundle.message("progress.title.rename"), null, {
            applyFileTextEdits(edits)
        })
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        // Only invoked from an editor, where there is no element to rename.
    }

    private fun showErrorHint(project: Project, editor: Editor, message: String) =
        CommonRefactoringUtil.showErrorHint(project, editor, message, CommonBundle.getErrorTitle(), null)

    private fun showErrorDialog(project: Project, message: String) =
        Messages.showErrorDialog(project, message, DartBundle.message("rename.lsp.dialog.title"))
}
