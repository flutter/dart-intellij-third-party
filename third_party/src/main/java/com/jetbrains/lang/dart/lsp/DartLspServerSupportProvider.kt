/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.dart.api.LspServerSupportProvider
import com.intellij.platform.lsp.dart.api.LspServerSupportProvider.LspServerStarter
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk

/**
 * Entry point registered in plugin.xml to declare the JetBrains native LSP server provider for Dart.
 *
 * This class is required by the JetBrains LSP framework to associate [LspServer] instances with a specific provider key.
 *
 * Note: While standard providers automatically manage server startup via [fileOpened], this provider ignores automatic
 * startup triggers because the Dart LSP Bridge lifecycle is managed manually by [DartAnalysisServerService] and
 * [DartBridgeLspServerManager]. It also suppresses the default LSP status bar widget by returning an empty list in
 * [createLspWidgetItems].
 */
class DartLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerStarter) {
        if (DartConfigurable.isExperimentalLspFeaturesEnabled(project) &&
            file.extension == "dart" && 
            DartSdk.getDartSdk(project) != null) {
            serverStarter.ensureServerStarted(DartLspServerDescriptor(project))
        }
    }
}
