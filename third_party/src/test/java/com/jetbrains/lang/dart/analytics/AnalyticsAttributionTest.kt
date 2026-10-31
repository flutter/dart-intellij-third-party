// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.analytics

import com.intellij.openapi.application.ApplicationInfo
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

  fun testToolMatchesRunningIde() {
    assertEquals(toolNameForProductCode(ApplicationInfo.getInstance().build.productCode), Analytics.tool)
  }

  fun testProductCodeIsRaw() {
    assertEquals(ApplicationInfo.getInstance().build.productCode, Analytics.productCode)
  }

  fun testPluginVersionForDartPlugin() {
    assertFalse(Analytics.pluginVersion(DART_PLUGIN_ID).isNullOrEmpty())
  }

  fun testPluginVersionForMissingPlugin() {
    assertNull(Analytics.pluginVersion("com.example.not.installed"))
  }

  fun testSettingsDataCarriesAttribution() {
    val data = buildSettingsData(project, settingsReportInfo()).data

    assertEquals("settings", data["type"])
    assertEquals("settings", data["id"])
    assertEquals(false, data["experimentalLspFeaturesEnabled"])
    assertEquals("3.14.0", data["sdkVersion"])
    assertEquals(Analytics.productCode, data["productCode"])
    assertEquals(Analytics.pluginVersion(DART_PLUGIN_ID), data["dartPluginVersion"])
  }

  fun testSettingsDataOmitsFlutterPluginVersionWithoutFlutter() {
    // The Flutter plugin isn't part of the Dart plugin's test sandbox.
    assertNull(Analytics.pluginVersion(FLUTTER_PLUGIN_ID))

    val data = buildSettingsData(project, settingsReportInfo()).data

    assertFalse(data.containsKey("flutterPluginVersion"))
  }

  private fun settingsReportInfo() = SettingsReportInfo(
    sdk = null,
    dartSupportEnabled = true,
    sdkVersion = "3.14.0",
    experimentalLspFeaturesEnabled = false,
  )
}
