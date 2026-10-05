// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.analytics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DartNavigationAnalyticsTest : BasePlatformTestCase() {
  private val ids = listOf("GotoSuperMethod", "GotoImplementation", "TypeHierarchy", "MethodHierarchy")

  fun testDartActionsPreserveIdsAndPlacesBeforeNavigation() {
    myFixture.configureByText("source.dart", "class Source {}")
    for ((index, id) in ids.withIndex()) {
      val place = listOf("MainMenu", "EditorPopup", "KeyboardShortcut", "GoToAction")[index]
      val events = record(id, context(), place)
      assertEquals(listOf(mapOf("id" to id, "type" to "action", "place" to place)), events.map { it.data })
      assertSame(project, events.single().project)
    }
  }

  fun testGutterPayloadSerialization() {
    val expectedIds = mapOf(
      DartNavigationAnalytics.OVERRIDE_METHOD to "dart.gutter.overrideMethod",
      DartNavigationAnalytics.IMPLEMENT_METHOD to "dart.gutter.implementMethod",
      DartNavigationAnalytics.SUBCLASSES to "dart.gutter.subclasses",
      DartNavigationAnalytics.OVERRIDING_METHODS to "dart.gutter.overridingMethods"
    )
    for ((category, id) in expectedIds) {
      val events = mutableListOf<AnalyticsData>()
      Analytics.withReportObserver(events::add) { category.report(project) }
      assertNavigationPayload(events.single(), id, "GutterIcon")
    }
  }

  fun testIdeActionPayloadSerialization() {
    myFixture.configureByText("source.dart", "class Source {}")
    val places = listOf("MainMenu", "EditorPopup", "KeyboardShortcut", "GoToAction")
    for ((index, id) in ids.withIndex()) {
      assertNavigationPayload(record(id, context(), places[index]).single(), id, places[index])
    }
  }

  fun testSerializerPreservesSupportedValuesAndOmitsUnsupportedValues() {
    val params = UnifiedAnalyticsReporter.createEventParams(mapOf(
      "text" to "fixed \"quoted\"\nvalue",
      "enabled" to true,
      "disabled" to false,
      "count" to 42,
      "negative" to -1,
      "unsupported" to 1L
    ))
    val data = parseEventData(params)
    assertEquals(setOf("text", "enabled", "disabled", "count", "negative"), data.keySet())
    assertTrue(data["text"].asJsonPrimitive.isString)
    assertEquals("fixed \"quoted\"\nvalue", data["text"].asString)
    assertTrue(data["enabled"].asJsonPrimitive.isBoolean)
    assertTrue(data["enabled"].asBoolean)
    assertTrue(data["disabled"].asJsonPrimitive.isBoolean)
    assertFalse(data["disabled"].asBoolean)
    assertTrue(data["count"].asJsonPrimitive.isNumber)
    assertEquals(42, data["count"].asInt)
    assertTrue(data["negative"].asJsonPrimitive.isNumber)
    assertEquals(-1, data["negative"].asInt)
    assertEquals(0, parseEventData(UnifiedAnalyticsReporter.createEventParams(emptyMap())).size())
  }

  private fun assertNavigationPayload(event: AnalyticsData, id: String, place: String) {
    val params = UnifiedAnalyticsReporter.createEventParams(event.data)
    val data = parseEventData(params)
    // Exact keys and fixed values exclude secrets and source data at this pre-send boundary.
    assertEquals(setOf("type", "id", "place"), data.keySet())
    assertEquals("action", data["type"].asString)
    assertEquals(id, data["id"].asString)
    assertEquals(place, data["place"].asString)
  }

  private fun parseEventData(params: JsonObject): JsonObject {
    assertEquals(setOf("tool", "event"), params.keySet())
    val expectedTool = if (ApplicationInfo.getInstance().build.productCode == "AI") {
      "android-studio-plugins"
    } else {
      "intellij-plugins"
    }
    assertEquals(expectedTool, params["tool"].asString)
    assertTrue("DTD expects event encoded as a JSON string", params["event"].asJsonPrimitive.isString)
    val event = JsonParser.parseString(params["event"].asString).asJsonObject
    assertEquals(setOf("eventName", "eventData"), event.keySet())
    assertEquals("ide_event", event["eventName"].asString)
    return event["eventData"].asJsonObject
  }

  fun testEditorOnlyDartContext() {
    myFixture.configureByText("source.dart", "class Source {}")
    val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.EDITOR, myFixture.editor).build()
    assertEquals(1, record(ids.first(), context).size)
  }

  fun testPsiElementOnlyDartContext() {
    myFixture.configureByText("source.dart", "class Source {}")
    val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.PSI_ELEMENT, myFixture.file.firstChild).build()
    assertEquals(1, record(ids.first(), context).size)
  }

  fun testNonDartAndMissingContextsAreIgnored() {
    myFixture.configureByText("source.txt", "not Dart")
    for (id in ids) {
      assertEmpty(record(id, context()))
      assertEmpty(record(id, SimpleDataContext.getProjectContext(project)))
      assertEmpty(record(id, DataContext.EMPTY_CONTEXT))
    }
  }

  fun testConflictingPsiAndEditorContextsAreIgnored() {
    val dart = myFixture.configureByText("source.dart", "class Source {}")
    myFixture.configureByText("source.txt", "not Dart")
    val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.PSI_FILE, dart).add(CommonDataKeys.EDITOR, myFixture.editor).build()
    assertEmpty(record(ids.first(), context))
  }

  fun testAmbiguousSelectionsAndDifferentDartFilesAreIgnored() {
    val first = myFixture.configureByText("first.dart", "class First {}")
    val second = myFixture.configureByText("second.dart", "class Second {}")
    val selection = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
      .add(PlatformDataKeys.PSI_ELEMENT_ARRAY, arrayOf(first, second)).build()
    assertEmpty(record(ids.first(), selection))
    val conflict = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.PSI_ELEMENT, first).add(CommonDataKeys.PSI_FILE, second).build()
    assertEmpty(record(ids.first(), conflict))
    val noProject = SimpleDataContext.builder().add(CommonDataKeys.PSI_FILE, first).build()
    assertEmpty(record(ids.first(), noProject))
  }

  fun testAfterNavigationDoesNotReportAgain() {
    myFixture.configureByText("source.dart", "class Source {}")
    val action = ActionManager.getInstance().getAction(ids.first())
    val event = AnActionEvent.createEvent(action, context(), null, "MainMenu", ActionUiKind.NONE, null)
    val events = mutableListOf<AnalyticsData>()
    Analytics.withReportObserver(events::add) {
      listener().beforeActionPerformed(action, event)
      assertEquals(1, events.size)
      myFixture.configureByText("destination.txt", "not Dart")
      listener().afterActionPerformed(action, event.withDataContext(context()), AnActionResult.PERFORMED)
      assertEquals(1, events.size)
    }
  }

  fun testConsentReporterSelectionRemainsSuppressiveByDefault() {
    assertSame(NoOpReporter, AnalyticsReporter.forConfiguration(null))
    val config = AnalyticsConfiguration()
    assertSame(NoOpReporter, AnalyticsReporter.forConfiguration(config))
    config.telemetryEnabled = true
    config.shouldShowMessage = true
    assertSame(NoOpReporter, AnalyticsReporter.forConfiguration(config))
    config.shouldShowMessage = false
    assertSame(UnifiedAnalyticsReporter, AnalyticsReporter.forConfiguration(config))
  }

  fun testUnrelatedAndUnregisteredActionsAreIgnored() {
    myFixture.configureByText("source.dart", "class Source {}")
    assertEmpty(record("GotoDeclaration", context()))
    val events = mutableListOf<AnalyticsData>()
    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }
    Analytics.withReportObserver(events::add) {
      listener().beforeActionPerformed(action, AnActionEvent.createEvent(action, context(), null, "GutterIcon", ActionUiKind.NONE, null))
    }
    assertEmpty(events)
  }

  private fun listener(): AnActionListener = ApplicationManager.getApplication().messageBus.syncPublisher(AnActionListener.TOPIC)

  private fun context(): DataContext = SimpleDataContext.builder()
    .add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.PSI_FILE, myFixture.file)
    .add(CommonDataKeys.EDITOR, myFixture.editor).build()

  private fun record(id: String, context: DataContext, place: String = "EditorPopup"): List<AnalyticsData> {
    val action = ActionManager.getInstance().getAction(id)
    assertNotNull("SDK action must be registered: $id", action)
    val events = mutableListOf<AnalyticsData>()
    Analytics.withReportObserver(events::add) {
      listener().beforeActionPerformed(action, AnActionEvent.createEvent(action, context, null, place, ActionUiKind.NONE, null))
    }
    return events
  }
}
