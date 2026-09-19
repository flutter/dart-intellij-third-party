/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.DartLspWorkspaceConfigurationConsumer
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import com.google.gson.JsonObject
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.hints.DartTypesInlayHintsProvider
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests when the settings of Settings | Editor | Inlay Hints are pushed to the Dart Analysis
 * Server. The server only reads them when it is told to, and it must not be told more often than
 * necessary, so what matters here is the state machine that notices the drift and the one
 * notification it lets through.
 */
class DartLspConfigurationSyncTest : DartCodeInsightFixtureTestCase() {

    /** The first protocol version of the server that understands a notification from the client. */
    private val newServerVersion = "1.41.0"
    private val oldServerVersion = "1.40.0"

    private val state = DartLspConfigurationPushState()
    private val capturedNotifications = CopyOnWriteArrayList<JsonObject>()
    private lateinit var bridgeServer: DartBridgeLspServer

    override fun setUp() {
        super.setUp()

        DartAnalysisServerService.getInstance(project).setServer(object : RemoteAnalysisServerImpl(createStubSocket()) {
            override fun isSocketOpen(): Boolean = true
            override fun generateUniqueId(): String = "das_1"

            override fun sendNotificationToServer(notification: JsonObject) {
                capturedNotifications.add(notification)
            }

            override fun server_openUrlRequest(url: String?) {}
            override fun server_showMessageRequest(
                messageType: String?,
                message: String?,
                messageActions: MutableList<MessageAction>?,
                consumer: ShowMessageRequestConsumer?,
            ) {}

            override fun lsp_workspaceApplyEdit(
                params: DartLspApplyWorkspaceEditParams?,
                consumer: DartLspWorkspaceApplyEditRequestConsumer?,
            ) {}

            override fun lsp_workspaceConfiguration(
                sections: MutableList<String>?,
                consumer: DartLspWorkspaceConfigurationConsumer?,
            ) {}
        })

        bridgeServer = DartBridgeLspServer(project)
        connectBridgeToTheManager(bridgeServer)
        setServerVersion(newServerVersion)
    }

    override fun tearDown() {
        try {
            setServerVersion("")
            connectBridgeToTheManager(null)
            if (::bridgeServer.isInitialized) {
                bridgeServer.stop()
            }
            DartAnalysisServerService.getInstance(project).setServer(null)
            capturedNotifications.clear()
            // DeclarativeInlayHintsSettings is an application-level service; reset it so that
            // enabled providers do not leak into other tests.
            DeclarativeInlayHintsSettings.getInstance().loadState(DeclarativeInlayHintsSettings.HintsState())
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    private fun createStubSocket(): AnalysisServerSocket = object : AnalysisServerSocket {
        override fun getErrorStream(): ByteLineReaderStream? = null
        override fun getRequestSink(): RequestSink? = null
        override fun getResponseStream(): ResponseStream? = null
        override fun isOpen(): Boolean = true
        override fun start() {}
        override fun stop() {}
    }

    /** The protocol version is only known once the server is connected, so fake it here. */
    private fun setServerVersion(version: String) {
        val field = DartAnalysisServerService::class.java.getDeclaredField("myServerVersion")
            .apply { isAccessible = true }
        field.set(DartAnalysisServerService.getInstance(project), version)
    }

    /**
     * Pretends that an LSP client has connected to the bridge; the real connection needs a socket
     * and the LSP client of the platform.
     */
    private fun connectBridgeToTheManager(bridge: DartBridgeLspServer?) {
        val manager = project.service<DartBridgeLspServerManager>()
        val connection = bridge?.let {
            val connectionClass = Class.forName("${DartBridgeLspServerManager::class.java.name}\$ActiveConnection")
            val constructor = connectionClass.declaredConstructors[0].apply { isAccessible = true }
            constructor.newInstance(manager, Socket(), it, CompletableFuture.completedFuture(null))
        }
        DartBridgeLspServerManager::class.java.getDeclaredField("activeConnection")
            .apply { isAccessible = true }
            .set(manager, connection)
    }

    private fun currentSection() = DartLspInlayHintsConfiguration.buildDartSection()

    /** Hands the server the settings as they are right now, the way answering its pull does. */
    private fun serverReadsTheSettings() = state.configurationSentToServer(currentSection())

    private fun enableTypeHints() {
        DeclarativeInlayHintsSettings.getInstance()
            .setProviderEnabled(DartTypesInlayHintsProvider.PROVIDER_ID, true)
    }

    private fun turnOffReturnTypes() {
        DeclarativeInlayHintsSettings.getInstance().setOptionEnabled(
            DartTypesInlayHintsProvider.RETURN_TYPES_OPTION_ID,
            DartTypesInlayHintsProvider.PROVIDER_ID,
            false,
        )
    }

    private fun dartFile(): VirtualFile = myFixture.configureByText("test.dart", "").virtualFile

    // --- The state machine that notices the drift ---

    fun testNothingIsPushedBeforeTheServerHasReadTheSettings() {
        enableTypeHints()

        assertFalse(
            "the first pull of the server hands out the current settings anyway",
            state.beginPush(currentSection()),
        )
    }

    fun testNothingIsPushedWhileTheServerKnowsTheSettings() {
        serverReadsTheSettings()

        assertFalse(state.beginPush(currentSection()))
        assertFalse(state.beginPush(currentSection()))
    }

    fun testAChangedSettingIsPushedExactlyOnce() {
        serverReadsTheSettings()
        enableTypeHints()

        assertTrue(state.beginPush(currentSection()))
        assertFalse(
            "the server must not be notified again before it has read the settings",
            state.beginPush(currentSection()),
        )
        assertFalse(state.beginPush(currentSection()))
    }

    fun testTheNextChangeIsPushedOnceTheServerHasReadTheSettings() {
        serverReadsTheSettings()
        enableTypeHints()
        assertTrue(state.beginPush(currentSection()))

        serverReadsTheSettings()
        assertFalse("the server is up to date again", state.beginPush(currentSection()))

        // A sub-option that only the server can apply: while it is off the server does not even
        // compute the hints of that category, so the client cannot filter them out on its own.
        turnOffReturnTypes()
        assertTrue(state.beginPush(currentSection()))
    }

    fun testAPushThatCouldNotBeSentIsRetried() {
        serverReadsTheSettings()
        enableTypeHints()
        assertTrue(state.beginPush(currentSection()))

        state.pushFailed()

        assertTrue(state.beginPush(currentSection()))
    }

    fun testAStoppedServerHasToReadTheSettingsAgain() {
        serverReadsTheSettings()
        state.serverStopped()
        enableTypeHints()

        assertFalse("the next server reads the settings while it starts up", state.beginPush(currentSection()))
    }

    // --- The way from a changed checkbox to the notification on the wire ---

    fun testAnInlayHintRequestCycleNotifiesTheServerOfAChangedSetting() {
        enableTypeHints()
        val sync = DartLspConfigurationSync.getInstance(project)
        sync.configurationSentToServer(currentSection())
        // A sub-option only takes effect while its parent checkbox is on.
        turnOffReturnTypes()

        val support = DartLspInlayHintSupport(project)
        support.shouldAskServerForInlayHints(dartFile())
        support.shouldAskServerForInlayHints(dartFile())

        assertEquals("the server must be notified exactly once", 1, capturedNotifications.size)
        val lspNotification = requireNotNull(
            capturedNotifications[0].getAsJsonObject("params")?.getAsJsonObject("lspNotification")
        ) { "the notification should carry an LSP notification: ${capturedNotifications[0]}" }
        assertEquals("workspace/didChangeConfiguration", lspNotification.get("method").asString)
    }

    fun testTheSettingsAreNotPushedToAServerThatCannotHandleTheNotification() {
        setServerVersion(oldServerVersion)
        enableTypeHints()
        val sync = DartLspConfigurationSync.getInstance(project)
        sync.configurationSentToServer(currentSection())
        turnOffReturnTypes()

        DartLspInlayHintSupport(project).shouldAskServerForInlayHints(dartFile())

        assertEquals("an older server logs a notification from the client as an error", 0, capturedNotifications.size)
    }

    fun testTheServerIsNotNotifiedWhileItStillKnowsTheSettings() {
        val sync = DartLspConfigurationSync.getInstance(project)
        sync.configurationSentToServer(currentSection())

        DartLspInlayHintSupport(project).shouldAskServerForInlayHints(dartFile())

        assertEquals(0, capturedNotifications.size)
    }
}
