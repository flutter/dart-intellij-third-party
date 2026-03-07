// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import com.google.dart.server.AnalysisServerStatusListener
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.PathUtil
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkUtil
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageServer
import java.util.MissingResourceException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal class LspClientConnectionManager(
  private val project: Project,
  private val sdk: DartSdk,
  private val suppressAnalytics: Boolean,
) {
  private companion object {
    private val LOG = Logger.getInstance(LspClientConnectionManager::class.java)
    private const val INITIALIZE_TIMEOUT_SECONDS = 10L
    private const val SHUTDOWN_TIMEOUT_SECONDS = 1L
  }

  private val lock = Any()
  private val statusListeners = CopyOnWriteArrayList<AnalysisServerStatusListener>()
  private val statusVersion = AtomicLong()

  @Volatile
  private var process: Process? = null

  @Volatile
  private var pendingProcess: Process? = null

  @Volatile
  private var remoteServer: DartExtendedLanguageServer? = null

  @Volatile
  private var pendingServer: DartExtendedLanguageServer? = null

  @Volatile
  private var remoteEndpoint: Endpoint? = null

  @Volatile
  private var pendingEndpoint: Endpoint? = null

  @Volatile
  private var listeningFuture: Future<*>? = null

  @Volatile
  private var pendingListeningFuture: Future<*>? = null

  private var startupInProgress = false

  @Volatile
  private var isServerAlive = false

  private val languageClient = NoOpDartLanguageClient()

  private class StartupSession(
    val process: Process,
    val server: DartExtendedLanguageServer,
    val endpoint: Endpoint,
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

      val startupPromoted = synchronized(lock) {
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
    }
    catch (t: Throwable) {
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
    val launcher = Launcher.Builder<DartExtendedLanguageServer>()
      .setLocalService(languageClient)
      .setRemoteInterface(DartExtendedLanguageServer::class.java)
      .setInput(startedProcess.inputStream)
      .setOutput(startedProcess.outputStream)
      .create()
    val startedServer = launcher.remoteProxy
    val startedEndpoint = launcher.remoteEndpoint
    val startedListeningFuture = launcher.startListening()
    return StartupSession(startedProcess, startedServer, startedEndpoint, startedListeningFuture)
  }

  private fun registerPendingStartup(startupSession: StartupSession) {
    synchronized(lock) {
      pendingProcess = startupSession.process
      pendingServer = startupSession.server
      pendingEndpoint = startupSession.endpoint
      pendingListeningFuture = startupSession.listeningFuture
    }
  }

  private fun isPendingStartupSessionCurrentLocked(startupSession: StartupSession): Boolean {
    return startupInProgress &&
           pendingProcess === startupSession.process &&
           pendingServer === startupSession.server &&
           pendingEndpoint === startupSession.endpoint &&
           pendingListeningFuture === startupSession.listeningFuture
  }

  private fun activatePendingStartupLocked(startupSession: StartupSession) {
    process = startupSession.process
    remoteServer = startupSession.server
    remoteEndpoint = startupSession.endpoint
    listeningFuture = startupSession.listeningFuture
    pendingProcess = null
    pendingServer = null
    pendingEndpoint = null
    pendingListeningFuture = null
  }

  private fun failStartup(startupSession: StartupSession?) {
    synchronized(lock) {
      if (startupSession != null) {
        if (pendingProcess === startupSession.process) pendingProcess = null
        if (pendingServer === startupSession.server) pendingServer = null
        if (pendingEndpoint === startupSession.endpoint) pendingEndpoint = null
        if (pendingListeningFuture === startupSession.listeningFuture) pendingListeningFuture = null
      }
      startupInProgress = false
    }
  }

  fun request(method: String, params: Any?): CompletableFuture<*> {
    val endpoint = synchronized(lock) { remoteEndpoint }
                   ?: throw IllegalStateException("Dart LSP server is not running")
    return endpoint.request(method, params)
  }

  fun requestDiagnosticServer(): CompletableFuture<DartDiagnosticServerResult> {
    val server = synchronized(lock) { remoteServer }
                 ?: throw IllegalStateException("Dart LSP server is not running")
    return server.diagnosticServer()
  }

  fun isSocketOpen(): Boolean = process?.isAlive == true

  fun addStatusListener(listener: AnalysisServerStatusListener) {
    statusListeners.add(listener)
    val statusVersionSnapshot = statusVersion.get()
    if (isServerAlive) {
      notifyStatusListener(listener, true)
      if (!isServerAlive && statusVersion.get() != statusVersionSnapshot) {
        notifyStatusListener(listener, false)
      }
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
      remoteEndpoint = null
      listeningFuture = null
      pendingProcess = null
      pendingServer = null
      pendingEndpoint = null
      pendingListeningFuture = null
      startupInProgress = false
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

    val runtimePath = PathUtil.toSystemDependentName(DartSdkUtil.getDartExePath(sdk))
    val vmArguments = try {
      StringUtil.split(Registry.stringValue("dart.server.vm.options"), " ")
    }
    catch (_: MissingResourceException) {
      emptyList()
    }

    val serverArgsRaw = buildString {
      append("--protocol=lsp")
      if (suppressAnalytics) {
        append(" --suppress-analytics")
      }
      try {
        val extra = Registry.stringValue("dart.server.additional.arguments")
        if (extra.isNotBlank()) {
          append(" ").append(extra)
        }
      }
      catch (_: MissingResourceException) {
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

  private fun monitorListening(startedProcess: Process, future: Future<*>?) {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        future?.get()
      }
      catch (_: CancellationException) {
      }
      catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
      }
      catch (e: ExecutionException) {
        LOG.warn("Dart LSP listening loop failed", e.cause ?: e)
      }
      catch (t: Throwable) {
        LOG.warn("Unexpected error in Dart LSP listening loop", t)
      }
      finally {
        var notifyDead = false
        synchronized(lock) {
          if (process === startedProcess) {
            process = null
            remoteServer = null
            remoteEndpoint = null
            listeningFuture = null
            notifyDead = true
          }
          if (pendingProcess === startedProcess) {
            pendingProcess = null
            pendingServer = null
            pendingEndpoint = null
            pendingListeningFuture = null
            startupInProgress = false
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
      }
      catch (t: Throwable) {
        LOG.debug("Error while sending shutdown to Dart LSP server", t)
      }

      try {
        localServer?.exit()
      }
      catch (t: Throwable) {
        LOG.debug("Error while sending exit to Dart LSP server", t)
      }
    }

    if (localProcess != null && localProcess.isAlive) {
      localProcess.destroy()
      try {
        if (!localProcess.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          localProcess.destroyForcibly()
        }
      }
      catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        localProcess.destroyForcibly()
      }
    }

    localListeningFuture?.cancel(true)
  }

  private fun initializeServer(server: LanguageServer) {
    val initializeParams = InitializeParams().apply {
      capabilities = ClientCapabilities()
      val basePath = project.basePath
      if (basePath != null) {
        workspaceFolders = listOf(WorkspaceFolder(VfsUtilCore.pathToUrl(basePath), project.name))
      }
      else {
        LOG.warn("Dart LSP: project.basePath is null, workspaceFolders not set")
      }
      val currentPid = ProcessHandle.current().pid()
      if (currentPid in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        processId = currentPid.toInt()
      }
    }

    server.initialize(initializeParams).get(INITIALIZE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    server.initialized(InitializedParams())
  }

  private fun notifyAliveIfCurrent(expectedProcess: Process) {
    val statusVersionSnapshot = synchronized(lock) {
      if (process !== expectedProcess) {
        return
      }
      isServerAlive = true
      statusVersion.incrementAndGet()
    }
    notifyStatusListeners(true, statusVersionSnapshot)
  }

  private fun notifyDeadIfChanged() {
    val statusVersionSnapshot = synchronized(lock) {
      if (!isServerAlive) return
      isServerAlive = false
      statusVersion.incrementAndGet()
    }
    notifyStatusListeners(false, statusVersionSnapshot)
  }

  private fun notifyStatusListeners(isAlive: Boolean, statusVersionSnapshot: Long) {
    statusListeners.forEach { listener ->
      if (statusVersion.get() != statusVersionSnapshot) {
        return
      }
      notifyStatusListener(listener, isAlive)
    }
  }

  private fun notifyStatusListener(listener: AnalysisServerStatusListener, isAlive: Boolean) {
    try {
      listener.isAliveServer(isAlive)
    }
    catch (t: Throwable) {
      LOG.warn("Error in LSP status listener", t)
    }
  }
}
