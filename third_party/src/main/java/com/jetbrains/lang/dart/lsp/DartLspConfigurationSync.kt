/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.impl.LspServerManagerImpl
import com.intellij.psi.PsiManager
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.jetbrains.annotations.VisibleForTesting

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
 *
 * That is the only place it is noticed: while no Dart editor is open there is no such cycle, so a
 * change the user makes then reaches the server when the next Dart file is opened - which is early
 * enough, because until then there are no hints to get wrong.
 */
@Service(Service.Level.PROJECT)
class DartLspConfigurationSync(private val project: Project) {

    companion object {
        fun getInstance(project: Project): DartLspConfigurationSync = project.service()
    }

    private val state = DartLspConfigurationPushState()

    /**
     * Has the inlay hints computed again. Replaced in tests, which have neither the EDT nor a
     * daemon to run the real one on.
     */
    @VisibleForTesting
    internal var refreshInlayHints: () -> Unit = ::scheduleInlayHintRefresh

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
        val sent = bridgeServer.forwardNotification(DID_CHANGE_CONFIGURATION, DidChangeConfigurationParams(JsonObject()))
        if (!sent) state.pushFailed()
    }

    /**
     * Remembers the section that the server has just read, and has the inlay hints computed again
     * if it read them because the settings had changed.
     */
    fun configurationSentToServer(section: JsonObject) {
        if (state.configurationSentToServer(section)) refreshInlayHints()
    }

    /**
     * Throws away the hints that the server computed from the old settings and has the inlay hint
     * pass run again.
     *
     * The server applies the new configuration silently - it sends no
     * `workspace/inlayHint/refresh` - and
     * [com.intellij.platform.dartlsp.impl.features.inlayHint.LspInlayHintsProvider] never asks for
     * hints itself, it only filters the ones the cache holds. That cache
     * ([com.intellij.platform.dartlsp.impl.features.highlightingCommon.LspHighlightingCache]) is
     * keyed on `PsiModificationTracker.modificationCount` and asks the server again only when that
     * count has moved, which neither a daemon restart nor `forceHintsUpdateOnNextPass` does. So a
     * category that the server stopped computing would stay on screen, and one it started
     * computing would never appear, until the user happens to edit the file.
     *
     * Bumping that count from here is therefore deliberate. [PsiManager.dropPsiCaches] is the
     * public API that does it (it fires a PSI change that `PsiModificationTrackerImpl` turns into
     * an increment, and it has to run on the EDT or in a write action); the lighter
     * `PsiModificationTrackerImpl.incCounter()` is not API, it would need a cast to an
     * implementation class. The cost is that every `CachedValue` keyed on the PSI modification
     * count is recomputed once, project-wide, per settings change - i.e. per rare and explicit
     * user action.
     *
     * The clean fix is an invalidation hook on the cache of the vendored LSP client; that is a
     * change to JetBrains-owned code and is proposed to the maintainers along with this work.
     */
    private fun scheduleInlayHintRefresh() {
        ApplicationManager.getApplication().invokeLater({
            PsiManager.getInstance(project).dropPsiCaches()
            LspServerManagerImpl.refreshInlayHints(project)
        }, ModalityState.nonModal(), project.disposed)
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

    /** The section a notification is on its way for, or `null` while none is outstanding. */
    private var pendingSection: JsonObject? = null

    /**
     * Returns whether the server has to be notified of [currentSection], and remembers that it is
     * being notified. A caller that gets `true` and cannot send the notification has to say so with
     * [pushFailed].
     */
    fun beginPush(currentSection: JsonObject): Boolean = synchronized(lock) {
        // As long as the server has not read the settings there is nothing to push: whenever it
        // gets around to reading them, it reads the current ones.
        val sentSection = lastSentSection ?: return false
        if (sentSection == currentSection) return false
        // A notification is only still outstanding while it is about these very settings; once the
        // settings have moved on, the server has to hear about the new ones.
        if (pendingSection == currentSection) return false

        pendingSection = currentSection
        return true
    }

    /** Takes back a [beginPush] whose notification never went out. */
    fun pushFailed() = synchronized(lock) {
        pendingSection = null
    }

    /**
     * Remembers the section that the server has just read, and returns whether the server has
     * thereby changed its mind about which hints to compute, i.e. whether the hints it computed
     * before are stale now.
     *
     * Every read makes whatever notification was outstanding pointless: the server has just taken
     * the current settings, so there is nothing left for it to be told about them. Keeping the
     * notification would block the very same change from being pushed again - the user flips a
     * checkbox back and forth while the notification is on its way, the server reads the old
     * settings, and the new ones would never be sent again.
     *
     * A read that changed nothing needs no new hints - that is the pull the server makes while it
     * starts up, and any pull it makes on its own afterwards.
     */
    fun configurationSentToServer(section: JsonObject): Boolean = synchronized(lock) {
        val previousSection = lastSentSection
        lastSentSection = section
        pendingSection = null
        return previousSection != null && previousSection != section
    }

    /** Forgets what the server knew; the next server has to read the settings again. */
    fun serverStopped() = synchronized(lock) {
        lastSentSection = null
        pendingSection = null
    }
}
