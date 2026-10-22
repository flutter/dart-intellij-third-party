/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.vmService

import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.VmService
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.VmServiceListener
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.GetIsolateConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.GetPerfettoCpuSamplesConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.ResumeConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.SuccessConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.VMConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Event
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.EventKind
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Isolate
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.PerfettoCpuSamples
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.RPCError
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Sentinel
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Success
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.VM
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class VmServicePerfettoCpuSamplesTest : VmServiceIntegrationTestBase() {

  private companion object {
    const val RESPONSE_TIMEOUT_SECONDS = 5
    const val WORKLOAD_TIMEOUT_SECONDS = 10
  }

  fun testGetPerfettoCpuSamplesFromLiveVm() {
    val service = connectToVmService(
      scriptName = "perfetto_cpu_samples.dart",
      vmOptions = listOf(
        "--pause-isolates-on-start",
        "--pause-isolates-on-exit",
        "--profiler=true",
        "--profile_period=100"
      )
    )

    val isolates = awaitVM(service).isolates
    assertFalse("The test VM should expose its workload isolate", isolates.isEmpty)
    val isolateId = requireNotNull(isolates[0].id) { "The workload isolate should have an id" }
    runWorkloadToPauseExit(service, isolateId)
    val samples = awaitPerfettoCpuSamples(service, isolateId)

    assertTrue("samplePeriod should be positive", samples.samplePeriod > 0)
    assertTrue("maxStackDepth should be positive", samples.maxStackDepth > 0)
    assertTrue("sampleCount should be positive", samples.sampleCount > 0)
    assertTrue("timeOriginMicros should be positive", samples.timeOriginMicros > 0)
    assertTrue("timeExtentMicros should be positive", samples.timeExtentMicros > 0)
    assertTrue("pid should be positive", samples.pid > 0)

    val encodedSamples = requireNotNull(samples.samples) { "Perfetto samples should be present" }
    assertTrue(
      "Perfetto samples should decode to a non-empty proto payload",
      Base64.getDecoder().decode(encodedSamples).isNotEmpty()
    )
  }

  private fun runWorkloadToPauseExit(service: VmService, isolateId: String) {
    val pauseStart = CountDownLatch(1)
    val pauseExit = CountDownLatch(1)
    val connectionFailure = AtomicReference<String>()
    val listener = object : VmServiceListener {
      override fun connectionOpened() = Unit

      override fun received(streamId: String, event: Event) {
        if (streamId == VmService.DEBUG_STREAM_ID && event.isolate?.id == isolateId) {
          when (event.kind) {
            EventKind.PauseStart -> pauseStart.countDown()
            EventKind.PauseExit -> pauseExit.countDown()
            else -> Unit
          }
        }
      }

      override fun connectionClosed() {
        connectionFailure.set("VM service connection closed before the workload completed")
        pauseStart.countDown()
        pauseExit.countDown()
      }
    }

    service.addVmServiceListener(listener)
    try {
      awaitDebugStreamSubscription(service)
      awaitIsolatePausedAtStart(service, isolateId, pauseStart)
      val startFailure = connectionFailure.get()
      if (startFailure != null) fail(startFailure)
      resumeIsolate(service, isolateId)
      waitForLatch(
        "The workload should pause at exit within ${WORKLOAD_TIMEOUT_SECONDS}s",
        pauseExit,
        WORKLOAD_TIMEOUT_SECONDS
      )
      val failure = connectionFailure.get()
      if (failure != null) fail(failure)
    } finally {
      service.removeVmServiceListener(listener)
    }
  }

  private fun awaitDebugStreamSubscription(service: VmService) {
    val latch = CountDownLatch(1)
    val failure = AtomicReference<String>()
    service.streamListen(VmService.DEBUG_STREAM_ID, object : SuccessConsumer {
      override fun received(response: Success?) = latch.countDown()

      override fun onError(error: RPCError?) {
        failure.set("streamListen(Debug) failed: ${error?.message ?: "unknown error"}")
        latch.countDown()
      }
    })
    waitForLatch(
      "streamListen(Debug) should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
      latch,
      RESPONSE_TIMEOUT_SECONDS
    )
    val error = failure.get()
    if (error != null) fail(error)
  }

  private fun resumeIsolate(service: VmService, isolateId: String) {
    val latch = CountDownLatch(1)
    val failure = AtomicReference<String>()
    service.resume(isolateId, object : ResumeConsumer {
      override fun received(response: Success) = latch.countDown()

      override fun received(response: Sentinel) {
        failure.set("resume returned sentinel ${response.kind}")
        latch.countDown()
      }

      override fun onError(error: RPCError) {
        failure.set("resume failed: ${error.message}")
        latch.countDown()
      }
    })
    waitForLatch(
      "resume should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
      latch,
      RESPONSE_TIMEOUT_SECONDS
    )
    val error = failure.get()
    if (error != null) fail(error)
  }

  private fun awaitIsolatePausedAtStart(service: VmService, isolateId: String, pauseStart: CountDownLatch) {
    val isolate = awaitIsolate(service, isolateId)
    if (isolate.runnable && isolate.pauseEvent.kind == EventKind.PauseStart) return

    waitForLatch(
      "The workload isolate should become runnable and pause at start within ${WORKLOAD_TIMEOUT_SECONDS}s; " +
      "initial state: runnable=${isolate.runnable}, pauseEvent=${isolate.pauseEvent.kind}",
      pauseStart,
      WORKLOAD_TIMEOUT_SECONDS
    )
  }

  private fun awaitIsolate(service: VmService, isolateId: String): Isolate {
    val latch = CountDownLatch(1)
    val result = AtomicReference<Isolate>()
    val failure = AtomicReference<String>()
    service.getIsolate(isolateId, object : GetIsolateConsumer {
      override fun received(response: Isolate) {
        result.set(response)
        latch.countDown()
      }

      override fun received(response: Sentinel) {
        failure.set("getIsolate returned sentinel ${response.kind}")
        latch.countDown()
      }

      override fun onError(error: RPCError) {
        failure.set("getIsolate failed: ${error.message}")
        latch.countDown()
      }
    })
    waitForLatch(
      "getIsolate should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
      latch,
      RESPONSE_TIMEOUT_SECONDS
    )
    return requireNotNull(result.get()) { failure.get() ?: "getIsolate returned no result" }
  }

  private fun awaitVM(service: VmService): VM {
    val latch = CountDownLatch(1)
    val result = AtomicReference<VM>()
    service.getVM(object : VMConsumer {
      override fun received(response: VM) {
        result.set(response)
        latch.countDown()
      }

      override fun onError(error: RPCError?) = latch.countDown()
    })
    waitForLatch(
      "getVM should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
      latch,
      RESPONSE_TIMEOUT_SECONDS
    )
    return requireNotNull(result.get()) { "getVM returned an error" }
  }

  private fun awaitPerfettoCpuSamples(service: VmService, isolateId: String): PerfettoCpuSamples {
    val latch = CountDownLatch(1)
    val result = AtomicReference<PerfettoCpuSamples>()
    val failure = AtomicReference<String>()
    service.getPerfettoCpuSamples(isolateId, object : GetPerfettoCpuSamplesConsumer {
      override fun received(response: PerfettoCpuSamples) {
        result.set(response)
        latch.countDown()
      }

      override fun received(response: Sentinel) {
        failure.set("getPerfettoCpuSamples returned sentinel ${response.kind}")
        latch.countDown()
      }

      override fun onError(error: RPCError) {
        failure.set("getPerfettoCpuSamples failed: ${error.message}")
        latch.countDown()
      }
    })
    waitForLatch(
      "getPerfettoCpuSamples should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
      latch,
      RESPONSE_TIMEOUT_SECONDS
    )
    return requireNotNull(result.get()) {
      failure.get() ?: "getPerfettoCpuSamples returned no result"
    }
  }
}
