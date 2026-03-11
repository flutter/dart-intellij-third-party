// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import org.dartlang.analysis.server.protocol.AddContentOverlay
import org.dartlang.analysis.server.protocol.RemoveContentOverlay
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier

internal class LspDocumentSyncManager(
    private val onDidOpen: (DidOpenTextDocumentParams) -> Unit,
    private val onDidChange: (DidChangeTextDocumentParams) -> Unit,
    private val onDidClose: (DidCloseTextDocumentParams) -> Unit,
    private val syncKindProvider: () -> TextDocumentSyncKind,
    private val languageId: String = "dart",
) {
    private val openDocuments = mutableMapOf<String, OpenDocumentState>()

    private data class OpenDocumentState(
        val version: Int,
        val content: String,
    )

    fun applyOverlay(
        uri: String,
        overlay: Any,
    ) {
        when (overlay) {
            is AddContentOverlay -> applyContent(uri, overlay.content)
            is RemoveContentOverlay -> removeContent(uri)
            else -> throw IllegalArgumentException("Unsupported content overlay type ${overlay::class.java.name} for $uri")
        }
    }

    fun clear() {
        // This manager is cleared only during connection teardown, so the server is
        // already going away and does not need individual didClose notifications.
        openDocuments.clear()
    }

    private fun applyContent(
        uri: String,
        newContent: String,
    ) {
        val currentState = openDocuments[uri]
        if (currentState == null) {
            onDidOpen(
                DidOpenTextDocumentParams(
                    TextDocumentItem(uri, languageId, 1, newContent),
                ),
            )
            openDocuments[uri] = OpenDocumentState(1, newContent)
            return
        }

        if (currentState.content == newContent) return

        val nextVersion = currentState.version + 1
        onDidChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, nextVersion),
                listOf(createChangeEvent(currentState.content, newContent)),
            ),
        )
        openDocuments[uri] = OpenDocumentState(nextVersion, newContent)
    }

    private fun removeContent(uri: String) {
        if (openDocuments.containsKey(uri)) {
            onDidClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri)))
            openDocuments.remove(uri)
        }
    }

    private fun createChangeEvent(
        previousContent: String,
        newContent: String,
    ): TextDocumentContentChangeEvent {
        return if (syncKindProvider() == TextDocumentSyncKind.Incremental) {
            // In incremental mode we currently send a full-document replacement range.
            // This is valid LSP for the compatibility layer and can be optimized away in a native LSP service.
            TextDocumentContentChangeEvent().apply {
                range = fullDocumentRange(previousContent)
                text = newContent
            }
        } else {
            TextDocumentContentChangeEvent(newContent)
        }
    }

    private fun fullDocumentRange(content: String): Range {
        return Range(Position(0, 0), endPosition(content))
    }

    private fun endPosition(content: String): Position {
        var line = 0
        var character = 0
        var index = 0
        while (index < content.length) {
            val ch = content[index]
            if (ch == '\n') {
                line++
                character = 0
            } else if (ch == '\r') {
                line++
                character = 0
                if (index + 1 < content.length && content[index + 1] == '\n') {
                    index++
                }
            } else {
                character++
            }
            index++
        }
        return Position(line, character)
    }
}
