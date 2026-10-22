/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.analytics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.NioFiles
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * A throwaway unified_analytics home directory with a pre-consented config.
 *
 * Spawned Dash tools (dartdev, the analysis server) are pointed at this directory via [harnessEnv], so
 * every event they would report is recorded in its telemetry log, where a test can read it back with
 * [events].
 */
internal class TelemetryHome private constructor(private val root: Path) {
  private val dartToolDir: Path = root.resolve(".dart-tool")
  private val configFile: Path = dartToolDir.resolve("dart-flutter-telemetry.config")
  private val logFile: Path = dartToolDir.resolve("dart-flutter-telemetry.log")

  /** The analysis server's state directory (`~/.dartServer`), created during analysis. */
  private val dasStateDir: Path = root.resolve(".dartServer")

  companion object {
    /**
     * The consent message version unified_analytics expects (`kToolsMessageVersion`).
     *
     * A tool line with a lower version is treated as a first run, and first-run events are dropped.
     */
    const val TOOLS_MESSAGE_VERSION = 1

    /**
     * Creates a new home that has consented to analytics for each label in [consentedTools].
     *
     * Every tool label a test expects events under needs a line here. Without one, unified_analytics
     * treats the tool as first-run and silently drops events, which would look like a failed env hop.
     * For the same reason, also consent any label a suppression test must prove silent (for example
     * `dart-tool`); otherwise that silence is vacuous.
     */
    fun create(consentedTools: List<String>): TelemetryHome {
      val home = TelemetryHome(Files.createTempDirectory("dash-telemetry-home"))
      try {
        home.seed(consentedTools)
      } catch (t: Throwable) {
        home.delete()
        throw t
      }
      return home
    }
  }

  private fun seed(tools: List<String>) {
    Files.createDirectories(dartToolDir)
    val today = LocalDate.now().toString() // yyyy-MM-dd
    val toolLines = tools.joinToString("\n") { "$it=$today,$TOOLS_MESSAGE_VERSION" }
    // The config file must already exist; if unified_analytics has to create it, the run counts as a
    // first run and nothing is reported. The client ID, session, and log files are created on demand.
    Files.writeString(configFile, "reporting=1\n$toolLines\n")
  }

  /** Env to inject into spawned Dash processes via `Analytics.setEnvironmentOverridesForTests`. */
  fun harnessEnv(): Map<String, String> {
    // Unified analytics has no endpoint override, so route its Google Analytics sends to a port nothing
    // listens on (discard, 9). The local log is written before the network send, so assertions still work.
    val blackhole = "http://127.0.0.1:9"
    return mapOf(
      // Redirect the home directory for the spawned processes only; the test JVM's HOME is untouched.
      // unified_analytics uses HOME on Linux and macOS and AppData on Windows. The analysis server keeps
      // its state under HOME, or LOCALAPPDATA on Windows; see [usedByAnalysisServer].
      "HOME" to root.toString(),
      "AppData" to root.toString(),
      "APPDATA" to root.toString(),
      "LOCALAPPDATA" to root.toString(),
      // Keep using the real pub cache so the tools don't try to create one under the temp home.
      "PUB_CACHE" to realPubCache(),
      // dartdev treats CI environments (GITHUB_ACTIONS, CI, ...) as bots and forces analytics suppression,
      // which would silence the analysis server. BOT=false short-circuits that check.
      "BOT" to "false",
      "https_proxy" to blackhole,
      "HTTPS_PROXY" to blackhole,
      "http_proxy" to blackhole,
      "HTTP_PROXY" to blackhole,
      "no_proxy" to "",
      "NO_PROXY" to "",
    )
  }

  /** The pub cache the tools would use without the harness (pub's own platform defaults). */
  private fun realPubCache(): String {
    System.getenv("PUB_CACHE")?.let { return it }
    if (SystemInfo.isWindows) {
      System.getenv("LOCALAPPDATA")?.let { return Path.of(it, "Pub", "Cache").toString() }
    }
    return Path.of(System.getProperty("user.home"), ".pub-cache").toString()
  }

  /**
   * Whether the analysis server used this directory as its home.
   *
   * A positive control for tests that expect an empty log: an empty log only means something if the
   * server was actually looking at this home.
   */
  fun usedByAnalysisServer(): Boolean = Files.isDirectory(dasStateDir)

  data class LoggedEvent(val name: String, val tool: String?, val raw: JsonObject)

  /** Parses the JSON-lines telemetry log. Returns an empty list if the log doesn't exist. */
  fun events(): List<LoggedEvent> {
    if (!Files.exists(logFile)) return emptyList()
    return Files.readAllLines(logFile).filter { it.isNotBlank() }.flatMap { line ->
      // The last line may be truncated if the server was killed mid-write. Skip it here; [dump] still
      // shows it in failure messages.
      val obj = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull()
        ?: return@flatMap emptyList()
      val tool = obj.getAsJsonObject("user_properties")
        ?.getAsJsonObject("tool")?.get("value")?.asString
      obj.getAsJsonArray("events")?.mapNotNull { e ->
        e.asJsonObject["name"]?.asString?.let { LoggedEvent(it, tool, obj) }
      } ?: emptyList()
    }
  }

  fun dump(): String = if (Files.exists(logFile)) Files.readString(logFile) else "<no log file>"

  /** Deletes the home. Throws if anything is left behind, so leaks surface in tearDown. */
  fun delete() {
    NioFiles.deleteRecursively(root)
  }
}
