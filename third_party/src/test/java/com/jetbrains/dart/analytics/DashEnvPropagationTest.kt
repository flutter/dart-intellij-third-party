/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.analytics

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.TimeoutUtil
import com.jetbrains.lang.dart.analytics.Analytics
import com.jetbrains.lang.dart.analytics.AnalyticsConfiguration
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.util.DartTestUtils
import java.io.IOException

private val LOG = Logger.getInstance(DashEnvPropagationTest::class.java)

// Tool labels, matching unified_analytics' DashTool labels exactly.
private const val INTELLIJ = "intellij-plugins"
private const val ANDROID_STUDIO = "android-studio-plugins"

/**
 * The tool dartdev substitutes when no `DASH__TOOL` reaches it.
 *
 * Every session consents to this tool too. Otherwise a broken env hop would make the server report
 * under an unconsented tool, which unified_analytics drops as a first run, and the suppression tests
 * would pass for the wrong reason.
 */
private const val DART_TOOL = "dart-tool"

/** The event the server sends on shutdown once initial analysis is done. */
private const val SERVER_SESSION = "server_session"

/**
 * Event names emitted only by the analysis server: the "language_server" `DashEvent` labels in
 * unified_analytics, as shipped in Dart 3.13.0.
 *
 * Assertions only count these because dartdev keeps the incoming `DASH__TOOL` and may log its own
 * events under the same tool label; those would mask what the server itself did.
 */
private val DAS_EVENTS = setOf(
  "analysis_statistics",
  "client_notification",
  "client_request",
  "command_executed",
  "context_structure",
  "lint_usage_count",
  "memory_info",
  "plugin_request",
  "plugin_use",
  "plugins",
  SERVER_SESSION,
  "severity_adjustment",
)

/**
 * How long to wait for [SERVER_SESSION] when events are expected. Generous because server startup on
 * CI runners (ubuntu-large) can be slow; see spec §12 Q5.
 */
private const val EVENTS_WAIT_TIMEOUT_MS = 10_000L

/**
 * How long to watch for stray server events when none are expected. [DartAnalysisServerService]
 * already waits for the server process to exit, so this only needs to cover a late write after a
 * forced kill.
 */
private const val STRAY_EVENTS_WATCH_MS = 2_000L

/** One analysis server session: the home it reported into and what it logged. */
private data class DasSession(val home: TelemetryHome, val events: List<TelemetryHome.LoggedEvent>) {
  val dasEvents: List<TelemetryHome.LoggedEvent> get() = events.filter { it.name in DAS_EVENTS }
}

/**
 * End-to-end test that the plugin's `DASH__*` analytics environment flows through
 * `dart language-server` (dartdev) into the Dart Analysis Server, and that the server
 * reports under the root tool or stays silent when suppressed.
 *
 * This asserts on what the server actually records in its unified_analytics log, not just on
 * which env vars the plugin sets. Each test starts a real analysis server whose telemetry home
 * is redirected to a throwaway [TelemetryHome].
 *
 * See flutter/dash-devexp-internal#106.
 */
class DashEnvPropagationTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {

  private lateinit var config: AnalyticsConfiguration
  private var savedEnvSuppress = true
  private var savedTelemetryEnabled = false
  private var savedShowMessage = false
  private var home: TelemetryHome? = null

  override fun setUp() {
    super.setUp()
    // Don't start the server here: it would run with the real HOME and default analytics settings.
    // Each test starts its single server in runDasSession, after the harness environment is installed.
    DartTestUtils.configureDartSdk(
      myModule,
      myFixture.testRootDisposable,
      /* realSdk = */ true,
      /* startServer = */ false,
    )
    // When server results arrive, DartServerData.forceFileAnnotation restarts the daemon, which the
    // fixture otherwise rejects as a model change during highlighting.
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    // Each test gets its own project, so no server should be running yet. Stop one anyway as a
    // cheap guard, so a server with a different environment can never leak into a test.
    das().stopServer_TESTS_ONLY()

    savedEnvSuppress = Analytics.suppressAnalytics
    // In unit-test mode this returns the same shared instance that DartAnalysisServerService uses.
    val sdk = checkNotNull(DartSdk.getDartSdk(project)) { "Dart SDK is not configured" }
    config = Analytics.getConfiguration(sdk, project)
    savedTelemetryEnabled = config.telemetryEnabled
    savedShowMessage = config.shouldShowMessage
  }

  override fun tearDown() {
    val homeToDelete = home
    home = null
    try {
      das().stopServer_TESTS_ONLY()
      Analytics.suppressAnalytics = savedEnvSuppress
      // Guard against a setUp that failed before config was assigned, so its error isn't masked.
      if (::config.isInitialized) {
        config.telemetryEnabled = savedTelemetryEnabled
        config.shouldShowMessage = savedShowMessage
      }
    } catch (e: Throwable) {
      addSuppressedException(e)
    } finally {
      var tearDownFailure: Throwable? = null
      try {
        // Disposes testRootDisposable, which removes the env overrides. Only after that is it safe
        // to delete the home they point at; a process spawned in between would recreate it.
        super.tearDown()
      } catch (t: Throwable) {
        tearDownFailure = t
        throw t
      } finally {
        try {
          homeToDelete?.delete()
        } catch (e: Throwable) {
          // Don't let a cleanup failure (e.g. files still locked on Windows) mask a tearDown failure.
          // addSuppressedException won't do here: super.tearDown() has already reported by now.
          val primary = tearDownFailure
          if (primary != null) primary.addSuppressed(e) else throw e
        }
      }
    }
  }

  /** 7.1: Happy path. With real plugin values, the server reports under `intellij-plugins`. */
  fun testDasReportsUnderRootTool_intellij() {
    val session = runDasSession(
      pluginTelemetryEnabled = true,
      pluginEnvSuppress = false,
      consentedTools = listOf(INTELLIJ),
    )

    assertFalse(
      "Expected the analysis server to log events. Check BOT=false, the consent line, and that analysis " +
        "completed. ${describe(session)}",
      session.dasEvents.isEmpty()
    )
    assertAllUnderTool(session, INTELLIJ)
    assertTrue(
      "Expected a $SERVER_SESSION event (sent on shutdown after initial analysis). ${describe(session)}",
      session.dasEvents.any { it.name == SERVER_SESSION }
    )
  }

  /**
   * 7.2: Proves `DASH__TOOL` reaches the server.
   *
   * Without the env var, the server derives the tool from `--client-id` and would report under
   * `intellij-plugins` anyway. Overriding `DASH__TOOL` to a different tool and seeing the server
   * report under *that* label shows the value came through dartdev.
   */
  fun testDasUsesEnvToolOverClientId() {
    val session = runDasSession(
      pluginTelemetryEnabled = true,
      pluginEnvSuppress = false,
      consentedTools = listOf(INTELLIJ, ANDROID_STUDIO),
      extraEnv = mapOf("DASH__TOOL" to ANDROID_STUDIO),
    )

    assertFalse("Expected the analysis server to log events. ${describe(session)}", session.dasEvents.isEmpty())
    assertAllUnderTool(session, ANDROID_STUDIO)
  }

  /**
   * 7.3: Proves `DASH__SUPPRESS_ANALYTICS` crosses the plugin-to-dartdev hop, and that dartdev's
   * resulting suppression reaches the server. (dartdev folds the value into its own decision and
   * overwrites it, so this doesn't show the plugin's original value reaching the server unchanged.)
   *
   * Telemetry is enabled in the plugin config, so no `--suppress-analytics` flag is passed. Only the
   * env var asks for suppression, so a silent server means the env value got through.
   */
  fun testEnvOnlySuppressionSilencesDas() {
    val session = runDasSession(
      pluginTelemetryEnabled = true,
      pluginEnvSuppress = true,
      consentedTools = listOf(INTELLIJ),
    )

    assertNoDasEvents(session)
    assertNothingUnderRootTool(session)
  }

  /**
   * 7.4: Regression guard for the `--suppress-analytics` flag.
   *
   * This doesn't prove env propagation on its own, but it keeps the flag channel covered so a
   * regression in either channel can be told apart.
   */
  fun testFlagSuppressionSilencesDas() {
    val session = runDasSession(
      pluginTelemetryEnabled = false,
      pluginEnvSuppress = false,
      consentedTools = listOf(INTELLIJ),
    )

    assertNoDasEvents(session)
    // dartdev doesn't report anything of its own for `language-server` either, so nothing should be
    // logged under the root tool at all.
    assertNothingUnderRootTool(session)
  }

  /** Runs one analysis server session with the given knobs and returns what it logged. */
  private fun runDasSession(
    pluginTelemetryEnabled: Boolean, // Controls the --suppress-analytics flag.
    pluginEnvSuppress: Boolean, // Controls DASH__SUPPRESS_ANALYTICS.
    consentedTools: List<String>,
    extraEnv: Map<String, String> = emptyMap(),
  ): DasSession {
    check(home == null) { "runDasSession may only be called once per test" }
    val sessionHome = TelemetryHome.create(consentedTools + DART_TOOL)
    home = sessionHome

    // Unit-test mode suppresses analytics by default through two independent channels, so a
    // positive test has to flip both.
    config.telemetryEnabled = pluginTelemetryEnabled
    config.shouldShowMessage = false
    Analytics.suppressAnalytics = pluginEnvSuppress
    Analytics.setEnvironmentOverridesForTests(sessionHome.harnessEnv() + extraEnv, myFixture.testRootDisposable)

    val file = myFixture.configureByText("main.dart", "void main() { print('hi'); }").virtualFile
    myFixture.doHighlighting() // Starts the server via serverReadyForRequest().

    // The server only sends server_session once initial analysis is done, so wait for it.
    das().waitForAnalysisToComplete_TESTS_ONLY(file)

    // Guard against vacuous passes in the suppression cases: the server must have actually run,
    // analyzed the file, and done so with the redirected home, not just failed to start or written
    // somewhere else.
    assertTrue("Expected the analysis server to be running", das().isServerProcessActive)
    assertFalse("Expected the analysis server to have analyzed ${file.name}", das().getHighlight(file).isEmpty())
    assertTrue("Expected the analysis server to use the temp home as HOME", sessionHome.usedByAnalysisServer())

    // Stopping the server flushes its analytics (server.shutdown -> AnalyticsManager.shutdown).
    das().stopServer_TESTS_ONLY()

    val expectEvents = pluginTelemetryEnabled && !pluginEnvSuppress
    val events = if (expectEvents) {
      // Wait for the event the positive tests rely on, not just the first server event.
      awaitEvents(sessionHome, EVENTS_WAIT_TIMEOUT_MS) { events -> events.any { it.name == SERVER_SESSION } }
    } else {
      awaitEvents(sessionHome, STRAY_EVENTS_WATCH_MS) { events -> events.any { it.name in DAS_EVENTS } }
    }

    val session = DasSession(sessionHome, events)
    LOG.info("${getTestName(false)}: ${describe(session)}")
    return session
  }

  /**
   * Polls the log until [isDone] holds or [timeoutMs] passes, then returns what's there. Dispatches
   * EDT events between polls, since the test runs on the EDT.
   */
  private fun awaitEvents(
    home: TelemetryHome,
    timeoutMs: Long,
    isDone: (List<TelemetryHome.LoggedEvent>) -> Boolean,
  ): List<TelemetryHome.LoggedEvent> {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      // A read can fail transiently (e.g. a sharing violation on Windows) while the server is still
      // writing or shutting down; just try again on the next poll.
      val events = try {
        home.events()
      } catch (_: IOException) {
        null
      }
      if (events != null && isDone(events)) return events
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      TimeoutUtil.sleep(100)
    }
    // The final read is deliberately strict: in the suppression tests an unreadable log must fail the
    // test rather than look like an empty one.
    return home.events()
  }

  private fun describe(session: DasSession): String =
    "clientId=${DartAnalysisServerService.getClientId()}. Telemetry log:\n${session.home.dump()}"

  private fun assertAllUnderTool(session: DasSession, tool: String) {
    assertEmpty(
      "Expected every analysis server event under $tool. ${describe(session)}",
      session.dasEvents.filter { it.tool != tool }
    )
  }

  private fun assertNoDasEvents(session: DasSession) {
    assertEmpty("Expected no analysis server events. ${describe(session)}", session.dasEvents)
  }

  private fun assertNothingUnderRootTool(session: DasSession) {
    assertEmpty(
      "Expected no events at all under $INTELLIJ (dartdev should be silent too). ${describe(session)}",
      session.events.filter { it.tool == INTELLIJ }
    )
  }

  private fun das(): DartAnalysisServerService = DartAnalysisServerService.getInstance(project)
}
