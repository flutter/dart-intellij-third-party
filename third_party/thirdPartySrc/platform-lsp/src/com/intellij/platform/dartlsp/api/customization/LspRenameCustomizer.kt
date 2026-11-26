// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.dartlsp.api.customization

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.util.ui.UIUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock

sealed class LspRenameCustomizer

/**
 * Enables LSP rename support.
 */
open class LspRenameSupport : LspRenameCustomizer() {
  open fun shouldRunRename(psiFile: PsiFile): Boolean {
    // Files with PSI structure are handled by PsiElementRenameHandler out of the box.
    // Plain text and TextMate files lack PSI structure, so LSP rename is used for them.
    // Plugins may override this logic as needed.
    return psiFile.language.id.let { it == "TEXT" || it == "textmate" }
  }

  /**
   * Returns the text range of the renameable word at the given offset, or null if rename is not available.
   *
   * This method is used as a fallback when:
   * - The LSP server doesn't support `textDocument/prepareRename`
   * - The LSP server returns `defaultBehavior: true` in the prepare rename response
   *
   * **Important**: This is the place to check for keywords, built-in identifiers, or other non-renameable elements.
   * If null is returned, the rename operation will be aborted and nothing will happen next.
   *
   * The default implementation uses word boundaries based on the document's content.
   * By default, [Character.isJavaIdentifierPart] is used as it works well for most languages.
   *
   * Override [getRenameableRangeAtOffset] to provide language-specific logic for determining renameable identifiers.
   */
  @RequiresReadLock
  open fun getRenameableRangeAtOffset(document: Document, offset: Int): TextRange? {
    val text = document.charsSequence
    if (offset < 0 || offset > text.length) return null

    var start = offset
    var end = offset

    while (start > 0 && Character.isJavaIdentifierPart(text[start - 1])) {
      start--
    }

    while (end < text.length && Character.isJavaIdentifierPart(text[end])) {
      end++
    }

    // No word found
    if (start == end) return null

    return TextRange(start, end)
  }

  // Dart patch: title and label of the dialog that asks for a name the in-place rename can't edit
  // (LspRenameHandler.renameWithInputDialog), at [offset] in [psiFile]. Plugins can name there what is renamed.
  open fun getInputDialogTitle(psiFile: PsiFile, offset: Int): @NlsContexts.DialogTitle String =
    RefactoringBundle.message("rename.title")

  open fun getInputDialogLabel(psiFile: PsiFile, offset: Int): @NlsContexts.Label String =
    UIUtil.removeMnemonic(RefactoringBundle.message("rename.dialog.new.name.label"))
}

object LspRenameDisabled : LspRenameCustomizer()
