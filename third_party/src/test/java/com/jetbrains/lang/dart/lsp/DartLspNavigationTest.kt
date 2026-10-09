/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.dartlsp.api.LspServerDescriptor
import com.intellij.platform.dartlsp.api.LspCommunicationChannel
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartComponent
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class DartLspNavigationTest : DartCodeInsightFixtureTestCase() {
    // Light fixtures use an in-memory VFS, rather than the local filesystem used by the real descriptor.
    private val descriptor: LspServerDescriptor
        get() = object : LspServerDescriptor(project, "Navigation test") {
            override fun isSupportedFile(file: VirtualFile) = true
            override fun getFileUri(file: VirtualFile) = file.url
            override fun findFileByUri(fileUri: String) = VirtualFileManager.getInstance().findFileByUrl(fileUri)
        }

    fun testLocationsResolveToDeduplicatedScopedDeclarations() {
        val file = myFixture.addFileToProject("targets.dart", "class First {}\nclass Second {}")
        val excluded = myFixture.addFileToProject("excluded.dart", "class Excluded {}")
        val descriptor = descriptor
        val first = location(descriptor.getFileUri(file.virtualFile), 0, 6)
        val second = location(first.uri, 1, 6)
        val targets = mutableListOf<PsiElement>()

        DartLspNavigationService.processLocations(
            project, descriptor,
            listOf(first, first, second, location(descriptor.getFileUri(excluded.virtualFile), 0, 6)),
            GlobalSearchScope.fileScope(file), Processor { targets.add(it); true }
        )

        assertEquals(listOf("First", "Second"), targets.map { (it as DartClass).name })
        assertTrue(targets.all { it.navigationElement.containingFile == file })
    }

    fun testMemberLocationsResolveToMembersRatherThanClasses() {
        val file = myFixture.addFileToProject("members.dart", "class Target {\n  void method() {}\n  Target operator +(Target other) => this;\n}")
        val descriptor = descriptor
        val uri = descriptor.getFileUri(file.virtualFile)
        val targets = mutableListOf<PsiElement>()
        DartLspNavigationService.processLocations(
            project, descriptor, listOf(location(uri, 1, 7), location(uri, 2, 18)),
            GlobalSearchScope.allScope(project), Processor { targets.add(it); true }
        )
        assertEquals(listOf("method", "+"), targets.map { (it as DartComponent).name })
    }

    fun testMissingFilesAndInvalidPositionsAreIgnored() {
        val file = myFixture.addFileToProject("target.dart", "class Target {}")
        val descriptor = descriptor
        val uri = descriptor.getFileUri(file.virtualFile)
        val targets = mutableListOf<PsiElement>()
        DartLspNavigationService.processLocations(
            project, descriptor,
            listOf(location("file:///nonexistent/navigation-target.dart", 0, 0), location(uri, -1, 0),
                   location(uri, 10, 0), location(uri, 0, -1), location(uri, 0, 100)),
            GlobalSearchScope.allScope(project), Processor { targets.add(it); true }
        )
        assertEmpty(targets)
    }

    fun testBodyWhitespaceIsNotADeclarationTarget() {
        val file = myFixture.addFileToProject("whitespace.dart", "class Target {\n  \n}")
        val targets = mutableListOf<PsiElement>()
        DartLspNavigationService.processLocations(
            project, descriptor, listOf(location(file.virtualFile.url, 1, 0)),
            GlobalSearchScope.allScope(project), Processor { targets.add(it); true }
        )
        assertEmpty("Body whitespace must not navigate to the enclosing class", targets)
    }

    fun testReversedAndOutOfBoundsEndsAreRejected() {
        val file = myFixture.addFileToProject("ranges.dart", "class Target {}")
        val targets = mutableListOf<PsiElement>()
        val invalidEnds = listOf(Position(0, 0), Position(-1, 6), Position(0, -1), Position(1, 0), Position(0, 100))
        for (end in invalidEnds) {
            DartLspNavigationService.processLocations(
                project, descriptor, listOf(Location(file.virtualFile.url, Range(Position(0, 6), end))),
                GlobalSearchScope.allScope(project), Processor { targets.add(it); true }
            )
            assertEmpty("Invalid end $end must not deliver a declaration", targets)
        }
    }

    fun testMalformedLocationsAndNameEndAreSkipped() {
        val file = myFixture.addFileToProject("malformed.dart", "class Target {}")
        val targets = mutableListOf<PsiElement>()
        DartLspNavigationService.processLocations(
            project, descriptor,
            listOf(null, Location(), Location().apply { uri = file.virtualFile.url },
                   Location(file.virtualFile.url, Range()), location(file.virtualFile.url, 0, 12)),
            GlobalSearchScope.allScope(project), Processor { targets.add(it); true }
        )
        assertEmpty(targets)
    }

    fun testDeclarationStartAndNameRemainNavigationTargets() {
        val file = myFixture.addFileToProject("declarations.dart", "class First {}\nclass Second {}")
        val targets = mutableListOf<PsiElement>()
        DartLspNavigationService.processLocations(
            project, descriptor,
            listOf(Location(file.virtualFile.url, Range(Position(0, 0), Position(0, 14))),
                   location(file.virtualFile.url, 1, 6)),
            GlobalSearchScope.allScope(project), Processor { targets.add(it); true }
        )
        assertEquals(listOf("First", "Second"), targets.map { (it as DartClass).name })
    }

    fun testConsumerStopsAfterFirstTarget() {
        val file = myFixture.addFileToProject("targets.dart", "class First {}\nclass Second {}")
        val descriptor = descriptor
        val uri = descriptor.getFileUri(file.virtualFile)
        val targets = mutableListOf<PsiElement>()
        DartLspNavigationService.processLocations(
            project, descriptor, listOf(location(uri, 0, 6), location(uri, 1, 6)),
            GlobalSearchScope.allScope(project), Processor { targets.add(it); false }
        )
        assertEquals(1, targets.size)
    }

    fun testCancellationPropagates() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val indicator = EmptyProgressIndicator()
            try {
                ProgressManager.getInstance().runProcess(Runnable {
                    indicator.cancel()
                    DartLspNavigationService.processLocations(
                        project, descriptor, listOf(location("file:///target.dart", 0, 0)),
                        GlobalSearchScope.allScope(project), Processor { fail("Canceled search must not deliver targets"); true }
                    )
                }, indicator)
                fail("Cancellation must propagate")
            } catch (_: ProcessCanceledException) {
                // Expected: cancellation must not be converted to an empty successful search.
            }
        }.get()
    }

    fun testRunningServerImplementationSearchAndSafeguards() {
        val previous = DartConfigurable.isExperimentalLspFeaturesEnabled(project)
        try {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
            checkRunningServerImplementationSearch()
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, previous)
        }
    }

    private fun checkRunningServerImplementationSearch() {
        val source = myFixture.addFileToProject("source.dart", "// source\nclass Source {}")
        val target = myFixture.addFileToProject("target.dart", "class Target {}")
        val declaration = requireNotNull(PsiTreeUtil.findChildOfType(source, DartClass::class.java))
        val targetUri = target.virtualFile.url
        val requests = CopyOnWriteArrayList<ImplementationParams>()
        val response = AtomicReference(CompletableFuture.completedFuture(
            Either.forLeft<List<Location>, List<LocationLink>>(listOf(location(targetUri, 0, 6)))
        ))
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
                    InitializeResult(ServerCapabilities().apply { setImplementationProvider(true) })
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
                override fun implementation(params: ImplementationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
                    requests.add(params)
                    return response.get()
                }
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
                    val targets = CopyOnWriteArrayList<PsiElement>()
                    fun search() {
                        val search = ApplicationManager.getApplication().executeOnPooledThread {
                            val query = runReadAction { DefinitionsScopedSearch.search(declaration) }
                            assertFalse("Search execution must not require caller read access", ApplicationManager.getApplication().isReadAccessAllowed)
                            query.forEach(Processor { targets.add(it); true })
                        }
                        PlatformTestUtil.waitWithEventsDispatching("Implementation search", { search.isDone }, 10)
                        search.get()
                    }
                    search()
                    assertEquals(1, requests.size)
                    assertEquals(source.virtualFile.url, requests.single().textDocument.uri)
                    assertEquals(Position(1, 6), requests.single().position)
                    assertEquals(listOf("Target"), targets.map { (it as DartClass).name })

                    val server = manager.getServersForProvider(DartLspServerSupportProvider::class.java).single()
                    val capabilities = requireNotNull(server.initializeResult).capabilities
                    targets.clear()
                    // Exercise both unsupported forms in the real client's public capability snapshot.
                    capabilities.setImplementationProvider(false)
                    search()
                    capabilities.implementationProvider = null
                    search()
                    assertEquals("Unsupported capability must not send a request", 1, requests.size)
                    assertEmpty(targets)

                    capabilities.setImplementationProvider(true)
                    response.set(CompletableFuture.completedFuture(Either.forLeft(emptyList())))
                    search()
                    assertEquals(2, requests.size)
                    assertEmpty(targets)

                    response.set(CompletableFuture.failedFuture(ResponseErrorException(ResponseError(-32601, "Method not found", null))))
                    search()
                    assertEquals("Server error must end the search without delivery", 3, requests.size)
                    assertEmpty(targets)

                    // Complete the implementation cancellation check before navigation changes the editor context.
                    response.set(CompletableFuture())
                    val indicator = EmptyProgressIndicator()
                    val canceledSearch = ApplicationManager.getApplication().executeOnPooledThread(java.util.concurrent.Callable {
                        try {
                            ProgressManager.getInstance().runProcess(Runnable {
                                val query = runReadAction { DefinitionsScopedSearch.search(declaration) }
                                query.forEach(Processor { targets.add(it); true })
                            }, indicator)
                            false
                        } catch (_: ProcessCanceledException) {
                            true
                        }
                    })
                    PlatformTestUtil.waitWithEventsDispatching("Pending implementation request", { requests.size == 4 }, 10)
                    indicator.cancel()
                    PlatformTestUtil.waitWithEventsDispatching("Canceled implementation search", { canceledSearch.isDone }, 10)
                    assertTrue("Request cancellation must propagate", canceledSearch.get())
                    assertEmpty(targets)

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
