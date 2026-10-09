/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.GetTypeHierarchyConsumer
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.DartLspWorkspaceConfigurationConsumer
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManagerListener
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.dartlsp.api.LspCommunicationChannel
import com.intellij.platform.dartlsp.api.LspServerDescriptor
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.createTestOpenProjectOptions
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.ide.actions.DartServerGotoSuperHandler
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.ServerSocket
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Direct action coverage: completion means the task's UI callbacks have returned, not just its request. */
class DartLspSuperNavigationTest : DartCodeInsightFixtureTestCase() {
    fun testValidCrossFileResponseNavigatesToDistinctCaret() = withSession { session ->
        session.response.set(CompletableFuture.completedFuture(session.destination()))
        session.invokeAndAwaitCompletion()
        val selected = session.editors.selectedTextEditor
        assertNotNull(selected)
        assertNotSame(session.sourceEditor, selected)
        assertEquals(LogicalPosition(1, 7), selected!!.caretModel.logicalPosition)
        assertTrue(session.editors.isFileOpen(session.target))
        session.assertNoLegacy()
    }

    fun testLegacyHierarchyObserverDetectsServiceFacadeCalls() = withSession { session ->
        session.assertLegacyFacadeReachable()
        session.assertUnchanged()
    }

    fun testCompletedNullDoesNotNavigate() = checkRejected(null)

    fun testCompletedInternalErrorDoesNotNavigate() = checkError(-32603)

    fun testCompletedMethodNotFoundDoesNotNavigate() = checkError(-32601)

    fun testMalformedUriDoesNotNavigate() = checkRejected(Location("not a URI", Range(Position(0, 0), Position(0, 1))))

    fun testMissingUriDoesNotNavigate() = checkRejected(Location().apply { range = Range(Position(0, 0), Position(0, 1)) })

    fun testMissingRangeDoesNotNavigate() = withSession { session ->
        session.reject(Location().apply { uri = session.target.url })
    }

    fun testNullPositionsDoNotNavigate() = withSession { session ->
        session.reject(Location(session.target.url, Range()))
        session.reject(Location(session.target.url, Range().apply { start = Position(1, 7) }))
    }

    fun testMissingFileDoesNotNavigate() = checkRejected(Location("file:///missing-super-target.dart", Range(Position(0, 0), Position(0, 1))))

    fun testNegativePositionsDoNotNavigate() = withSession { session ->
        session.reject(Location(session.target.url, Range(Position(-1, 0), Position(0, 1))))
        session.reject(Location(session.target.url, Range(Position(0, -1), Position(0, 1))))
        session.reject(Location(session.target.url, Range(Position(0, 0), Position(0, -1))))
    }

    fun testOutOfBoundsPositionsDoNotNavigate() = withSession { session ->
        session.reject(Location(session.target.url, Range(Position(99, 0), Position(99, 1))))
        session.reject(Location(session.target.url, Range(Position(0, 999), Position(0, 1000))))
        session.reject(Location(session.target.url, Range(Position(0, 0), Position(99, 0))))
        session.reject(Location(session.target.url, Range(Position(0, 0), Position(0, 999))))
    }

    fun testReversedRangeDoesNotNavigate() = withSession { session ->
        session.reject(Location(session.target.url, Range(Position(1, 8), Position(1, 7))))
    }

    fun testPendingRequestCancellationFinishesTaskWithoutNavigation() = withSession { session ->
        val response = CompletableFuture<Location?>()
        session.response.set(response)
        session.invokePending()
        session.assertPending()
        session.taskIndicator().cancel()
        session.awaitCompletion()
        session.assertUnchanged()
    }

    fun testValidLateResponseAfterCancellationIsProcessedWithoutNavigation() = withSession { session ->
        val response = CancelIgnoringFuture<Location?>()
        session.response.set(response)
        session.invokePending()
        session.assertPending()
        session.taskIndicator().cancel()
        session.awaitCompletion()
        assertFalse("The server response must remain deliverable", response.isDone)
        session.deliverAndAwaitClient(response)
        session.assertUnchanged()
    }

    fun testDisposedEditorSuppressesPendingValidResponse() = withSession { session ->
        val response = CompletableFuture<Location?>()
        session.response.set(response)
        session.invokePending()
        session.assertPending()
        session.editors.closeFile(session.source)
        // TestFileEditorManager does not publish the real manager's close events.
        if (!session.sourceEditor.isDisposed) EditorFactory.getInstance().releaseEditor(session.sourceEditor)
        assertTrue(session.sourceEditor.isDisposed)
        project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
            .fileClosed(session.editors, session.source)
        session.deliverAndAwaitClient(response)
        session.awaitCompletion()
        assertFalse(session.editors.isFileOpen(session.target))
        session.assertNoLegacy()
    }

    fun testOwnedProjectDisposalTerminatesPendingTaskWithoutNavigation() {
        // These files belong to neither the inherited light fixture nor its project model.
        val source = LightVirtualFile("ownedSource.dart", DartFileType.INSTANCE, "class Source {}")
        val target = LightVirtualFile("ownedTarget.dart", DartFileType.INSTANCE, "class Target {\n  void method() {}\n}")
        val owned = requireNotNull(ProjectManagerEx.getInstanceEx().newProject(
            TemporaryDirectory.generateTemporaryPath("owned-super-project"),
            createTestOpenProjectOptions(false)
        ))
        try {
            withSession(owned, source, target) { session ->
                val response = CompletableFuture<Location?>()
                session.response.set(response)
                session.invokePending()
                session.assertPending()
                session.assertLegacyFacadeReachable()
                ProjectManagerEx.getInstanceEx().forceCloseProject(owned, false)
                assertTrue("Only the separately owned project is disposed", owned.isDisposed)
                assertFalse(project.isDisposed)
                session.assertShutdownCancelled(response)
                session.awaitProcessTermination()
                // Process termination and the subsequent UI callbacks are distinct boundaries.
                session.awaitCompletion()
                assertFalse("Disposed project must never open the destination", session.editors.isFileOpen(target))
                session.assertNoLegacy()
            }
        } finally {
            if (!owned.isDisposed) ProjectManagerEx.getInstanceEx().forceCloseProject(owned, false)
        }
    }

    private fun checkRejected(location: Location?) = withSession { it.reject(location) }

    private fun checkError(code: Int) = withSession { session ->
        session.response.set(CompletableFuture.failedFuture(ResponseErrorException(ResponseError(code, "Test response", null))))
        session.invokeAndAwaitCompletion()
        session.assertUnchanged()
    }

    private fun withSession(action: (Session) -> Unit) {
        val source = myFixture.addFileToProject("source.dart", "class Source {\n  void method() {}\n}")
        val target = myFixture.addFileToProject("target.dart", "class Target {\n  void method() {}\n}")
        withSession(project, source.virtualFile, target.virtualFile, action)
    }

    private fun withSession(owner: Project, source: VirtualFile, target: VirtualFile, action: (Session) -> Unit) {
        // Tasks otherwise run synchronously in headless tests. Restore the property even on failure.
        PlatformTestUtil.withSystemProperty<RuntimeException>("intellij.progress.task.ignoreHeadless", "true") {
            val previous = DartConfigurable.isExperimentalLspFeaturesEnabled(owner)
            DartConfigurable.setExperimentalLspFeaturesEnabled(owner, true)
            try {
                Session(owner, source, target).use { session ->
                    session.connect()
                    session.installLegacyObserver()
                    session.assertLegacyFacadeReachable()
                    action(session)
                }
            } finally {
                if (!owner.isDisposed) DartConfigurable.setExperimentalLspFeaturesEnabled(owner, previous)
            }
        }
    }

    private class CancelIgnoringFuture<T> : CompletableFuture<T>() {
        // RemoteEndpoint stores and cancels precisely the future returned by getSuper.
        override fun cancel(mayInterruptIfRunning: Boolean) = false
        fun cancelForCleanup() = super.cancel(true)
    }

    private inner class Session(val owner: Project, val source: VirtualFile, val target: VirtualFile) : AutoCloseable {
        val editors = FileEditorManager.getInstance(owner)
        val sourceEditor = requireNotNull(editors.openTextEditor(OpenFileDescriptor(owner, source, 8), true))
        private val initialCaret = sourceEditor.caretModel.offset
        private val initialOpenFiles = editors.openFiles.toSet()
        private val requests = CopyOnWriteArrayList<TextDocumentPositionParams>()
        private val pendingRequests = CopyOnWriteArrayList<CompletableFuture<Location?>>()
        private val shutdownEntered = CompletableFuture<Unit>()
        private val shutdownAcknowledged = CompletableFuture<Unit>()
        private val pendingAtShutdown = CopyOnWriteArrayList<CompletableFuture<Location?>>()
        private val cancelledDuringShutdown = CopyOnWriteArrayList<CompletableFuture<Location?>>()
        private var allCancelledBeforeAcknowledgement = false
        private val legacyHierarchyCalls = CopyOnWriteArrayList<Triple<String, Int, Boolean>>()
        private val das = DartAnalysisServerService.getInstance(owner)
        // Construction starts no process or reader threads. Only the hierarchy facade is exercised.
        private val legacyServer = object : RemoteAnalysisServerImpl(object : AnalysisServerSocket {
            override fun getErrorStream(): ByteLineReaderStream? = null
            override fun getRequestSink(): RequestSink? = null
            override fun getResponseStream(): ResponseStream? = null
            override fun isOpen() = true
            override fun start() {}
            override fun stop() {}
        }) {
            override fun search_getTypeHierarchy(file: String, offset: Int, superOnly: Boolean, consumer: GetTypeHierarchyConsumer) {
                legacyHierarchyCalls.add(Triple(file, offset, superOnly))
                consumer.computedHierarchy(emptyList())
            }

            // Project disposal calls shutdown; this fake owns no daemon, streams, or pending requests.
            override fun server_shutdown() {}
            override fun server_openUrlRequest(url: String?) {}
            override fun server_showMessageRequest(
                messageType: String?, message: String?, messageActions: MutableList<MessageAction>?, consumer: ShowMessageRequestConsumer?
            ) {}
            override fun lsp_workspaceApplyEdit(
                params: DartLspApplyWorkspaceEditParams?, consumer: DartLspWorkspaceApplyEditRequestConsumer?
            ) {}
            override fun lsp_workspaceConfiguration(
                sections: MutableList<String?>?, consumer: DartLspWorkspaceConfigurationConsumer?
            ) {}
        }
        val response = AtomicReference<CompletableFuture<Location?>>(CompletableFuture.completedFuture(null))
        private val task = AtomicReference<Task?>()
        private val indicator = AtomicReference<ProgressIndicator?>()
        private val completed = AtomicReference<Task?>()
        private val connection = ApplicationManager.getApplication().messageBus.connect()
        private val manager = LspServerManager.getInstance(owner)
        private val listener = ServerSocket(0)
        private val serverExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        private var accepted: java.util.concurrent.Future<java.net.Socket>? = null
        private var socket: java.net.Socket? = null
        private var listening: java.util.concurrent.Future<Void>? = null
        private lateinit var launcher: Launcher<LanguageClient>
        private val transmitted = CopyOnWriteArrayList<ResponseMessage>()

        init {
            connection.subscribe(ProgressManagerListener.TOPIC, object : ProgressManagerListener {
                override fun beforeTaskStart(candidate: Task, progress: ProgressIndicator) {
                    if (candidate.project === owner && candidate.title == "Go to Super") {
                        task.set(candidate)
                        indicator.set(progress)
                    }
                }

                override fun afterTaskFinished(candidate: Task) {
                    if (candidate === task.get()) {
                        assertTrue("Task callbacks must finish on the UI thread", ApplicationManager.getApplication().isDispatchThread)
                        completed.set(candidate)
                    }
                }
            })
        }

        fun connect() {
            val accepted = ApplicationManager.getApplication().executeOnPooledThread(Callable { listener.accept() })
            this.accepted = accepted
            val descriptor = object : LspServerDescriptor(owner, "Super lifecycle test") {
                override val lsp4jServerClass = DartLanguageServer::class.java
                override val lspCommunicationChannel = LspCommunicationChannel.Socket(listener.localPort, startProcess = false)
                override fun isSupportedFile(file: VirtualFile) = file.extension == "dart"
                override fun getFileUri(file: VirtualFile) = file.url
                override fun findFileByUri(fileUri: String) =
                    listOf(source, target).firstOrNull { it.url == fileUri }
                        ?: VirtualFileManager.getInstance().findFileByUrl(fileUri)
            }
            manager.ensureServerStarted(DartLspServerSupportProvider::class.java, descriptor)
            waitFor("Socket accepted") { accepted.isDone }
            val connected = accepted.get()
            socket = connected
            val endpoint = object : DartLanguageServer, TextDocumentService, WorkspaceService {
                override fun getSuper(params: TextDocumentPositionParams): CompletableFuture<Location?> {
                    val pending = response.get()
                    pendingRequests.add(pending)
                    pending.whenComplete { _, _ ->
                        if (pending.isCancelled && shutdownEntered.isDone && !shutdownAcknowledged.isDone) {
                            cancelledDuringShutdown.add(pending)
                        }
                    }
                    requests.add(params)
                    return pending
                }
                override fun diagnosticServer() = CompletableFuture.completedFuture(DiagnosticServerResult(0))
                override fun initialize(params: InitializeParams) = CompletableFuture.completedFuture(InitializeResult(ServerCapabilities()))
                override fun shutdown(): CompletableFuture<Any> {
                    shutdownEntered.complete(Unit)
                    pendingAtShutdown.addAll(pendingRequests.filter { !it.isDone })
                    // Match DartBridgeLspServer.shutdown(): stop cancels pending requests before acknowledging.
                    pendingAtShutdown.forEach { it.cancel(true) }
                    allCancelledBeforeAcknowledgement = pendingAtShutdown.all { it.isCancelled }
                    shutdownAcknowledged.complete(Unit)
                    return CompletableFuture.completedFuture(null)
                }
                override fun exit() {}
                override fun getTextDocumentService(): TextDocumentService = this
                override fun getWorkspaceService(): WorkspaceService = this
                override fun didOpen(params: DidOpenTextDocumentParams) {}
                override fun didChange(params: DidChangeTextDocumentParams) {}
                override fun didClose(params: DidCloseTextDocumentParams) {}
                override fun didSave(params: DidSaveTextDocumentParams) {}
                override fun didChangeConfiguration(params: DidChangeConfigurationParams) {}
                override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {}
            }
            launcher = Launcher.Builder<LanguageClient>().setLocalService(endpoint).setRemoteInterface(LanguageClient::class.java)
                .setInput(connected.getInputStream()).setOutput(connected.getOutputStream())
                .setExecutorService(serverExecutor)
                .wrapMessages { consumer -> MessageConsumer { message ->
                    consumer.consume(message)
                    if (message is ResponseMessage) transmitted.add(message)
                } }.create()
            listening = launcher.startListening()
            waitFor("Running LSP server") {
                manager.getServersForProvider(DartLspServerSupportProvider::class.java).any { it.state == LspServerState.Running }
            }
        }

        fun destination() = Location(target.url, Range(Position(1, 7), Position(1, 13)))

        fun invokePending() {
            task.set(null)
            completed.set(null)
            indicator.set(null)
            val before = requests.size
            val psiFile = requireNotNull(PsiManager.getInstance(owner).findFile(source))
            DartServerGotoSuperHandler().invoke(owner, sourceEditor, psiFile)
            waitFor("Super request received") { requests.size > before && indicator.get() != null }
            assertEquals(source.url, requests.last().textDocument.uri)
            assertEquals(Position(0, initialCaret), requests.last().position)
        }

        fun invokeAndAwaitCompletion() {
            invokePending()
            awaitCompletion()
        }

        fun assertPending() {
            assertFalse("The server response must still be pending", response.get().isDone)
            assertNotSame("The task must not have finished before the lifecycle event", task.get(), completed.get())
            assertTrue("The task's process must have started: ${progressContext()}", taskIndicator().isRunning)
        }

        fun taskIndicator() = requireNotNull(indicator.get())

        private fun progressContext() =
            "indicator=${taskIndicator().javaClass.name}, headless=${ApplicationManager.getApplication().isHeadlessEnvironment}"

        fun assertShutdownCancelled(pending: CompletableFuture<Location?>) {
            val context = progressContext()
            println("Owned-project disposal: $context")
            // A deadline below the default 10-second request timeout is only a failure guard.
            PlatformTestUtil.waitWithEventsDispatching("Bridge shutdown entered: $context", { shutdownEntered.isDone }, 5)
            PlatformTestUtil.waitWithEventsDispatching("Bridge shutdown acknowledged: $context", { shutdownAcknowledged.isDone }, 5)
            assertEquals("The owned request must still be pending when shutdown enters: $context",
                         listOf(pending), pendingAtShutdown.toList())
            assertEquals("Shutdown must cause the cancellation transition before acknowledgement: $context",
                         listOf(pending), cancelledDuringShutdown.toList())
            assertTrue("Every outstanding request must be cancelled before shutdown acknowledgement: $context",
                       allCancelledBeforeAcknowledgement)
            assertTrue("The owned request must be cancelled, not completed with a result: $context", pending.isCancelled)
        }

        fun awaitProcessTermination() {
            // isRunning=false observes the progress process boundary, not worker-thread return.
            // Remote cancellation may become a null result; indicator cancellation is not required.
            PlatformTestUtil.waitWithEventsDispatching("Super process terminated after shutdown: ${progressContext()}",
                                                       { !taskIndicator().isRunning }, 5)
        }

        fun awaitCompletion() {
            waitFor("Super task UI callbacks completed") { task.get() != null && completed.get() === task.get() }
            assertFalse("Completed task must not remain running", taskIndicator().isRunning)
        }

        fun reject(location: Location?) {
            response.set(CompletableFuture.completedFuture(location))
            invokeAndAwaitCompletion()
            assertUnchanged()
        }

        fun deliverAndAwaitClient(pending: CompletableFuture<Location?>) {
            val destination = destination()
            assertTrue("A canceled server future cannot prove late delivery", pending.complete(destination))
            waitFor("Valid response transmitted") { transmitted.any { it.result == destination } }
            // The connector calls StreamMessageProducer.listen(RemoteEndpoint) on one listener thread.
            // This subsequent server-to-client request is read only after the preceding response was handled.
            val barrier = launcher.remoteProxy.configuration(ConfigurationParams(emptyList()))
            waitFor("Client processed ordered barrier") { barrier.isDone }
            barrier.get()
        }

        fun installLegacyObserver() {
            // This fixture uses a fake SDK and owns the service; no real legacy server was started.
            das.setServer(legacyServer)
        }

        fun assertLegacyFacadeReachable() {
            assertEmpty("The positive control must not erase an unexpected hierarchy call", legacyHierarchyCalls)
            val result = das.search_getTypeHierarchy(source, initialCaret, true)
            assertEquals("The real service facade must reach the semantic observer",
                         listOf(Triple(das.getFileUri(source), initialCaret, true)), legacyHierarchyCalls.toList())
            assertEmpty("The control completes the hierarchy consumer without navigation", result)
            legacyHierarchyCalls.clear()
        }

        fun assertNoLegacy() {
            assertEmpty("Direct Super must not invoke legacy hierarchy", legacyHierarchyCalls)
            // Detect an accidentally detached spy as well as an accidental fallback.
            if (!owner.isDisposed) assertLegacyFacadeReachable()
        }

        fun assertUnchanged() {
            assertSame(sourceEditor, editors.selectedTextEditor)
            assertEquals(initialCaret, sourceEditor.caretModel.offset)
            assertEquals(initialOpenFiles, editors.openFiles.toSet())
            assertFalse(editors.isFileOpen(target))
            assertNoLegacy()
        }

        override fun close() {
            try {
                indicator.get()?.cancel()
                if (task.get() != null && completed.get() !== task.get()) awaitCompletion()
                if (!owner.isDisposed) {
                    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
                    manager.stopServers(DartLspServerSupportProvider::class.java)
                    waitFor("LSP stopped") {
                        manager.getServersForProvider(DartLspServerSupportProvider::class.java).none { it.state == LspServerState.Running }
                    }
                    editors.closeFile(source)
                    editors.closeFile(target)
                }
            } finally {
                RunAll(
                    { if (!sourceEditor.isDisposed) EditorFactory.getInstance().releaseEditor(sourceEditor) },
                    {
                        val pending = response.get()
                        if (pending is CancelIgnoringFuture) pending.cancelForCleanup() else pending.cancel(true)
                    },
                    { das.setServer(null) },
                    { connection.disconnect() },
                    { socket?.close() },
                    { listener.close() },
                    {
                        accepted?.let { future ->
                            waitFor("Socket accept terminated") { future.isDone }
                            if (socket == null && !future.isCancelled) {
                                try {
                                    future.get().close()
                                } catch (_: java.util.concurrent.ExecutionException) {
                                    // Closing the listening socket unblocks an unfinished accept.
                                }
                            }
                        }
                    },
                    { listening?.let { future -> waitFor("Socket listener terminated") { future.isDone } } },
                    {
                        serverExecutor.shutdown()
                        waitFor("Server executor terminated") { serverExecutor.isTerminated }
                    }
                ).run()
            }
        }
    }

    private fun waitFor(description: String, condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching(description, condition, 10)
}
