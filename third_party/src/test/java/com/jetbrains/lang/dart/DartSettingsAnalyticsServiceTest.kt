/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil
import com.jetbrains.lang.dart.util.DartTestUtils
import kotlinx.coroutines.Job
import java.util.concurrent.CopyOnWriteArrayList

/** [DartSettingsAnalyticsService] reports the settings event once per session, as soon as Dart support is usable. */
class DartSettingsAnalyticsServiceTest : BasePlatformTestCase() {
  private val reports = CopyOnWriteArrayList<SettingsReportInfo>()
  private lateinit var service: DartSettingsAnalyticsService

  override fun setUp() {
    super.setUp()
    service = DartSettingsAnalyticsService.getInstance(project)
    service.resetForTests() // The light project, and so this service, is shared with other tests.
  }

  override fun tearDown() {
    try {
      service.resetForTests()
    }
    finally {
      super.tearDown()
    }
  }

  fun testDoesNothingWithoutDartSupport() {
    assertNull(DartSdk.getDartSdk(project))
    service.reportForTests = { reports.add(it) }

    join(service.reportIfNeeded())

    assertEmpty(reports)
  }

  fun testACallWithoutDartSupportDoesNotUseUpTheReport() {
    service.reportForTests = { reports.add(it) }
    join(service.reportIfNeeded())

    DartTestUtils.configureDartSdk(module, testRootDisposable, false)
    PlatformTestUtil.waitWithEventsDispatching("The settings event wasn't reported", { reports.isNotEmpty() }, 10)
    join(service.reportIfNeeded())

    assertSize(1, reports)
  }

  fun testReportsWhenDartSupportIsEnabledAfterOpen() {
    service.reportForTests = { reports.add(it) }

    // Fires DartWorkspaceModelChangeListener, as when Dart support is enabled in Settings after the project opens.
    DartTestUtils.configureDartSdk(module, testRootDisposable, false)

    PlatformTestUtil.waitWithEventsDispatching("The settings event wasn't reported", { reports.isNotEmpty() }, 10)
    val info = reports.single()
    assertTrue(info.dartSupportEnabled)
    assertEquals(DartSdk.getDartSdk(project)?.version, info.sdkVersion)
  }

  fun testReportsWhenTheDartSdkLibraryAppearsAfterTheDependency() {
    service.reportForTests = { reports.add(it) }
    VfsRootAccess.allowRootAccess(testRootDisposable, DartTestUtils.BASE_TEST_DATA_PATH)

    // As in the Flutter project wizard: the module depends on "Dart SDK" before the library exists.
    ApplicationManager.getApplication().runWriteAction {
      Disposer.register(testRootDisposable, DartSdkLibUtil.enableDartSdkAndReturnUndoingDisposable(module))
    }
    ApplicationManager.getApplication().runWriteAction {
      Disposer.register(testRootDisposable,
                        DartSdkLibUtil.configureDartSdkAndReturnUndoingDisposable(project, DartTestUtils.SDK_HOME_PATH))
    }

    PlatformTestUtil.waitWithEventsDispatching("The settings event wasn't reported", { reports.isNotEmpty() }, 10)
    assertSize(1, reports)
  }

  fun testReportsAtMostOnce() {
    DartTestUtils.configureDartSdk(module, testRootDisposable, false)
    service.reportForTests = { reports.add(it) }

    join(*Array(5) { service.reportIfNeeded() })
    join(service.reportIfNeeded())

    assertSize(1, reports)
  }

  fun testDoesNothingInTestsWithoutAHook() {
    DartTestUtils.configureDartSdk(module, testRootDisposable, false)

    assertNull(service.reportIfNeeded())
  }

  /** Waits for the report jobs, dispatching EDT events (blocking the EDT could deadlock with their read actions). */
  private fun join(vararg jobs: Job?) {
    val active = jobs.filterNotNull()
    PlatformTestUtil.waitWithEventsDispatching("The report jobs didn't finish", { active.all(Job::isCompleted) }, 10)
  }
}
