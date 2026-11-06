// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.dart.analysisServer;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsConstants;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.analytics.AssistData;
import com.jetbrains.lang.dart.analytics.FixData;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.assists.DartQuickAssistSet;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.SourceChange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy (non-LSP) quick assists and quick fixes report analytics only when the user applies
 * one, not when the platform checks their availability (on caret moves, edits, and selection
 * changes).
 */
public class DartLegacyAssistFixAnalyticsTest extends CodeInsightFixtureTestCase {
  private static final String ASSIST_SOURCE = """
    void f() {
      <selection><caret>var x = 3;</selection>
    }
    """;
  private static final String ASSIST_TEXT = "Surround with block";

  private static final String FIX_SOURCE = "<caret>ServerSocket f;\nclass ServerSockets{}";
  private static final String FIX_TEXT = "Change to 'ServerSockets'";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    DartAnalysisServerService.getInstance(getProject()).serverReadyForRequest();
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
    // These tests are only meaningful on the legacy path; LSP code actions bypass the legacy classes.
    assertFalse(DartConfigurable.isLspCodeActionsEnabled(getProject()));
  }

  public void testAssistAvailabilityCheckIsNotReported() {
    myFixture.configureByText("foo.dart", ASSIST_SOURCE);

    // Call DartQuickAssistSet directly (it backs DartQuickAssistIntention.isAvailable) so the request
    // runs on this thread: the platform computes available intentions off-thread, where the
    // thread-local report observer can't see it.
    final DartQuickAssistSet assistSet = ApplicationManager.getApplication().getService(DartQuickAssistSet.class);
    final List<AnalyticsData> events = record(() -> {
      final List<SourceChange> assists = assistSet.getQuickAssists(getEditor(), getFile());
      // Ensure the server actually returned assists (not an empty or timed-out response).
      assertContainsElements(ContainerUtil.map(assists, SourceChange::getMessage), ASSIST_TEXT);
    });

    assertEmpty(events);
  }

  public void testAppliedAssistIsReportedOnce() {
    myFixture.configureByText("foo.dart", ASSIST_SOURCE);
    final IntentionAction assist = myFixture.findSingleIntention(ASSIST_TEXT);

    final List<AnalyticsData> events = record(() -> myFixture.launchAction(assist));

    final AnalyticsData event = assertOneElement(events);
    assertInstanceOf(event, AssistData.class);
    assertEquals("assist", event.getData().get(AnalyticsConstants.TYPE.getName()));
    assertNotNull(event.getId());
    assertFalse(event.getData().containsKey(AnalyticsConstants.DURATION_MS.getName()));
  }

  // Note: there is no fix counterpart to testAssistAvailabilityCheckIsNotReported. Fixes are
  // fetched asynchronously and the (now removed) per-request event was reported on the analysis
  // server's response thread, which the thread-local report observer can't see.

  public void testAppliedFixIsReportedOnce() {
    myFixture.configureByText("foo.dart", FIX_SOURCE);
    final IntentionAction fix = myFixture.findSingleIntention(FIX_TEXT);

    final List<AnalyticsData> events = record(() -> myFixture.launchAction(fix));

    final AnalyticsData event = assertOneElement(events);
    assertInstanceOf(event, FixData.class);
    assertEquals("fix", event.getData().get(AnalyticsConstants.TYPE.getName()));
    assertNotNull(event.getId());
    assertFalse(event.getData().containsKey(AnalyticsConstants.DURATION_MS.getName()));
  }

  private static @NotNull List<AnalyticsData> record(@NotNull Runnable action) {
    final List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add, action);
    return events;
  }
}
