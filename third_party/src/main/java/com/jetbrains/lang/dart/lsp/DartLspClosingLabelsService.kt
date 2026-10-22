/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.lang.dart.analyzer.DartLocalFileInfo
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import org.eclipse.lsp4j.Range
import java.util.concurrent.ConcurrentHashMap

data class DartPublishClosingLabelsParams(
    val uri: String?,
    val labels: List<DartLspClosingLabel>? = emptyList()
)

data class DartLspClosingLabel(
    val label: String?,
    val range: Range?
)

@Service(Service.Level.PROJECT)
class DartLspClosingLabelsService(private val project: Project) {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): DartLspClosingLabelsService =
            project.getService(DartLspClosingLabelsService::class.java)
    }

    private val closingLabelsByFile = ConcurrentHashMap<VirtualFile, List<DartLspClosingLabel>>()

    fun updateClosingLabels(uri: String?, labels: List<DartLspClosingLabel>?) {
        if (project.isDisposed || uri.isNullOrEmpty()) return
        val fileInfo = getDartFileInfo(project, uri)
        val vFile = fileInfo.findFile() ?: (fileInfo as? DartLocalFileInfo) ?.let {
            val path = FileUtil.toSystemIndependentName(it.filePath)
            VirtualFileManager.getInstance().findFileByUrl("temp://$path")
        }?: return
        if (!vFile.isValid) return
        val validLabels =
            labels?.filter { !it.label.isNullOrBlank() && it.range?.end != null }
            ?: emptyList()
        closingLabelsByFile[vFile] = validLabels
        EditorFactory.getInstance().allEditors
            .filter { vFile == it.virtualFile }
            .forEach { editor ->
                DeclarativeInlayHintsPassFactory.scheduleRecompute(editor, project)
            }
    }

    fun getClosingLabels(file: VirtualFile): List<DartLspClosingLabel> {
        return closingLabelsByFile[file] ?: emptyList()
    }

}