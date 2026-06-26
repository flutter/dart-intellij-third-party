/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.LspCommunicationChannel
import com.intellij.platform.dartlsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.dartlsp.api.customization.LspCallHierarchyDisabled
import com.intellij.platform.dartlsp.api.customization.LspCodeActionsDisabled
import com.intellij.platform.dartlsp.api.customization.LspCodeLensDisabled
import com.intellij.platform.dartlsp.api.customization.LspCommandsDisabled
import com.intellij.platform.dartlsp.api.customization.LspCompletionDisabled
import com.intellij.platform.dartlsp.api.customization.LspCustomization
import com.intellij.platform.dartlsp.api.customization.LspDiagnosticsDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentColorDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentHighlightsDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentLinkDisabled
import com.intellij.platform.dartlsp.api.customization.LspDocumentSymbolDisabled
import com.intellij.platform.dartlsp.api.customization.LspFindReferencesDisabled
import com.intellij.platform.dartlsp.api.customization.LspFoldingRangeDisabled
import com.intellij.platform.dartlsp.api.customization.LspFormattingDisabled
import com.intellij.platform.dartlsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.platform.dartlsp.api.customization.LspGoToTypeDefinitionDisabled
import com.intellij.platform.dartlsp.api.customization.LspHoverCustomizer
import com.intellij.platform.dartlsp.api.customization.LspHoverDisabled
import com.intellij.platform.dartlsp.api.customization.LspHoverSupport
import com.intellij.platform.dartlsp.api.customization.LspInlayHintDisabled
import com.intellij.platform.dartlsp.api.customization.LspOptimizeImportsDisabled
import com.intellij.platform.dartlsp.api.customization.LspRenameDisabled
import com.intellij.platform.dartlsp.api.customization.LspSelectionRangeDisabled
import com.intellij.platform.dartlsp.api.customization.LspSemanticTokensDisabled
import com.intellij.platform.dartlsp.api.customization.LspSignatureHelpDisabled
import com.intellij.platform.dartlsp.api.customization.LspTypeHierarchyDisabled
import com.intellij.platform.dartlsp.api.customization.LspWorkspaceSymbolDisabled
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartConfigurable

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

    override fun isSupportedFile(file: VirtualFile): Boolean {
        return DartAnalysisServerService.isFileNameRespectedByAnalysisServer(file.name)
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
        override val hoverCustomizer: LspHoverCustomizer
            get() = if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
                LspHoverSupport()
            } else {
                LspHoverDisabled
            }
        
        override val goToDefinitionCustomizer = LspGoToDefinitionDisabled
        override val goToTypeDefinitionCustomizer = LspGoToTypeDefinitionDisabled
        override val completionCustomizer = LspCompletionDisabled
        override val semanticTokensCustomizer = LspSemanticTokensDisabled
        override val diagnosticsCustomizer = LspDiagnosticsDisabled
        override val codeActionsCustomizer = LspCodeActionsDisabled
        override val commandsCustomizer = LspCommandsDisabled
        override val formattingCustomizer = LspFormattingDisabled
        override val findReferencesCustomizer = LspFindReferencesDisabled
        override val optimizeImportsCustomizer = LspOptimizeImportsDisabled
        override val documentColorCustomizer = LspDocumentColorDisabled
        override val documentLinkCustomizer = LspDocumentLinkDisabled
        override val foldingRangeCustomizer = LspFoldingRangeDisabled
        override val inlayHintCustomizer = LspInlayHintDisabled
        override val documentHighlightsCustomizer = LspDocumentHighlightsDisabled
        override val signatureHelpCustomizer = LspSignatureHelpDisabled
        override val documentSymbolCustomizer = LspDocumentSymbolDisabled
        override val workspaceSymbolCustomizer = LspWorkspaceSymbolDisabled
        override val callHierarchyCustomizer = LspCallHierarchyDisabled
        override val typeHierarchyCustomizer = LspTypeHierarchyDisabled
        override val selectionRangeCustomizer = LspSelectionRangeDisabled
        override val codeLensCustomizer = LspCodeLensDisabled
        override val renameCustomizer = LspRenameDisabled
    }
}
