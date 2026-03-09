// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import org.dartlang.analysis.server.protocol.LinkedEditGroup
import org.dartlang.analysis.server.protocol.LinkedEditSuggestion
import org.dartlang.analysis.server.protocol.SourceChange
import org.dartlang.analysis.server.protocol.SourceEdit
import org.dartlang.analysis.server.protocol.SourceFileEdit
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.math.min
import org.dartlang.analysis.server.protocol.Position as ServerPosition

internal class LspSourceChangeConverter(
    private val project: Project,
) {
    fun toRange(
        fileUri: String,
        offset: Int,
        length: Int,
    ): Range {
        val document = getDocument(fileUri)
        val start = toPosition(document, offset)
        val end = toPosition(document, offset + length)
        return Range(start, end)
    }

    fun toSourceChange(
        action: CodeAction,
        workspaceEdit: WorkspaceEdit,
    ): SourceChange? = toSourceChange(action.title, action.command?.command ?: action.kind ?: action.title, workspaceEdit)

    fun toSourceChange(
        message: String,
        id: String?,
        workspaceEdit: WorkspaceEdit,
    ): SourceChange? {
        val fileEdits = mutableListOf<SourceFileEdit>()
        val linkedEditGroups = mutableListOf<LinkedEditGroup>()
        var selection: ServerPosition? = null
        var selectionLength: Int? = null
        workspaceEdit.changes?.forEach { (fileUri, textEdits) ->
            val convertedEdit = toSourceFileEdit(fileUri, textEdits) ?: return null
            fileEdits.add(convertedEdit.fileEdit)
            linkedEditGroups.addAll(convertedEdit.linkedEditGroups)
            if (selection == null && convertedEdit.selection != null) {
                selection = convertedEdit.selection
                selectionLength = convertedEdit.selectionLength
            }
        }
        workspaceEdit.documentChanges?.forEach { change ->
            if (!change.isLeft) {
                return null
            }
            val documentChange = change.left ?: return null
            val convertedEdit = toSourceFileEdit(documentChange) ?: return null
            fileEdits.add(convertedEdit.fileEdit)
            linkedEditGroups.addAll(convertedEdit.linkedEditGroups)
            if (selection == null && convertedEdit.selection != null) {
                selection = convertedEdit.selection
                selectionLength = convertedEdit.selectionLength
            }
        }
        if (fileEdits.isEmpty()) {
            return null
        }
        return SourceChange(message, fileEdits, linkedEditGroups, selection, selectionLength, id)
    }

    fun hasTranslatableChanges(workspaceEdit: WorkspaceEdit?): Boolean {
        if (workspaceEdit == null) return false
        if (!workspaceEdit.changes.isNullOrEmpty()) return true
        return workspaceEdit.documentChanges?.all(Either<TextDocumentEdit, *>::isLeft) == true &&
            !workspaceEdit.documentChanges.isNullOrEmpty()
    }

    private fun toSourceFileEdit(documentEdit: TextDocumentEdit): ConvertedSourceFileEdit? {
        val fileUri = documentEdit.textDocument.uri ?: return null
        return toSourceFileEdit(fileUri, documentEdit.edits)
    }

    private fun toSourceFileEdit(
        fileUri: String,
        textEdits: List<TextEdit>,
    ): ConvertedSourceFileEdit? {
        val document = getDocument(fileUri)
        val convertedEdits =
            textEdits.map { textEdit ->
                toConvertedTextEdit(fileUri, document, textEdit) ?: return null
            }
        val sourceEdits = convertedEdits.map { it.sourceEdit }.sortedByDescending(SourceEdit::getOffset)
        val selection = convertedEdits.firstNotNullOfOrNull(ConvertedTextEdit::selection)
        val selectionLength = convertedEdits.firstOrNull { it.selection != null }?.selectionLength
        val linkedEditGroups = convertedEdits.flatMap(ConvertedTextEdit::linkedEditGroups)
        return ConvertedSourceFileEdit(
            SourceFileEdit(fileUri, getFileStamp(fileUri), sourceEdits),
            selection,
            selectionLength,
            linkedEditGroups,
        )
    }

    private fun toConvertedTextEdit(
        fileUri: String,
        document: Document,
        textEdit: TextEdit,
    ): ConvertedTextEdit? {
        val startOffset = getOffset(document, textEdit.range.start) ?: return null
        val endOffset = getOffset(document, textEdit.range.end) ?: return null
        val newText = textEdit.newText ?: ""
        val decodedSnippet = LspSnippetTextEditDecoder.decode(newText)
        val replacement = decodedSnippet?.text ?: newText
        val sourceEdit = SourceEdit(startOffset, endOffset - startOffset, replacement, null, null)
        if (decodedSnippet == null || decodedSnippet.placeholders.isEmpty()) {
            return ConvertedTextEdit(sourceEdit, null, null, emptyList())
        }

        val linkedEditGroups = mutableListOf<LinkedEditGroup>()
        var selection: ServerPosition? = null
        var selectionLength: Int? = null
        decodedSnippet.placeholders.groupBy(DecodedSnippetPlaceholder::number).forEach { (number, placeholders) ->
            val sortedPlaceholders = placeholders.sortedBy(DecodedSnippetPlaceholder::offset)
            val choices = sortedPlaceholders.firstNotNullOfOrNull(DecodedSnippetPlaceholder::choices)
            if (number == 0 && shouldUsePlaceholderGroupAsSelection(sortedPlaceholders, choices)) {
                val placeholder = sortedPlaceholders.first()
                selection = ServerPosition(fileUri, startOffset + placeholder.offset)
                selectionLength = placeholder.length
            } else {
                linkedEditGroups.add(
                    LinkedEditGroup(
                        sortedPlaceholders.map { placeholder ->
                            ServerPosition(fileUri, startOffset + placeholder.offset)
                        },
                        sortedPlaceholders.first().length,
                        choices
                            .orEmpty()
                            .map { choice ->
                                LinkedEditSuggestion(choice, "")
                            },
                    ),
                )
            }
        }

        return ConvertedTextEdit(sourceEdit, selection, selectionLength, linkedEditGroups)
    }

    private fun shouldUsePlaceholderGroupAsSelection(
        placeholders: List<DecodedSnippetPlaceholder>,
        choices: List<String>?,
    ): Boolean = placeholders.size == 1 && placeholders.first().length == 0 && choices.isNullOrEmpty()

    private data class ConvertedSourceFileEdit(
        val fileEdit: SourceFileEdit,
        val selection: ServerPosition?,
        val selectionLength: Int?,
        val linkedEditGroups: List<LinkedEditGroup>,
    )

    private data class ConvertedTextEdit(
        val sourceEdit: SourceEdit,
        val selection: ServerPosition?,
        val selectionLength: Int?,
        val linkedEditGroups: List<LinkedEditGroup>,
    )

    private fun getDocument(fileUri: String): Document {
        val virtualFile =
            getDartFileInfo(project, fileUri).findFile()
                ?: throw IllegalStateException("Unable to resolve Dart file for $fileUri")
        return FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: throw IllegalStateException("Unable to load document for $fileUri")
    }

    private fun getFileStamp(fileUri: String): Long {
        return getDartFileInfo(project, fileUri).findFile()?.modificationStamp ?: 0L
    }

    private fun toPosition(
        document: Document,
        offset: Int,
    ): Position {
        val clampedOffset = offset.coerceIn(0, document.textLength)
        val line = document.getLineNumber(clampedOffset)
        val character = clampedOffset - document.getLineStartOffset(line)
        return Position(line, character)
    }

    private fun getOffset(
        document: Document,
        position: Position?,
    ): Int? {
        if (position == null) return null

        val lineCount = document.lineCount
        val line = position.line
        var character = position.character
        if (line == lineCount && character == 0) return document.textLength
        if (line < 0 || line >= lineCount || character < 0) return null

        val lineStartOffset = document.getLineStartOffset(line)
        val lineEndOffset = document.getLineEndOffset(line)
        character = min(character, lineEndOffset - lineStartOffset)

        val offset = lineStartOffset + character
        return offset.takeIf { it <= document.textLength }
    }
}
