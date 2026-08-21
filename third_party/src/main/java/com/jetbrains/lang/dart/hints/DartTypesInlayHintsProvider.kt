package com.jetbrains.lang.dart.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * Settings-only provider that anchors the "Types" checkbox for Dart in
 * Settings | Editor | Inlay Hints.
 *
 * The hints themselves are computed by the Dart Analysis Server and rendered by the LSP inlay
 * hint support, so this provider never collects anything. Its enabled state is read by
 * [com.jetbrains.lang.dart.lsp.DartLspInlayHintSupport] to decide whether type hints are
 * requested from the server and displayed.
 */
class DartTypesInlayHintsProvider : InlayHintsProvider {
  companion object {
    const val PROVIDER_ID: String = "dart.types"
  }

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? = null
}
