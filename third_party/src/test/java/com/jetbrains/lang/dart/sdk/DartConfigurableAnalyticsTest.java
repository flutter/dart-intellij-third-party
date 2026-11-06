// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.sdk;

import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.analytics.SettingsChangeData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DartConfigurableAnalyticsTest extends DartCodeInsightFixtureTestCase {

  public void testOptOutIsReported() {
    final AnalyticsData event = assertSingleEvent(record(true, true, false));
    assertEquals(Boolean.FALSE, event.getData().get("enabled"));
  }

  public void testOptInIsReported() {
    final AnalyticsData event = assertSingleEvent(record(true, false, true));
    assertEquals(Boolean.TRUE, event.getData().get("enabled"));
  }

  public void testUnchangedValueIsNotReported() {
    assertEmpty(record(true, true, true));
    assertEmpty(record(true, false, false));
  }

  public void testNothingReportedWhenDartSupportDisabled() {
    assertEmpty(record(false, true, false));
    assertEmpty(record(false, false, true));
  }

  private List<AnalyticsData> record(boolean dartSupportEnabled, boolean previouslyEnabled, boolean enabled) {
    final List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add, () -> DartConfigurable.reportExperimentalLspFeaturesChange(
      getProject(), dartSupportEnabled, previouslyEnabled, enabled));
    return events;
  }

  private AnalyticsData assertSingleEvent(List<AnalyticsData> events) {
    final AnalyticsData event = assertOneElement(events);
    assertInstanceOf(event, SettingsChangeData.class);
    assertEquals(DartConfigurable.EXPERIMENTAL_LSP_FEATURES_SETTING_ID, event.getId());
    assertSame(getProject(), event.getProject());

    final Map<String, Object> data = event.getData();
    assertEquals("settings_change", data.get("type"));
    assertEquals(DartConfigurable.EXPERIMENTAL_LSP_FEATURES_SETTING_ID, data.get("id"));

    final DartSdk sdk = DartSdk.getDartSdk(getProject());
    assertNotNull(sdk);
    assertEquals(sdk.getVersion(), data.get("sdkVersion"));
    return event;
  }
}
