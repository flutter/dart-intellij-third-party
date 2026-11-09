/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil

/**
 * Suppresses legacy [com.jetbrains.lang.dart.psi.impl.DartFileReference] PSI usages when renaming
 * Dart files and directories with experimental LSP features enabled.
 *
 * The `workspace/willRenameFiles` request and workspace edit application are handled by the
 * platform LSP `LspWillRenameFilesHandler` (IJPL-249183) on `refactoringStarted`. Without this
 * processor (`order="first"`), `RenameProcessor.findUsages` would collect legacy `DartFileReference`
 * usages beforehand (querying legacy `analysis.getNavigation`) and `handleElementRename` would
 * later re-slice already-updated import URIs using stale cached ranges.
 *
 * Can be removed once legacy `DartFileReference` is retired.
 */
class DartLspRenamePsiElementProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        val project = element.project
        if (!DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
            return false
        }
        if (DartSdk.getDartSdk(project) == null) {
            return false
        }

        val fileIndex = ProjectFileIndex.getInstance(project)
        return when (element) {
            is DartFile -> {
                val vFile = element.virtualFile ?: return false
                fileIndex.isInContent(vFile)
            }
            is PsiDirectory -> {
                val vFile = element.virtualFile
                val module = fileIndex.getModuleForFile(vFile) ?: return false
                DartSdkLibUtil.isDartSdkEnabled(module)
            }
            else -> false
        }
    }

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> {
        // Return empty collection so legacy DartFileReference does not duplicate or corrupt LSP edits.
        return emptyList()
    }
}
