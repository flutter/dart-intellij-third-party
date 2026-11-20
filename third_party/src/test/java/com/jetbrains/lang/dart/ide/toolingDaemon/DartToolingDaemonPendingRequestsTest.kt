/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.ide.toolingDaemon

import com.google.gson.JsonObject
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The request queue of a DTD that isn't connected (never started, or terminated) is bounded. */
class DartToolingDaemonPendingRequestsTest : BasePlatformTestCase() {

  override fun tearDown() {
    try {
      // The light project, and so this service, is shared with other tests: clear the queue.
      DartToolingDaemonService.getInstance(project).stopService()
    }
    finally {
      super.tearDown()
    }
  }

  fun testQueueIsBoundedWhileSocketIsNotReady() {
    val service = DartToolingDaemonService.getInstance(project)
    assertFalse(service.webSocketReady)
    service.stopService() // start from an empty queue

    repeat(DartToolingDaemonService.MAX_PENDING_REQUESTS + 5) {
      service.sendRequest("UnifiedAnalytics.send", JsonObject(), true) {}
    }

    assertEquals(DartToolingDaemonService.MAX_PENDING_REQUESTS, service.pendingRequestCount)

    // Service registrations are never dropped.
    service.sendRequest("registerService", JsonObject(), false) {}
    assertEquals(DartToolingDaemonService.MAX_PENDING_REQUESTS + 1, service.pendingRequestCount)
  }
}
