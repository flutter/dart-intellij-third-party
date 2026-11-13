/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.platform.dartlsp.api.LspServerNotificationsHandler
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture

class DartLspServerNotificationsHandlerTest : DartCodeInsightFixtureTestCase() {
    private val uri = "file:///project/lib/a.dart"
    private val edit = ApplyWorkspaceEditParams(
        WorkspaceEdit(mapOf(uri to listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "// x\n"))))
    )

    fun testEditWithoutCaptureGoesToTheDelegate() {
        val delegate = RecordingHandler()
        val handler = DartLspServerNotificationsHandler(delegate.proxy, DartLspApplyEditCapture(project))

        handler.applyEdit(edit)

        assertEquals(listOf("applyEdit"), delegate.calls)
    }

    fun testCapturedEditIsAnsweredWithoutTheDelegate() {
        val delegate = RecordingHandler()
        val capture = DartLspApplyEditCapture(project)
        val handler = DartLspServerNotificationsHandler(delegate.proxy, capture)
        capture.start("/project/lib/a.dart")

        val response = handler.applyEdit(edit).get()

        assertTrue(response.isApplied)
        assertEmpty(delegate.calls)
    }

    fun testOtherMessagesGoToTheDelegate() {
        val delegate = RecordingHandler()
        val handler = DartLspServerNotificationsHandler(delegate.proxy, DartLspApplyEditCapture(project))

        handler.logMessage(MessageParams(MessageType.Info, "hello"))

        assertEquals(listOf("logMessage"), delegate.calls)
    }

    private class RecordingHandler {
        val calls = mutableListOf<String>()
        val proxy = Proxy.newProxyInstance(
            LspServerNotificationsHandler::class.java.classLoader,
            arrayOf(LspServerNotificationsHandler::class.java),
        ) { _, method, _ ->
            calls += method.name
            if (method.name == "applyEdit") CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(true)) else null
        } as LspServerNotificationsHandler
    }
}
