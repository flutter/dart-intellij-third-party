// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.dart.analysisServer;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;

import java.awt.event.MouseEvent;
import java.util.Map;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.util.DartTestUtils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class DartServerOverrideMarkerProviderTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    myFixture.setTestDataPath(DartTestUtils.BASE_TEST_DATA_PATH + getBasePath());
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
  }

  @Override
  protected String getBasePath() {
    return "/analysisServer/overrideMarker";
  }

  private void doTest(final String expectedText, final Icon expectedIcon) {
    final String testName = getTestName(false);
    myFixture.configureByFile(testName + ".dart");

    List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add, () -> {
      myFixture.doHighlighting(); // make sure server is warmed up
      checkGutter(myFixture.findGuttersAtCaret(), expectedText, expectedIcon);
    });
    assertTrue("Marker construction must not report", events.isEmpty());
  }

  public static void checkGutter(final List<GutterMark> gutters, final String expectedText, final Icon expectedIcon) {
    final List<String> textList = new ArrayList<>();
    for (GutterMark gutter : gutters) {
      final String text = gutter.getTooltipText();
      textList.add(text);
      if (expectedText.equals(text) && expectedIcon.equals(gutter.getIcon())) {
        checkClickAnalytics((GutterIconRenderer)gutter, expectedText);
        return;
      }
    }
    fail("Not found gutter mark: " + expectedText + "  " + expectedIcon + "\nin\n" + StringUtil.join(textList, "\n"));
  }

  private static void checkClickAnalytics(GutterIconRenderer gutter, String text) {
    String id = text.startsWith("Implements") ? "dart.gutter.implementMethod" :
                text.startsWith("Overrides") ? "dart.gutter.overrideMethod" :
                text.equals("Has subclasses") ? "dart.gutter.subclasses" : "dart.gutter.overridingMethods";
    List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add, () -> {
      assertEquals(text, gutter.getTooltipText());
      assertEquals(text, gutter.getAccessibleName());
      assertTrue(events.isEmpty());
      AnAction action = gutter.getClickAction();
      assertNotNull(action);
      MouseEvent click = new MouseEvent(new JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false);
      AnActionEvent event = AnActionEvent.createEvent(action, DataContext.EMPTY_CONTEXT, null, "GutterIcon", ActionUiKind.NONE, click);
      ApplicationManager.getApplication().getMessageBus().syncPublisher(AnActionListener.TOPIC).beforeActionPerformed(action, event);
      action.actionPerformed(event);
      assertEquals(1, events.size());
      assertEquals(Map.of("id", id, "type", "action", "place", "GutterIcon"), events.getFirst().getData());
    });
  }

  public void testImplementMarker() {
    doTest("Implements method 'm' in 'A'", AllIcons.Gutter.ImplementingMethod);
  }

  public void testOverrideMarker() {
    doTest("Overrides method 'm' in 'A'", AllIcons.Gutter.OverridingMethod);
    assertEquals(myFixture.getFile().getText().indexOf("m()"),
                 FileEditorManager.getInstance(getProject()).getSelectedTextEditor().getCaretModel().getOffset());
  }

  public void testMixedOverrideAndImplementationMarker() {
    myFixture.configureByText("mixed.dart", "class A { m() {} }\nclass I { m() {} }\nclass B extends A implements I { <caret>m() {} }");
    myFixture.doHighlighting();
    checkGutter(myFixture.findGuttersAtCaret(), "Overrides method 'm' in 'A'", AllIcons.Gutter.OverridingMethod);
  }

  public void testOverriddenOperator() {
    doTest("Overrides operator '==' in 'Object'", AllIcons.Gutter.OverridingMethod);
  }
}
