/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.analytics

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.lang.dart.dtd.DTDProcess
import com.jetbrains.lang.dart.dtd.DTDProcessListener
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.util.PrintingLogger
import de.roderick.weberknecht.WebSocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/// Sends logging to the console.
private const val DEBUGGING_LOCALLY = true

private val DEFAULT_RESPONSE_TIMEOUT = 1.seconds

private object UnifiedAnalytics {
  private val logger: Logger =
    if (DEBUGGING_LOCALLY) PrintingLogger.SYSTEM_OUT else Logger.getInstance(UnifiedAnalytics::class.java)

  /// Service name for the DTD-hosted unified analytics service.
  const val SERVICE_NAME = "UnifiedAnalytics"

  /// Service method name for the method that determines whether the unified
  /// analytics client should display the consent message.
  const val SHOULD_SHOW_MESSAGE = "shouldShowMessage"

  /// Service method name for the method that returns the unified analytics
  /// consent message to prompt users with.
  const val GET_CONSENT_MESSAGE = "getConsentMessage"

  /// Service method name for the method that sends an event to unified
  /// analytics.
  const val SEND = "send"

  /// Service method name for the method that returns whether unified analytics
  /// telemetry is enabled.
  const val TELEMETRY_ENABLED = "telemetryEnabled"

  fun callServiceWithJsonResponse(dtdProcess: DTDProcess, name: String, timeout: Duration = DEFAULT_RESPONSE_TIMEOUT): JsonElement? {
    val params = JsonObject()
    params.addProperty("tool", getToolName())
    var value: JsonElement? = null
    try {
      val latch = CountDownLatch(1)
      dtdProcess.sendRequest(
        "$SERVICE_NAME.$name",
        params,
        true
      ) { response ->
        logger.debug("$SERVICE_NAME.$name.received: ")
        val result = response["result"]
        if (result is JsonObject) {
          value = result["value"]
        }
        logger.debug("\t$response")
        latch.countDown()
      }

      val completed = latch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      if (!completed) {
        throw TimeoutException("Call to $SERVICE_NAME.$name timed out after $DEFAULT_RESPONSE_TIMEOUT.")
      }

    } catch (e: WebSocketException) {
      logger.error(e)
    }
    return value
  }

  fun callServiceWithStringResponse(dtdProcess: DTDProcess, name: String): String? =
    callServiceWithJsonResponse(dtdProcess, name)?.asString

  fun callServiceWithBoolResponse(dtdProcess: DTDProcess, name: String): Boolean =
    callServiceWithJsonResponse(dtdProcess, name)?.asBoolean ?: false
}

class UnifiedAnalyticsData {
  var shouldShowMessage: Boolean = false
    internal set
  var consentMessage: String? = null
    internal set
  var telemetryEnabled: Boolean = false
    internal set
}

object Analytics {
  private val logger: Logger =
    if (DEBUGGING_LOCALLY) PrintingLogger.SYSTEM_OUT else Logger.getInstance(Analytics::class.java)

  private var data: UnifiedAnalyticsData? = null

  @JvmStatic
  fun initialize(sdk: DartSdk): UnifiedAnalyticsData {
    logger.debug("Analytics.initialize")

    data?.let { return it }

    data = UnifiedAnalyticsData()
    
    val dtdProcess = DTDProcess()
    dtdProcess.listener = object : DTDProcessListener {
      override fun onProcessStarted(uri: String?) {
        logger.debug("DartAnalysisServerService.onProcessStarted")

        val params = JsonObject()
        params.addProperty("tool", getToolName())

        try {
          data!!.shouldShowMessage =
            UnifiedAnalytics.callServiceWithBoolResponse(dtdProcess, UnifiedAnalytics.SHOULD_SHOW_MESSAGE)
          if (data!!.shouldShowMessage) {
            data!!.consentMessage =
              UnifiedAnalytics.callServiceWithStringResponse(dtdProcess, UnifiedAnalytics.GET_CONSENT_MESSAGE)
            data!!.telemetryEnabled = false
          } else {
            data!!.telemetryEnabled =
              UnifiedAnalytics.callServiceWithBoolResponse(dtdProcess, UnifiedAnalytics.TELEMETRY_ENABLED)
          }
        } catch (t: Throwable) {
          logger.error(t)
        } finally {
          dtdProcess.terminate()
        }
      }
    }
    dtdProcess.start(sdk)

    // TODO: fix race condition if we get here before the first countdown latch is started

    return data!!
  }
}

private fun getToolName(): String = when (ApplicationInfo.getInstance().build.productCode) {
  "AI" -> "android-studio-plugins"
  else -> "intellij-plugins"
}
