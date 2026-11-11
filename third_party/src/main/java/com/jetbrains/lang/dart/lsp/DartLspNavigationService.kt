/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentPositionParams

/** Adapts direct Dart navigation to the public LSP API, independently of gutter hierarchy caches. */
object DartLspNavigationService {
    @JvmStatic
    fun gotoSuper(project: Project, editor: Editor, psiFile: PsiFile) {
        ProgressManager.checkCanceled()
        if (project.isDefault || project.isDisposed) return
        val file = psiFile.virtualFile ?: return
        val server = LspServerManager.getInstance(project)
            .getServersForProvider(DartLspServerSupportProvider::class.java)
            .firstOrNull { it.state == LspServerState.Running && it.descriptor.isSupportedFile(file) } ?: return
        val offset = editor.caretModel.offset
        val document = editor.document
        val line = document.getLineNumber(offset)
        val params = TextDocumentPositionParams(
            server.getDocumentIdentifier(file), Position(line, offset - document.getLineStartOffset(line))
        )
        object : Task.Backgroundable(project, "Go to Super", true) {
            private var destination: OpenFileDescriptor? = null

            override fun run(indicator: ProgressIndicator) {
                // No separate capability exists for this Dart extension. Unsupported requests return no target.
                val location = server.sendRequestSync { (it as DartLanguageServer).getSuper(params) } ?: return
                ProgressManager.checkCanceled()
                destination = runReadAction {
                    val uri = location.uri ?: return@runReadAction null
                    val range = location.range ?: return@runReadAction null
                    val target = server.descriptor.findFileByUri(uri) ?: return@runReadAction null
                    val targetDocument = FileDocumentManager.getInstance().getDocument(target) ?: return@runReadAction null
                    val start = targetDocument.offsetOf(range.start) ?: return@runReadAction null
                    val end = targetDocument.offsetOf(range.end) ?: return@runReadAction null
                    if (end < start) return@runReadAction null
                    OpenFileDescriptor(project, target, start)
                }
            }

            override fun onSuccess() {
                if (project.isDisposed || editor.isDisposed) return
                destination?.let { FileEditorManager.getInstance(project).openTextEditor(it, true) }
            }
        }.queue()
    }

    private fun Document.offsetOf(position: Position?): Int? {
        if (position == null || position.line !in 0 until lineCount || position.character < 0) return null
        val start = getLineStartOffset(position.line)
        if (position.character > getLineEndOffset(position.line) - start) return null
        return start + position.character
    }
}
