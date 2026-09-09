/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.dartlsp.util.applyTextEdits
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.util.io.URLUtil
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.logging.PluginLogger
import com.jetbrains.lang.dart.lsp.DartLspService
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DartLspRenamePsiElementProcessor : RenamePsiElementProcessor() {

    companion object {
        private val logger = PluginLogger.createLogger(DartLspRenamePsiElementProcessor::class.java)

        @JvmStatic
        fun getFileUri(filePath: String): String {
            val escapedPath = URLUtil.encodePath(FileUtil.toSystemIndependentName(filePath))
            val url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, escapedPath)
            val uri = VfsUtil.toUri(url)?.toString() ?: url
            val prefix = "file:///"
            if (uri.startsWith(prefix) && OSAgnosticPathUtil.startsWithWindowsDrive(uri.substring(prefix.length))) {
                return prefix + uri[prefix.length].uppercase() + uri.substring(prefix.length + 1)
            }
            return uri
        }

        @JvmStatic
        fun applyWorkspaceEdit(project: Project, workspaceEdit: WorkspaceEdit) {
            val documentChanges = workspaceEdit.documentChanges ?: return
            if (documentChanges.isEmpty()) return
            logger.info("applyWorkspaceEdit: ${documentChanges.size} document changes to update")

            val editsToApply = mutableListOf<Pair<Document, List<TextEdit>>>()
            for (either in documentChanges) {
                if (!either.isLeft) continue
                val textDocEdit = either.left
                val uri = textDocEdit.textDocument.uri
                val textEdits = textDocEdit.edits
                val virtualFile = findVirtualFileByUri(uri)
                logger.info("applyWorkspaceEdit for uri: $uri -> resolved VirtualFile: $virtualFile with ${textEdits.size} edits")
                if (virtualFile != null) {
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                    if (document != null) {
                        editsToApply.add(Pair(document, textEdits))
                    } else {
                        logger.warn("Could not obtain Document for $virtualFile")
                    }
                } else {
                    logger.warn("Could not find VirtualFile for URI: $uri")
                }
            }

            if (editsToApply.isEmpty()) return

            WriteCommandAction.runWriteCommandAction(project, "Update Dart Imports", null, Runnable {
                for ((document, textEdits) in editsToApply) {
                    applyTextEdits(document, textEdits)
                    FileDocumentManager.getInstance().saveDocument(document)
                }
            })
        }

        @JvmStatic
        @Throws(TimeoutException::class)
        fun <T> awaitFutureCheckingCanceled(future: CompletableFuture<T>, timeoutSeconds: Long = 10): T? {
            val timeoutMillis = timeoutSeconds * 1000
            val startTime = System.currentTimeMillis()
            while (true) {
                ProgressManager.checkCanceled()
                if (future.isDone) {
                    return future.get()
                }
                if (System.currentTimeMillis() > startTime + timeoutMillis) {
                    throw TimeoutException("Timed out waiting for LSP response after ${timeoutSeconds}s")
                }
                try {
                    return future.get(50, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) {
                    // Continue checking canceled
                }
            }
        }

        private fun findVirtualFileByUri(uri: String): VirtualFile? {
            val vfm = VirtualFileManager.getInstance()
            vfm.findFileByUrl(uri)?.let { return it }
            vfm.refreshAndFindFileByUrl(uri)?.let { return it }
            return runCatching {
                val parsed = URI(uri)
                val path = parsed.path ?: uri.removePrefix("file://")
                vfm.findFileByUrl(VirtualFileManager.constructUrl("file", path))
                    ?: VfsUtil.findFileByIoFile(File(path), true)
            }.getOrNull()
        }
    }

    override fun canProcessElement(element: PsiElement): Boolean {
        val project = element.project
        if (!DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
            return false
        }
        val sdk = DartSdk.getDartSdk(project) ?: return false

        return when (element) {
            is DartFile -> {
                val vFile = element.virtualFile ?: return false
                DartAnalysisServerService.getInstance(project).isInIncludedRoots(vFile)
            }
            is PsiDirectory -> {
                val vFile = element.virtualFile
                DartAnalysisServerService.getInstance(project).isInIncludedRoots(vFile)
            }
            else -> false
        }
    }

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> {
        // Return empty collection so IntelliJ's legacy PSI string slicer does not duplicate edits
        return emptyList()
    }

    override fun prepareRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>
    ) {
        val psiFileSystemItem = element as? PsiFileSystemItem ?: return
        val virtualFile = psiFileSystemItem.virtualFile ?: return
        val project = element.project

        DartAnalysisServerService.getInstance(project).updateFilesContent()

        val oldUri = getFileUri(virtualFile.path)
        val parentPath = virtualFile.parent?.path ?: return
        val newPath = "$parentPath/$newName"
        val newUri = getFileUri(newPath)
        logger.info("prepareRenaming via LSP: oldUri=$oldUri, newUri=$newUri")

        val params = RenameFilesParams(listOf(FileRename(oldUri, newUri)))

        try {
            val workspaceEditFuture = DartLspService.willRenameFiles(project, params)
            val workspaceEdit = awaitFutureCheckingCanceled(workspaceEditFuture, 10)
            logger.info("prepareRenaming willRenameFiles response: $workspaceEdit")
            if (workspaceEdit != null) {
                applyWorkspaceEdit(project, workspaceEdit)
            }
        } catch (e: ProcessCanceledException) {
            logger.info("willRenameFiles was canceled")
            throw e
        } catch (e: TimeoutException) {
            logger.warn("Timeout waiting for willRenameFiles from Dart Analysis Server", e)
        } catch (e: Exception) {
            logger.error("Error executing willRenameFiles for Dart rename", e)
        }
    }
}
