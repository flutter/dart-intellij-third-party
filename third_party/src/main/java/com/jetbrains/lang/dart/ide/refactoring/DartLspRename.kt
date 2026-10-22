/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import org.jetbrains.annotations.TestOnly

/**
 * Decides whether Dart symbols are renamed over LSP (`textDocument/rename`) behind IntelliJ's standard rename UI
 * instead of the legacy `edit.getRefactoring` RENAME and the Dart rename dialog.
 */
object DartLspRename {
    /**
     * Overrides the gate in tests: the light test SDK is older than [DartAnalysisServerService.MIN_LSP_RENAME_SDK_VERSION].
     */
    @TestOnly
    @Volatile
    var enabledInTests: Boolean? = null

    fun isEnabled(project: Project): Boolean =
        enabledInTests ?: DartAnalysisServerService.isLspRenameEnabled(project)
}
