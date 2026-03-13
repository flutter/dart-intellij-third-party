// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import com.google.dart.server.AnalysisServerStatusListener
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkUtil
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.MissingResourceException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal class LspClientConnectionManager(
    private val project: Project,
    private val sdk: DartSdk,
    private val suppressAnalytics: Boolean,
    onPublishDiagnostics: (PublishDiagnosticsParams) -> Unit = {},
    onApplyEdit: (
        org.eclipse.lsp4j.ApplyWorkspaceEditParams,
    ) -> org.eclipse.lsp4j.ApplyWorkspaceEditResponse = { org.eclipse.lsp4j.ApplyWorkspaceEditResponse(false) },
) {
    private companion object {
        private val LOG = Logger.getInstance(LspClientConnectionManager::class.java)
        private const val INITIALIZE_TIMEOUT_SECONDS = 10L
        private const val SHUTDOWN_TIMEOUT_SECONDS = 1L
    }

    private val lock = Any()
    private val statusListeners = CopyOnWriteArrayList<AnalysisServerStatusListener>()
    private val statusVersion = AtomicLong()
    private val listenerStatusVersions = ConcurrentHashMap<AnalysisServerStatusListener, Long>()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var pendingProcess: Process? = null

    @Volatile
    private var remoteServer: DartExtendedLanguageServer? = null

    @Volatile
    private var pendingServer: DartExtendedLanguageServer? = null

    @Volatile
    private var listeningFuture: Future<*>? = null

    @Volatile
    private var pendingListeningFuture: Future<*>? = null

    private var startupInProgress = false

    @Volatile
    private var isServerAlive = false

    @Volatile
    private var serverTextDocumentSyncKind = TextDocumentSyncKind.Full

    private val languageClient = DartLspLanguageClient(onPublishDiagnostics, onApplyEdit)
    private val workspaceFoldersManager = LspWorkspaceFoldersManager()

    private class StartupSession(
        val process: Process,
        val server: DartExtendedLanguageServer,
        val listeningFuture: Future<*>,
    )

    fun start() {
        if (!beginStartupIfNeeded()) return

        var startupSession: StartupSession? = null

        try {
            val createdSession = createStartupSession()
            startupSession = createdSession
            registerPendingStartup(createdSession)

            startErrorReader(createdSession.process)
            monitorListening(createdSession.process, createdSession.listeningFuture)
            initializeServer(createdSession.server)

            val startupPromoted =
                synchronized(lock) {
                    val startupStillCurrent = isPendingStartupSessionCurrentLocked(createdSession)
                    if (startupStillCurrent) {
                        activatePendingStartupLocked(createdSession)
                    }
                    startupInProgress = false
                    startupStillCurrent
                }

            if (!startupPromoted) {
                closeConnection(
                    createdSession.server,
                    createdSession.process,
                    createdSession.listeningFuture,
                    sendShutdownRequest = false,
                )
                return
            }

            notifyAliveIfCurrent(createdSession.process)
        } catch (t: Throwable) {
            failStartup(startupSession)
            closeConnection(
                localServer = startupSession?.server,
                localProcess = startupSession?.process,
                localListeningFuture = startupSession?.listeningFuture,
                sendShutdownRequest = false,
            )
            LOG.warn("Failed to start Dart LSP server", t)
            notifyDeadIfChanged()
            throw t
        }
    }

    private fun beginStartupIfNeeded(): Boolean {
        synchronized(lock) {
            if (process?.isAlive == true || startupInProgress) {
                return false
            }
            startupInProgress = true
            return true
        }
    }

    private fun createStartupSession(): StartupSession {
        val command = buildCommandLine()
        val startedProcess = ProcessBuilder(command).start()
        val launcher =
            Launcher.Builder<DartExtendedLanguageServer>()
                .setLocalService(languageClient)
                .setRemoteInterface(DartExtendedLanguageServer::class.java)
                .setInput(startedProcess.inputStream)
                .setOutput(startedProcess.outputStream)
                .create()
        val startedServer = launcher.remoteProxy
        val startedListeningFuture = launcher.startListening()
        return StartupSession(startedProcess, startedServer, startedListeningFuture)
    }

    private fun registerPendingStartup(startupSession: StartupSession) {
        synchronized(lock) {
            pendingProcess = startupSession.process
            pendingServer = startupSession.server
            pendingListeningFuture = startupSession.listeningFuture
        }
    }

    private fun isPendingStartupSessionCurrentLocked(startupSession: StartupSession): Boolean {
        return startupInProgress &&
            pendingProcess === startupSession.process &&
            pendingServer === startupSession.server &&
            pendingListeningFuture === startupSession.listeningFuture
    }

    private fun activatePendingStartupLocked(startupSession: StartupSession) {
        process = startupSession.process
        remoteServer = startupSession.server
        listeningFuture = startupSession.listeningFuture
        pendingProcess = null
        pendingServer = null
        pendingListeningFuture = null
        resetRegisteredWorkspaceFoldersLocked()
    }

    private fun failStartup(startupSession: StartupSession?) {
        synchronized(lock) {
            if (startupSession != null) {
                if (pendingProcess === startupSession.process) pendingProcess = null
                if (pendingServer === startupSession.server) pendingServer = null
                if (pendingListeningFuture === startupSession.listeningFuture) pendingListeningFuture = null
            }
            startupInProgress = false
            workspaceFoldersManager.clear()
        }
    }

    fun requestDiagnosticServer(): CompletableFuture<DartDiagnosticServerResult> {
        val server =
            synchronized(lock) { remoteServer }
                ?: throw IllegalStateException("Dart LSP server is not running")
        return server.diagnosticServer()
    }

    fun textDocumentSyncKind(): TextDocumentSyncKind = serverTextDocumentSyncKind

    fun didOpen(params: DidOpenTextDocumentParams) {
        textDocumentService().didOpen(params)
    }

    fun didChange(params: DidChangeTextDocumentParams) {
        textDocumentService().didChange(params)
    }

    fun didClose(params: DidCloseTextDocumentParams) {
        textDocumentService().didClose(params)
    }

    fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
        return textDocumentService().codeAction(params)
    }

    fun prepareRename(
        params: PrepareRenameParams,
    ): CompletableFuture<Either3<org.eclipse.lsp4j.Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> {
        return textDocumentService().prepareRename(params)
    }

    fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
        return textDocumentService().rename(params)
    }

    fun executeCommand(
        command: String,
        arguments: List<Any?>,
    ): CompletableFuture<Any> {
        return workspaceService().executeCommand(ExecuteCommandParams(command, ArrayList(arguments)))
    }

    fun willRenameFiles(files: List<FileRename>): CompletableFuture<WorkspaceEdit> {
        return workspaceService().willRenameFiles(RenameFilesParams(files))
    }

    fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        workspaceService().didChangeWorkspaceFolders(params)
    }

    fun ensureWorkspaceFolderRegistered(
        workspaceFolderUri: String,
        toWorkspaceFolder: (String) -> WorkspaceFolder,
    ) {
        workspaceFoldersManager.ensureWorkspaceFolderRegistered(workspaceFolderUri, toWorkspaceFolder, this::didChangeWorkspaceFolders)
    }

    fun synchronizeWorkspaceFolders(
        newWorkspaceFolderUris: Set<String>,
        toWorkspaceFolder: (String) -> WorkspaceFolder,
    ) {
        workspaceFoldersManager.synchronizeWorkspaceFolders(newWorkspaceFolderUris, toWorkspaceFolder, this::didChangeWorkspaceFolders)
    }

    fun isSocketOpen(): Boolean = process?.isAlive == true

    fun addStatusListener(listener: AnalysisServerStatusListener) {
        val statusVersionSnapshot: Long
        val serverAlive: Boolean
        synchronized(lock) {
            statusListeners.add(listener)
            statusVersionSnapshot = statusVersion.get()
            serverAlive = isServerAlive
        }
        if (serverAlive) {
            notifyStatusListener(listener, true, statusVersionSnapshot)
        }
    }

    fun shutdown() {
        val localProcess: Process?
        val localServer: LanguageServer?
        val localListeningFuture: Future<*>?
        synchronized(lock) {
            localProcess = process ?: pendingProcess
            localServer = remoteServer ?: pendingServer
            localListeningFuture = listeningFuture ?: pendingListeningFuture
            process = null
            remoteServer = null
            listeningFuture = null
            pendingProcess = null
            pendingServer = null
            pendingListeningFuture = null
            startupInProgress = false
            workspaceFoldersManager.clear()
        }

        closeConnection(localServer, localProcess, localListeningFuture, sendShutdownRequest = true)
        notifyDeadIfChanged()
    }

    private fun buildCommandLine(): List<String> {
        if (!DartAnalysisServerService.isDartSdkVersionSufficientForDartLangServer(sdk)) {
            throw IllegalStateException(
                "LSP client requires Dart SDK version ${DartAnalysisServerService.MIN_DART_LANG_SERVER_SDK_VERSION} or newer",
            )
        }

        val runtimePath = FileUtil.toSystemDependentName(DartSdkUtil.getDartExePath(sdk))
        val vmArguments =
            try {
                StringUtil.split(Registry.stringValue("dart.server.vm.options"), " ")
            } catch (_: MissingResourceException) {
                emptyList()
            }

        val serverArgsRaw =
            buildString {
                append("--protocol=lsp")
                if (suppressAnalytics) {
                    append(" --suppress-analytics")
                }
                try {
                    val extra = Registry.stringValue("dart.server.additional.arguments")
                    if (extra.isNotBlank()) {
                        append(" ").append(extra)
                    }
                } catch (_: MissingResourceException) {
                }
            }
        val serverArguments = StringUtil.split(serverArgsRaw, " ")

        val clientId = ApplicationNamesInfo.getInstance().fullProductName.replace(' ', '-')
        val clientVersion = ApplicationInfo.getInstance().apiVersion

        return mutableListOf<String>().apply {
            add(runtimePath)
            addAll(vmArguments)
            add("language-server")
            add("--client-id=$clientId")
            add("--client-version=$clientVersion")
            addAll(serverArguments)
        }
    }

    private fun startErrorReader(startedProcess: Process) {
        ApplicationManager.getApplication().executeOnPooledThread {
            startedProcess.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    LOG.debug("[dart-lsp stderr] $line")
                }
            }
        }
    }

    private fun monitorListening(
        startedProcess: Process,
        future: Future<*>?,
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                future?.get()
            } catch (_: CancellationException) {
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: ExecutionException) {
                LOG.warn("Dart LSP listening loop failed", e.cause ?: e)
            } catch (t: Throwable) {
                LOG.warn("Unexpected error in Dart LSP listening loop", t)
            } finally {
                var notifyDead = false
                synchronized(lock) {
                    if (process === startedProcess) {
                        process = null
                        remoteServer = null
                        listeningFuture = null
                        workspaceFoldersManager.clear()
                        notifyDead = true
                    }
                    if (pendingProcess === startedProcess) {
                        pendingProcess = null
                        pendingServer = null
                        pendingListeningFuture = null
                        startupInProgress = false
                        workspaceFoldersManager.clear()
                    }
                }
                if (notifyDead) {
                    notifyDeadIfChanged()
                }
            }
        }
    }

    private fun closeConnection(
        localServer: LanguageServer?,
        localProcess: Process?,
        localListeningFuture: Future<*>?,
        sendShutdownRequest: Boolean,
    ) {
        if (sendShutdownRequest) {
            try {
                localServer?.shutdown()?.get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (t: Throwable) {
                LOG.debug("Error while sending shutdown to Dart LSP server", t)
            }

            try {
                localServer?.exit()
            } catch (t: Throwable) {
                LOG.debug("Error while sending exit to Dart LSP server", t)
            }
        }

        if (localProcess != null && localProcess.isAlive) {
            localProcess.destroy()
            try {
                if (!localProcess.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    localProcess.destroyForcibly()
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                localProcess.destroyForcibly()
            }
        }

        localListeningFuture?.cancel(true)
    }

    private fun initializeServer(server: LanguageServer) {
        val initialWorkspaceFolders = initialWorkspaceFolders()
        val initializeParams =
            InitializeParams().apply {
                capabilities = createClientCapabilities()
                if (initialWorkspaceFolders.isNotEmpty()) {
                    workspaceFolders = initialWorkspaceFolders
                } else {
                    LOG.warn("Dart LSP: project.basePath is null, workspaceFolders not set")
                }
                val currentPid = ProcessHandle.current().pid()
                if (currentPid in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    processId = currentPid.toInt()
                }
            }

        val initializeResult = server.initialize(initializeParams).get(INITIALIZE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        serverTextDocumentSyncKind = extractTextDocumentSyncKind(initializeResult)
        server.initialized(InitializedParams())
    }

    private fun initialWorkspaceFolders(): List<WorkspaceFolder> {
        val basePath = project.basePath ?: return emptyList()
        return listOf(WorkspaceFolder(VfsUtilCore.pathToUrl(basePath), project.name))
    }

    private fun resetRegisteredWorkspaceFoldersLocked() {
        workspaceFoldersManager.resetRegisteredWorkspaceFolders(initialWorkspaceFolders().map(WorkspaceFolder::getUri))
    }

    private fun extractTextDocumentSyncKind(initializeResult: InitializeResult?): TextDocumentSyncKind {
        val capabilities = initializeResult?.capabilities ?: return TextDocumentSyncKind.Full
        val textDocumentSync = capabilities.textDocumentSync ?: return TextDocumentSyncKind.Full
        return if (textDocumentSync.isLeft) {
            textDocumentSync.left ?: TextDocumentSyncKind.Full
        } else {
            textDocumentSync.right?.change ?: TextDocumentSyncKind.Full
        }
    }

    private fun textDocumentService(): TextDocumentService {
        val server =
            synchronized(lock) { remoteServer }
                ?: throw IllegalStateException("Dart LSP server is not running")
        return server.textDocumentService
    }

    private fun workspaceService(): WorkspaceService {
        val server =
            synchronized(lock) { remoteServer }
                ?: throw IllegalStateException("Dart LSP server is not running")
        return server.workspaceService
    }

    private fun notifyAliveIfCurrent(expectedProcess: Process) {
        val statusVersionSnapshot =
            synchronized(lock) {
                if (process !== expectedProcess) {
                    return
                }
                isServerAlive = true
                statusVersion.incrementAndGet()
            }
        notifyStatusListeners(true, statusVersionSnapshot)
    }

    private fun notifyDeadIfChanged() {
        val statusVersionSnapshot =
            synchronized(lock) {
                if (!isServerAlive) return
                isServerAlive = false
                statusVersion.incrementAndGet()
            }
        notifyStatusListeners(false, statusVersionSnapshot)
    }

    private fun notifyStatusListeners(
        isAlive: Boolean,
        statusVersionSnapshot: Long,
    ) {
        statusListeners.forEach { listener ->
            notifyStatusListener(listener, isAlive, statusVersionSnapshot)
        }
    }

    private fun notifyStatusListener(
        listener: AnalysisServerStatusListener,
        isAlive: Boolean,
        statusVersionSnapshot: Long,
    ) {
        if (!recordNotificationVersion(listener, statusVersionSnapshot)) return

        try {
            listener.isAliveServer(isAlive)
        } catch (t: Throwable) {
            LOG.warn("Error in LSP status listener", t)
        }
    }

    private fun recordNotificationVersion(
        listener: AnalysisServerStatusListener,
        statusVersionSnapshot: Long,
    ): Boolean {
        while (true) {
            val previousVersion = listenerStatusVersions.putIfAbsent(listener, statusVersionSnapshot) ?: return true
            if (previousVersion >= statusVersionSnapshot) {
                return false
            }
            if (listenerStatusVersions.replace(listener, previousVersion, statusVersionSnapshot)) {
                return true
            }
        }
    }
}
