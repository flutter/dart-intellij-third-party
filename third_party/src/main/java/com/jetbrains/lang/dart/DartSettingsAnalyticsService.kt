/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.analytics.Analytics
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reports the settings analytics event ([buildSettingsData]) once per project session, the first time Dart support is
 * usable: on project open ([DartStartupActivity]), or later, when Dart support is enabled or the Dart SDK library
 * appears ([DartWorkspaceModelChangeListener]) or a `pub get` finishes (`DartPubActionBase`). Without the later calls,
 * new and cloned Flutter projects, and projects whose SDK is set after open, would never report it.
 */
@Service(Service.Level.PROJECT)
class DartSettingsAnalyticsService private constructor(private val project: Project, private val cs: CoroutineScope) {

  /**
   * Set once a report is in flight or done (even if analytics turn out to be suppressed): it happens at most once.
   * [resetForTests] swaps in a new one, so a report still in flight from an earlier test can't claim it.
   */
  @Volatile
  private var claimed = AtomicBoolean()

  /** Replaces the config wait and the report in [reportIfNeeded] for tests. */
  @TestOnly
  @Volatile
  internal var reportForTests: ((SettingsReportInfo) -> Unit)? = null

  /**
   * Reports the settings event if Dart support is usable and it hasn't been reported in this session. Safe to call
   * repeatedly, from any thread: it returns at once and reports in the background.
   */
  fun reportIfNeeded(): Job? {
    val claimed = claimed
    if (claimed.get() || project.isDisposed) return null
    val reportForTests = reportForTests
    // Analytics are always suppressed in tests: don't fetch the config from the many tests that configure an SDK.
    if (reportForTests == null && ApplicationManager.getApplication().isUnitTestMode) return null

    return cs.launch {
      val info = readAction { settingsReportInfo() } ?: return@launch
      if (!claimed.compareAndSet(false, true)) return@launch

      if (reportForTests != null) {
        reportForTests(info)
        return@launch
      }
      val sdk = info.sdk ?: return@launch
      runInterruptible(Dispatchers.IO) {
        Analytics.getConfiguration(sdk, project)
        // getConfiguration swallows an interrupt, so check for cancellation before the next wait.
        ensureActive()
        // getConfiguration gives up after 700 ms; wait (bounded) for a slower resolution rather than drop the event.
        Analytics.awaitConfigurationResolved(ANALYTICS_CONFIG_WAIT_MS)
      }
      ensureActive()
      // The global state, not getConfiguration's result: a retry of a failed resolution replaces that object.
      if (project.isDisposed || Analytics.suppressAnalytics) return@launch

      Analytics.report(buildSettingsData(project, info))
    }
  }

  /**
   * The settings to report, or `null` if Dart support isn't usable: no SDK, no module with Dart enabled, or an SDK too
   * old to run the DTD, which delivers analytics events (fixing it later can still report).
   */
  private fun settingsReportInfo(): SettingsReportInfo? {
    if (project.isDisposed) return null
    val sdk = DartSdk.getDartSdk(project) ?: return null
    if (!DartToolingDaemonService.isDartSdkVersionSufficient(sdk)) return null
    if (ModuleManager.getInstance(project).modules.none { DartSdkLibUtil.isDartSdkEnabled(it) }) return null
    return SettingsReportInfo(sdk, true, sdk.version, DartConfigurable.isExperimentalLspFeaturesEnabled(project))
  }

  /** Lets a test report again: the light test project, and so this service, is shared between tests. */
  @TestOnly
  internal fun resetForTests() {
    reportForTests = null
    claimed = AtomicBoolean()
  }

  companion object {
    /** How long the report waits for the analytics config to resolve; if it takes longer, the event is dropped. */
    private const val ANALYTICS_CONFIG_WAIT_MS = 3_000L

    @JvmStatic
    fun getInstance(project: Project): DartSettingsAnalyticsService = project.service()
  }
}
