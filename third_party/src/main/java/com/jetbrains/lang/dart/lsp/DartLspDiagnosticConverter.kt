/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.util.getOffsetInDocument
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.DartLocalFileInfo
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import org.dartlang.analysis.server.protocol.AnalysisError
import org.dartlang.analysis.server.protocol.DiagnosticMessage
import org.dartlang.analysis.server.protocol.Location
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity

object DartLspDiagnosticConverter {

    /**
     * Converts an LSP [Diagnostic] object received from `textDocument/publishDiagnostics`
     * into a legacy DAS [AnalysisError] protocol object so it can be displayed in the
     * Dart Analysis tool window (`DartProblemsView`) and used by Project View decorators.
     */
    fun convertDiagnosticToAnalysisError(
        project: Project,
        das: DartAnalysisServerService,
        uri: String,
        diagnostic: Diagnostic
    ): AnalysisError {
        val severity = when (diagnostic.severity) {
            DiagnosticSeverity.Error -> "ERROR"
            DiagnosticSeverity.Warning -> "WARNING"
            DiagnosticSeverity.Information, DiagnosticSeverity.Hint -> "INFO"
            else -> "ERROR"
        }
        val codeStr = diagnostic.code?.let { if (it.isLeft) it.left else it.right.toString() }
        val type = when {
            codeStr?.equals("todo", ignoreCase = true) == true -> "TODO"
            severity == "ERROR" -> "COMPILE_TIME_ERROR"
            severity == "WARNING" -> "STATIC_WARNING"
            else -> "HINT"
        }
        val fileInfo = getDartFileInfo(project, uri)
        val filePath = if (fileInfo is DartLocalFileInfo) fileInfo.filePath else uri
        val vFile = fileInfo.findFile()
        val document = vFile?.let {
            runReadAction {
                FileDocumentManager.getInstance().getDocument(it)
            }
        }
        val docOffset = if (document != null) {
            getOffsetInDocument(document, diagnostic.range.start) ?: 0
        } else {
            0
        }
        val docEnd = if (document != null) {
            getOffsetInDocument(document, diagnostic.range.end) ?: 0
        } else {
            0
        }
        val offset = das.getOriginalOffset(vFile, docOffset)
        val length = (das.getOriginalOffset(vFile, docEnd) - offset).coerceAtLeast(0)
        val location = Location(
            filePath,
            offset,
            length,
            (diagnostic.range.start.line + 1).coerceAtLeast(1),
            (diagnostic.range.start.character + 1).coerceAtLeast(1),
            (diagnostic.range.end.line + 1).coerceAtLeast(1),
            (diagnostic.range.end.character + 1).coerceAtLeast(1)
        )
        val contextMessages = diagnostic.relatedInformation?.mapNotNull { info ->
            val infoUri = info.location.uri
            val infoFileInfo = getDartFileInfo(project, infoUri)
            val infoFilePath = if (infoFileInfo is DartLocalFileInfo) infoFileInfo.filePath else infoUri
            val infoVFile = infoFileInfo.findFile()
            val infoDoc = infoVFile?.let {
                runReadAction {
                    FileDocumentManager.getInstance().getDocument(it)
                }
            }
            val infoDocOffset = if (infoDoc != null) {
                getOffsetInDocument(infoDoc, info.location.range.start) ?: 0
            } else {
                0
            }
            val infoDocEnd = if (infoDoc != null) {
                getOffsetInDocument(infoDoc, info.location.range.end) ?: 0
            } else {
                0
            }
            val infoOffset = das.getOriginalOffset(infoVFile, infoDocOffset)
            val infoLen = (das.getOriginalOffset(infoVFile, infoDocEnd) - infoOffset).coerceAtLeast(0)
            val infoLocation = Location(
                infoFilePath,
                infoOffset,
                infoLen,
                (info.location.range.start.line + 1).coerceAtLeast(1),
                (info.location.range.start.character + 1).coerceAtLeast(1),
                (info.location.range.end.line + 1).coerceAtLeast(1),
                (info.location.range.end.character + 1).coerceAtLeast(1)
            )
            DiagnosticMessage(info.message, infoLocation)
        }
        val url = diagnostic.codeDescription?.href

        return AnalysisError(
            severity,
            type,
            location,
            diagnostic.message,
            null,
            codeStr,
            url,
            contextMessages,
            false
        )
    }
}
