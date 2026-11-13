/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.LspServerState
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.lsp.DartLspServerDescriptor
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameFile
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture

class DartLspRenameRequestsTest : DartCodeInsightFixtureTestCase() {

    private fun findFile(uri: String): VirtualFile? = VirtualFileManager.getInstance().findFileByUrl(uri)

    private fun edit(line: Int, start: Int, end: Int, newText: String) =
        TextEdit(Range(Position(line, start), Position(line, end)), newText)

    private fun documentEdit(file: VirtualFile, vararg edits: TextEdit): Either<TextDocumentEdit, ResourceOperation> =
        Either.forLeft(TextDocumentEdit(VersionedTextDocumentIdentifier(file.url, null), edits.toList()))

    private fun success(result: DartLspRenameResult<List<DartLspFileTextEdit>>): List<DartLspFileTextEdit> {
        assertTrue("Expected Success but was $result", result is DartLspRenameResult.Success)
        return (result as DartLspRenameResult.Success).value
    }

    fun testDocumentChangesToFileTextEdits() {
        val a = myFixture.addFileToProject("lib/a.dart", "class Greeter {\n  String greet() => 'hi';\n}\n").virtualFile
        val b = myFixture.addFileToProject("lib/b.dart", "void f() => Greeter().greet();\n").virtualFile

        val result = toFileTextEdits(
            WorkspaceEdit(listOf(documentEdit(a, edit(1, 9, 14, "welcome")), documentEdit(b, edit(0, 22, 27, "welcome")))),
            ::findFile,
        )

        assertEquals(
            listOf(
                DartLspFileTextEdit(a, TextRange(25, 30), "welcome"),
                DartLspFileTextEdit(b, TextRange(22, 27), "welcome"),
            ),
            success(result),
        )
    }

    fun testChangesMapToFileTextEdits() {
        val a = myFixture.addFileToProject("lib/a.dart", "var greet = 1;\n").virtualFile

        val result = toFileTextEdits(WorkspaceEdit(mapOf(a.url to listOf(edit(0, 4, 9, "hello")))), ::findFile)

        assertEquals(listOf(DartLspFileTextEdit(a, TextRange(4, 9), "hello")), success(result))
    }

    fun testEitherWrappedTextEditsAreUnwrapped() {
        // lsp4j 1.0 (IntelliJ 2026.3) declares TextDocumentEdit.edits as List<Either<TextEdit, SnippetTextEdit>>.
        val a = myFixture.addFileToProject("lib/a.dart", "var greet = 1;\n").virtualFile
        val wrapped: MutableList<Any> = mutableListOf(Either.forLeft<TextEdit, Any>(edit(0, 4, 9, "hello")))
        @Suppress("UNCHECKED_CAST")
        val textDocumentEdit = TextDocumentEdit(VersionedTextDocumentIdentifier(a.url, null), wrapped as List<TextEdit>)

        val result = toFileTextEdits(WorkspaceEdit(listOf(Either.forLeft(textDocumentEdit))), ::findFile)

        assertEquals(listOf(DartLspFileTextEdit(a, TextRange(4, 9), "hello")), success(result))
    }

    fun testResourceOperationIsFailure() {
        val a = myFixture.addFileToProject("lib/greeter.dart", "class Greeter {}\n").virtualFile
        val edit = WorkspaceEdit(
            listOf(
                documentEdit(a, edit(0, 6, 13, "Welcomer")),
                Either.forRight<TextDocumentEdit, ResourceOperation>(RenameFile(a.url, a.url.replace("greeter", "welcomer"))),
            )
        )

        assertTrue(toFileTextEdits(edit, ::findFile) is DartLspRenameResult.Failure)
    }

    fun testUnknownFileIsFailure() {
        val edit = WorkspaceEdit(mapOf("file:///does/not/exist.dart" to listOf(edit(0, 0, 1, "x"))))

        val result = toFileTextEdits(edit, ::findFile)

        assertTrue(result is DartLspRenameResult.Failure)
        assertTrue((result as DartLspRenameResult.Failure).message.contains("exist.dart"))
    }

    fun testPositionOutsideDocumentIsFailure() {
        val a = myFixture.addFileToProject("lib/a.dart", "var greet = 1;\n").virtualFile

        val result = toFileTextEdits(WorkspaceEdit(mapOf(a.url to listOf(edit(5, 0, 1, "x")))), ::findFile)

        assertTrue(result is DartLspRenameResult.Failure)
    }

    fun testRenameSendsUnsavedChangesFirst() {
        val a = myFixture.addFileToProject("lib/a.dart", "var greet = 1;\n").virtualFile
        var synced = 0
        val requests = DartLspRenameRequestsImpl(project).apply { syncFileContents = { synced++ } }

        // No Dart LSP server runs in light tests, but the unsaved changes must be sent before the server is asked.
        val result = ApplicationManager.getApplication().executeOnPooledThread<DartLspRenameResult<*>> {
            requests.rename(a, 4, "hello")
        }.get()

        assertEquals(DartLspRenameResult.NoServer, result)
        assertEquals(1, synced)
    }

    fun testPrepareRenameSendsUnsavedChangesFirst() {
        val a = myFixture.addFileToProject("lib/a.dart", "var greet = 1;\n").virtualFile
        var synced = 0
        val requests = DartLspRenameRequestsImpl(project).apply { syncFileContents = { synced++ } }

        val result = ApplicationManager.getApplication().executeOnPooledThread<DartLspRenameResult<*>> {
            requests.prepareRename(a, 4)
        }.get()

        assertEquals(DartLspRenameResult.NoServer, result)
        assertEquals(1, synced)
    }

    fun testRequestThatThrowsAtOnceBecomesFailure() {
        // The JetBrains LSP client runs the request on its own executor: an exception there would leave the request
        // without an answer until the timeout, so it has to become a Failure.
        val a = myFixture.addFileToProject("lib/a.dart", "var greet = 1;\n").virtualFile
        val textDocumentService = proxy<TextDocumentService> { name, _ ->
            if (name == "rename") throw IllegalStateException("Stream closed") else null
        }
        val lsp4jServer = proxy<Lsp4jServer> { name, _ -> if (name == "getTextDocumentService") textDocumentService else null }
        val descriptor = DartLspServerDescriptor(project)
        val server = proxy<LspServer> { name, args ->
            when (name) {
                "getDescriptor" -> descriptor
                "getState" -> LspServerState.Running
                "sendRequestSync" -> {
                    @Suppress("UNCHECKED_CAST")
                    val sender = requireNotNull(args)[1] as (Lsp4jServer) -> CompletableFuture<*>
                    sender(lsp4jServer).get()
                }
                else -> null
            }
        }
        val requests = DartLspRenameRequestsImpl(project).apply {
            syncFileContents = {}
            findServer = { server }
        }

        val result = ApplicationManager.getApplication().executeOnPooledThread<DartLspRenameResult<*>> {
            requests.rename(a, 4, "hello")
        }.get()

        assertEquals(DartLspRenameResult.Failure("Stream closed"), result)
    }

    private inline fun <reified T> proxy(crossinline answer: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            answer(method.name, args)
        } as T

    fun testUtf16Positions() {
        // LSP positions count UTF-16 code units: '😀' is two of them, like in a Java String.
        val a = myFixture.addFileToProject("lib/a.dart", "var s = 'ä😀'; var greet = 1;\n").virtualFile

        val result = toFileTextEdits(WorkspaceEdit(mapOf(a.url to listOf(edit(0, 19, 24, "hello")))), ::findFile)

        assertEquals(listOf(DartLspFileTextEdit(a, TextRange(19, 24), "hello")), success(result))
        assertEquals("greet", "var s = 'ä😀'; var greet = 1;\n".substring(19, 24))
    }
}
