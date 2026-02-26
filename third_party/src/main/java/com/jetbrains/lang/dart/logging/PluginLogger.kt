/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.logging

import com.intellij.openapi.application.PathManager
import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Logger
import com.intellij.openapi.diagnostic.Logger as IJLogger

object PluginLogger {
  private const val LOG_FILE_NAME = "dash.log"

  private val rootLogger: Logger = Logger.getLogger("com.jetbrains.lang.dart")

  init {
    val logPath = PathManager.getLogPath()
    val fullPath = logPath + File.separatorChar + LOG_FILE_NAME

    val flutterLogger = Logger.getLogger("io.flutter")
    
    // Check if either logger already has a FileHandler for dash.log
    val existingHandler = rootLogger.handlers.filterIsInstance<FileHandler>().firstOrNull()
      ?: flutterLogger.handlers.filterIsInstance<FileHandler>().firstOrNull()

    if (existingHandler != null) {
      // Another plugin initialized first; reuse its handler
      rootLogger.addHandler(existingHandler)
      flutterLogger.addHandler(existingHandler)
    } else {
      // We are the first plugin to initialize; create the handler
      try {
        val newHandler = FileHandler(fullPath, 10 * 1024 * 1024, 5, true)
        System.setProperty(
          "java.util.logging.SimpleFormatter.format",
          "%1\$tF %1\$tT %3\$s [%4$-7s] %5\$s %6\$s %n"
        )
        newHandler.formatter = java.util.logging.SimpleFormatter()

        // Attach to both loggers so the next plugin finds it
        rootLogger.addHandler(newHandler)
        flutterLogger.addHandler(newHandler)
      } catch (e: Exception) {
        throw RuntimeException(e)
      }
    }
  }

  fun createLogger(logClass: Class<*>): IJLogger {
    return IJLogger.getInstance(logClass.getName())
  }
}
