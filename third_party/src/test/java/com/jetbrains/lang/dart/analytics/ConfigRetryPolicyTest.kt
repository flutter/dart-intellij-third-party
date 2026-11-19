// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.analytics

import junit.framework.TestCase

class ConfigRetryPolicyTest : TestCase() {

  fun testNoRetryBeforeAnyFailure() {
    assertFalse(ConfigRetryPolicy(longArrayOf(10, 20)).tryClaimRetry(1_000))
  }

  fun testRetriesAfterEachBackoffThenGivesUp() {
    val policy = ConfigRetryPolicy(longArrayOf(10, 20))

    assertEquals(10L, policy.onFailure(100))
    assertFalse("not before the backoff", policy.tryClaimRetry(109))
    assertTrue(policy.tryClaimRetry(110))
    assertFalse("claimed only once", policy.tryClaimRetry(110))

    assertEquals(20L, policy.onFailure(200))
    assertFalse(policy.tryClaimRetry(219))
    assertTrue(policy.tryClaimRetry(220))

    assertNull("no retries left", policy.onFailure(300))
    assertFalse(policy.tryClaimRetry(Long.MAX_VALUE - 1))
  }

  fun testSuccessResetsTheBackoff() {
    val policy = ConfigRetryPolicy(longArrayOf(10, 20))
    policy.onFailure(100)
    assertTrue(policy.tryClaimRetry(110))
    policy.onSuccess()

    assertFalse(policy.tryClaimRetry(1_000))
    assertEquals("starts again from the first backoff", 10L, policy.onFailure(1_000))
  }
}
