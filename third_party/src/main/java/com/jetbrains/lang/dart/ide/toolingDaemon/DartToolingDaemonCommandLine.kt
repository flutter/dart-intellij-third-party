/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.ide.toolingDaemon

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.lang.dart.analytics.Analytics
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkUtil

private val DTD_COMMAND_LINE_PARAMETERS = listOf(
  "tooling-daemon",
  "--machine",
  "--ping-interval=15",
)

private val DTD_COMMAND_LINE_SUFFIX = DTD_COMMAND_LINE_PARAMETERS.joinToString(separator = " ", prefix = " ")

internal fun createDtdCommandLine(sdk: DartSdk): GeneralCommandLine {
  val commandLine = GeneralCommandLine().withWorkDirectory(sdk.homePath)
  commandLine.exePath = FileUtil.toSystemDependentName(DartSdkUtil.getDartExePath(sdk))
  commandLine.charset = Charsets.UTF_8
  DTD_COMMAND_LINE_PARAMETERS.forEach(commandLine::addParameter)
  Analytics.updateEnvironment(commandLine)
  return commandLine
}

internal fun isDtdCommandLine(text: String): Boolean = text.endsWith(DTD_COMMAND_LINE_SUFFIX)
