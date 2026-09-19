/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.vmService

import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.VmService
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.SuccessConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.VMConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.VersionConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.RPCError
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Success
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.VM
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Version
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class VmServiceWebsocketTest : VmServiceIntegrationTestBase() {

  private companion object {
    const val RESPONSE_TIMEOUT_SECONDS = 5
  }

  fun testVmServiceExchangesMessagesOverWebSocket() {
    val service = connectToVmService()

    // connect() already performed a getVersion handshake, so the connection is open.
    assertNotNull("VmService connection should be open", service.runtimeVersion)

    // Version should be the same as the initial one
    val version = awaitVersion(service)
    assertEquals("Major version should match the handshake", service.runtimeVersion.major, version.major)
    assertEquals("Minor version should match the handshake", service.runtimeVersion.minor, version.minor)

    // Request/response: getVM
    val vm = awaitVM(service)
    assertNotNull("VM name should be present", vm.name)

    // Subscribe to the Isolate stream and expect a Success response.
    val streamSuccess = CountDownLatch(1)
    service.streamListen(VmService.ISOLATE_STREAM_ID, object : SuccessConsumer {
      override fun received(response: Success?) = streamSuccess.countDown()
      override fun onError(error: RPCError?) {}
    })
    assertTrue(
      "streamListen(Isolate) should be acknowledged within ${RESPONSE_TIMEOUT_SECONDS}s",
      streamSuccess.await(RESPONSE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
    )
  }

  private fun awaitVersion(service: VmService): Version {
    val latch = CountDownLatch(1)
    val result = AtomicReference<Version>()
    service.getVersion(object : VersionConsumer {
      override fun received(response: Version) {
        result.set(response)
        latch.countDown()
      }
      override fun onError(error: RPCError?) = latch.countDown()
    })
    assertTrue(
      "getVersion should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
      latch.await(RESPONSE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
    )
    return requireNotNull(result.get()) { "getVersion returned an error" }
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
    assertTrue(
      "getVM should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
      latch.await(RESPONSE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
    )
    return requireNotNull(result.get()) { "getVM returned an error" }
  }
}
