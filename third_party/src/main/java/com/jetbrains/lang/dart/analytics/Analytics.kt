/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.analytics

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.CommonBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.dtd.DTDProcess
import com.jetbrains.lang.dart.dtd.DTDProcessListener
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.util.PrintingLogger
import de.roderick.weberknecht.WebSocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/// Sends logging to the console.
private const val DEBUGGING_LOCALLY = false
private const val DAS_NOTIFICATION_GROUP = "Dart Analysis Server"

private val DEFAULT_RESPONSE_TIMEOUT = 1.seconds

private object UnifiedAnalytics {
  object Property {
    const val EVENT = "event"
    const val EVENT_DATA = "eventData"
    const val EVENT_NAME = "eventName"
    const val RESULT = "result"
    const val TOOL = "tool"
    const val VALUE = "value"
  }

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

  /// Service method name for the method that confirms that a unified analytics
  /// client showed the required consent message.
  const val CLIENT_SHOWED_MESSAGE = "clientShowedMessage"

  /// Service method name for the method that sends an event to unified
  /// analytics.
  const val SEND = "send"

  /// Service method name for the method that returns whether unified analytics
  /// telemetry is enabled.
  const val TELEMETRY_ENABLED = "telemetryEnabled"

  fun callServiceWithJsonResponse(
    dtdProcess: DTDProcess,
    name: String,
    timeout: Duration = DEFAULT_RESPONSE_TIMEOUT
  ): JsonElement? {
    val params = JsonObject()
    params.addProperty(Property.TOOL, getToolName())
    var value: JsonElement? = null
    try {
      val latch = CountDownLatch(1)
      dtdProcess.sendRequest(
        "$SERVICE_NAME.$name",
        params,
        true
      ) { response ->
        logger.debug("$SERVICE_NAME.$name.received: ")
        val result = response[Property.RESULT]
        if (result is JsonObject) {
          value = result[Property.VALUE]
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

  fun callServiceWithNoResponse(dtdProcess: DTDProcess, name: String) {
    callServiceWithJsonResponse(dtdProcess, name)
  }

  fun callServiceWithStringResponse(dtdProcess: DTDProcess, name: String): String? =
    callServiceWithJsonResponse(dtdProcess, name)?.asString

  fun callServiceWithBoolResponse(dtdProcess: DTDProcess, name: String): Boolean =
    callServiceWithJsonResponse(dtdProcess, name)?.asBoolean ?: false
}

class AnalyticsConfiguration {
  var shouldShowMessage: Boolean = false
    internal set
  var consentMessage: String? = null
    internal set
  var telemetryEnabled: Boolean = false
    internal set

  val suppressAnalytics: Boolean
    get() = shouldShowMessage || !telemetryEnabled
}

private object AnalyticsConfigurationManager {
  lateinit var data: AnalyticsConfiguration

  fun getConfiguration(sdk: DartSdk, project: Project, logger: Logger): AnalyticsConfiguration {
    logger.debug("Analytics.getConfiguration")

    if (::data.isInitialized) return data

    // TODO (pq): capture timing info and report (if analytics are enabled)

    data = AnalyticsConfiguration()

    // Return a default configuration (that suppresses analytics) when running tests.
    if (ApplicationManager.getApplication().isUnitTestMode) return data

    val dtdProcess = DTDProcess()
    dtdProcess.listener = object : DTDProcessListener {
      override fun onProcessStarted(uri: String?) {
        logger.debug("DartAnalysisServerService.onProcessStarted")

        val params = JsonObject()
        params.addProperty(UnifiedAnalytics.Property.TOOL, getToolName())

        try {
          data.shouldShowMessage =
            UnifiedAnalytics.callServiceWithBoolResponse(dtdProcess, UnifiedAnalytics.SHOULD_SHOW_MESSAGE)
          if (data.shouldShowMessage) {
            data.consentMessage =
              UnifiedAnalytics.callServiceWithStringResponse(dtdProcess, UnifiedAnalytics.GET_CONSENT_MESSAGE)
            data.telemetryEnabled = false // No need to ask
          } else {
            data.telemetryEnabled =
              UnifiedAnalytics.callServiceWithBoolResponse(dtdProcess, UnifiedAnalytics.TELEMETRY_ENABLED)
          }

          if (data.shouldShowMessage) {
            // Process termination happens after the prompt.
            scheduleConsentPromptNotification(project, dtdProcess)
          } else {
            dtdProcess.terminate()
          }
        } catch (t: Throwable) {
          logger.error(t)
          dtdProcess.terminate()
        }
      }
    }
    dtdProcess.start(sdk)

    // TODO (pq): fix race condition if we get here before the first countdown latch is started
    return data
  }

  private fun scheduleConsentPromptNotification(project: Project, dtdProcess: DTDProcess) {
    ApplicationManager.getApplication().invokeLater {
      NotificationGroupManager.getInstance()
        .getNotificationGroup(DAS_NOTIFICATION_GROUP)
        .createNotification(data.consentMessage!!, NotificationType.INFORMATION).also { notification ->
          notification.addAction(object : AnAction(CommonBundle.getOkButtonText()) {
            override fun actionPerformed(e: AnActionEvent) {
              try {
                notification.expire()
              } finally {
                UnifiedAnalytics.callServiceWithNoResponse(dtdProcess, UnifiedAnalytics.CLIENT_SHOWED_MESSAGE)
                dtdProcess.terminate()
              }
            }
          })
          notification.notify(project)
        }
    }
  }
}

object Analytics {
  private val logger: Logger =
    if (DEBUGGING_LOCALLY) PrintingLogger.SYSTEM_OUT else Logger.getInstance(Analytics::class.java)

  private val reporter: AnalyticsReporter
    get() = if (DEBUGGING_LOCALLY) PrintingReporter else AnalyticsReporter.forConfiguration(
      AnalyticsConfigurationManager.data
    )


  @JvmStatic
  fun getConfiguration(sdk: DartSdk, project: Project): AnalyticsConfiguration =
    AnalyticsConfigurationManager.getConfiguration(sdk, project, logger)

  @JvmStatic
  fun report(data: AnalyticsData) = data.reportTo(reporter)
}


class ActionData(id: String?, place: String, project: Project?) :
  AnalyticsData(AnalyticsConstants.ACTION_TYPE, id, project) {

  init {
    add(AnalyticsConstants.PLACE, place)
  }
}

class AssistData(id: String?, project: Project?) :
  AnalyticsData(AnalyticsConstants.ASSIST_TYPE, id, project)

class FixData(id: String?, project: Project?) :
  AnalyticsData(AnalyticsConstants.FIX_TYPE, id, project)

abstract class AnalyticsData(type: String, val id: String?, val project: Project? = null) {
  val data = mutableMapOf<String, Any>()

  init {
    id?.let { add(AnalyticsConstants.ID, it) }
    add(AnalyticsConstants.TYPE, type)
  }

  fun <T> add(key: DataValue<T>, value: T) = key.addTo(this, value)

  internal operator fun set(key: String, value: Boolean) {
    data[key] = value
  }

  internal operator fun set(key: String, value: Int) {
    data[key] = value
  }

  internal operator fun set(key: String, value: String) {
    data[key] = value
  }

  open fun reportTo(reporter: AnalyticsReporter) {
    // We only report if we have an id for the event.
    if (id == null) return
    reporter.process(this)
  }

  companion object {
    @JvmStatic
    fun forAssist(id: String?, project: Project?): AssistData = AssistData(id, project)

    @JvmStatic
    fun forFix(id: String?, project: Project?): FixData = FixData(id, project)

    @JvmStatic
    fun forAction(action: AnAction, event: AnActionEvent): ActionData = forAction(
      event.actionManager.getId(action), event.place, event.project
    )

    @JvmStatic
    fun forAction(id: String?, place: String, project: Project?): ActionData = ActionData(
      id, place, project
    )

    @JvmStatic
    fun forAction(id: String?, project: Project?): ActionData = forAction(
      id, ActionPlaces.UNKNOWN, project
    )
  }
}

object AnalyticsConstants {
  /**
   * The unique identifier for an action or event.
   */
  @JvmField
  val ID = StringValue("id")

  /**
   * The UI location where an action was invoked, as provided by
   * [com.intellij.ui.PlaceProvider.getPlace] (for example, "MainMenu",
   * "MainToolbar", "EditorPopup", "GoToAction", etc.).
   */
  @JvmField
  val PLACE = StringValue("place")

  /**
   * The type of the analytics event (e.g., "action", "assist", "fix", ...).
   */
  @JvmField
  val TYPE = StringValue("type")

  internal const val ACTION_TYPE = "action"
  internal const val ASSIST_TYPE = "assist"
  internal const val FIX_TYPE = "fix"
}

sealed class DataValue<T>(val name: String) {
  abstract fun addTo(data: AnalyticsData, value: T)
}

class StringValue(name: String) : DataValue<String>(name) {
  override fun addTo(data: AnalyticsData, value: String) {
    data[name] = value
  }
}

class IntValue(name: String) : DataValue<Int>(name) {
  override fun addTo(data: AnalyticsData, value: Int) {
    data[name] = value
  }
}

class BooleanValue(name: String) : DataValue<Boolean>(name) {
  override fun addTo(data: AnalyticsData, value: Boolean) {
    data[name] = value
  }
}

abstract class AnalyticsReporter {
  internal abstract fun process(data: AnalyticsData)

  companion object {
    fun forConfiguration(config: AnalyticsConfiguration?): AnalyticsReporter = config?.let { c ->
      if (c.suppressAnalytics || !c.telemetryEnabled) {
        NoOpReporter
      } else {
        UnifiedAnalyticsReporter
      }
    } ?: NoOpReporter
  }
}

internal object PrintingReporter : AnalyticsReporter() {
  override fun process(data: AnalyticsData) = println(data.data)
}

internal object NoOpReporter : AnalyticsReporter() {
  override fun process(data: AnalyticsData) = Unit
}

internal object UnifiedAnalyticsReporter : AnalyticsReporter() {
  const val IDE_EVENT = "ide_event"

  override fun process(data: AnalyticsData) {
    val project = data.project ?: return

    val params = JsonObject()
    params.addProperty(UnifiedAnalytics.Property.TOOL, getToolName())

    val event = JsonObject()
    event.addProperty(UnifiedAnalytics.Property.EVENT_NAME, IDE_EVENT)

    val evenData = JsonObject()
    for (entry in data.data) {
      when (val value = entry.value) {
        is String -> evenData.addProperty(entry.key, value)
        is Boolean -> evenData.addProperty(entry.key, value)
        is Int -> evenData.addProperty(entry.key, value)
        else -> {
          // TODO (pq): consider logging
        }
      }
    }
    event.add(UnifiedAnalytics.Property.EVENT_DATA, evenData)

    // Note: encoded as a string.
    params.addProperty(UnifiedAnalytics.Property.EVENT, event.toString())

    // TODO (pq): temporary
    // print(params.toString())

    DartToolingDaemonService.getInstance(project)
      .sendRequest("${UnifiedAnalytics.SERVICE_NAME}.${UnifiedAnalytics.SEND}", params, true) { response: JsonObject ->
        // TODO (pq): temporary
        // print(response)
      }
  }
}

private fun getToolName(): String = when (ApplicationInfo.getInstance().build.productCode) {
  "AI" -> "android-studio-plugins"
  else -> "intellij-plugins"
}
