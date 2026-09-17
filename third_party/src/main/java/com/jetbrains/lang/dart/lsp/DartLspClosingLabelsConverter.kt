// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.dartlsp.util.getOffsetInDocument
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.DartLocalFileInfo
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import com.jetbrains.lang.dart.analyzer.DartServerData.DartClosingLabel
import org.eclipse.lsp4j.Range

data class DartPublishClosingLabelsParams(
    val uri: String,
    val labels: List<DartLspClosingLabel> = emptyList()
)

data class DartLspClosingLabel(
    val label: String,
    val range: Range
)

object DartLspClosingLabelsConverter {
    fun convertClosingLabels(
        project: Project,
        uri: String,
        lspLabels: List<DartLspClosingLabel>?
    ): List<DartClosingLabel> {
        if (project.isDisposed || lspLabels.isNullOrEmpty()) return emptyList()

        val fileInfo = getDartFileInfo(project, uri)

        return ApplicationManager.getApplication().runReadAction(Computable {
            val vFile = fileInfo.findFile()
                ?: (fileInfo as? DartLocalFileInfo)?.let {
                    VirtualFileManager.getInstance().findFileByUrl("temp://${it.filePath}")
                }
                ?: return@Computable emptyList()

            if (!vFile.isValid) return@Computable emptyList()

            val document = FileDocumentManager.getInstance().getDocument(vFile) ?: return@Computable emptyList()

            lspLabels.mapNotNull { lspLabel ->
                if (lspLabel.label.isBlank()) return@mapNotNull null
                val startDocOffset = getOffsetInDocument(document, lspLabel.range.start) ?: return@mapNotNull null
                val endDocOffset = getOffsetInDocument(document, lspLabel.range.end) ?: return@mapNotNull null
                val length = endDocOffset - startDocOffset

                if (length > 0) {
                    DartClosingLabel(startDocOffset, length, lspLabel.label)
                } else {
                    null
                }
            }
        })
    }
}