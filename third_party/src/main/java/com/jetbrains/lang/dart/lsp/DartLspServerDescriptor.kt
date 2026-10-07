/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspCommunicationChannel
import com.intellij.platform.dartlsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.dartlsp.api.customization.LspCallHierarchyCustomizer
import com.intellij.platform.dartlsp.api.customization.LspCallHierarchyDisabled
import com.intellij.platform.dartlsp.api.customization.LspCallHierarchySupport
import com.intellij.platform.dartlsp.api.customization.LspCodeActionsCustomizer
import com.intellij.platform.dartlsp.api.customization.LspCodeActionsDisabled
import com.intellij.platform.dartlsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.dartlsp.api.customization.LspCodeLensDisabled
import com.intellij.platform.dartlsp.api.customization.LspCommandsCustomizer
import com.intellij.platform.dartlsp.api.customization.LspCommandsDisabled
import com.intellij.platform.dartlsp.api.customization.LspCommandsSupport
import com.intellij.platform.dartlsp.api.customization.LspCompletionCustomizer
import com.intellij.platform.dartlsp.api.customization.LspCompletionDisabled
import com.intellij.platform.dartlsp.api.customization.LspCompletionSupport
import com.intellij.platform.dartlsp.api.customization.LspCustomization
import com.intellij.platform.dartlsp.api.customization.LspDiagnosticsCustomizer
import com.intellij.platform.dartlsp.api.customization.LspDiagnosticsDisabled
import com.intellij.platform.dartlsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.dartlsp.api.customization.LspDocumentColorDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentHighlightsCustomizer
import com.intellij.platform.dartlsp.api.customization.LspDocumentHighlightsSupport
import com.intellij.platform.dartlsp.api.customization.LspDocumentLinkDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentSymbolCustomizer
import com.intellij.platform.dartlsp.api.customization.LspDocumentSymbolDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentSymbolSupport
import com.intellij.platform.dartlsp.api.customization.LspFindReferencesCustomizer
import com.intellij.platform.dartlsp.api.customization.LspFindReferencesDisabled
import com.intellij.platform.dartlsp.api.customization.LspFindReferencesSupport
import com.intellij.platform.dartlsp.api.customization.LspFoldingRangeDisabled
import com.intellij.platform.dartlsp.api.customization.LspFormattingDisabled
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionCustomizer
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionSupport
import com.intellij.platform.dartlsp.api.customization.LspGoToTypeDefinitionSupport
import com.intellij.platform.dartlsp.api.customization.LspHoverSupport
import com.intellij.platform.dartlsp.api.customization.LspInlayHintCustomizer
import com.intellij.platform.dartlsp.api.customization.LspInlayHintDisabled
import com.intellij.platform.dartlsp.api.customization.LspOptimizeImportsDisabled
import com.intellij.platform.dartlsp.api.customization.LspRenameDisabled
import com.intellij.platform.dartlsp.api.customization.LspSelectionRangeDisabled
import com.intellij.platform.dartlsp.api.customization.LspSemanticTokensCustomizer
import com.intellij.platform.dartlsp.api.customization.LspSemanticTokensDisabled
import com.intellij.platform.dartlsp.api.customization.LspSemanticTokensSupport
import com.intellij.platform.dartlsp.api.customization.LspSignatureHelpDisabled
import com.intellij.platform.dartlsp.api.customization.LspTypeHierarchyCustomizer
import com.intellij.platform.dartlsp.api.customization.LspTypeHierarchyDisabled
import com.intellij.platform.dartlsp.api.customization.LspTypeHierarchySupport
import com.intellij.platform.dartlsp.api.customization.LspWorkspaceSymbolDisabled
import com.intellij.psi.PsiFile
import com.intellij.util.io.URLUtil
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.highlight.DartSyntaxHighlighterColors
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.SemanticTokenModifiers
import org.eclipse.lsp4j.SemanticTokenTypes
import javax.swing.Icon

/**
 * Configuration descriptor that defines how the JetBrains LSP client communicates with the Dart Bridge server.
 *
 * This descriptor specifies:
 * 1. Which files are supported (only `.dart` files).
 * 2. The communication channel to use (a TCP Socket channel using the dynamically allocated port of [DartBridgeLspServerManager]).
 *    `startProcess = false` tells the platform that the server is already running internally, so it only needs to connect.
 * 3. The LSP feature customizations (e.g. enabling/disabling hover support dynamically based on settings).
 */
class DartLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Dart (Bridge)") {

    override val lsp4jServerClass: Class<out Lsp4jServer> = DartLanguageServer::class.java

    override fun isSupportedFile(file: VirtualFile): Boolean {
        return DartAnalysisServerService.isFileNameRespectedByAnalysisServer(file.name)
    }

    /**
     * Re-implements [LspServerDescriptor.getFileUri] to preserve uppercase Windows drive letters (`C:`).
     *
     * By default, the underlying JetBrains [LspServerDescriptor.getFileUri] lowercases drive letters (`c%3A`)
     * to match VS Code conventions. However, legacy server messages sent by the Dart plugin (such as file
     * synchronizations via `analysis.updateContent`) use uppercase drive letters derived from IntelliJ VFS paths.
     * Because the Analysis Server evaluates path strings case-sensitively, a casing discrepancy between legacy
     * and LSP-over-legacy requests causes the server to treat the same file as two distinct contexts, leading
     * to duplicate analysis errors and sticky markers (see dart-lang/sdk#63819).
     */
    override fun getFileUri(file: VirtualFile): String {
        val escapedPath = URLUtil.encodePath(getFilePath(file))
        val url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, escapedPath)
        val uri = VfsUtil.toUri(url)?.toString() ?: url
        val prefix = "file:///"
        if (uri.startsWith(prefix) && OSAgnosticPathUtil.startsWithWindowsDrive(uri.substring(prefix.length))) {
            return prefix + uri[prefix.length].uppercase() + uri.substring(prefix.length + 1)
        }
        return uri
    }

    override val lspCommunicationChannel: LspCommunicationChannel
        get() {
            val manager = project.getService(DartBridgeLspServerManager::class.java)
            val port = manager.port
            // The JetBrains LSP framework calls this getter to determine how to connect to the server.
            // We return a Socket channel pointing to the random port allocated by our Bridge Manager.
            // 'startProcess = false' tells the platform that the server process is already running 
            // (managed by DartBridgeLspServerManager) and it should only establish a socket connection.
            if (port == -1) {
                return LspCommunicationChannel.Socket(0, startProcess = false)
            }
            return LspCommunicationChannel.Socket(port, startProcess = false)
        }

    override val lspCustomization: LspCustomization = object : LspCustomization() {
        override val hoverCustomizer = LspHoverSupport()
        
        override val goToDefinitionCustomizer: LspGoToDefinitionCustomizer
            get() = if (DartAnalysisServerService.isLspNavigationEnabled(project)) {
                LspGoToDefinitionSupport()
            } else {
                LspGoToDefinitionDisabled
            }
        override val goToTypeDefinitionCustomizer = LspGoToTypeDefinitionSupport()
        override val completionCustomizer: LspCompletionCustomizer
            get() = if (DartAnalysisServerService.isLspCompletionEnabled(project)) {
                DartLspCompletionSupport
            } else {
                LspCompletionDisabled
            }
        override val semanticTokensCustomizer: LspSemanticTokensCustomizer
            get() = if (DartAnalysisServerService.isLspHighlightingEnabled(project)) {
                DartLspSemanticTokensSupport
            } else {
                LspSemanticTokensDisabled
            }
        override val diagnosticsCustomizer: LspDiagnosticsCustomizer
            get() = if (DartAnalysisServerService.isLspPublishDiagnosticsEnabled(project)) {
                LspDiagnosticsSupport()
            } else {
                LspDiagnosticsDisabled
            }
        override val codeActionsCustomizer: LspCodeActionsCustomizer
            get() = if (DartConfigurable.isLspCodeActionsEnabled(project)) {
                LspCodeActionsSupport()
            } else {
                LspCodeActionsDisabled
            }
        override val commandsCustomizer: LspCommandsCustomizer
            get() = if (DartConfigurable.isLspCodeActionsEnabled(project)) {
                LspCommandsSupport()
            } else {
                LspCommandsDisabled
            }
        override val formattingCustomizer = LspFormattingDisabled
        override val findReferencesCustomizer: LspFindReferencesCustomizer
            get() = if (DartAnalysisServerService.isLspReferencesEnabled(project)) {
                LspFindReferencesSupport()
            } else {
                LspFindReferencesDisabled
            }
        override val optimizeImportsCustomizer = LspOptimizeImportsDisabled
        override val documentColorCustomizer = LspDocumentColorDisabled
        override val documentLinkCustomizer = LspDocumentLinkDisabled
        override val foldingRangeCustomizer = LspFoldingRangeDisabled
        override val inlayHintCustomizer: LspInlayHintCustomizer
            get() = if (DartAnalysisServerService.isLspInlayHintsEnabled(project)) {
                DartLspInlayHintSupport(project)
            } else {
                LspInlayHintDisabled
            }
        override val documentHighlightsCustomizer: LspDocumentHighlightsCustomizer =
            object : LspDocumentHighlightsSupport() {
                // The default implementation only serves plain-text/TextMate files.
                override fun shouldAskServerForDocumentHighlights(psiFile: PsiFile): Boolean = true
            }
        override val signatureHelpCustomizer = LspSignatureHelpDisabled
        override val documentSymbolCustomizer: LspDocumentSymbolCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                LspDocumentSymbolSupport()
            } else {
                LspDocumentSymbolDisabled
            }
        override val workspaceSymbolCustomizer = LspWorkspaceSymbolDisabled
        override val callHierarchyCustomizer: LspCallHierarchyCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                LspCallHierarchySupport()
            } else {
                LspCallHierarchyDisabled
            }
        override val typeHierarchyCustomizer: LspTypeHierarchyCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                LspTypeHierarchySupport()
            } else {
                LspTypeHierarchyDisabled
            }

        override val selectionRangeCustomizer = LspSelectionRangeDisabled
        override val codeLensCustomizer = LspCodeLensDisabled
        override val renameCustomizer = LspRenameDisabled
    }
}

object DartLspCompletionSupport : LspCompletionSupport() {
    public override fun getIcon(item: CompletionItem): Icon? = when (item.kind) {
        CompletionItemKind.Constructor -> AllIcons.Nodes.ClassInitializer
        CompletionItemKind.Function -> AllIcons.Nodes.Lambda
        else -> super.getIcon(item)
    }
}

object DartLspSemanticTokensSupport : LspSemanticTokensSupport() {
    override fun shouldAskServerForSemanticTokens(psiFile: PsiFile): Boolean {
        return psiFile.fileType == DartFileType.INSTANCE
    }

    /**
     * Default fallback token types matching Dart Analysis Server's standard legend.
     * In Dart SDK 3.14.0-307.0.dev+, the active legend used to decode tokens is dynamically provided
     * by DAS via server.setClientCapabilities and populated in DartBridgeLspServer.initialize.
     */
    val DEFAULT_TOKEN_TYPES: List<String> = listOf(
        "annotation",
        SemanticTokenTypes.Class,
        SemanticTokenTypes.Comment,
        SemanticTokenTypes.Method,
        SemanticTokenTypes.Variable,
        SemanticTokenTypes.Parameter,
        SemanticTokenTypes.Enum,
        SemanticTokenTypes.EnumMember,
        SemanticTokenTypes.Type,
        "source",
        SemanticTokenTypes.Property,
        SemanticTokenTypes.Keyword,
        "label",
        SemanticTokenTypes.Namespace,
        "boolean",
        SemanticTokenTypes.Number,
        SemanticTokenTypes.String,
        SemanticTokenTypes.Function,
        SemanticTokenTypes.TypeParameter
    )

    /**
     * Default fallback token modifiers matching Dart Analysis Server's standard legend.
     * In Dart SDK 3.14.0-307.0.dev+, the active legend used to decode tokens is dynamically provided
     * by DAS via server.setClientCapabilities and populated in DartBridgeLspServer.initialize.
     */
    val DEFAULT_TOKEN_MODIFIERS: List<String> = listOf(
        SemanticTokenModifiers.Documentation,
        "constructor",
        SemanticTokenModifiers.Declaration,
        "importPrefix",
        "instance",
        SemanticTokenModifiers.Static,
        "escape",
        "annotation",
        "control",
        "label",
        "interpolation",
        "source",
        "void",
        "wildcard"
    )

    override val tokenTypes: List<String> = DEFAULT_TOKEN_TYPES

    override val tokenModifiers: List<String> = DEFAULT_TOKEN_MODIFIERS

    override fun getTextAttributesKey(tokenType: String, modifiers: List<String>): TextAttributesKey? {
        val isDecl = modifiers.contains(SemanticTokenModifiers.Declaration)
        val isStatic = modifiers.contains(SemanticTokenModifiers.Static)
        val isInstance = modifiers.contains("instance")

        if (modifiers.contains("annotation") || tokenType == "annotation" || tokenType == SemanticTokenTypes.Decorator) {
            return DartSyntaxHighlighterColors.ANNOTATION
        }

        return when (tokenType) {
            SemanticTokenTypes.Class,
            SemanticTokenTypes.Interface,
            SemanticTokenTypes.Struct -> when {
                modifiers.contains("constructor") -> DartSyntaxHighlighterColors.CONSTRUCTOR
                else -> DartSyntaxHighlighterColors.CLASS
            }

            SemanticTokenTypes.Enum -> DartSyntaxHighlighterColors.ENUM
            SemanticTokenTypes.EnumMember -> DartSyntaxHighlighterColors.ENUM_CONSTANT
            SemanticTokenTypes.TypeParameter -> DartSyntaxHighlighterColors.TYPE_PARAMETER
            SemanticTokenTypes.Type -> DartSyntaxHighlighterColors.TYPE_ALIAS

            SemanticTokenTypes.Method -> when {
                modifiers.contains("constructor") -> DartSyntaxHighlighterColors.CONSTRUCTOR
                isStatic -> if (isDecl) DartSyntaxHighlighterColors.STATIC_METHOD_DECLARATION else DartSyntaxHighlighterColors.STATIC_METHOD_REFERENCE
                isInstance -> if (isDecl) DartSyntaxHighlighterColors.INSTANCE_METHOD_DECLARATION else DartSyntaxHighlighterColors.INSTANCE_METHOD_REFERENCE
                else -> if (isDecl) DartSyntaxHighlighterColors.INSTANCE_METHOD_DECLARATION else DartSyntaxHighlighterColors.INSTANCE_METHOD_REFERENCE
            }

            SemanticTokenTypes.Function -> when {
                isStatic -> if (isDecl) DartSyntaxHighlighterColors.TOP_LEVEL_FUNCTION_DECLARATION else DartSyntaxHighlighterColors.TOP_LEVEL_FUNCTION_REFERENCE
                else -> if (isDecl) DartSyntaxHighlighterColors.LOCAL_FUNCTION_DECLARATION else DartSyntaxHighlighterColors.LOCAL_FUNCTION_REFERENCE
            }

            SemanticTokenTypes.Property -> when {
                isStatic -> if (isDecl) DartSyntaxHighlighterColors.STATIC_FIELD_DECLARATION else DartSyntaxHighlighterColors.STATIC_GETTER_REFERENCE
                isInstance -> if (isDecl) DartSyntaxHighlighterColors.INSTANCE_FIELD_DECLARATION else DartSyntaxHighlighterColors.INSTANCE_GETTER_REFERENCE
                else -> if (isDecl) DartSyntaxHighlighterColors.TOP_LEVEL_GETTER_DECLARATION else DartSyntaxHighlighterColors.TOP_LEVEL_GETTER_REFERENCE
            }

            SemanticTokenTypes.Variable -> when {
                modifiers.contains("importPrefix") -> DartSyntaxHighlighterColors.IMPORT_PREFIX
                isStatic -> DartSyntaxHighlighterColors.STATIC_FIELD_DECLARATION
                isInstance -> if (isDecl) DartSyntaxHighlighterColors.INSTANCE_FIELD_DECLARATION else DartSyntaxHighlighterColors.INSTANCE_FIELD_REFERENCE
                isDecl -> DartSyntaxHighlighterColors.LOCAL_VARIABLE_DECLARATION
                else -> DartSyntaxHighlighterColors.LOCAL_VARIABLE_REFERENCE
            }

            SemanticTokenTypes.Parameter -> if (isDecl) DartSyntaxHighlighterColors.PARAMETER_DECLARATION else DartSyntaxHighlighterColors.PARAMETER_REFERENCE
            "annotation", SemanticTokenTypes.Decorator -> DartSyntaxHighlighterColors.ANNOTATION
            "label" -> DartSyntaxHighlighterColors.LABEL
            SemanticTokenTypes.Namespace -> DartSyntaxHighlighterColors.LIBRARY_NAME
            SemanticTokenTypes.Keyword, "boolean" -> DartSyntaxHighlighterColors.KEYWORD
            SemanticTokenTypes.String -> if (modifiers.contains("escape")) DartSyntaxHighlighterColors.VALID_STRING_ESCAPE else DartSyntaxHighlighterColors.STRING
            SemanticTokenTypes.Comment -> if (modifiers.contains(SemanticTokenModifiers.Documentation)) DartSyntaxHighlighterColors.DOC_COMMENT else DartSyntaxHighlighterColors.LINE_COMMENT
            SemanticTokenTypes.Number -> DartSyntaxHighlighterColors.NUMBER
            SemanticTokenTypes.Operator -> DartSyntaxHighlighterColors.OPERATION_SIGN
            "source" -> DartSyntaxHighlighterColors.IDENTIFIER
            else -> super.getTextAttributesKey(tokenType, modifiers)
        }
    }
}
