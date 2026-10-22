/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import junit.framework.TestCase

class VmServiceDriverElementTest : TestCase() {

  fun testDeprecatedTypeRefKindRemainsDecodable() {
    val json = JsonObject().apply {
      addProperty("kind", "TypeRef")
    }

    assertEquals(InstanceKind.TypeRef, InstanceRef(json).kind)
  }

  fun testDeprecatedAsyncStackValuesRemainDecodable() {
    val awaiterFrameJson = JsonObject().apply {
      addProperty("kind", "AsyncActivation")
    }
    val stackJson = JsonObject().apply {
      add("awaiterFrames", JsonArray().apply { add(awaiterFrameJson) })
    }

    val awaiterFrames = Stack(stackJson).awaiterFrames
    assertEquals(1, awaiterFrames.size())
    assertEquals(FrameKind.AsyncActivation, awaiterFrames[0].kind)
  }

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

  fun testIsolateReloadEventIncludesIsolateGroupWithoutIsolate() {
    val json = JsonObject().apply {
      addProperty("type", "Event")
      addProperty("kind", "IsolateReload")
      add("isolateGroup", JsonObject().apply {
        addProperty("type", "@IsolateGroup")
        addProperty("id", "isolateGroups/1")
        addProperty("number", "1")
        addProperty("name", "main.dart")
        addProperty("isSystemIsolateGroup", false)
      })
    }

    val event = Event(json)
    val isolateGroup = event.isolateGroup
    assertEquals(EventKind.IsolateReload, event.kind)
    assertEquals("isolateGroups/1", isolateGroup?.id)
    assertEquals("1", isolateGroup?.number)
    assertEquals("main.dart", isolateGroup?.name)
    assertFalse(isolateGroup?.isSystemIsolateGroup ?: true)
    assertNull(event.isolate)
  }

  fun testEventIsolateGroupIsOptional() {
    val json = JsonObject().apply {
      addProperty("type", "Event")
      addProperty("kind", "VMUpdate")
    }

    assertNull(Event(json).isolateGroup)
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

  private fun assertMissingAndJsonNull(propertyName: String, accessor: (JsonObject) -> Long) {
    assertEquals(-1L, accessor(JsonObject()))

    val jsonNull = JsonObject().apply {
      add(propertyName, JsonNull.INSTANCE)
    }
    assertEquals(-1L, accessor(jsonNull))
  }
}
