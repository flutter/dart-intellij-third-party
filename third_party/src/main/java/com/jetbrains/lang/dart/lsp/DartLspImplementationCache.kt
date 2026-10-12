/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.psi.util.PsiModificationTracker
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.util.concurrent.atomic.AtomicLong

/** One completed implementation response, independent of the DAS hierarchy cache. */
@Service(Service.Level.PROJECT)
class DartLspImplementationCache(private val project: Project) : Disposable {
    private val documentGeneration = AtomicLong()
    @Volatile private var lastResult: Entry? = null

    init {
        // Include uncommitted documents everywhere, including SDK and pub-cache files.
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                documentGeneration.incrementAndGet()
            }
        }, this)
    }

    internal data class Key(
        val file: VirtualFile, val uri: String, val line: Int, val character: Int,
        val documentStamp: Long, val documents: Long, val psi: Long, val vfs: Long,
        val roots: Long, val server: LspServer
    ) {
        fun matches(other: Key): Boolean = server === other.server && file === other.file &&
            uri == other.uri && line == other.line && character == other.character &&
            documentStamp == other.documentStamp && documents == other.documents &&
            psi == other.psi && vfs == other.vfs && roots == other.roots
    }

    // Retain only scalars; neither mutable protocol objects nor resolved PSI escape into the cache.
    internal data class Target(val uri: String, val startLine: Int, val startCharacter: Int,
                               val endLine: Int, val endCharacter: Int) {
        fun location() = Location(uri, Range(Position(startLine, startCharacter), Position(endLine, endCharacter)))
    }

    private data class Entry(val key: Key, val targets: List<Target>)

    internal fun snapshot(file: VirtualFile, document: Document, uri: String, position: Position, server: LspServer) = Key(
        file, uri, position.line, position.character, document.modificationStamp, documentGeneration.get(),
        PsiModificationTracker.getInstance(project).modificationCount,
        VirtualFileManager.getInstance().modificationCount,
        ProjectRootModificationTracker.getInstance(project).modificationCount, server
    )

    internal fun get(key: Key): List<Location>? = lastResult?.takeIf { it.key.matches(key) }?.targets?.map { it.location() }

    internal fun normalize(locations: List<Location?>): List<Target> = locations.mapNotNull { location ->
        val uri = location?.uri ?: return@mapNotNull null
        val start = location.range?.start ?: return@mapNotNull null
        val end = location.range?.end ?: return@mapNotNull null
        Target(uri, start.line, start.character, end.line, end.character)
    }

    internal fun put(key: Key, targets: List<Target>) {
        lastResult = Entry(key, targets.toList())
    }

    override fun dispose() {
        lastResult = null
    }
}
