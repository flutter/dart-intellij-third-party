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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.LspServerDescriptor
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.lang.dart.psi.DartComponent
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.Location
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

    @JvmStatic
    fun processImplementations(
        project: Project,
        file: VirtualFile,
        offset: Int,
        scope: SearchScope,
        consumer: Processor<in PsiElement>
    ) {
        ProgressManager.checkCanceled()
        if (project.isDefault || project.isDisposed) return
        val server = LspServerManager.getInstance(project)
            .getServersForProvider(DartLspServerSupportProvider::class.java)
            .firstOrNull { it.state == LspServerState.Running && it.descriptor.isSupportedFile(file) } ?: return
        val capability = server.initializeResult?.capabilities?.implementationProvider ?: return
        if (capability.isLeft && capability.left != true) return
        val params = runReadAction {
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runReadAction null
            if (offset !in 0..document.textLength) return@runReadAction null
            val line = document.getLineNumber(offset)
            ImplementationParams().apply {
                textDocument = server.getDocumentIdentifier(file)
                position = Position(line, offset - document.getLineStartOffset(line))
            }
        } ?: return
        // sendRequestSync handles unavailable/error responses and propagates IDE cancellation.
        val response = server.sendRequestSync { it.textDocumentService.implementation(params) } ?: return
        val locations = if (response.isLeft) response.left else response.right.map {
            Location(it.targetUri, it.targetSelectionRange)
        }
        processLocations(project, server.descriptor, locations, scope, consumer)
    }

    internal fun processLocations(
        project: Project,
        descriptor: LspServerDescriptor,
        locations: List<Location?>,
        scope: SearchScope,
        consumer: Processor<in PsiElement>
    ) {
        ProgressManager.checkCanceled()
        runReadAction {
            val seen = HashSet<PsiElement>()
            for (location in locations) {
                ProgressManager.checkCanceled()
                val uri = location?.uri ?: continue
                val range = location.range ?: continue
                val file = descriptor.findFileByUri(uri) ?: continue
                if (!scope.contains(file)) continue
                val document = FileDocumentManager.getInstance().getDocument(file) ?: continue
                val start = document.offsetOf(range.start) ?: continue
                val end = document.offsetOf(range.end) ?: continue
                if (end < start) continue
                val psiFile = PsiManager.getInstance(project).findFile(file) ?: continue
                val leaf = psiFile.findElementAt(start) ?: continue
                val target = PsiTreeUtil.getParentOfType(leaf, DartComponent::class.java, false) ?: continue
                // A location must identify the declaration or its name, not an arbitrary leaf in its body.
                val nameRange = target.componentName?.textRange
                val inName = nameRange != null && start >= nameRange.startOffset && start < nameRange.endOffset
                if (start != target.textRange.startOffset && !inName) continue
                if (seen.add(target) && !consumer.process(target)) return@runReadAction
            }
        }
    }

    private fun Document.offsetOf(position: Position?): Int? {
        if (position == null || position.line !in 0 until lineCount || position.character < 0) return null
        val start = getLineStartOffset(position.line)
        if (position.character > getLineEndOffset(position.line) - start) return null
        return start + position.character
    }
}
