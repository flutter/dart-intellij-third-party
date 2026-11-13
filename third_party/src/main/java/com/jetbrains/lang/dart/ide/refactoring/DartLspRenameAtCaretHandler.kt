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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
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
        renameWithInputDialog(project, virtualFile, offset, target.placeholder)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        // Only invoked from an editor, where there is no element to rename.
    }

    private fun showErrorHint(project: Project, editor: Editor, message: String) =
        CommonRefactoringUtil.showErrorHint(project, editor, message, CommonBundle.getErrorTitle(), null)
}

/**
 * Asks for the new name, starting with the server's [placeholder], and applies the server's rename edits for [offset] in
 * [file]. For renames that IntelliJ's rename UI can't show, because it would offer a different old name.
 */
internal fun renameWithInputDialog(project: Project, file: VirtualFile, offset: Int, placeholder: String) {
    val newName = Messages.showInputDialog(
        project, DartBundle.message("rename.lsp.new.name"), DartBundle.message("rename.lsp.dialog.title"), null,
        placeholder, null,
    ) ?: return

    val requests = DartLspRenameRequests.getInstance(project)
    val edits = when (val result = runRenameRequestUnderProgress(project) { requests.rename(file, offset, newName) }) {
        is DartLspRenameResult.Success -> result.value
        is DartLspRenameResult.Failure -> return showRenameErrorDialog(project, result.message)
        DartLspRenameResult.NoServer -> return showRenameErrorDialog(project, DartBundle.message("rename.lsp.server.not.ready"))
        null -> return
    }
    if (edits.isEmpty()) return

    // With the files, the command first makes sure they are writable (read-only status, VCS checkout).
    val psiManager = PsiManager.getInstance(project)
    val files = edits.map { it.file }.distinct().mapNotNull { psiManager.findFile(it) }
    WriteCommandAction.runWriteCommandAction(project, DartBundle.message("progress.title.rename"), null, {
        applyFileTextEdits(edits)
    }, *files.toTypedArray())
}

private fun showRenameErrorDialog(project: Project, message: String) =
    Messages.showErrorDialog(project, message, DartBundle.message("rename.lsp.dialog.title"))
