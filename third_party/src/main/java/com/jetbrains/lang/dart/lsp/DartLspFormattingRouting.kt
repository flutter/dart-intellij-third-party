/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.lang.dart.sdk.DartConfigurable

/**
 * Defines the narrower file eligibility used only by editor formatting.
 *
 * The descriptor supports a broader set of files for other LSP features, so this policy must not
 * be reused from [DartLspServerDescriptor.isSupportedFile].
 */
object DartLspFormattingRouting {
    @JvmStatic
    fun isFormattingEligible(file: VirtualFile): Boolean {
        return file.isInLocalFileSystem && file.extension.equals("dart", ignoreCase = true)
    }

    @JvmStatic
    fun isLspOwnedEditorFormatting(project: Project, file: VirtualFile): Boolean {
        return DartConfigurable.isExperimentalLspFeaturesEnabled(project) && isFormattingEligible(file)
    }
}
