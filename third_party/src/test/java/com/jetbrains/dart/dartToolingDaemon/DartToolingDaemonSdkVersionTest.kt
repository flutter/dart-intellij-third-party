/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.dartToolingDaemon

import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService
import junit.framework.TestCase

/**
 * SDKs older than 3.4 don't ship `dart tooling-daemon`, so DTD (and DTD-backed analytics) must not be started for them.
 * See https://github.com/flutter/dart-intellij-third-party/issues/704.
 */
class DartToolingDaemonSdkVersionTest : TestCase() {

  fun testSupportedVersions() {
    assertTrue(DartToolingDaemonService.isDartSdkVersionSufficient("3.4.0"))
    assertTrue(DartToolingDaemonService.isDartSdkVersionSufficient("3.4.0-1.0.dev"))
    assertTrue(DartToolingDaemonService.isDartSdkVersionSufficient("3.10.1"))
    assertTrue(DartToolingDaemonService.isDartSdkVersionSufficient("4.0.0"))
  }

  fun testUnsupportedVersions() {
    assertFalse(DartToolingDaemonService.isDartSdkVersionSufficient("2.13.4"))
    assertFalse(DartToolingDaemonService.isDartSdkVersionSufficient("3.3.4"))
    assertFalse(DartToolingDaemonService.isDartSdkVersionSufficient("3.0.0"))
  }
}
