/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element

import com.google.gson.JsonNull
import com.google.gson.JsonObject
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

  fun testPerfettoCpuSamples() {
    val json = JsonObject().apply {
      addProperty("samplePeriod", 1_000)
      addProperty("maxStackDepth", 128)
      addProperty("sampleCount", 42)
      addProperty("timeOriginMicros", 3_000_000_000L)
      addProperty("timeExtentMicros", 4_000_000_000L)
      addProperty("pid", 12_345)
      addProperty("samples", "AA==")
    }

    val samples = PerfettoCpuSamples(json)
    assertEquals(1_000, samples.samplePeriod)
    assertEquals(128, samples.maxStackDepth)
    assertEquals(42, samples.sampleCount)
    assertEquals(3_000_000_000L, samples.timeOriginMicros)
    assertEquals(4_000_000_000L, samples.timeExtentMicros)
    assertEquals(12_345, samples.pid)
    assertEquals("AA==", samples.samples)
  }

  fun testPerfettoCpuSamplesWithNullTimestamps() {
    val json = JsonObject().apply {
      add("timeOriginMicros", JsonNull.INSTANCE)
      add("timeExtentMicros", JsonNull.INSTANCE)
    }

    val samples = PerfettoCpuSamples(json)
    assertEquals(-1L, samples.timeOriginMicros)
    assertEquals(-1L, samples.timeExtentMicros)
  }
}
