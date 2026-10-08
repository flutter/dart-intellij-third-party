/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.findUsages

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.symbol.SearchTargetSymbol
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.platform.dartlsp.api.customization.LspFindReferencesSupport
import com.intellij.platform.dartlsp.impl.LspServerManagerImpl
import com.intellij.platform.dartlsp.impl.features.usages.LspSearchTarget
import com.intellij.platform.dartlsp.util.getLsp4jPosition
import com.intellij.psi.PsiElement
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.psi.DartNamedElement

/**
 * Provides LSP-backed [SearchTargetSymbol] declarations for [DartNamedElement]s when
 * [DartAnalysisServerService.isLspReferencesEnabled] is active.
 *
 * When a user invokes "Go to Declaration or Usages" (`Cmd+Click` / `Cmd+B`) on a Dart declaration,
 * `LspImplicitReferenceProvider` ignores the self-definition and returns no references, causing
 * `GotoDeclarationOrUsageHandler2` (`GtduKt.fromTargetData`) to fall back to PSI declarations via
 * `Declarations.allDeclarationsAround`. Without this provider, the platform wraps the PSI element in
 * a `PsiTargetVariant`, which fails to find usages because [DartTargetElementEvaluator] and
 * [DartServerFindUsagesHandler] are disabled when LSP references are enabled.
 *
 * By returning a [PsiSymbolDeclaration] whose symbol implements [SearchTargetSymbol] backed by
 * [LspSearchTarget], `GotoDeclarationOrUsageHandler2` produces a `SearchTargetVariant` that routes
 * directly to `LspUsageSearcher` (`textDocument/references`) without modifying `platform-lsp`.
 */
class DartLspSymbolDeclarationProvider : PsiSymbolDeclarationProvider {
  override fun getDeclarations(
    declaringElement: PsiElement,
    offsetInElement: Int,
  ): Collection<PsiSymbolDeclaration> {
    if (declaringElement !is DartNamedElement) return emptyList()
    val project = declaringElement.project
    if (project.isDefault || !DartAnalysisServerService.isLspReferencesEnabled(project)) {
      return emptyList()
    }

    val psiFile = declaringElement.containingFile ?: return emptyList()
    val file = psiFile.virtualFile ?: return emptyList()
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()

    val lspServers = LspServerManagerImpl.getInstanceImpl(project)
      .getServersWithThisFileOpen(file)
      .filter { it.descriptor.lspCustomization.findReferencesCustomizer is LspFindReferencesSupport }
      .filter { it.supportsFindReferences(file) }
      .ifEmpty { return emptyList() }

    val position = getLsp4jPosition(document, declaringElement.textRange.startOffset)
    val searchTarget = LspSearchTarget(lspServers, file, position)

    return listOf(DartLspSymbolDeclaration(declaringElement, DartLspSearchTargetSymbol(searchTarget)))
  }
}

private class DartLspSymbolDeclaration(
  private val element: PsiElement,
  private val symbol: Symbol,
) : PsiSymbolDeclaration {
  override fun getDeclaringElement(): PsiElement = element
  override fun getRangeInDeclaringElement(): TextRange = TextRange(0, element.textLength)
  override fun getSymbol(): Symbol = symbol
}

private data class DartLspSearchTargetSymbol(
  override val searchTarget: LspSearchTarget,
) : SearchTargetSymbol {
  override fun createPointer(): Pointer<out Symbol> = Pointer.hardPointer(this)
}
