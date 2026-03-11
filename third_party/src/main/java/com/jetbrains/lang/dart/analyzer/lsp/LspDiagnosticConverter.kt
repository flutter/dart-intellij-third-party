// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import org.dartlang.analysis.server.protocol.AnalysisError
import org.dartlang.analysis.server.protocol.AnalysisErrorSeverity
import org.dartlang.analysis.server.protocol.AnalysisErrorType
import org.dartlang.analysis.server.protocol.DiagnosticMessage
import org.dartlang.analysis.server.protocol.Location
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticRelatedInformation
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range

internal class LspDiagnosticConverter(
    private val project: Project,
) {
    data class ConvertedDiagnostic(
        val diagnostic: Diagnostic,
        val analysisError: AnalysisError,
    )

    fun toAnalysisErrors(params: PublishDiagnosticsParams): List<AnalysisError> {
        return toConvertedDiagnostics(params).map(ConvertedDiagnostic::analysisError)
    }

    fun toConvertedDiagnostics(params: PublishDiagnosticsParams): List<ConvertedDiagnostic> {
        val fileUri = params.uri ?: return emptyList()
        val documentText = getDocumentText(fileUri) ?: return emptyList()
        return params.diagnostics.orEmpty().mapNotNull { diagnostic ->
            toConvertedDiagnostic(fileUri, documentText, diagnostic)
        }
    }

    private fun toConvertedDiagnostic(
        fileUri: String,
        documentText: DocumentText,
        diagnostic: Diagnostic,
    ): ConvertedDiagnostic? {
        val location = toLocation(fileUri, documentText, diagnostic.range) ?: return null
        val (message, correction) = splitMessageAndCorrection(diagnostic.message)
        val contextMessages =
            diagnostic.relatedInformation
                ?.mapNotNull { toDiagnosticMessage(it) }
                ?.takeIf { it.isNotEmpty() }
        return ConvertedDiagnostic(
            diagnostic,
            AnalysisError(
                toSeverity(diagnostic.severity),
                toType(diagnostic),
                location,
                message,
                correction,
                diagnostic.getCodeAsString() ?: "LSP",
                diagnostic.codeDescription?.href,
                contextMessages,
                null,
            ),
        )
    }

    private fun toDiagnosticMessage(relatedInformation: DiagnosticRelatedInformation): DiagnosticMessage? {
        val relatedLocation = relatedInformation.location ?: return null
        val uri = relatedLocation.uri ?: return null
        val documentText = getDocumentText(uri) ?: return null
        val location = toLocation(uri, documentText, relatedLocation.range) ?: return null
        return DiagnosticMessage(relatedInformation.message, location)
    }

    private fun toLocation(
        fileUri: String,
        documentText: DocumentText,
        range: Range?,
    ): Location? {
        val safeRange = range ?: return null
        val startOffset = toOffset(documentText, safeRange.start) ?: return null
        val endOffset = toOffset(documentText, safeRange.end) ?: return null
        if (endOffset < startOffset) return null

        val startLine = safeRange.start.line + 1
        val startColumn = safeRange.start.character + 1
        val endLine = safeRange.end.line + 1
        val endColumn = safeRange.end.character + 1
        return Location(fileUri, startOffset, endOffset - startOffset, startLine, startColumn, endLine, endColumn)
    }

    private fun toOffset(
        documentText: DocumentText,
        position: Position?,
    ): Int? {
        if (position == null) return null

        val lineCount = documentText.lineCount
        val line = position.line
        var character = position.character
        if (line == lineCount && character == 0) return documentText.textLength
        if (line < 0 || line >= lineCount || character < 0) return null

        val lineStartOffset = documentText.getLineStartOffset(line)
        val lineEndOffset = documentText.getLineEndOffset(line)
        character = character.coerceAtMost(lineEndOffset - lineStartOffset)

        val offset = lineStartOffset + character
        return offset.takeIf { it <= documentText.textLength }
    }

    private fun getDocumentText(fileUri: String): DocumentText? {
        val virtualFile = getDartFileInfo(project, fileUri).findFile() ?: return null
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        if (document != null) {
            return DocumentText.FromDocument(document)
        }
        return DocumentText.FromString(VfsUtilCore.loadText(virtualFile))
    }

    private fun toSeverity(severity: DiagnosticSeverity?): String =
        when (severity) {
            DiagnosticSeverity.Error -> AnalysisErrorSeverity.ERROR
            DiagnosticSeverity.Warning -> AnalysisErrorSeverity.WARNING
            DiagnosticSeverity.Information, DiagnosticSeverity.Hint, null -> AnalysisErrorSeverity.INFO
        }

    private fun toType(diagnostic: Diagnostic): String =
        when (diagnostic.severity) {
            DiagnosticSeverity.Error -> AnalysisErrorType.COMPILE_TIME_ERROR
            DiagnosticSeverity.Warning -> {
                val source = diagnostic.source.orEmpty()
                if (source.contains("lint", ignoreCase = true)) AnalysisErrorType.LINT else AnalysisErrorType.STATIC_WARNING
            }
            DiagnosticSeverity.Information, DiagnosticSeverity.Hint, null -> AnalysisErrorType.HINT
        }

    private fun Diagnostic.getCodeAsString(): String? {
        val diagnosticCode = code ?: return null
        return if (diagnosticCode.isLeft) diagnosticCode.left else diagnosticCode.right?.toString()
    }

    private fun splitMessageAndCorrection(message: String): Pair<String, String?> {
        val lines = StringUtil.splitByLinesDontTrim(message)
        if (lines.size < 2) return message to null

        val primaryMessage = lines.first().trimEnd()
        val correction = lines.drop(1).joinToString("\n").trim()
        if (!looksLikeCorrection(correction)) return message to null

        return primaryMessage to correction
    }

    private fun looksLikeCorrection(text: String): Boolean {
        if (text.isEmpty()) return false

        return text.startsWith("Try ") ||
            text.startsWith("Consider ") ||
            text.startsWith("Remove ") ||
            text.startsWith("Use ") ||
            text.startsWith("Add ") ||
            text.startsWith("Replace ") ||
            text.startsWith("Change ")
    }

    private sealed interface DocumentText {
        val lineCount: Int
        val textLength: Int

        fun getLineStartOffset(line: Int): Int

        fun getLineEndOffset(line: Int): Int

        data class FromDocument(
            private val document: com.intellij.openapi.editor.Document,
        ) : DocumentText {
            override val lineCount: Int get() = document.lineCount
            override val textLength: Int get() = document.textLength

            override fun getLineStartOffset(line: Int): Int = document.getLineStartOffset(line)

            override fun getLineEndOffset(line: Int): Int = document.getLineEndOffset(line)
        }

        data class FromString(
            private val text: String,
        ) : DocumentText {
            private val lineStartOffsets = buildLineStartOffsets(text)

            override val lineCount: Int get() = lineStartOffsets.size
            override val textLength: Int get() = text.length

            override fun getLineStartOffset(line: Int): Int = lineStartOffsets[line]

            override fun getLineEndOffset(line: Int): Int {
                val lineStart = lineStartOffsets[line]
                val nextLineStart = lineStartOffsets.getOrNull(line + 1) ?: text.length
                var lineEnd = nextLineStart
                if (lineEnd > lineStart && text[lineEnd - 1] == '\n') {
                    lineEnd--
                    if (lineEnd > lineStart && text[lineEnd - 1] == '\r') {
                        lineEnd--
                    }
                } else if (lineEnd > lineStart && text[lineEnd - 1] == '\r') {
                    lineEnd--
                }
                return lineEnd
            }

            private fun buildLineStartOffsets(text: String): IntArray {
                val offsets = ArrayList<Int>()
                offsets.add(0)
                var index = 0
                while (index < text.length) {
                    when (text[index]) {
                        '\r' -> {
                            index++
                            if (index < text.length && text[index] == '\n') {
                                index++
                            }
                            offsets.add(index)
                        }

                        '\n' -> {
                            index++
                            offsets.add(index)
                        }

                        else -> index++
                    }
                }
                return offsets.toIntArray()
            }
        }
    }
}
