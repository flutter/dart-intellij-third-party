/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.ide.toolingDaemon

import junit.framework.TestCase

class DartToolingDaemonCommandLineTest : TestCase() {

    fun testIdentifiesDtdCommandLine() {
        assertTrue(isDtdCommandLine("/path/to/dart tooling-daemon --machine --ping-interval=15"))
        assertFalse(isDtdCommandLine("/path/to/dart tooling-daemon --machine"))
    }
}
