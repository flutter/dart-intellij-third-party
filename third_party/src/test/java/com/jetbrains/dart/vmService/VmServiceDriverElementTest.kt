/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.vmService

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.CpuSample
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Event
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Instance
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.InstanceKind
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.InstanceRef
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.PerfettoTimeline
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Timestamp
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

  fun testPerfettoTimeline() {
    val json = JsonObject().apply {
      addProperty("trace", "AA==")
      addProperty("timeOriginMicros", 3_000_000_000L)
      addProperty("timeExtentMicros", 4_000_000_000L)
    }

    val timeline = PerfettoTimeline(json)
    assertEquals("AA==", timeline.trace)
    assertEquals(3_000_000_000L, timeline.timeOriginMicros)
    assertEquals(4_000_000_000L, timeline.timeExtentMicros)
  }

  fun testTimestampAccessorsSupportLongValues() {
    val json = JsonObject().apply {
      addProperty("timestamp", 3_000_000_000L)
    }

    assertEquals(3_000_000_000L, CpuSample(json).timestamp)
    assertEquals(3_000_000_000L, Event(json).timestamp)
    assertEquals(3_000_000_000L, Timestamp(json).timestamp)
  }

  fun testLongAccessorsHandleMissingAndJsonNullValues() {
    assertMissingAndJsonNull("timestamp") { CpuSample(it).timestamp }
    assertMissingAndJsonNull("timestamp") { Event(it).timestamp }
    assertMissingAndJsonNull("timestamp") { Timestamp(it).timestamp }
    assertMissingAndJsonNull("timeOriginMicros") { PerfettoTimeline(it).timeOriginMicros }
    assertMissingAndJsonNull("timeExtentMicros") { PerfettoTimeline(it).timeExtentMicros }
  }

  private fun assertMissingAndJsonNull(propertyName: String, accessor: (JsonObject) -> Long) {
    assertEquals(-1L, accessor(JsonObject()))

    val jsonNull = JsonObject().apply {
      add(propertyName, JsonNull.INSTANCE)
    }
    assertEquals(-1L, accessor(jsonNull))
  }
}
