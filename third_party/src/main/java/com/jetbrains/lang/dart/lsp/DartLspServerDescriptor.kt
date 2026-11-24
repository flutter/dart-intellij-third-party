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
import com.intellij.platform.dartlsp.api.Lsp4jClient
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspCommunicationChannel
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.LspServerListener
import com.intellij.platform.dartlsp.api.LspServerNotificationsHandler
import com.intellij.platform.dartlsp.api.ProjectWideLspServerDescriptor
import com.jetbrains.lang.dart.analyzer.DartLocalFileInfo
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import com.jetbrains.lang.dart.ide.errorTreeView.DartProblemsView
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
import com.intellij.platform.dartlsp.api.customization.LspFormattingCustomizer
import com.intellij.platform.dartlsp.api.customization.LspFormattingDisabled
import com.intellij.platform.dartlsp.api.customization.LspFormattingSupport
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionCustomizer
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionSupport
import com.intellij.platform.dartlsp.api.customization.LspGoToTypeDefinitionSupport
import com.intellij.platform.dartlsp.api.customization.LspHoverSupport
import com.intellij.platform.dartlsp.api.customization.LspInlayHintCustomizer
import com.intellij.platform.dartlsp.api.customization.LspInlayHintDisabled
import com.intellij.platform.dartlsp.api.customization.LspIntentionAction
import com.intellij.platform.dartlsp.api.customization.LspOptimizeImportsCustomizer
import com.intellij.platform.dartlsp.api.customization.LspOptimizeImportsDisabled
import com.intellij.platform.dartlsp.api.customization.LspOptimizeImportsSupport
import com.intellij.platform.dartlsp.api.customization.LspRenameCustomizer
import com.intellij.platform.dartlsp.api.customization.LspRenameDisabled
import com.intellij.platform.dartlsp.api.customization.LspRenameSupport
import com.intellij.platform.dartlsp.api.customization.LspSelectionRangeDisabled
import com.intellij.platform.dartlsp.api.customization.LspSemanticTokensCustomizer
import com.intellij.platform.dartlsp.api.customization.LspSemanticTokensDisabled
import com.intellij.platform.dartlsp.api.customization.LspSemanticTokensSupport
import com.intellij.platform.dartlsp.api.customization.LspSignatureHelpCustomizer
import com.intellij.platform.dartlsp.api.customization.LspSignatureHelpDisabled
import com.intellij.platform.dartlsp.api.customization.LspSignatureHelpSupport
import com.intellij.platform.dartlsp.api.customization.LspTypeHierarchySupport
import com.intellij.platform.dartlsp.api.customization.LspWorkspaceSymbolDisabled
import com.intellij.psi.PsiFile
import com.intellij.util.io.URLUtil
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.highlight.DartSyntaxHighlighterColors
import com.jetbrains.lang.dart.ide.refactoring.DartLspRename
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.SemanticTokenModifiers
import org.eclipse.lsp4j.SemanticTokenTypes
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import javax.swing.Icon

private const val DART_CONFIGURATION_SECTION = "dart"

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

    override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient =
        DartLsp4jClient(project, handler)

    override val lspServerListener: LspServerListener = object : LspServerListener {
        override fun serverInitialized(params: InitializeResult) {
            // If the Dart Tooling Daemon (DTD) started before the LSP server (or if the LSP server was
            // restarted), connect the newly initialized LSP server to the running DTD instance.
            val dtdUri = project.getServiceIfCreated(DartToolingDaemonService::class.java)?.uri
            if (dtdUri != null) {
                DartLspService.connectToDtd(project, dtdUri)
            }
        }
    }

    override val clientCapabilities: ClientCapabilities
        get() = super.clientCapabilities.apply {
            val ws = workspace ?: WorkspaceClientCapabilities().also { workspace = it }
            ws.fileOperations = FileOperationsWorkspaceCapabilities().apply {
                willRename = true
            }
            val td = textDocument ?: TextDocumentClientCapabilities().also { textDocument = it }
            val pd = td.publishDiagnostics ?: PublishDiagnosticsCapabilities().also { td.publishDiagnostics = it }
            pd.relatedInformation = true
            pd.codeDescriptionSupport = true
            experimental = mapOf("closingLabels" to emptyMap<String, Any>())
        }

    override fun createInitializationOptions(): Any = mapOf("closingLabels" to true)

    override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? {
        if (item.section == DART_CONFIGURATION_SECTION) {
            return DartLspInlayHintsConfiguration.buildDartSection().also { dartSection ->
                DartLspConfigurationSync.getInstance(project).configurationSentToServer(dartSection)
            }
        }
        return super.getWorkspaceConfiguration(item)
    }

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
                DartLspCodeActionsSupport
            } else {
                LspCodeActionsDisabled
            }
        override val commandsCustomizer: LspCommandsCustomizer
            get() = if (DartConfigurable.isLspCodeActionsEnabled(project)) {
                LspCommandsSupport()
            } else {
                LspCommandsDisabled
            }
        override val formattingCustomizer: LspFormattingCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                DartLspFormattingSupport
            } else {
                LspFormattingDisabled
            }
        override val findReferencesCustomizer: LspFindReferencesCustomizer
            get() = if (DartAnalysisServerService.isLspReferencesEnabled(project)) {
                LspFindReferencesSupport()
            } else {
                LspFindReferencesDisabled
            }
        override val optimizeImportsCustomizer: LspOptimizeImportsCustomizer
            get() = if (DartConfigurable.isLspCodeActionsEnabled(project)) {
                LspOptimizeImportsSupport()
            } else {
                LspOptimizeImportsDisabled
            }
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
        override val signatureHelpCustomizer: LspSignatureHelpCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                LspSignatureHelpSupport()
            } else {
                LspSignatureHelpDisabled
            }
        override val documentSymbolCustomizer: LspDocumentSymbolCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                LspDocumentSymbolSupport()
            } else {
                LspDocumentSymbolDisabled
            }
        override val workspaceSymbolCustomizer = LspWorkspaceSymbolDisabled
        override val callHierarchyCustomizer = LspCallHierarchySupport()
        override val typeHierarchyCustomizer = LspTypeHierarchySupport()

        override val selectionRangeCustomizer = LspSelectionRangeDisabled
        override val codeLensCustomizer = LspCodeLensDisabled
        override val renameCustomizer: LspRenameCustomizer
            get() = if (DartLspRename.isEnabled(project)) {
                DartLspRenameSupport
            } else {
                LspRenameDisabled
            }
    }
}

private object DartLspRenameSupport : LspRenameSupport() {
    // The default serves only files without PSI. Dart files are renamed by the server at the caret, so the rename doesn't
    // depend on resolving PSI. Not for pubspec.yaml and analysis_options.yaml: the server has them open but doesn't rename in them.
    override fun shouldRunRename(psiFile: PsiFile): Boolean = psiFile.fileType == DartFileType.INSTANCE
}

private object DartLspFormattingSupport : LspFormattingSupport() {
    override fun shouldFormatThisFileExclusivelyByServer(
        file: VirtualFile,
        ideCanFormatThisFileItself: Boolean,
        serverExplicitlyWantsToFormatThisFile: Boolean
    ): Boolean = file.isInLocalFileSystem && file.extension.equals("dart", ignoreCase = true)
}

object DartLspCodeActionsSupport : LspCodeActionsSupport() {
    /**
     * Excludes whole-file LSP `Source` code actions (`source` and `source.*`, such as
     * `source.sortMembers`, `source.organizeImports`, and `source.fixAll`) from the `Alt+Enter`
     * intention menu (dart-lang/sdk#64939).
     *
     * JetBrains' `LspIntentionActionService` requests `textDocument/codeAction` with `only = null`
     * and only filters out `quickfix*`, so whole-file `Source` actions otherwise crowd cursor-specific
     * assists/refactorings and consume slots in the fixed `LspIntention0..14` pool. Whole-file Organize
     * Imports and Sort Members have dedicated IDE actions under the **Code** menu, and per-diagnostic
     * quick fixes (including "Fix all '<lint>' in file", which uses `quickfix.*`) go through
     * [createQuickFix] and remain untouched.
     */
    override fun createIntentionAction(lspServer: LspServer, codeAction: CodeAction): LspIntentionAction? {
        if (codeAction.kind == CodeActionKind.Source ||
            codeAction.kind?.startsWith("${CodeActionKind.Source}.") == true
        ) {
            return null
        }
        return super.createIntentionAction(lspServer, codeAction)
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

    override fun getTextAttributesKey(tokenType: String, modifiers: List<String>): TextAttributesKey? {
        val isDecl = modifiers.contains(SemanticTokenModifiers.Declaration)
        val isStatic = modifiers.contains(SemanticTokenModifiers.Static)
        val isInstance = modifiers.contains("instance")
        // Custom modifier added in Dart SDK 3.15.0-67.0.dev (dart-lang/sdk#64621).
        val isTearOff = modifiers.contains("tearOff")

        if (modifiers.contains("annotation") || tokenType == "annotation") {
            return DartSyntaxHighlighterColors.ANNOTATION
        }

        return when (tokenType) {
            SemanticTokenTypes.Class -> when {
                modifiers.contains("constructor") -> DartSyntaxHighlighterColors.CONSTRUCTOR
                modifiers.contains("mixin") -> DartSyntaxHighlighterColors.MIXIN
                modifiers.contains("extensionType") -> DartSyntaxHighlighterColors.EXTENSION_TYPE
                modifiers.contains("extension") -> DartSyntaxHighlighterColors.EXTENSION
                else -> DartSyntaxHighlighterColors.CLASS
            }

            SemanticTokenTypes.Enum -> DartSyntaxHighlighterColors.ENUM
            SemanticTokenTypes.EnumMember -> DartSyntaxHighlighterColors.ENUM_CONSTANT
            SemanticTokenTypes.TypeParameter -> DartSyntaxHighlighterColors.TYPE_PARAMETER
            SemanticTokenTypes.Type -> when {
                modifiers.contains("functionTypeAlias") -> DartSyntaxHighlighterColors.FUNCTION_TYPE_ALIAS
                modifiers.contains("typeAlias") -> DartSyntaxHighlighterColors.TYPE_ALIAS
                // Since 3.15.0-67.0.dev every typedef carries an alias modifier, so a bare `type` is `dynamic` (TYPE_NAME_DYNAMIC).
                else -> DartSyntaxHighlighterColors.TYPE_NAME_DYNAMIC
            }

            SemanticTokenTypes.Method -> when {
                modifiers.contains("constructor") ->
                    if (isTearOff) DartSyntaxHighlighterColors.CONSTRUCTOR_TEAR_OFF else DartSyntaxHighlighterColors.CONSTRUCTOR
                isStatic -> when {
                    isTearOff -> DartSyntaxHighlighterColors.STATIC_METHOD_TEAR_OFF
                    isDecl -> DartSyntaxHighlighterColors.STATIC_METHOD_DECLARATION
                    else -> DartSyntaxHighlighterColors.STATIC_METHOD_REFERENCE
                }
                else -> when {
                    isTearOff -> DartSyntaxHighlighterColors.INSTANCE_METHOD_TEAR_OFF
                    isDecl -> DartSyntaxHighlighterColors.INSTANCE_METHOD_DECLARATION
                    else -> DartSyntaxHighlighterColors.INSTANCE_METHOD_REFERENCE
                }
            }

            // DAS marks top-level functions (declarations, references, and tear-offs) with `static`; local functions have no `static`.
            SemanticTokenTypes.Function -> when {
                isStatic -> when {
                    isTearOff -> DartSyntaxHighlighterColors.TOP_LEVEL_FUNCTION_TEAR_OFF
                    isDecl -> DartSyntaxHighlighterColors.TOP_LEVEL_FUNCTION_DECLARATION
                    else -> DartSyntaxHighlighterColors.TOP_LEVEL_FUNCTION_REFERENCE
                }
                isTearOff -> DartSyntaxHighlighterColors.LOCAL_FUNCTION_TEAR_OFF
                isDecl -> DartSyntaxHighlighterColors.LOCAL_FUNCTION_DECLARATION
                else -> DartSyntaxHighlighterColors.LOCAL_FUNCTION_REFERENCE
            }

            // Known gap: DAS emits identical type/modifiers for getters and setters, so setters use the getter keys.
            SemanticTokenTypes.Property -> when {
                isStatic -> if (isDecl) DartSyntaxHighlighterColors.STATIC_GETTER_DECLARATION else DartSyntaxHighlighterColors.STATIC_GETTER_REFERENCE
                isInstance -> if (isDecl) DartSyntaxHighlighterColors.INSTANCE_GETTER_DECLARATION else DartSyntaxHighlighterColors.INSTANCE_GETTER_REFERENCE
                else -> if (isDecl) DartSyntaxHighlighterColors.TOP_LEVEL_GETTER_DECLARATION else DartSyntaxHighlighterColors.TOP_LEVEL_GETTER_REFERENCE
            }

            SemanticTokenTypes.Variable -> when {
                modifiers.contains("importPrefix") -> DartSyntaxHighlighterColors.IMPORT_PREFIX
                // Known gap: static field and top-level variable declarations are both emitted as [declaration, static].
                isStatic -> DartSyntaxHighlighterColors.STATIC_FIELD_DECLARATION
                isInstance -> if (isDecl) DartSyntaxHighlighterColors.INSTANCE_FIELD_DECLARATION else DartSyntaxHighlighterColors.INSTANCE_FIELD_REFERENCE
                // Known gap: dynamic local variables/parameters are not distinguished on the wire.
                isDecl -> DartSyntaxHighlighterColors.LOCAL_VARIABLE_DECLARATION
                else -> DartSyntaxHighlighterColors.LOCAL_VARIABLE_REFERENCE
            }

            SemanticTokenTypes.Parameter -> if (isDecl) DartSyntaxHighlighterColors.PARAMETER_DECLARATION else DartSyntaxHighlighterColors.PARAMETER_REFERENCE
            "label" -> DartSyntaxHighlighterColors.LABEL
            SemanticTokenTypes.Namespace -> DartSyntaxHighlighterColors.LIBRARY_NAME
            SemanticTokenTypes.Keyword -> DartSyntaxHighlighterColors.KEYWORD
            SemanticTokenTypes.String -> if (modifiers.contains("escape")) DartSyntaxHighlighterColors.VALID_STRING_ESCAPE else null
            // Covers IDENTIFIER_DEFAULT and UNRESOLVED_INSTANCE_MEMBER_REFERENCE (known gap: not distinguished on the wire).
            "source" -> DartSyntaxHighlighterColors.IDENTIFIER
            // Comments, numbers, plain strings, booleans, and operators are left to DartSyntaxHighlighter (lexer), matching legacy behavior.
            else -> null
        }
    }
}

/**
 * Custom [Lsp4jClient] that intercepts standard `textDocument/publishDiagnostics` notifications
 * to update the Dart Analysis tool window (`DartProblemsView`) identically in both Bridge mode and Full LSP
 * (while delegating to [super.publishDiagnostics] for editor highlightings and squiggles), and handles
 * custom `dart/textDocument/publishClosingLabels` notifications via [DartLanguageClient].
 */
open class DartLsp4jClient(
    private val project: Project,
    handler: LspServerNotificationsHandler,
) : Lsp4jClient(handler), DartLanguageClient {

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        if (project.isDisposed) return
        super.publishDiagnostics(params)
        val diagnostics = params.diagnostics ?: emptyList()
        val errors = DartLspDiagnosticConverter.convertDiagnostics(project, params.uri, diagnostics)
            .filter { it.type != "TODO" }
        val fileInfo = getDartFileInfo(project, params.uri)
        val filePath = if (fileInfo is DartLocalFileInfo) fileInfo.filePath else params.uri
        DartProblemsView.getInstance(project).updateErrorsForFile(filePath, errors)
    }

    override fun publishClosingLabels(params: DartPublishClosingLabelsParams) {
        if (params.uri != null) {
            DartLspClosingLabelsService.getInstance(project).updateClosingLabels(params.uri, params.labels)
        }
    }
}
