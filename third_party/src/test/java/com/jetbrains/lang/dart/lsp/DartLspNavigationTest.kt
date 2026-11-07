/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.dartlsp.api.LspServerDescriptor
import com.intellij.platform.dartlsp.api.LspCommunicationChannel
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class DartLspNavigationTest : DartCodeInsightFixtureTestCase() {
    fun testRunningServerSuperNavigatesToNativeTargets() {
        val previous = DartConfigurable.isExperimentalLspFeaturesEnabled(project)
        try {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
            checkRunningServerSuperNavigation()
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, previous)
        }
    }

    private fun checkRunningServerSuperNavigation() {
        val target = myFixture.addFileToProject("target.dart", "class Target {}")
        val targetUri = target.virtualFile.url
        val superRequests = CopyOnWriteArrayList<org.eclipse.lsp4j.TextDocumentPositionParams>()
        val superResponse = AtomicReference<CompletableFuture<Location?>>(CompletableFuture.completedFuture(location(targetUri, 0, 6)))
        val manager = LspServerManager.getInstance(project)
        ServerSocket(0).use { listener ->
            val endpoint = object : DartLanguageServer, TextDocumentService, WorkspaceService {
                override fun diagnosticServer() = CompletableFuture.completedFuture(DiagnosticServerResult(0))
                override fun getSuper(params: org.eclipse.lsp4j.TextDocumentPositionParams): CompletableFuture<Location?> {
                    superRequests.add(params)
                    return superResponse.get()
                }
                override fun initialize(params: InitializeParams) = CompletableFuture.completedFuture(
                    InitializeResult(ServerCapabilities())
                )
                override fun shutdown() = CompletableFuture.completedFuture<Any>(null)
                override fun exit() {}
                override fun getTextDocumentService(): TextDocumentService = this
                override fun getWorkspaceService(): WorkspaceService = this
                override fun didOpen(params: org.eclipse.lsp4j.DidOpenTextDocumentParams) {}
                override fun didChange(params: org.eclipse.lsp4j.DidChangeTextDocumentParams) {}
                override fun didClose(params: org.eclipse.lsp4j.DidCloseTextDocumentParams) {}
                override fun didSave(params: org.eclipse.lsp4j.DidSaveTextDocumentParams) {}
                override fun didChangeConfiguration(params: org.eclipse.lsp4j.DidChangeConfigurationParams) {}
                override fun didChangeWatchedFiles(params: org.eclipse.lsp4j.DidChangeWatchedFilesParams) {}
            }
            val connection = ApplicationManager.getApplication().executeOnPooledThread(java.util.concurrent.Callable {
                listener.accept()
            })
            val testDescriptor = object : LspServerDescriptor(project, "Navigation integration") {
                override val lsp4jServerClass = DartLanguageServer::class.java
                override val lspCommunicationChannel = LspCommunicationChannel.Socket(listener.localPort, startProcess = false)
                override fun isSupportedFile(file: VirtualFile) = file.extension == "dart"
                override fun getFileUri(file: VirtualFile) = file.url
                override fun findFileByUri(fileUri: String) = VirtualFileManager.getInstance().findFileByUrl(fileUri)
            }
            manager.ensureServerStarted(DartLspServerSupportProvider::class.java, testDescriptor)
            PlatformTestUtil.waitWithEventsDispatching("LSP connection", { connection.isDone }, 10)
            connection.get().use { socket ->
                val launcher = Launcher.Builder<LanguageClient>().setLocalService(endpoint)
                    .setRemoteInterface(LanguageClient::class.java)
                    .setInput(socket.getInputStream()).setOutput(socket.getOutputStream()).create()
                val listening = launcher.startListening()
                try {
                    PlatformTestUtil.waitWithEventsDispatching("LSP initialization", {
                        manager.getServersForProvider(DartLspServerSupportProvider::class.java).any { it.state == LspServerState.Running }
                    }, 10)
                    // The direct action sends the actual caret, not an enclosing declaration's name.
                    val superSource = myFixture.addFileToProject("superSource.dart", "class Source {\n  void method() {}\n}")
                    myFixture.openFileInEditor(superSource.virtualFile)
                    myFixture.editor.caretModel.moveToLogicalPosition(com.intellij.openapi.editor.LogicalPosition(1, 17))
                    val sourceEditor = myFixture.editor
                    fun navigateSuper(targetFile: com.intellij.psi.PsiFile, line: Int, character: Int) {
                        superResponse.set(CompletableFuture.completedFuture(location(targetFile.virtualFile.url, line, character)))
                        val count = superRequests.size
                        com.jetbrains.lang.dart.ide.actions.DartServerGotoSuperHandler().invoke(project, sourceEditor, superSource)
                        PlatformTestUtil.waitWithEventsDispatching("Super navigation", {
                            val selected = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor
                            superRequests.size == count + 1 && selected != null &&
                                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(selected.document) == targetFile.virtualFile &&
                                selected.caretModel.logicalPosition == com.intellij.openapi.editor.LogicalPosition(line, character)
                        }, 10)
                        assertEquals(superSource.virtualFile.url, superRequests.last().textDocument.uri)
                        assertEquals(Position(1, 17), superRequests.last().position)
                    }
                    navigateSuper(target, 0, 6)
                    val members = myFixture.addFileToProject("superMembers.dart", "class Parent {\n  void method() {}\n  Parent();\n}")
                    navigateSuper(members, 1, 7)
                    navigateSuper(members, 2, 2)
                    // Native Object results are not filtered by the legacy client-side exclusion.
                    val objectFile = myFixture.addFileToProject("object.dart", "class Object {}")
                    navigateSuper(objectFile, 0, 6)

                } finally {
                    // Drain file-open synchronization before shutting down the fixture server.
                    com.intellij.openapi.application.impl.NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
                    manager.stopServers(DartLspServerSupportProvider::class.java)
                    PlatformTestUtil.waitWithEventsDispatching("LSP shutdown", {
                        manager.getServersForProvider(DartLspServerSupportProvider::class.java).none { it.state == LspServerState.Running }
                    }, 10)
                    listening.cancel(true)
                }
            }
        }
    }

    private fun location(uri: String, line: Int, character: Int) =
        Location(uri, Range(Position(line, character), Position(line, character + 1)))
}
