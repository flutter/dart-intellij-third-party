// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.analytics

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.SettingsReportInfo
import com.jetbrains.lang.dart.buildSettingsData

class AnalyticsAttributionTest : BasePlatformTestCase() {

  fun testToolForAndroidStudio() {
    assertEquals("android-studio-plugins", toolNameForProductCode("AI"))
  }

  fun testToolForOtherProducts() {
    for (code in listOf("IC", "IU", "WS", "PS", "GO", "")) {
      assertEquals(code, "intellij-plugins", toolNameForProductCode(code))
    }
  }

  fun testToolIsAKnownLabel() {
    // DashTool.fromLabel throws on unknown labels, so only these two may ever be reported.
    assertTrue(Analytics.tool, Analytics.tool in setOf("intellij-plugins", "android-studio-plugins"))
  }

  fun testPluginVersionForDartPlugin() {
    assertFalse(Analytics.pluginVersion(AnalyticsConstants.DART_PLUGIN_ID).isNullOrEmpty())
  }

  fun testPluginVersionForMissingPlugin() {
    assertNull(Analytics.pluginVersion("com.example.not.installed"))
  }

  fun testSettingsDataWithFlutter() {
    val data = buildSettingsData(project, settingsReportInfo(), productCode = "AI", pluginVersion = versions(
      AnalyticsConstants.DART_PLUGIN_ID to "512.0.0",
      AnalyticsConstants.FLUTTER_PLUGIN_ID to "90.1.0",
    )).data

    assertEquals(
      mapOf(
        "type" to "settings",
        "id" to "settings",
        "experimentalLspFeaturesEnabled" to false,
        "sdkVersion" to "3.14.0",
        "productCode" to "AI",
        "dartPluginVersion" to "512.0.0",
        "flutterPluginVersion" to "90.1.0",
      ),
      data,
    )
  }

  fun testSettingsDataWithoutFlutter() {
    val data = buildSettingsData(project, settingsReportInfo(), productCode = "IU", pluginVersion = versions(
      AnalyticsConstants.DART_PLUGIN_ID to "512.0.0",
    )).data

    assertEquals(
      mapOf(
        "type" to "settings",
        "id" to "settings",
        "experimentalLspFeaturesEnabled" to false,
        "sdkVersion" to "3.14.0",
        "productCode" to "IU",
        "dartPluginVersion" to "512.0.0",
      ),
      data,
    )
  }

  fun testSettingsDataDefaultsToLiveValues() {
    val data = buildSettingsData(project, settingsReportInfo()).data

    assertEquals(Analytics.productCode, data["productCode"])
    val dartVersion = Analytics.pluginVersion(AnalyticsConstants.DART_PLUGIN_ID)
    assertNotNull(dartVersion)
    assertEquals(dartVersion, data["dartPluginVersion"])
  }

  private fun versions(vararg entries: Pair<String, String>): (String) -> String? = mapOf(*entries)::get

  private fun settingsReportInfo() = SettingsReportInfo(
    sdk = null,
    dartSupportEnabled = true,
    sdkVersion = "3.14.0",
    experimentalLspFeaturesEnabled = false,
  )
}
