/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.analytics

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.CopyOnWriteArrayList

/** Silently dropped events and fields are recorded with a reason. */
class AnalyticsDropTest : BasePlatformTestCase() {
  private val drops = CopyOnWriteArrayList<String>()

  override fun setUp() {
    super.setUp()
    Analytics.setDropObserverForTests({ drops.add(it) }, testRootDisposable)
  }

  fun testEventWithoutIdIsDropped() {
    Analytics.report(AnalyticsData.forAction(null, "place", project))

    assertEquals(listOf(Analytics.DropReason.NO_ID), drops)
  }

  fun testEventWithoutProjectIsDropped() {
    UnifiedAnalyticsReporter.process(AnalyticsData.forAction("id", "place", null))

    assertEquals(listOf(Analytics.DropReason.NO_PROJECT), drops)
  }

  fun testEventWithDefaultProjectIsDropped() {
    UnifiedAnalyticsReporter.process(AnalyticsData.forAction("id", "place", ProjectManager.getInstance().defaultProject))

    assertEquals(listOf(Analytics.DropReason.NO_PROJECT), drops)
  }

  fun testUnsupportedValueIsDropped() {
    val params = UnifiedAnalyticsReporter.createEventParams(mapOf("count" to 1L, "id" to "x"))

    assertEquals(listOf(Analytics.DropReason.UNSUPPORTED_VALUE), drops)
    assertFalse(params.toString().contains("count"))
  }
}
