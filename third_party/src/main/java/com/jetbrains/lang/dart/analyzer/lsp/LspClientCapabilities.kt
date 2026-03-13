// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionKindCapabilities
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.RenameCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities

internal fun createClientCapabilities(): ClientCapabilities {
    val workspaceCapabilities =
        WorkspaceClientCapabilities().apply {
            applyEdit = true
            workspaceFolders = true
            workspaceEdit =
                WorkspaceEditCapabilities().apply {
                    documentChanges = true
                }
            fileOperations =
                FileOperationsWorkspaceCapabilities().apply {
                    willRename = true
                }
        }
    val textDocumentCapabilities =
        TextDocumentClientCapabilities().apply {
            codeAction =
                CodeActionCapabilities().apply {
                    codeActionLiteralSupport =
                        CodeActionLiteralSupportCapabilities(
                            CodeActionKindCapabilities(
                                listOf(
                                    CodeActionKind.QuickFix,
                                    CodeActionKind.Refactor,
                                    CodeActionKind.RefactorExtract,
                                    CodeActionKind.RefactorInline,
                                    CodeActionKind.RefactorRewrite,
                                    CodeActionKind.Source,
                                    CodeActionKind.SourceOrganizeImports,
                                    CodeActionKind.SourceFixAll,
                                ),
                            ),
                        )
                    isPreferredSupport = true
                    disabledSupport = true
                }
            rename =
                RenameCapabilities().apply {
                    prepareSupport = true
                }
        }
    return ClientCapabilities().apply {
        workspace = workspaceCapabilities
        textDocument = textDocumentCapabilities
        experimental =
            mapOf(
                "snippetTextEdit" to true,
            )
    }
}
