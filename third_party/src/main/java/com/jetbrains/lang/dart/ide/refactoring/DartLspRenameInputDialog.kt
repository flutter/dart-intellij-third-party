/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.lang.dart.DartBundle

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
