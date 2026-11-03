/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.analyzer

import com.google.dart.server.generated.AnalysisServer
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.intellij.openapi.util.io.NioFiles
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class DASSCacheInvalidatorTest : BasePlatformTestCase() {

  fun testDeleteDartServerCacheAsyncHandlesNullAndMissingDirectory() {
    assertNull(deleteDartServerCacheAsync(null))

    val tempParent = Files.createTempDirectory("dart-server-cache-missing")
    try {
      val missingDir = tempParent.resolve("does-not-exist").toFile()
      assertNull(deleteDartServerCacheAsync(missingDir))
    } finally {
      NioFiles.deleteRecursively(tempParent)
    }
  }

  fun testDeleteDartServerCacheAsyncDeletesDirectoryRecursively() {
    val cacheDir = Files.createTempDirectory("dart-server-cache-test")
    try {
      val nestedDir = Files.createDirectories(cacheDir.resolve("driver/snapshots"))
      Files.writeString(nestedDir.resolve("cache.bin"), "cached-state")

      val future = deleteDartServerCacheAsync(cacheDir.toFile())
      assertNotNull(future)
      future!!.get(10, TimeUnit.SECONDS)

      assertFalse(Files.exists(cacheDir))
    } finally {
      NioFiles.deleteRecursively(cacheDir)
    }
  }

  fun testRemovedDeprecatedAnalysisServerMethodsAreAbsent() {
    val removedMethods = setOf(
      "analysis_getReachableSources",
      "analysis_updateOptions",
      "completion_registerLibraryPaths",
      "execution_setSubscriptions",
    )

    val interfaceMethods = AnalysisServer::class.java.declaredMethods.map { it.name }.toSet()
    val implMethods = RemoteAnalysisServerImpl::class.java.declaredMethods.map { it.name }.toSet()

    for (methodName in removedMethods) {
      assertFalse("Expected $methodName to be removed from AnalysisServer", methodName in interfaceMethods)
      assertFalse("Expected $methodName to be removed from RemoteAnalysisServerImpl", methodName in implMethods)
    }
  }
}
