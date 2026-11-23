/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.ide.toolingDaemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil
import com.jetbrains.lang.dart.util.DartTestUtils
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicInteger

/** [DartToolingDaemonService.startServiceIfNeeded] starts one DTD once Dart support is usable, however late. */
class DartToolingDaemonStartTest : BasePlatformTestCase() {
  private val starts = AtomicInteger()
  private lateinit var service: DartToolingDaemonService

  override fun setUp() {
    super.setUp()
    service = DartToolingDaemonService.getInstance(project)
    service.stopService() // The light project, and so this service, is shared with other tests.
  }

  override fun tearDown() {
    try {
      service.startForTests = null
      service.stopService()
    }
    finally {
      super.tearDown()
    }
  }

  fun testDoesNothingWithoutDartSupport() {
    assertNull(DartSdk.getDartSdk(project))
    service.startForTests = { starts.incrementAndGet(); true }

    join(service.startServiceIfNeeded())

    assertEquals(0, starts.get())
  }

  fun testStartsWhenDartSupportIsEnabledAfterOpen() {
    service.startForTests = { starts.incrementAndGet(); true }

    // Fires DartWorkspaceModelChangeListener, as when the Flutter plugin configures the SDK after the project opens.
    DartTestUtils.configureDartSdk(module, testRootDisposable, false)

    PlatformTestUtil.waitWithEventsDispatching("The DTD wasn't started", { starts.get() > 0 }, 10)
    assertEquals(1, starts.get())
  }

  fun testStartsWhenTheDartSdkLibraryAppearsAfterTheDependency() {
    service.startForTests = { starts.incrementAndGet(); true }
    VfsRootAccess.allowRootAccess(testRootDisposable, DartTestUtils.BASE_TEST_DATA_PATH)

    // As in the Flutter project wizard: the module depends on "Dart SDK" before the library exists, so adding the
    // library changes no module.
    ApplicationManager.getApplication().runWriteAction {
      Disposer.register(testRootDisposable, DartSdkLibUtil.enableDartSdkAndReturnUndoingDisposable(module))
    }
    assertNull(DartSdk.getDartSdk(project))
    ApplicationManager.getApplication().runWriteAction {
      Disposer.register(testRootDisposable,
                        DartSdkLibUtil.configureDartSdkAndReturnUndoingDisposable(project, DartTestUtils.SDK_HOME_PATH))
    }

    PlatformTestUtil.waitWithEventsDispatching("The DTD wasn't started", { starts.get() > 0 }, 10)
    assertEquals(1, starts.get())
  }

  fun testStartsAtMostOnce() {
    DartTestUtils.configureDartSdk(module, testRootDisposable, false)
    service.startForTests = { starts.incrementAndGet(); true }

    join(*Array(5) { service.startServiceIfNeeded() })
    join(service.startServiceIfNeeded())

    assertEquals(1, starts.get())
  }

  fun testRetriesAfterAFailedStart() {
    DartTestUtils.configureDartSdk(module, testRootDisposable, false)
    service.startForTests = { starts.incrementAndGet() > 1 } // The first start fails.

    join(service.startServiceIfNeeded())
    join(service.startServiceIfNeeded())
    join(service.startServiceIfNeeded())

    assertEquals(2, starts.get())
  }

  fun testDoesNothingInTestsWithoutAHook() {
    DartTestUtils.configureDartSdk(module, testRootDisposable, false)

    assertNull(service.startServiceIfNeeded())
  }

  /** Waits for the start jobs, dispatching EDT events (blocking the EDT could deadlock with their read actions). */
  private fun join(vararg jobs: Job?) {
    val active = jobs.filterNotNull()
    PlatformTestUtil.waitWithEventsDispatching("The DTD start jobs didn't finish", { active.all(Job::isCompleted) }, 10)
  }
}
