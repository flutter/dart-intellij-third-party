/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.vmService

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.VmService
import com.jetbrains.lang.dart.sdk.DartSdkUtil
import com.jetbrains.lang.dart.util.DartTestUtils
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

abstract class VmServiceIntegrationTestBase : BasePlatformTestCase() {

  private companion object {
    const val CONNECT_TIMEOUT_SECONDS = 5
  }

  private var processHandler: KillableProcessHandler? = null
  private var vmService: VmService? = null

  override fun setUp() {
    super.setUp()
    DartTestUtils.configureDartSdk(module, myFixture.projectDisposable, true)
  }

  override fun tearDown() {
    try {
      try {
        vmService?.disconnect()
      } finally {
        processHandler?.killProcess()
      }
    } finally {
      super.tearDown()
    }
  }

  protected fun connectToVmService(
    scriptName: String = "hello.dart",
    vmOptions: List<String> = listOf("--pause-isolates-on-start")
  ): VmService {
    val service = VmService.connect(launchDartProcessAndGetWsUri(scriptName, vmOptions))
    vmService = service
    return service
  }

  /**
   * Launches a Dart script with the VM service enabled and parses the `ws://` URI from stdout.
   * See DartCommandLineRunningState.java.
   */
  private fun launchDartProcessAndGetWsUri(scriptName: String, vmOptions: List<String>): String {
    val sdkHome = requireNotNull(System.getProperty("dart.sdk") ?: System.getenv("dart.sdk")) {
      "dart.sdk system property must be set"
    }
    val script = "${DartTestUtils.BASE_TEST_DATA_PATH}/vmService/$scriptName"

    val commandLine = GeneralCommandLine().withWorkDirectory(sdkHome)
    commandLine.exePath = DartSdkUtil.getDartExePath(sdkHome)
    commandLine.charset = StandardCharsets.UTF_8
    commandLine.addParameter("--enable-vm-service:0")
    for (option in vmOptions) {
      commandLine.addParameter(option)
    }
    commandLine.addParameter("run")
    commandLine.addParameter(script)

    val wsUri = AtomicReference<String>()
    val uriLatch = CountDownLatch(1)

    val handler = object : KillableProcessHandler(commandLine) {
      override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.forMostlySilentProcess()
    }
    processHandler = handler

    // Matches: "The Dart VM service is listening on http://127.0.0.1:1234/abc=/"
    val vmServiceLaunchUriPattern: Pattern =
      Pattern.compile("listening on (http://[\\d.]+:\\d+/[\\w=/-]*)")

    handler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (uriLatch.count == 0L) return
        val matcher = vmServiceLaunchUriPattern.matcher(event.text)
        if (matcher.find()) {
          wsUri.set(httpToWs(matcher.group(1)))
          uriLatch.countDown()
        }
      }
    })
    handler.startNotify()

    PlatformTestUtil.waitWithEventsDispatching(
      "Did not receive a VM service URI within ${CONNECT_TIMEOUT_SECONDS}s",
      { uriLatch.count == 0L },
      CONNECT_TIMEOUT_SECONDS
    )
    return requireNotNull(wsUri.get()) { "Failed to parse VM service URI" }
  }

  // "http://127.0.0.1:1234/abc=/" -> "ws://127.0.0.1:1234/abc=/ws"
  private fun httpToWs(httpUri: String): String {
    val trimmed = httpUri.removeSuffix("/")
    return "ws" + trimmed.removePrefix("http") + "/ws"
  }
}
