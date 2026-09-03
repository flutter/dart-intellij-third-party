/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.vmService

import com.google.gson.JsonObject
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Instance
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.InstanceKind
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.InstanceRef
import junit.framework.TestCase

class VmServiceDriverElementTest : TestCase() {

  fun testUserTagKindAndLabel() {
    val json = JsonObject().apply {
      addProperty("kind", "UserTag")
      addProperty("label", "manual-test-tag")
    }

    val instanceRef = InstanceRef(json)
    assertEquals(InstanceKind.UserTag, instanceRef.kind)
    assertEquals("manual-test-tag", instanceRef.label)

    val instance = Instance(json)
    assertEquals(InstanceKind.UserTag, instance.kind)
    assertEquals("manual-test-tag", instance.label)
  }
}
