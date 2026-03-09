// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent

internal class LspWorkspaceFoldersManager {
    private val registeredWorkspaceFolderUris = mutableSetOf<String>()

    fun clear() {
        synchronized(this) {
            registeredWorkspaceFolderUris.clear()
        }
    }

    fun resetRegisteredWorkspaceFolders(initialWorkspaceFolderUris: Collection<String>) {
        synchronized(this) {
            registeredWorkspaceFolderUris.clear()
            registeredWorkspaceFolderUris.addAll(initialWorkspaceFolderUris)
        }
    }

    fun ensureWorkspaceFolderRegistered(
        workspaceFolderUri: String,
        toWorkspaceFolder: (String) -> WorkspaceFolder,
        sendChange: (DidChangeWorkspaceFoldersParams) -> Unit,
    ) {
        synchronized(this) {
            if (!registeredWorkspaceFolderUris.add(workspaceFolderUri)) return
        }

        try {
            sendChange(
                DidChangeWorkspaceFoldersParams(
                    WorkspaceFoldersChangeEvent(
                        listOf(toWorkspaceFolder(workspaceFolderUri)),
                        emptyList(),
                    ),
                ),
            )
        } catch (t: Throwable) {
            synchronized(this) {
                registeredWorkspaceFolderUris.remove(workspaceFolderUri)
            }
            throw t
        }
    }

    fun synchronizeWorkspaceFolders(
        newWorkspaceFolderUris: Set<String>,
        toWorkspaceFolder: (String) -> WorkspaceFolder,
        sendChange: (DidChangeWorkspaceFoldersParams) -> Unit,
    ) {
        val previousWorkspaceFolderUris: Set<String>
        val addedWorkspaceFolderUris: Set<String>
        val removedWorkspaceFolderUris: Set<String>
        synchronized(this) {
            addedWorkspaceFolderUris = newWorkspaceFolderUris - registeredWorkspaceFolderUris
            removedWorkspaceFolderUris = registeredWorkspaceFolderUris - newWorkspaceFolderUris
            if (addedWorkspaceFolderUris.isEmpty() && removedWorkspaceFolderUris.isEmpty()) return

            previousWorkspaceFolderUris = LinkedHashSet(registeredWorkspaceFolderUris)
            registeredWorkspaceFolderUris.clear()
            registeredWorkspaceFolderUris.addAll(newWorkspaceFolderUris)
        }

        try {
            sendChange(
                DidChangeWorkspaceFoldersParams(
                    WorkspaceFoldersChangeEvent(
                        addedWorkspaceFolderUris.map(toWorkspaceFolder),
                        removedWorkspaceFolderUris.map(toWorkspaceFolder),
                    ),
                ),
            )
        } catch (t: Throwable) {
            synchronized(this) {
                registeredWorkspaceFolderUris.clear()
                registeredWorkspaceFolderUris.addAll(previousWorkspaceFolderUris)
            }
            throw t
        }
    }
}
