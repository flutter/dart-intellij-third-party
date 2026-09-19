/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import org.eclipse.lsp4j.DidChangeConfigurationParams

/** The LSP notification that makes the server read the configuration again. */
private const val DID_CHANGE_CONFIGURATION = "workspace/didChangeConfiguration"

/**
 * Keeps the `dart` configuration section that the Dart Analysis Server holds in sync with the Dart
 * checkboxes in Settings | Editor | Inlay Hints.
 *
 * The server never reads the settings on its own, it pulls them with `workspace/configuration`:
 * once while it starts up, because the client advertises the `workspace.configuration` capability,
 * and once more for every `workspace/didChangeConfiguration` notification it gets. There is no
 * event to hang that notification on - `DeclarativeInlayHintsSettings` publishes nothing when the
 * user flips a checkbox - so the drift is noticed in
 * [DartLspInlayHintSupport.shouldAskServerForInlayHints], which runs before every inlay hint
 * request cycle.
 */
@Service(Service.Level.PROJECT)
class DartLspConfigurationSync(private val project: Project) {

    companion object {
        @JvmStatic
        fun getInstance(project: Project): DartLspConfigurationSync = project.service()
    }

    private val state = DartLspConfigurationPushState()

    /**
     * Notifies the server that the settings have changed, unless it already knows them or it has
     * been notified and has not read them again yet.
     */
    fun pushConfigurationIfChanged() {
        if (!DartAnalysisServerService.getInstance(project).isLspConfigurationNotificationSupported) return
        if (!state.beginPush(DartLspInlayHintsConfiguration.buildDartSection())) return

        // There is no bridge while no LSP client is connected, and then there are no inlay hints to
        // get wrong either, so the next request cycle can try again.
        val bridgeServer = project.serviceIfCreated<DartBridgeLspServerManager>()?.bridgeServer
        if (bridgeServer == null) {
            state.pushFailed()
            return
        }

        // The settings stay empty: the server ignores them and pulls workspace/configuration.
        bridgeServer.forwardNotification(DID_CHANGE_CONFIGURATION, DidChangeConfigurationParams(JsonObject()))
    }

    /** Remembers the section that the server has just read. */
    fun configurationSentToServer(section: JsonObject) {
        state.configurationSentToServer(section)
    }

    /** Forgets what the server knew; the next server has to read the settings again. */
    fun serverStopped() {
        state.serverStopped()
    }
}

/**
 * Decides when the server has to be told that the configuration has changed.
 *
 * The state is shared between the thread that computes the inlay hints and the response reader
 * thread of the server, which answers the pull, so it is guarded by a lock.
 */
internal class DartLspConfigurationPushState {

    private val lock = Any()

    /** The section the server read last, or `null` while it has not read the settings at all. */
    private var lastSentSection: JsonObject? = null

    /** Whether a notification is on its way that the server has not answered with a pull yet. */
    private var pushPending = false

    /**
     * Returns whether the server has to be notified of [currentSection], and remembers that it is
     * being notified. A caller that gets `true` and cannot send the notification has to say so with
     * [pushFailed].
     */
    fun beginPush(currentSection: JsonObject): Boolean = synchronized(lock) {
        // As long as the server has not read the settings there is nothing to push: whenever it
        // gets around to reading them, it reads the current ones.
        val sentSection = lastSentSection ?: return false
        if (pushPending || sentSection == currentSection) return false

        pushPending = true
        return true
    }

    /** Takes back a [beginPush] whose notification never went out. */
    fun pushFailed() = synchronized(lock) {
        pushPending = false
    }

    /** Remembers the section that the server has just read. */
    fun configurationSentToServer(section: JsonObject) = synchronized(lock) {
        pushPending = false
        lastSentSection = section
    }

    /** Forgets what the server knew; the next server has to read the settings again. */
    fun serverStopped() = synchronized(lock) {
        lastSentSection = null
        pushPending = false
    }
}
