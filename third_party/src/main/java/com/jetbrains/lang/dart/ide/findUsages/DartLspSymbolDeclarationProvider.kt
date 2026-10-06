/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.findUsages

import com.intellij.find.usages.symbol.SearchTargetSymbol
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.customization.LspFindReferencesSupport
import com.intellij.platform.dartlsp.impl.LspServerImpl
import com.intellij.platform.dartlsp.impl.LspServerManagerImpl
import com.intellij.platform.dartlsp.impl.features.usages.LspSearchTarget
import com.intellij.platform.dartlsp.util.getLsp4jPosition
import com.intellij.psi.PsiElement
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.psi.DartNamedElement
import com.jetbrains.lang.dart.util.DartResolveUtil
import org.eclipse.lsp4j.Position

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

    val nameIdentifier = declaringElement.nameIdentifier ?: return emptyList()
    val relativeStart = nameIdentifier.textRange.startOffset - declaringElement.textRange.startOffset
    val nameRangeInParent = TextRange.from(relativeStart, nameIdentifier.textLength)
    if (offsetInElement >= 0 && !nameRangeInParent.containsOffset(offsetInElement)) {
      return emptyList()
    }

    val psiFile = declaringElement.containingFile ?: return emptyList()
    val file = DartResolveUtil.getRealVirtualFile(psiFile) ?: return emptyList()
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()

    val lspServers = findReferenceServers(project, file).ifEmpty { return emptyList() }

    val position = getLsp4jPosition(document, nameIdentifier.textRange.startOffset)
    val searchTarget = LspSearchTarget(lspServers, file, position)

    val symbol = DartLspSearchTargetSymbol(searchTarget, project, file, position)
    return listOf(DartLspSymbolDeclaration(declaringElement, nameRangeInParent, symbol))
  }
}

private fun findReferenceServers(project: Project, file: VirtualFile): Collection<LspServerImpl> {
  return LspServerManagerImpl.getInstanceImpl(project)
    .getServersWithThisFileOpen(file)
    .filter {
      it.descriptor.lspCustomization.findReferencesCustomizer is LspFindReferencesSupport &&
        it.supportsFindReferences(file)
    }
}

private data class DartLspSymbolDeclaration(
  private val element: PsiElement,
  private val rangeInElement: TextRange,
  private val symbol: Symbol,
) : PsiSymbolDeclaration {
  override fun getDeclaringElement(): PsiElement = element
  override fun getRangeInDeclaringElement(): TextRange = rangeInElement
  override fun getSymbol(): Symbol = symbol
}

private data class DartLspSearchTargetSymbol(
  override val searchTarget: LspSearchTarget,
  private val project: Project,
  private val file: VirtualFile,
  private val position: Position,
) : SearchTargetSymbol {
  override fun createPointer(): Pointer<DartLspSearchTargetSymbol> =
    DartLspSearchTargetSymbolPointer(project, file, position)
}

private class DartLspSearchTargetSymbolPointer(
  private val project: Project,
  private val file: VirtualFile,
  private val position: Position,
) : Pointer<DartLspSearchTargetSymbol> {
  override fun dereference(): DartLspSearchTargetSymbol? {
    if (project.isDisposed || !file.isValid) return null
    val lspServers = findReferenceServers(project, file).ifEmpty { return null }

    val searchTarget = LspSearchTarget(lspServers, file, position)
    return DartLspSearchTargetSymbol(searchTarget, project, file, position)
  }
}
