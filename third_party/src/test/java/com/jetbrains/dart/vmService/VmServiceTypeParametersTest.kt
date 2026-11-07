/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.vmService

import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.VmService
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.VmServiceListener
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.GetAllocationProfileConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.GetInstancesConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.GetObjectConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.AllocationProfile
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Event
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.EventKind
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.InstanceSet
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Obj
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.RPCError
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Sentinel
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.TypeParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class VmServiceTypeParametersTest : VmServiceIntegrationTestBase() {

  private companion object {
    const val RESPONSE_TIMEOUT_SECONDS = 5
    const val TYPE_PARAMETERS_SEARCH_TIMEOUT_SECONDS = 10
  }

  fun testGetTypeParametersFromLiveVm() {
    val service = connectToVmService(
      scriptName = "type_parameters.dart",
      vmOptions = listOf("--pause-isolates-on-exit")
    )

    val isolates = awaitVM(service).isolates
    assertFalse("The test VM should expose its workload isolate", isolates.isEmpty)
    val isolateId = requireNotNull(isolates[0].id) { "The workload isolate should have an id" }
    awaitPauseExit(service, isolateId)

    val profile = awaitAllocationProfile(service, isolateId)
    val objectId = findTypeParametersObjectId(service, isolateId, profile)
    val response = awaitObject(service, isolateId, objectId)
    assertTrue("getObject should decode TypeParameters", response is TypeParameters)

    val typeParameters = response as TypeParameters
    assertNotNull("The decoded TypeParameters object should have an id", typeParameters.id)
    assertNotNull("The names instance should have an id", typeParameters.names.id)
  }

  private fun findTypeParametersObjectId(service: VmService, isolateId: String, profile: AllocationProfile): String {
    val searchTimeoutNanos = TimeUnit.SECONDS.toNanos(TYPE_PARAMETERS_SEARCH_TIMEOUT_SECONDS.toLong())
    val deadlineNanos = System.nanoTime() + searchTimeoutNanos
    val firstLookupFailure = AtomicReference<String>()
    val members = profile.members
    for (index in 0 until members.size()) {
      val member = members[index]
      if (member.instancesCurrent == 0) continue

      val classId = member.classRef.id ?: continue
      val remainingNanos = deadlineNanos - System.nanoTime()
      if (remainingNanos <= 0) break

      val remainingSeconds = TimeUnit.NANOSECONDS.toSeconds(remainingNanos).toInt() + 1
      val instances = awaitInstances(service, isolateId, classId, remainingSeconds, firstLookupFailure) ?: continue
      for (instanceIndex in 0 until instances.instances.size()) {
        val instance = instances.instances[instanceIndex]
        if (instance.type == "@TypeParameters") {
          return requireNotNull(instance.id) { "The TypeParameters reference should have an id" }
        }
      }
    }
    val failure = firstLookupFailure.get()?.let { "; first skipped lookup failure: $it" } ?: ""
    throw AssertionError(
      "The live VM should expose a @TypeParameters reference within " +
      "${TYPE_PARAMETERS_SEARCH_TIMEOUT_SECONDS}s$failure"
    )
  }

  private fun awaitPauseExit(service: VmService, isolateId: String) {
    val pauseExit = CountDownLatch(1)
    val connectionFailure = AtomicReference<String>()
    val listener = object : VmServiceListener {
      override fun connectionOpened() = Unit

      override fun received(streamId: String, event: Event) {
        if (streamId == VmService.DEBUG_STREAM_ID &&
            event.isolate?.id == isolateId &&
            event.kind == EventKind.PauseExit) {
          pauseExit.countDown()
        }
      }

      override fun connectionClosed() {
        connectionFailure.set("VM service connection closed before the workload paused at exit")
        pauseExit.countDown()
      }
    }

    service.addVmServiceListener(listener)
    try {
      awaitDebugStreamSubscription(service)
      if (awaitIsolate(service, isolateId).pauseEvent.kind != EventKind.PauseExit) {
        waitForLatch("The workload should pause at exit", pauseExit, RESPONSE_TIMEOUT_SECONDS)
      }
      val failure = connectionFailure.get()
      if (failure != null) fail(failure)
    } finally {
      service.removeVmServiceListener(listener)
    }
  }

  private fun awaitAllocationProfile(service: VmService, isolateId: String): AllocationProfile {
    val latch = CountDownLatch(1)
    val result = AtomicReference<AllocationProfile>()
    val failure = AtomicReference<String>()
    service.getAllocationProfile(isolateId, object : GetAllocationProfileConsumer {
      override fun received(response: AllocationProfile) {
        result.set(response)
        latch.countDown()
      }

      override fun received(response: Sentinel) {
        failure.set("getAllocationProfile returned sentinel ${response.kind}")
        latch.countDown()
      }

      override fun onError(error: RPCError) {
        failure.set("getAllocationProfile failed: ${error.message}")
        latch.countDown()
      }
    })
    waitForLatch("getAllocationProfile should respond", latch, RESPONSE_TIMEOUT_SECONDS)
    return requireNotNull(result.get()) { failure.get() ?: "getAllocationProfile returned no result" }
  }

  private fun awaitInstances(
    service: VmService,
    isolateId: String,
    classId: String,
    timeoutSeconds: Int,
    firstLookupFailure: AtomicReference<String>
  ): InstanceSet? {
    val latch = CountDownLatch(1)
    val result = AtomicReference<InstanceSet>()
    service.getInstances(isolateId, classId, 1, object : GetInstancesConsumer {
      override fun received(response: InstanceSet) {
        result.set(response)
        latch.countDown()
      }

      override fun received(response: Sentinel) {
        firstLookupFailure.compareAndSet(null, "$classId returned sentinel ${response.kind}")
        latch.countDown()
      }

      override fun onError(error: RPCError) {
        firstLookupFailure.compareAndSet(null, "$classId failed: ${error.message}")
        latch.countDown()
      }
    })
    waitForLatch("getInstances($classId) should respond", latch, timeoutSeconds)
    return result.get()
  }

  private fun awaitObject(service: VmService, isolateId: String, objectId: String): Any {
    val latch = CountDownLatch(1)
    val result = AtomicReference<Any>()
    val failure = AtomicReference<String>()
    service.getObject(isolateId, objectId, object : GetObjectConsumer {
      override fun received(response: Obj) {
        result.set(response)
        latch.countDown()
      }

      override fun received(response: Sentinel) {
        failure.set("getObject returned sentinel ${response.kind}")
        latch.countDown()
      }

      override fun onError(error: RPCError) {
        failure.set("getObject failed: ${error.message}")
        latch.countDown()
      }
    })
    waitForLatch("getObject should respond", latch, RESPONSE_TIMEOUT_SECONDS)
    return requireNotNull(result.get()) { failure.get() ?: "getObject returned no result" }
  }
}
