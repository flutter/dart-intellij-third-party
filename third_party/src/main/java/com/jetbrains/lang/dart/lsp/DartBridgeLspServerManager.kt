/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.ensureServerStarted
import com.intellij.platform.dartlsp.api.stopServers
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.lang.dart.logging.PluginLogger
import org.eclipse.lsp4j.jsonrpc.JsonRpcException
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Project-level service that manages the lifecycle of the LSP bridge server.
 *
 * This manager sets up a local TCP [ServerSocket] on a dynamically allocated port
 * and spawns a background thread to listen for incoming connection requests from the JetBrains
 * native LSP client. When [startBridgeServer] is called, it opens the socket and manually
 * triggers the JetBrains LSP client framework via [LspServerManager.ensureServerStarted]
 * to connect to this bridge.
 */
@Service(Service.Level.PROJECT)
class DartBridgeLspServerManager(private val project: Project) : Disposable {
    companion object {
        private val logger = PluginLogger.createLogger(DartBridgeLspServerManager::class.java)
        private const val GRACEFUL_CLOSE_TIMEOUT_MS = 2_000L
    }

    @VisibleForTesting
    internal var gracefulCloseTimeoutMs: Long = GRACEFUL_CLOSE_TIMEOUT_MS

    private val stateLock = Any()
    private var serverSocket: ServerSocket? = null
    private var listenFuture: Future<*>? = null
    private var activeConnection: ActiveConnection? = null

    val port: Int
        get() = synchronized(stateLock) { serverSocket?.localPort ?: -1 }

    /** The bridge of the connected LSP client, or `null` while no client is connected. */
    internal val bridgeServer: DartBridgeLspServer?
        get() = synchronized(stateLock) { activeConnection?.bridgeServer }

    @get:VisibleForTesting
    internal val activeClientSocket: Socket?
        get() = synchronized(stateLock) { activeConnection?.socket }

    fun isClientConnected(): Boolean = bridgeServer?.isClientConnected == true
    val isSemanticTokensAdvertised: Boolean
        get() = bridgeServer?.isSemanticTokensAdvertised == true


    fun startBridgeServer() {
        if (!bindAndListenForConnections()) {
            return
        }
        // Manually trigger the JetBrains LSP client to connect to our bridge.
        // This replaces the automatic file-opened trigger.
        LspServerManager.getInstance(project).ensureServerStarted<DartLspServerSupportProvider>(DartLspServerDescriptor(project))
        logger.info("Triggered LSP client start via ensureServerStarted")
    }

    @VisibleForTesting
    internal fun bindAndListenForConnections(): Boolean {
        val boundSocket = synchronized(stateLock) {
            if (serverSocket != null) {
                logger.warn("Bridge LSP Server is already running")
                return false
            }
            try {
                // Bind to a random free port on localhost. The OS will assign an available port.
                // This port is then queried by DartLspServerDescriptor to tell the JetBrains LSP client where to connect.
                ServerSocket(0, 50, InetAddress.getLoopbackAddress()).also { serverSocket = it }
            } catch (e: IOException) {
                logger.error("Failed to start Bridge ServerSocket", e)
                return false
            }
        }
        logger.info("Bridge LSP Server socket listening on port ${boundSocket.localPort}")
        startListening(boundSocket)
        return true
    }

    fun stopBridgeServer() {
        logger.info("Stopping Bridge LSP Server")
        val socketToClose: ServerSocket?
        val futureToCancel: Future<*>?
        val connectionToClose: ActiveConnection?
        synchronized(stateLock) {
            socketToClose = serverSocket
            serverSocket = null
            futureToCancel = listenFuture
            listenFuture = null
            connectionToClose = activeConnection
            activeConnection = null
        }

        socketToClose.closeQuietly()
        futureToCancel?.cancel(true)

        // Detach the old bridge from DAS immediately (before a new bridge can start),
        // while keeping the socket open until the LSP client finishes shutdown -> exit -> disconnect.
        connectionToClose?.bridgeServer?.stop()

        // Disconnect the JetBrains LSP client if initialized.
        try {
            if (!project.isDisposed) {
                project.serviceIfCreated<LspServerManager>()?.stopServers<DartLspServerSupportProvider>()
            }
        } finally {
            connectionToClose?.closeGracefully(gracefulCloseTimeoutMs)
        }
    }

    private fun startListening(currentServerSocket: ServerSocket) {
        // Start a background thread to listen for incoming connections from the JetBrains LSP client.
        val future = ApplicationManager.getApplication().executeOnPooledThread {
            while (!currentServerSocket.isClosed) {
                try {
                    // accept() is a blocking call. The thread suspends here and consumes no CPU
                    // until the JetBrains LSP client initiates a connection.
                    val socket = currentServerSocket.accept()
                    logger.info("Accepted connection from client: ${socket.remoteSocketAddress}")
                    handleClientConnection(currentServerSocket, socket)
                } catch (e: IOException) {
                    if (!currentServerSocket.isClosed) {
                        logger.error("Error accepting connection", e)
                    }
                }
            }
        }
        synchronized(stateLock) {
            if (serverSocket === currentServerSocket) {
                listenFuture = future
            } else {
                future.cancel(true)
            }
        }
    }


    private fun handleClientConnection(currentServerSocket: ServerSocket, socket: Socket) {
        val (previousConnection, acceptedOnActiveServer) = synchronized(stateLock) {
            if (currentServerSocket.isClosed || serverSocket !== currentServerSocket) {
                null to false
            } else {
                val previous = activeConnection
                activeConnection = null
                previous to true
            }
        }
        if (!acceptedOnActiveServer) {
            socket.closeQuietly()
            return
        }
        previousConnection?.closeGracefully(gracefulCloseTimeoutMs)

        var server: DartBridgeLspServer? = null
        try {
            val bridge = DartBridgeLspServer(project)
            server = bridge
            val launcher = createBridgeLauncher(bridge, socket)
            bridge.connect(launcher.remoteProxy)
            val newConnection = ActiveConnection(socket, bridge, launcher.startListening())
            val installed = synchronized(stateLock) {
                if (!currentServerSocket.isClosed && serverSocket === currentServerSocket) {
                    activeConnection = newConnection
                    true
                } else {
                    false
                }
            }
            if (!installed) {
                newConnection.close()
            }
        } catch (e: Exception) {
            logger.error("Failed to handle client connection", e)
            server?.stop()
            socket.closeQuietly()
        }
    }

    private fun createBridgeLauncher(server: DartBridgeLspServer, socket: Socket): Launcher<DartLanguageClient> =
        Launcher.Builder<DartLanguageClient>()
            .setLocalService(server)
            .setRemoteInterface(DartLanguageClient::class.java)
            .setExecutorService(AppExecutorUtil.getAppExecutorService())
            .setInput(socket.getInputStream())
            .setOutput(socket.getOutputStream())
            .wrapMessages { consumer ->
                MessageConsumer { message ->
                    try {
                        consumer.consume(message)
                    } catch (e: JsonRpcException) {
                        // Suppress write failures only for outbound ResponseMessages once shutdown has started,
                        // preventing RemoteEndpoint.handleRequest from logging a Level.SEVERE IDE Internal Error
                        // on a closed socket while allowing server->client RequestMessages to fail fast.
                        if (message is ResponseMessage && server.isStopped) {
                            logger.debug("Ignoring response write on closing LSP bridge socket: ${e.message}")
                        } else {
                            throw e
                        }
                    }
                }
            }
            .create()

    override fun dispose() {
        logger.info("Disposing DartBridgeLspServerManager")
        stopBridgeServer()
    }

    private fun ServerSocket?.closeQuietly() {
        try {
            this?.close()
        } catch (_: IOException) {
            // Ignore
        }
    }

    private fun Socket?.closeQuietly() {
        try {
            this?.close()
        } catch (_: IOException) {
            // Ignore
        }
    }

    private inner class ActiveConnection(
        val socket: Socket,
        val bridgeServer: DartBridgeLspServer,
        val listeningFuture: Future<*>
    ) {
        fun closeGracefully(timeoutMs: Long) {
            bridgeServer.stop()
            if (listeningFuture.isDone) {
                socket.closeQuietly()
                return
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    listeningFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                    // Timed out, cancelled, or failed — fall through to force-close.
                } finally {
                    listeningFuture.cancel(true)
                    socket.closeQuietly()
                }
            }
        }

        fun close() {
            logger.info("Closing active connection")
            bridgeServer.stop()
            listeningFuture.cancel(true)
            socket.closeQuietly()
        }
    }
}
