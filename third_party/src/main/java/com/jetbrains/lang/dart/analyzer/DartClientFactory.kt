// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer

import com.google.dart.server.internal.remote.DebugPrintStream
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.lang.dart.analyzer.legacy.LegacyDartAnalysisClient
import com.jetbrains.lang.dart.analyzer.lsp.LspDartAnalysisClient
import com.jetbrains.lang.dart.sdk.DartSdk

internal object DartClientFactory {
  @JvmStatic
  fun create(project: Project, sdk: DartSdk, debugStream: DebugPrintStream, suppressAnalytics: Boolean): DartClient {
    return if (Registry.`is`("dart.use.lsp.client", false)) {
      LspDartAnalysisClient(project, sdk, suppressAnalytics)
    }
    else {
      LegacyDartAnalysisClient(project, sdk, debugStream, suppressAnalytics)
    }
  }
}
