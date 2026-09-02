/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.analyzer

import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditResult
import org.dartlang.analysis.server.protocol.DartLspCreateFile
import org.dartlang.analysis.server.protocol.DartLspPosition
import org.dartlang.analysis.server.protocol.DartLspRange
import org.dartlang.analysis.server.protocol.DartLspTextDocumentEdit
import org.dartlang.analysis.server.protocol.DartLspTextEdit
import org.dartlang.analysis.server.protocol.DartLspVersionedTextDocumentIdentifier
import org.dartlang.analysis.server.protocol.DartLspWorkspaceEdit
import org.dartlang.analysis.server.protocol.MessageAction
import java.util.concurrent.CompletableFuture

class DartLspWorkspaceEditTest : DartCodeInsightFixtureTestCase() {

    private fun createStubSocket(): AnalysisServerSocket = object : AnalysisServerSocket {
        override fun getErrorStream(): ByteLineReaderStream? = null
        override fun getRequestSink(): RequestSink? = null
        override fun getResponseStream(): ResponseStream? = null
        override fun isOpen(): Boolean = true
        override fun start() {}
        override fun stop() {}
    }

    private open class TestRemoteAnalysisServer(socket: AnalysisServerSocket) : RemoteAnalysisServerImpl(socket) {
        override fun isSocketOpen(): Boolean = true
        override fun server_openUrlRequest(url: String?) {}
        override fun server_showMessageRequest(
            type: String?,
            message: String?,
            actions: MutableList<MessageAction>?,
            consumer: ShowMessageRequestConsumer?
        ) {}
        override fun lsp_workspaceApplyEdit(
            params: DartLspApplyWorkspaceEditParams?,
            consumer: DartLspWorkspaceApplyEditRequestConsumer?
        ) {}
        fun testProcessResponse(response: JsonObject) {
            processResponse(response)
        }
    }

    fun testBuildLspCapabilitiesWithDocumentChanges() {
        val caps38 = DartAnalysisServerService.buildLspCapabilities("3.8.0")
        val workspace38 = caps38.getAsJsonObject("workspace")
        assertNotNull("workspace capability should be present for SDK >= 3.8", workspace38)
        assertTrue(workspace38.get("applyEdit").asBoolean)
        val workspaceEdit38 = workspace38.getAsJsonObject("workspaceEdit")
        assertNotNull(workspaceEdit38)
        assertTrue("documentChanges should be true for SDK >= 3.8", workspaceEdit38.get("documentChanges").asBoolean)

        val caps37 = DartAnalysisServerService.buildLspCapabilities("3.7.0")
        assertNull("workspace capability should NOT be present for SDK < 3.8", caps37.getAsJsonObject("workspace"))
    }

    fun testRemoteAnalysisServerParsesDocumentChanges() {
        var capturedParams: DartLspApplyWorkspaceEditParams? = null
        val server = object : TestRemoteAnalysisServer(createStubSocket()) {
            override fun lsp_workspaceApplyEdit(params: DartLspApplyWorkspaceEditParams?, consumer: DartLspWorkspaceApplyEditRequestConsumer?) {
                capturedParams = params
            }
        }

        val json = """
        {
          "id": "das_1",
          "method": "lsp.handle",
          "params": {
            "lspMessage": {
              "id": "lsp_1",
              "jsonrpc": "2.0",
              "method": "workspace/applyEdit",
              "params": {
                "label": "Organize Imports",
                "edit": {
                  "documentChanges": [
                    {
                      "textDocument": {
                        "uri": "file:///path/to/test.dart",
                        "version": 42
                      },
                      "edits": [
                        {
                          "range": {
                            "start": { "line": 1, "character": 2 },
                            "end": { "line": 3, "character": 4 }
                          },
                          "newText": "replacement text"
                        }
                      ]
                    }
                  ]
                }
              }
            }
          }
        }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(json).asJsonObject
        server.testProcessResponse(jsonObject)

        val params = checkNotNull(capturedParams) { "capturedParams should not be null" }
        assertEquals("Organize Imports", params.label)
        assertNull("legacy changes should be null", params.workspaceEdit.changes)

        val docChanges = checkNotNull(params.workspaceEdit.documentChanges) { "documentChanges should be present" }
        assertEquals(1, docChanges.size)

        val textDocEdit = docChanges[0] as DartLspTextDocumentEdit
        assertEquals("file:///path/to/test.dart", textDocEdit.textDocument.uri)
        assertEquals(42, textDocEdit.textDocument.version)
        assertEquals(1, textDocEdit.edits.size)

        val edit = textDocEdit.edits[0]
        assertEquals("replacement text", edit.newText)
        assertEquals(1, edit.range.start.line)
        assertEquals(2, edit.range.start.character)
        assertEquals(3, edit.range.end.line)
        assertEquals(4, edit.range.end.character)
    }

    fun testRemoteAnalysisServerIgnoresChangesWithoutDocumentChanges() {
        var capturedParams: DartLspApplyWorkspaceEditParams? = null
        val server = object : TestRemoteAnalysisServer(createStubSocket()) {
            override fun lsp_workspaceApplyEdit(params: DartLspApplyWorkspaceEditParams?, consumer: DartLspWorkspaceApplyEditRequestConsumer?) {
                capturedParams = params
            }
        }

        val json = """
        {
          "id": "das_2",
          "method": "lsp.handle",
          "params": {
            "lspMessage": {
              "id": "lsp_2",
              "jsonrpc": "2.0",
              "method": "workspace/applyEdit",
              "params": {
                "label": "Legacy QuickFix",
                "edit": {
                  "changes": {
                    "file:///path/to/legacy.dart": []
                  }
                }
              }
            }
          }
        }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(json).asJsonObject
        server.testProcessResponse(jsonObject)

        assertNull("capturedParams should be null when edit has no documentChanges", capturedParams)
    }

    fun testDartAnalysisServerImplAppliesDocumentChanges() {
        val file = myFixture.addFileToProject("lib/example.dart", "void main() {\n  print('old');\n}\n")
        val fileUrl = file.virtualFile.url

        val server = DartAnalysisServerImpl(project, createStubSocket())

        val textEdit = DartLspTextEdit(
            DartLspRange(DartLspPosition(1, 9), DartLspPosition(1, 12)),
            "new"
        )
        val docEdit = DartLspTextDocumentEdit(
            DartLspVersionedTextDocumentIdentifier(fileUrl, 1),
            listOf(textEdit)
        )
        val workspaceEdit = DartLspWorkspaceEdit(null, listOf(docEdit))
        val params = DartLspApplyWorkspaceEditParams(workspaceEdit, "Test Document Edit")

        val future = CompletableFuture<DartLspApplyWorkspaceEditResult>()
        server.lsp_workspaceApplyEdit(params) { result ->
            future.complete(result)
        }

        PlatformTestUtil.waitWithEventsDispatching(
            "Timed out waiting for workspace edit to be applied",
            { future.isDone },
            5
        )
        val result = future.get()
        assertTrue("workspaceEdit should report applied = true", result.applied)

        val doc = checkNotNull(FileDocumentManager.getInstance().getDocument(file.virtualFile))
        assertEquals("void main() {\n  print('new');\n}\n", doc.text)
    }

    fun testDartAnalysisServerImplAppliesEmptyDocumentChangesAsNoOp() {
        val server = DartAnalysisServerImpl(project, createStubSocket())
        val workspaceEdit = DartLspWorkspaceEdit(null, emptyList())
        val params = DartLspApplyWorkspaceEditParams(workspaceEdit, "No-op Edit")

        val future = CompletableFuture<DartLspApplyWorkspaceEditResult>()
        server.lsp_workspaceApplyEdit(params) { result ->
            future.complete(result)
        }

        PlatformTestUtil.waitWithEventsDispatching(
            "Timed out waiting for no-op workspace edit",
            { future.isDone },
            5
        )
        val result = future.get()
        assertTrue("empty workspaceEdit should report applied = true as a successful no-op", result.applied)
    }

    fun testDartAnalysisServerImplRejectsUnsupportedDocumentChange() {
        val server = DartAnalysisServerImpl(project, createStubSocket())
        val workspaceEdit = DartLspWorkspaceEdit(null, listOf(DartLspCreateFile()))
        val params = DartLspApplyWorkspaceEditParams(workspaceEdit, "Unsupported CreateFile Edit")

        val future = CompletableFuture<DartLspApplyWorkspaceEditResult>()
        server.lsp_workspaceApplyEdit(params) { result ->
            future.complete(result)
        }

        PlatformTestUtil.waitWithEventsDispatching(
            "Timed out waiting for workspace edit to complete",
            { future.isDone },
            5
        )
        val result = future.get()
        assertFalse("unsupported document change should report applied = false", result.applied)
    }
}
