// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.dartlsp.impl.features.workspaceFileOperations

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.platform.dartlsp.api.LspBundle
import com.intellij.platform.dartlsp.impl.LspDynamicCapabilities
import com.intellij.platform.dartlsp.impl.LspServerImpl
import com.intellij.platform.dartlsp.impl.LspServerManagerImpl
import com.intellij.platform.dartlsp.impl.platformListeners.MOVE_REFACTORING_ID
import com.intellij.platform.dartlsp.impl.platformListeners.RENAME_REFACTORING_ID
import com.intellij.platform.dartlsp.impl.util.LspWorkspaceEditApplier
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.URLUtil
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.WorkspaceEdit
import kotlin.time.Duration.Companion.milliseconds

private val LOG = fileLogger()

// The spec allows a client to drop a response that takes too long.
// Dart patch (IJPL-249183): use a 10s timeout (instead of upstream's 1000ms) to accommodate
// Dart Analysis Server cross-package import resolution; users can still click "Skip" in the progress dialog.
private val WILL_RENAME_FILES_TIMEOUT = 10_000.milliseconds

internal object LspWillRenameFilesHandler {
  // Dart patch (IJPL-249183): RefactoringEventData.RENAMES_KEY and TARGET_ELEMENT_KEY were added in
  // IntelliJ 2026.3. Resolve them reflectively when running on 263+, and fall back to pre-refactoring
  // listeners (LspRenameEventDataFallback / LspMoveEventDataFallback) on 2025.3-2026.2.
  @Suppress("UNCHECKED_CAST")
  private val RENAMES_KEY: Key<Map<PsiElement, String>>? = runCatching {
    RefactoringEventData::class.java.getField("RENAMES_KEY").get(null) as? Key<Map<PsiElement, String>>
  }.getOrNull()

  @Suppress("UNCHECKED_CAST")
  private val TARGET_ELEMENT_KEY: Key<PsiElement>? = runCatching {
    RefactoringEventData::class.java.getField("TARGET_ELEMENT_KEY").get(null) as? Key<PsiElement>
  }.getOrNull()

  internal val hasPlatformRenamesKey: Boolean
    get() = RENAMES_KEY != null

  internal val hasPlatformTargetElementKey: Boolean
    get() = TARGET_ELEMENT_KEY != null

  private const val MAX_FALLBACK_ENTRIES = 16
  private val fallbackLock = Any()
  private val capturedRenames = ArrayDeque<CapturedRename>()
  private val capturedMoves = ArrayDeque<CapturedMove>()

  private class CapturedRename(
    val projectRef: WeakReference<Project>,
    val primaryElementRef: WeakReference<PsiElement>,
    val allRenamesRef: WeakReference<Map<PsiElement, String>>,
  )

  private class CapturedMove(
    val projectRef: WeakReference<Project>,
    val elementsRef: WeakReference<Array<out PsiElement>>,
    val targetDirRef: WeakReference<PsiDirectory>,
  )

  internal fun hasWillRenameCapableServer(project: Project): Boolean {
    if (project.isDisposed) return false
    return LspServerManagerImpl.getInstanceImpl(project).getAllRunningServers()
      .any { it.clientCapabilities?.workspace?.fileOperations?.willRename == true }
  }

  internal fun hasWillRenameCapableServerForFile(project: Project, fileOrDir: VirtualFile): Boolean {
    if (project.isDisposed) return false
    return LspServerManagerImpl.getInstanceImpl(project).getAllRunningServers()
      .any { server ->
        server.clientCapabilities?.workspace?.fileOperations?.willRename == true &&
        server.descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, fileOrDir, false) }
      }
  }

  internal fun recordFallbackRename(primaryElement: PsiElement, allRenames: Map<PsiElement, String>) {
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      capturedRenames.removeAll { it.allRenamesRef.get() === allRenames }
      capturedRenames.addFirst(
        CapturedRename(WeakReference(primaryElement.project), WeakReference(primaryElement), WeakReference(allRenames))
      )
      while (capturedRenames.size > MAX_FALLBACK_ENTRIES) {
        capturedRenames.removeLast()
      }
    }
  }

  internal fun recordFallbackMove(elementsToMove: Array<out PsiElement>, targetDirectory: PsiDirectory) {
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      capturedMoves.removeAll { it.elementsRef.get() === elementsToMove }
      capturedMoves.addFirst(
        CapturedMove(WeakReference(targetDirectory.project), WeakReference(elementsToMove), WeakReference(targetDirectory))
      )
      while (capturedMoves.size > MAX_FALLBACK_ENTRIES) {
        capturedMoves.removeLast()
      }
    }
  }

  internal fun renameOrMoveRefactoringDone(project: Project, refactoringId: String, afterData: RefactoringEventData?) {
    if (RENAMES_KEY != null && TARGET_ELEMENT_KEY != null) return
    val element = afterData?.getUserData(RefactoringEventData.PSI_ELEMENT_KEY)
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      when (refactoringId) {
        RENAME_REFACTORING_ID -> if (element != null) {
          capturedRenames.removeAll { entry ->
            entry.projectRef.get() == project &&
            (entry.primaryElementRef.get() == element || entry.allRenamesRef.get()?.containsKey(element) == true)
          }
        }
        MOVE_REFACTORING_ID -> if (element != null) {
          val index = capturedMoves.indexOfFirst { it.projectRef.get() == project && it.targetDirRef.get() == element }
          if (index >= 0) capturedMoves.removeAt(index)
        }
      }
    }
  }

  private fun pruneFallbackEntriesLocked() {
    capturedRenames.removeAll { it.projectRef.get()?.isDisposed != false || it.allRenamesRef.get() == null }
    capturedMoves.removeAll {
      it.projectRef.get()?.isDisposed != false || it.elementsRef.get() == null || it.targetDirRef.get() == null
    }
  }

  private fun findFallbackRename(project: Project, primaryElement: PsiElement?): Map<PsiElement, String>? {
    if (primaryElement == null) return null
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      val match = capturedRenames.firstOrNull { entry ->
        val map = entry.allRenamesRef.get() ?: return@firstOrNull false
        entry.projectRef.get() == project && (entry.primaryElementRef.get() == primaryElement || map.containsKey(primaryElement))
      }
      return match?.allRenamesRef?.get()?.let { LinkedHashMap(it) }
    }
  }

  private fun findFallbackMoveTarget(project: Project, elements: Array<out PsiElement>): PsiDirectory? {
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      val match = capturedMoves.firstOrNull { entry ->
        val capturedElements = entry.elementsRef.get() ?: return@firstOrNull false
        entry.projectRef.get() == project && (capturedElements === elements || capturedElements.contentEquals(elements))
      }
      return match?.targetDirRef?.get()
    }
  }

  /**
   * Sends the
   * [workspace/willRenameFiles](https://microsoft.github.io/language-server-protocol/specification/#workspace_willRenameFiles)
   * request before a Rename or Move refactoring changes the files, and applies the edits from the servers in the command of the refactoring.
   */
  internal fun renameOrMoveRefactoringStarted(project: Project, refactoringId: String, beforeData: RefactoringEventData) {
    val lspServers = LspServerManagerImpl.getInstanceImpl(project).getAllRunningServers()
      .filter { it.clientCapabilities?.workspace?.fileOperations?.willRename == true }
    if (lspServers.isEmpty()) return

    val application = ApplicationManager.getApplication()
    if (!application.isDispatchThread || application.isWriteAccessAllowed) {
      LOG.warn("Skipping workspace/willRenameFiles for '$refactoringId': the event came " +
               "${if (application.isDispatchThread) "on the EDT" else "off the EDT"}, " +
               "${if (application.isWriteAccessAllowed) "inside" else "outside"} a write action. " +
               "Expected: EDT outside a write action.")
      return
    }

    val plannedRenames = when (refactoringId) {
      RENAME_REFACTORING_ID -> getPlannedRenamesFromRenameRefactoring(project, beforeData)
      MOVE_REFACTORING_ID -> getPlannedRenamesFromMoveRefactoring(project, beforeData)
      else -> emptyList()
    }
    if (plannedRenames.isEmpty()) return

    try {
      sendWillRenameFilesRequestsAndApplyEdits(project, lspServers, plannedRenames)
    }
    catch (e: Throwable) {
      if (e is CancellationException || e is ControlFlowException) throw e
      LOG.error(e)
    }
  }

  private fun getPlannedRenamesFromRenameRefactoring(project: Project, beforeData: RefactoringEventData): List<LspPlannedRename> {
    val renames = (if (RENAMES_KEY != null) {
      beforeData.getUserData(RENAMES_KEY)
    }
    else {
      findFallbackRename(project, beforeData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY))
    }) ?: return emptyList()
    return renames.mapNotNull { (element, newName) ->
      if (element !is PsiFileSystemItem || !element.isValid || element.name == newName) return@mapNotNull null
      val parentDir = element.parent as? PsiDirectory ?: return@mapNotNull null
      createPlannedRename(element, parentDir, newName)
    }
  }

  private fun getPlannedRenamesFromMoveRefactoring(project: Project, beforeData: RefactoringEventData): List<LspPlannedRename> {
    val elements = beforeData.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY) ?: return emptyList()
    val targetDir = (if (TARGET_ELEMENT_KEY != null) {
      beforeData.getUserData(TARGET_ELEMENT_KEY) as? PsiDirectory
    }
    else {
      findFallbackMoveTarget(project, elements)
    }) ?: return emptyList()
    return elements.mapNotNull { element ->
      // the move skips an item that is already in the target, and a file whose name already exists there
      if (element !is PsiFileSystemItem || !element.isValid || element.parent == targetDir) return@mapNotNull null
      if (element is PsiFile && targetDir.findFile(element.name) != null) return@mapNotNull null
      createPlannedRename(element, targetDir, element.name)
    }
  }

  private fun createPlannedRename(fileSystemItem: PsiFileSystemItem, newParentDir: PsiDirectory, newName: String): LspPlannedRename? {
    val fileOrDir = fileSystemItem.virtualFile
    if (fileOrDir == null || !fileOrDir.isValid) return null
    return LspPlannedRename(fileOrDir, newParentDir.virtualFile, newName)
  }

  private class LspPlannedRename(val fileOrDir: VirtualFile, val newParentDir: VirtualFile, val newName: String)

  @RequiresEdt
  private fun sendWillRenameFilesRequestsAndApplyEdits(
    project: Project,
    lspServers: List<LspServerImpl>,
    plannedRenames: List<LspPlannedRename>,
  ) {
    val lspServerToFilters = lspServers
      .mapNotNull { lspServer -> getWillRenameFilters(lspServer).takeIf { it.isNotEmpty() }?.let { lspServer to it } }
    if (lspServerToFilters.isEmpty()) return

    var workspaceEditAppliers = emptyList<LspWorkspaceEditApplier>()
    val completed = ApplicationManagerEx.getApplicationEx().runProcessWithProgressSynchronously(
      { workspaceEditAppliers = sendWillRenameFilesRequests(lspServerToFilters, plannedRenames) },
      LspBundle.message("lsp.will.rename.files.progress.title"),
      true,
      true,
      project,
      null,
      LspBundle.message("lsp.will.rename.files.skip"),
    )

    if (completed) {
      workspaceEditAppliers.forEach { it.applyWorkspaceEdit() }
    }
  }

  private fun getWillRenameFilters(lspServer: LspServerImpl): List<FileOperationFilter> {
    val staticFilters = lspServer.serverCapabilities?.workspace?.fileOperations?.willRename?.filters.orEmpty()
    val dynamicFilters = lspServer.dynamicCapabilities.getCapabilityRegistrationOptions(LspDynamicCapabilities.willRenameFiles)
      .flatMap { it.filters.orEmpty() }
    return staticFilters + dynamicFilters
  }

  @RequiresBackgroundThread
  private fun sendWillRenameFilesRequests(
    lspServerToFilters: List<Pair<LspServerImpl, List<FileOperationFilter>>>,
    plannedRenames: List<LspPlannedRename>,
  ): List<LspWorkspaceEditApplier> = runBlockingCancellable {
    lspServerToFilters.map { (lspServer, filters) ->
      async {
        val serverPlannedRenames = filterPlannedRenames(lspServer, filters, plannedRenames)
        if (serverPlannedRenames.isEmpty()) return@async null
        val edit = sendWillRenameFilesRequest(lspServer, serverPlannedRenames) ?: return@async null
        readAction { LspWorkspaceEditApplier.create(lspServer, edit) }
      }
    }.awaitAll().filterNotNull()
  }

  @RequiresBackgroundThread
  private fun filterPlannedRenames(
    lspServer: LspServerImpl,
    filters: List<FileOperationFilter>,
    plannedRenames: List<LspPlannedRename>,
  ): List<LspPlannedRename> {
    return plannedRenames.filter { plannedRename ->
      val filePath = lspServer.descriptor.getFilePath(plannedRename.fileOrDir)
      val uriScheme = plannedRename.fileOrDir.fileSystem.protocol
      val isDirectory = plannedRename.fileOrDir.isDirectory

      lspServer.descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, plannedRename.fileOrDir, false) } &&
      filters.any { lspServer.globMatcher.pathMatchesFileOperationFilter(filePath, uriScheme, isDirectory, it) }
    }
  }

  private suspend fun sendWillRenameFilesRequest(lspServer: LspServerImpl, plannedRenames: List<LspPlannedRename>): WorkspaceEdit? {
    val fileRenames = plannedRenames.map {
      val oldUri = lspServer.descriptor.getFileUri(it.fileOrDir)
      val newUri = lspServer.descriptor.getFileUri(LspRenameTargetFile(it.newParentDir, it.newName, it.fileOrDir.isDirectory))
      FileRename(oldUri, newUri)
    }
    val params = RenameFilesParams(fileRenames)

    return try {
      withTimeoutOrNull(WILL_RENAME_FILES_TIMEOUT) {
        lspServer.sendRequest { it.workspaceService.willRenameFiles(params) }
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      // a failed server must not cancel the requests to other servers
      LOG.warn("${lspServer.descriptor}: workspace/willRenameFiles request failed", e)
      null
    }
  }
}

/**
 * The file or directory at the new location of a planned rename. It does not exist yet.
 * It has the file system and the path of the real file after the rename,
 * so [com.intellij.platform.dartlsp.api.LspServerDescriptor.getFileUri] and its overrides give the same URI for both.
 */
private class LspRenameTargetFile(
  newParentDir: VirtualFile,
  newName: String,
  private val isDirectory: Boolean,
) : FakeVirtualFile(newParentDir, newName) {

  override fun getUrl(): String = fileSystem.protocol + URLUtil.SCHEME_SEPARATOR + path
  override fun isDirectory(): Boolean = isDirectory
  override fun isValid(): Boolean = true
}
