/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service

import com.google.gson.JsonObject
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.Consumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.GetPerfettoCpuSamplesConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.PerfettoTimelineConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.PerfettoCpuSamples
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.PerfettoTimeline
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.RPCError
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Sentinel
import junit.framework.TestCase

class VmServiceDriverRequestTest : TestCase() {

  fun testPerfettoTimelineRequestSupportsLongTimestamps() {
    val service = RecordingVmService()
    val consumer = object : PerfettoTimelineConsumer {
      override fun received(response: PerfettoTimeline) = Unit
      override fun onError(error: RPCError) = Unit
    }

    service.getPerfettoVMTimeline(3_000_000_000L, 4_000_000_000L, consumer)

    assertEquals("getPerfettoVMTimeline", service.method)
    assertEquals(3_000_000_000L, service.params.get("timeOriginMicros").asLong)
    assertEquals(4_000_000_000L, service.params.get("timeExtentMicros").asLong)
    assertSame(consumer, service.consumer)
  }

  fun testPerfettoCpuSamplesRequestSupportsLongTimestamps() {
    val service = RecordingVmService()
    val consumer = perfettoCpuSamplesConsumer()

    service.getPerfettoCpuSamples("isolates/1", 3_000_000_000L, 4_000_000_000L, consumer)

    assertEquals("getPerfettoCpuSamples", service.method)
    assertEquals("isolates/1", service.params.get("isolateId").asString)
    assertEquals(3_000_000_000L, service.params.get("timeOriginMicros").asLong)
    assertEquals(4_000_000_000L, service.params.get("timeExtentMicros").asLong)
    assertSame(consumer, service.consumer)
  }

  fun testPerfettoCpuSamplesRequestOmitsOptionalTimestamps() {
    val service = RecordingVmService()
    val consumer = perfettoCpuSamplesConsumer()

    service.getPerfettoCpuSamples("isolates/1", consumer)

    assertEquals("getPerfettoCpuSamples", service.method)
    assertEquals("isolates/1", service.params.get("isolateId").asString)
    assertFalse(service.params.has("timeOriginMicros"))
    assertFalse(service.params.has("timeExtentMicros"))
    assertSame(consumer, service.consumer)
  }

  fun testPerfettoCpuSamplesRequestSupportsOnlyTimeOrigin() {
    val service = RecordingVmService()
    val consumer = perfettoCpuSamplesConsumer()

    service.getPerfettoCpuSamples("isolates/1", 3_000_000_000L, null, consumer)

    assertEquals(3_000_000_000L, service.params.get("timeOriginMicros").asLong)
    assertFalse(service.params.has("timeExtentMicros"))
  }

  fun testPerfettoCpuSamplesRequestSupportsOnlyTimeExtent() {
    val service = RecordingVmService()
    val consumer = perfettoCpuSamplesConsumer()

    service.getPerfettoCpuSamples("isolates/1", null, 4_000_000_000L, consumer)

    assertFalse(service.params.has("timeOriginMicros"))
    assertEquals(4_000_000_000L, service.params.get("timeExtentMicros").asLong)
  }

  private fun perfettoCpuSamplesConsumer(): GetPerfettoCpuSamplesConsumer =
    object : GetPerfettoCpuSamplesConsumer {
      override fun received(response: PerfettoCpuSamples) = Unit
      override fun received(response: Sentinel) = Unit
      override fun onError(error: RPCError) = Unit
    }

  private class RecordingVmService : VmService() {
    lateinit var method: String
    lateinit var params: JsonObject
    lateinit var consumer: Consumer

    override fun request(method: String, params: JsonObject, consumer: Consumer) {
      this.method = method
      this.params = params
      this.consumer = consumer
    }
  }
}
