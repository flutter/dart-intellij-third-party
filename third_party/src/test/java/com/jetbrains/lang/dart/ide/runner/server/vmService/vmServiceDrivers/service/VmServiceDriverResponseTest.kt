/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service

import com.google.gson.JsonObject
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.GetIsolatePauseEventConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.GetPerfettoCpuSamplesConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Event
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.EventKind
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.PerfettoCpuSamples
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.RPCError
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Sentinel
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.SentinelKind
import junit.framework.TestCase

class VmServiceDriverResponseTest : TestCase() {

  fun testIsolatePauseEventResponseDispatch() {
    val consumer = RecordingIsolatePauseEventConsumer()
    val json = JsonObject().apply {
      addProperty("type", "Event")
      addProperty("kind", "PauseBreakpoint")
      addProperty("timestamp", 3_000_000_000L)
    }

    VmService().forwardResponse(consumer, "Event", json)

    assertEquals(EventKind.PauseBreakpoint, consumer.event?.kind)
    assertNull(consumer.sentinel)
    assertNull(consumer.error)
  }

  fun testIsolatePauseEventSentinelDispatch() {
    val consumer = RecordingIsolatePauseEventConsumer()
    val json = JsonObject().apply {
      addProperty("type", "Sentinel")
      addProperty("kind", "Collected")
      addProperty("valueAsString", "<collected>")
    }

    VmService().forwardResponse(consumer, "Sentinel", json)

    assertEquals(SentinelKind.Collected, consumer.sentinel?.kind)
    assertNull(consumer.event)
    assertNull(consumer.error)
  }

  fun testPerfettoCpuSamplesResponseDispatch() {
    val consumer = RecordingPerfettoCpuSamplesConsumer()
    val json = JsonObject().apply {
      addProperty("type", "PerfettoCpuSamples")
      addProperty("samplePeriod", 1_000)
      addProperty("maxStackDepth", 128)
      addProperty("sampleCount", 42)
      addProperty("timeOriginMicros", 3_000_000_000L)
      addProperty("timeExtentMicros", 4_000_000_000L)
      addProperty("pid", 12_345)
      addProperty("samples", "AA==")
    }

    VmService().forwardResponse(consumer, "PerfettoCpuSamples", json)

    val response = requireNotNull(consumer.response) { "Expected a PerfettoCpuSamples response" }
    assertEquals(3_000_000_000L, response.timeOriginMicros)
    assertEquals(4_000_000_000L, response.timeExtentMicros)
    assertNull(consumer.sentinel)
    assertNull(consumer.error)
  }

  fun testPerfettoCpuSamplesSentinelDispatch() {
    val consumer = RecordingPerfettoCpuSamplesConsumer()
    val json = JsonObject().apply {
      addProperty("type", "Sentinel")
      addProperty("kind", "Collected")
      addProperty("valueAsString", "<collected>")
    }

    VmService().forwardResponse(consumer, "Sentinel", json)

    assertEquals(SentinelKind.Collected, consumer.sentinel?.kind)
    assertNull(consumer.response)
    assertNull(consumer.error)
  }

  private class RecordingPerfettoCpuSamplesConsumer : GetPerfettoCpuSamplesConsumer {
    var response: PerfettoCpuSamples? = null
    var sentinel: Sentinel? = null
    var error: RPCError? = null

    override fun received(response: PerfettoCpuSamples) {
      this.response = response
    }

    override fun received(response: Sentinel) {
      sentinel = response
    }

    override fun onError(error: RPCError) {
      this.error = error
    }
  }

  private class RecordingIsolatePauseEventConsumer : GetIsolatePauseEventConsumer {
    var event: Event? = null
    var sentinel: Sentinel? = null
    var error: RPCError? = null

    override fun received(response: Event) {
      event = response
    }

    override fun received(response: Sentinel) {
      sentinel = response
    }

    override fun onError(error: RPCError) {
      this.error = error
    }
  }
}
