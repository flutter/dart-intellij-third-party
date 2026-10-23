/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.CommonBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.logging.PluginLogger
import com.jetbrains.lang.dart.util.DartResolveUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

private val LOG = PluginLogger.createLogger(DartLspSymbolRenameProcessor::class.java)

/**
 * Renames Dart symbols with the analysis server's LSP `textDocument/rename` behind IntelliJ's standard rename UI (in-place
 * or the rename dialog, as set in *Settings | Editor | Code Editing*), while [DartLspRename] is enabled.
 *
 * IntelliJ chooses the element and runs the UI; this processor only replaces the logic:
 * - [substituteElementToRename] asks the server with `textDocument/prepareRename` whether the element can be renamed and
 *   shows the server's message otherwise, before any UI appears.
 * - [prepareRenaming] sends `textDocument/rename` for the new name. It runs on the EDT before the usage search, so the
 *   request runs under a modal progress of its own without a read lock: while it runs, the server may ask the user
 *   *Rename Anyway* / *Cancel* (see `DartAnalysisServerImpl.server_showMessageRequest`).
 * - [findCollisions] turns every edit of the answer into a [DartLspRenameUsageInfo], so the preview shows them and single
 *   ones can be excluded; a server error becomes a [DartLspRenameProblemUsageInfo] (shown in IntelliJ's conflicts dialog).
 * - [renameElement] applies the edits the user kept, inside the rename command.
 *
 * Dart files and directories are handled by [DartLspRenamePsiElementProcessor] (`workspace/willRenameFiles`).
 */
class DartLspSymbolRenameProcessor : RenamePsiElementProcessor(), DumbAware {

    private class PendingRename(val newName: String, val result: DartLspRenameResult<List<DartLspFileTextEdit>>)

    /** The server's answer per element, from [prepareRenaming] to [findCollisions] of the same rename. */
    private val pendingRenames: MutableMap<PsiElement, PendingRename> = ContainerUtil.createConcurrentWeakMap()

    override fun canProcessElement(element: PsiElement): Boolean =
        element is PsiNamedElement &&
        element !is PsiFileSystemItem &&
        element.language == DartLanguage.INSTANCE &&
        DartLspRename.isEnabled(element.project)

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
        val project = element.project
        val (file, offset) = prepareTarget(element, editor) ?: return element
        val result = runRenameRequestUnderProgress(project) { DartLspRenameRequests.getInstance(project).prepareRename(file, offset) }
        val message = when (result) {
            is DartLspRenameResult.Success -> return element
            is DartLspRenameResult.Failure -> result.message
            DartLspRenameResult.NoServer -> DartBundle.message("rename.lsp.server.not.ready")
            null -> return null
        }
        CommonRefactoringUtil.showErrorHint(project, editor, message, CommonBundle.getErrorTitle(), null)
        return null
    }

    override fun prepareRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        scope: SearchScope,
    ) {
        // MemberInplaceRenamer calls this with an empty name when its template starts.
        if (newName.isBlank()) return
        val project = element.project
        val (file, offset) = nameTarget(element) ?: return
        val result = runRenameRequestUnderProgress(project) { DartLspRenameRequests.getInstance(project).rename(file, offset, newName) }
            // Cancelling the progress renames nothing.
            ?: DartLspRenameResult.Success(emptyList())
        pendingRenames[element] = PendingRename(newName, result)
    }

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference> = emptyList()

    override fun findCollisions(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<out PsiElement, String>,
        result: MutableList<UsageInfo>,
    ) {
        val pending = pendingRenames.remove(element)
        if (pending == null || pending.newName != newName) {
            LOG.warn("No LSP rename result for '$newName'")
            return
        }
        when (val renameResult = pending.result) {
            is DartLspRenameResult.Success -> {
                if (renameResult.value.isEmpty()) {
                    // Nothing to rename, e.g. the user picked Cancel in the server's Rename Anyway question. Cancelling
                    // the usage search ends the rename like its own Cancel button, without an empty undo step.
                    ProgressManager.getInstance().progressIndicator?.let {
                        it.cancel()
                        ProgressManager.checkCanceled()
                    }
                    return
                }
                val psiManager = PsiManager.getInstance(element.project)
                for (edit in renameResult.value) {
                    val psiFile = psiManager.findFile(edit.file)
                    if (psiFile == null) {
                        result += DartLspRenameProblemUsageInfo(
                            element, DartBundle.message("rename.lsp.unknown.file", edit.file.presentableUrl))
                        continue
                    }
                    result += DartLspRenameUsageInfo(psiFile, edit.range, element, edit.newText)
                }
            }
            is DartLspRenameResult.Failure -> result += DartLspRenameProblemUsageInfo(element, renameResult.message)
            DartLspRenameResult.NoServer ->
                result += DartLspRenameProblemUsageInfo(element, DartBundle.message("rename.lsp.server.not.ready"))
        }
    }

    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<out UsageInfo>,
        listener: RefactoringElementListener?,
    ) {
        val documentManager = PsiDocumentManager.getInstance(element.project)
        usages.filterIsInstance<DartLspRenameUsageInfo>()
            .mapNotNull { usage ->
                val file = usage.file ?: return@mapNotNull null
                val segment = usage.segment ?: return@mapNotNull null
                val document = documentManager.getDocument(file) ?: return@mapNotNull null
                Triple(document, segment, usage.newText)
            }
            .groupBy({ it.first }, { it.second to it.third })
            .forEach { (document, edits) ->
                // From the end, so that earlier offsets stay valid.
                for ((segment, newText) in edits.sortedByDescending { it.first.startOffset }) {
                    document.replaceString(segment.startOffset, segment.endOffset, newText)
                }
            }
    }

    override fun isToSearchInComments(element: PsiElement): Boolean = false

    override fun setToSearchInComments(element: PsiElement, enabled: Boolean) {}

    override fun isToSearchForTextOccurrences(element: PsiElement): Boolean = false

    override fun setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean) {}

    override fun createRenameDialog(
        project: Project,
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        editor: Editor?,
    ): RenameDialog = DartLspRenameDialog(project, element, nameSuggestionContext, editor)

    /**
     * Where to ask `textDocument/prepareRename`: at the element's name if it is declared in the project. Otherwise (SDK,
     * packages) at the caret, where the user started the rename, so the server reports why it can't rename it.
     */
    private fun prepareTarget(element: PsiElement, editor: Editor?): Pair<VirtualFile, Int>? {
        val nameTarget = nameTarget(element)
        if (nameTarget != null && ProjectFileIndex.getInstance(element.project).isInContent(nameTarget.first)) return nameTarget
        editor ?: return nameTarget
        val editorFile = PsiDocumentManager.getInstance(element.project).getPsiFile(editor.document) ?: return nameTarget
        val file = DartResolveUtil.getRealVirtualFile(editorFile) ?: return nameTarget
        return file to editor.caretModel.offset
    }

    /** The position of the element's name in its file. */
    private fun nameTarget(element: PsiElement): Pair<VirtualFile, Int>? {
        val file = DartResolveUtil.getRealVirtualFile(element.containingFile) ?: return null
        val name = (element as? PsiNameIdentifierOwner)?.nameIdentifier ?: element
        return file to name.textRange.startOffset
    }
}

/** One text edit of an LSP rename, shown as a usage. It has no [PsiReference]: the server's edit is applied as it is. */
class DartLspRenameUsageInfo(
    file: PsiFile,
    range: TextRange,
    renamedElement: PsiElement,
    val newText: String,
) : MoveRenameUsageInfo(file, null, range.startOffset, range.endOffset, renamedElement, false) {
    override fun getReference(): PsiReference? = null
}

/** A rename the server refused, shown in IntelliJ's conflicts dialog with the server's message. */
class DartLspRenameProblemUsageInfo(
    element: PsiElement,
    private val message: @NlsContexts.DialogMessage String,
) : UnresolvableCollisionUsageInfo(element, element) {
    override fun getDescription(): String = message
}

/**
 * IntelliJ's rename dialog without *Search in comments and strings*, *Search for text occurrences* and the scope: the
 * server decides what the rename changes.
 */
class DartLspRenameDialog(
    project: Project,
    element: PsiElement,
    nameSuggestionContext: PsiElement?,
    editor: Editor?,
) : RenameDialog(project, element, nameSuggestionContext, editor) {

    override fun createCheckboxes(panel: JPanel, gbConstraints: GridBagConstraints) {
        // The base dialog dereferences its checkboxes later, so they are created, just not shown.
        super.createCheckboxes(JPanel(GridBagLayout()), gbConstraints)
    }

    override fun createSearchScopePanel(): JComponent? {
        // Creates the scope chooser the base dialog dereferences later, without showing it.
        super.createSearchScopePanel()
        return null
    }

    override fun isSearchInComments(): Boolean = false

    override fun isSearchInNonJavaFiles(): Boolean = false
}
