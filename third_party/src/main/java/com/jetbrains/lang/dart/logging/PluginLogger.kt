/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.logging

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger as IJLogger
import java.io.File
import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

object PluginLogger {
  private const val LOG_FILE_NAME = "dash.log"

  private val rootLogger: Logger = Logger.getLogger("com.jetbrains.lang.dart")

  // This handler specifies the logging format and location.
  private var fileHandler: FileHandler

  init {
    val logPath = PathManager.getLogPath()
    val fullPath = logPath + File.separatorChar + LOG_FILE_NAME
    
    // Check if a FileHandler for dash.log already exists on the rootLogger.
    // This happens when multiple plugins (e.g., Dart and Flutter) use the same logger name.
    val existingHandler = rootLogger.handlers.filterIsInstance<FileHandler>().firstOrNull()
    
    if (existingHandler != null) {
      fileHandler = existingHandler
    } else {
      try {
        fileHandler = FileHandler(fullPath, 10 * 1024 * 1024, 5, true)
        System.setProperty(
          "java.util.logging.SimpleFormatter.format",
          "%1\$tF %1\$tT %3\$s [%4$-7s] %5\$s %6\$s %n"
        )
        fileHandler.formatter = SimpleFormatter()
        rootLogger.addHandler(fileHandler)
      } catch (e: IOException) {
        throw RuntimeException(e)
      }
    }
  }

  fun createLogger(logClass: Class<*>): IJLogger {
    return IJLogger.getInstance(logClass.getName())
  }
}
