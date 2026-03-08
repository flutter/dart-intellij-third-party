// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import org.dartlang.analysis.server.protocol.AddContentOverlay
import org.dartlang.analysis.server.protocol.RemoveContentOverlay
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LspDocumentSyncManagerTest {
    private val openCalls = mutableListOf<DidOpenTextDocumentParams>()
    private val changeCalls = mutableListOf<DidChangeTextDocumentParams>()
    private val closeCalls = mutableListOf<DidCloseTextDocumentParams>()

    @Before
    fun setUp() {
        openCalls.clear()
        changeCalls.clear()
        closeCalls.clear()
    }

    private fun createManager(syncKind: TextDocumentSyncKind = TextDocumentSyncKind.Full) =
        LspDocumentSyncManager(
            onDidOpen = { openCalls.add(it) },
            onDidChange = { changeCalls.add(it) },
            onDidClose = { closeCalls.add(it) },
            syncKindProvider = { syncKind },
        )

    @Test
    fun `first AddContentOverlay sends didOpen`() {
        val manager = createManager()
        manager.applyOverlay("file:///test.dart", AddContentOverlay("void main() {}"))

        assertEquals(1, openCalls.size)
        assertEquals(0, changeCalls.size)
        assertEquals(0, closeCalls.size)

        val params = openCalls[0]
        assertEquals("file:///test.dart", params.textDocument.uri)
        assertEquals("dart", params.textDocument.languageId)
        assertEquals(1, params.textDocument.version)
        assertEquals("void main() {}", params.textDocument.text)
    }

    @Test
    fun `second AddContentOverlay with different content sends didChange`() {
        val manager = createManager()
        manager.applyOverlay("file:///test.dart", AddContentOverlay("void main() {}"))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("void main() { print('hi'); }"))

        assertEquals(1, openCalls.size)
        assertEquals(1, changeCalls.size)

        val params = changeCalls[0]
        assertEquals("file:///test.dart", params.textDocument.uri)
        assertEquals(2, params.textDocument.version)
        assertEquals("void main() { print('hi'); }", params.contentChanges[0].text)
    }

    @Test
    fun `second AddContentOverlay with same content is no-op`() {
        val manager = createManager()
        manager.applyOverlay("file:///test.dart", AddContentOverlay("void main() {}"))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("void main() {}"))

        assertEquals(1, openCalls.size)
        assertEquals(0, changeCalls.size)
    }

    @Test
    fun `RemoveContentOverlay sends didClose`() {
        val manager = createManager()
        manager.applyOverlay("file:///test.dart", AddContentOverlay("void main() {}"))
        manager.applyOverlay("file:///test.dart", RemoveContentOverlay())

        assertEquals(1, openCalls.size)
        assertEquals(1, closeCalls.size)
        assertEquals("file:///test.dart", closeCalls[0].textDocument.uri)
    }

    @Test
    fun `RemoveContentOverlay for unknown file is no-op`() {
        val manager = createManager()
        manager.applyOverlay("file:///unknown.dart", RemoveContentOverlay())

        assertEquals(0, openCalls.size)
        assertEquals(0, changeCalls.size)
        assertEquals(0, closeCalls.size)
    }

    @Test
    fun `version increments across multiple changes`() {
        val manager = createManager()
        manager.applyOverlay("file:///test.dart", AddContentOverlay("v1"))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("v2"))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("v3"))

        assertEquals(1, openCalls[0].textDocument.version)
        assertEquals(2, changeCalls[0].textDocument.version)
        assertEquals(3, changeCalls[1].textDocument.version)
    }

    @Test
    fun `reopen after close starts fresh version`() {
        val manager = createManager()
        manager.applyOverlay("file:///test.dart", AddContentOverlay("v1"))
        manager.applyOverlay("file:///test.dart", RemoveContentOverlay())
        manager.applyOverlay("file:///test.dart", AddContentOverlay("v2"))

        assertEquals(2, openCalls.size)
        assertEquals(1, openCalls[0].textDocument.version)
        assertEquals(1, openCalls[1].textDocument.version)
    }

    @Test
    fun `multiple files are tracked independently`() {
        val manager = createManager()
        manager.applyOverlay("file:///a.dart", AddContentOverlay("a1"))
        manager.applyOverlay("file:///b.dart", AddContentOverlay("b1"))
        manager.applyOverlay("file:///a.dart", AddContentOverlay("a2"))

        assertEquals(2, openCalls.size)
        assertEquals(1, changeCalls.size)
        assertEquals("file:///a.dart", changeCalls[0].textDocument.uri)
    }

    @Test
    fun `clear resets all state`() {
        val manager = createManager()
        manager.applyOverlay("file:///a.dart", AddContentOverlay("a1"))
        manager.clear()

        openCalls.clear()
        changeCalls.clear()

        manager.applyOverlay("file:///a.dart", AddContentOverlay("a1"))

        assertEquals(1, openCalls.size)
        assertEquals(0, changeCalls.size)
    }

    @Test
    fun `full sync sends content without range`() {
        val manager = createManager(TextDocumentSyncKind.Full)
        manager.applyOverlay("file:///test.dart", AddContentOverlay("old"))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("new"))

        val changeEvent = changeCalls[0].contentChanges[0]
        assertNull(changeEvent.range)
        assertEquals("new", changeEvent.text)
    }

    @Test
    fun `incremental sync sends content with full document range`() {
        val manager = createManager(TextDocumentSyncKind.Incremental)
        manager.applyOverlay("file:///test.dart", AddContentOverlay("line1\nline2"))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("replaced"))

        val changeEvent = changeCalls[0].contentChanges[0]
        assertNotNull(changeEvent.range)
        assertEquals(Position(0, 0), changeEvent.range.start)
        assertEquals(Position(1, 5), changeEvent.range.end)
        assertEquals("replaced", changeEvent.text)
    }

    @Test
    fun `end position handles CRLF`() {
        val manager = createManager(TextDocumentSyncKind.Incremental)
        manager.applyOverlay("file:///test.dart", AddContentOverlay("a\r\nb\r\nc"))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("replaced"))

        val changeEvent = changeCalls[0].contentChanges[0]
        assertEquals(Position(2, 1), changeEvent.range.end)
    }

    @Test
    fun `end position handles lone CR`() {
        val manager = createManager(TextDocumentSyncKind.Incremental)
        manager.applyOverlay("file:///test.dart", AddContentOverlay("a\rb\rc"))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("replaced"))

        val changeEvent = changeCalls[0].contentChanges[0]
        assertEquals(Position(2, 1), changeEvent.range.end)
    }

    @Test
    fun `end position for empty string`() {
        val manager = createManager(TextDocumentSyncKind.Incremental)
        manager.applyOverlay("file:///test.dart", AddContentOverlay(""))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("x"))

        val changeEvent = changeCalls[0].contentChanges[0]
        assertEquals(Position(0, 0), changeEvent.range.end)
    }

    @Test
    fun `end position for trailing newline`() {
        val manager = createManager(TextDocumentSyncKind.Incremental)
        manager.applyOverlay("file:///test.dart", AddContentOverlay("line1\n"))
        manager.applyOverlay("file:///test.dart", AddContentOverlay("x"))

        val changeEvent = changeCalls[0].contentChanges[0]
        assertEquals(Position(1, 0), changeEvent.range.end)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported overlay type throws`() {
        val manager = createManager()
        manager.applyOverlay("file:///test.dart", "not an overlay")
    }

    @Test
    fun `closing one file does not affect the other`() {
        val manager = createManager()
        manager.applyOverlay("file:///a.dart", AddContentOverlay("a"))
        manager.applyOverlay("file:///b.dart", AddContentOverlay("b"))
        manager.applyOverlay("file:///a.dart", RemoveContentOverlay())

        assertEquals(1, closeCalls.size)
        assertEquals("file:///a.dart", closeCalls[0].textDocument.uri)

        changeCalls.clear()
        manager.applyOverlay("file:///b.dart", AddContentOverlay("b2"))
        assertEquals(1, changeCalls.size)
        assertEquals("file:///b.dart", changeCalls[0].textDocument.uri)
        assertEquals(2, changeCalls[0].textDocument.version)
    }
}
