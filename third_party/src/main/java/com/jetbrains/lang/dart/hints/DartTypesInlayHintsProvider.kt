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

    /** Option ids of the type category sub-checkboxes, see plugin.xml. */
    const val VARIABLE_TYPES_OPTION_ID: String = "dart.types.variable"
    const val RETURN_TYPES_OPTION_ID: String = "dart.types.return"
    const val PARAMETER_TYPES_OPTION_ID: String = "dart.types.parameter"
    const val TYPE_ARGUMENTS_OPTION_ID: String = "dart.types.type.arguments"
    const val DOT_SHORTHAND_TYPES_OPTION_ID: String = "dart.types.dot.shorthand"
  }

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? = null
}
