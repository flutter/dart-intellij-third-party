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
import org.dartlang.analysis.server.protocol.ClosingLabel
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
        das: DartAnalysisServerService,
        uri: String,
        lspLabels: List<DartLspClosingLabel>?
    ): List<ClosingLabel> {
        if (lspLabels.isNullOrEmpty()) return emptyList()

        val fileInfo = getDartFileInfo(project, uri)

        return ApplicationManager.getApplication().runReadAction(Computable {
            val vFile = fileInfo.findFile()
                ?: (fileInfo as? DartLocalFileInfo)?.let {
                    VirtualFileManager.getInstance().findFileByUrl("temp://${it.filePath}")
                }
                ?: return@Computable emptyList()
            val document = FileDocumentManager.getInstance().getDocument(vFile) ?: return@Computable emptyList()

            lspLabels.mapNotNull { lspLabel ->
                val startDocOffset = getOffsetInDocument(document, lspLabel.range.start) ?: return@mapNotNull null
                val endDocOffset = getOffsetInDocument(document, lspLabel.range.end) ?: return@mapNotNull null

                val offset = das.getOriginalOffset(vFile, startDocOffset)
                val length = (das.getOriginalOffset(vFile, endDocOffset) - offset).coerceAtLeast(0)

                if (length > 0) {
                    ClosingLabel(offset, length, lspLabel.label)
                } else {
                    null
                }
            }
        })
    }
}