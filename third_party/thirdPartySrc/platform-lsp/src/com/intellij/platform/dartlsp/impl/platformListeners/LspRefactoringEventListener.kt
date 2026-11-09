// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.dartlsp.impl.platformListeners

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.impl.features.workspaceFileOperations.LspWillRenameFilesHandler
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap

/** The ID of the refactoring in [com.intellij.refactoring.rename.RenameProcessor]. */
internal const val RENAME_REFACTORING_ID = "refactoring.rename"

/** The ID of the refactoring in [com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor]. */
internal const val MOVE_REFACTORING_ID = "refactoring.move"

internal class LspRefactoringEventListener(private val project: Project) : RefactoringEventListener {
  override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
    if (beforeData == null) return
    if (refactoringId == RENAME_REFACTORING_ID || refactoringId == MOVE_REFACTORING_ID) {
      LspWillRenameFilesHandler.renameOrMoveRefactoringStarted(project, refactoringId, beforeData)
    }
  }

  override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
    if (refactoringId == RENAME_REFACTORING_ID || refactoringId == MOVE_REFACTORING_ID) {
      LspWillRenameFilesHandler.renameOrMoveRefactoringDone(project, refactoringId, afterData)
    }
  }
}

/**
 * Dart patch (IJPL-249183): compatibility fallback for IntelliJ 2025.3-2026.2 (`pluginSinceBuild < 263`),
 * where `RefactoringEventData.RENAMES_KEY` does not yet exist.
 *
 * Inactive when `RefactoringEventData.RENAMES_KEY` is present at runtime (2026.3+).
 * Implements [RenamePsiElementProcessorBase.DefaultRenamePsiElementProcessor] with `order="last"` so
 * platform default-processor checks behave identically for directories and files without a custom processor.
 */
internal class LspRenameEventDataFallback :
  RenamePsiElementProcessor(),
  RenamePsiElementProcessorBase.DefaultRenamePsiElementProcessor,
  DumbAware {

  override fun canProcessElement(element: PsiElement): Boolean {
    if (LspWillRenameFilesHandler.hasPlatformRenamesKey) return false
    if (element !is PsiFileSystemItem) return false
    val virtualFile = element.virtualFile ?: return false
    return LspWillRenameFilesHandler.hasWillRenameCapableServerForFile(element.project, virtualFile)
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    if (!LspWillRenameFilesHandler.hasPlatformRenamesKey) {
      LspWillRenameFilesHandler.recordFallbackRename(element, allRenames)
    }
  }
}

/**
 * Dart patch (IJPL-249183): compatibility fallback for IntelliJ 2025.3-2026.2 (`pluginSinceBuild < 263`),
 * where `RefactoringEventData.TARGET_ELEMENT_KEY` does not yet exist.
 *
 * [canProcessElement] always returns `false` so [MoveFileHandler.forElement] never selects this handler;
 * [MoveFileHandler.detectConflicts] invokes all registered handlers regardless of [canProcessElement].
 */
internal class LspMoveEventDataFallback : MoveFileHandler() {
  override fun canProcessElement(element: PsiFile?): Boolean = false

  override fun prepareMovedFile(file: PsiFile?, moveDestination: PsiDirectory?, oldToNewMap: MutableMap<PsiElement, PsiElement>?) {}

  override fun findUsages(
    psiFile: PsiFile,
    newParent: PsiDirectory,
    searchInComments: Boolean,
    searchInNonJavaFiles: Boolean,
  ): List<UsageInfo>? = null

  override fun retargetUsages(usageInfos: List<UsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>) {}

  override fun updateMovedFile(file: PsiFile?) {}

  override fun detectConflicts(
    conflicts: MultiMap<PsiElement, String>?,
    elementsToMove: Array<out PsiElement>?,
    usages: Array<out UsageInfo>?,
    targetDirectory: PsiDirectory?,
  ) {
    if (LspWillRenameFilesHandler.hasPlatformTargetElementKey) return
    if (elementsToMove == null || targetDirectory == null) return
    if (!LspWillRenameFilesHandler.hasWillRenameCapableServer(targetDirectory.project)) return
    LspWillRenameFilesHandler.recordFallbackMove(elementsToMove, targetDirectory)
  }
}
