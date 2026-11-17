/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import com.jetbrains.lang.dart.psi.DartNamedElement

/**
 * IntelliJ's member in-place rename for all Dart symbols, local variables included, while [DartLspRename] is enabled and
 * *Settings | Editor | Code Editing* says *In the editor*. It edits the name in the editor and then runs the rename with
 * [DartLspSymbolRenameProcessor].
 *
 * Unlike the platform's handler, it doesn't show the options inlay after the name: its *comments and strings* option
 * has no effect, because the server decides what the rename changes.
 */
class DartLspInplaceRenameHandler : MemberInplaceRenameHandler() {

    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean =
        element is DartNamedElement &&
        editor.settings.isVariableInplaceRenameEnabled &&
        DartLspRename.isEnabled(element.project)

    override fun createMemberRenamer(
        element: PsiElement,
        elementToRename: PsiNameIdentifierOwner,
        editor: Editor,
    ): MemberInplaceRenamer = DartLspInplaceRenamer(elementToRename, element, editor, elementToRename.name, elementToRename.name)
}

private class DartLspInplaceRenamer(
    elementToRename: PsiNamedElement,
    substituted: PsiElement?,
    editor: Editor,
    initialName: String?,
    oldName: String?,
) : MemberInplaceRenamer(elementToRename, substituted, editor, initialName, oldName) {

    /** Without the options inlay; see [DartLspInplaceRenameHandler]. */
    override fun afterTemplateStart() {}

    override fun createInplaceRenamerToRestart(variable: PsiNamedElement, editor: Editor, initialName: String?): VariableInplaceRenamer =
        DartLspInplaceRenamer(variable, substituted, editor, initialName, myOldName)
}
