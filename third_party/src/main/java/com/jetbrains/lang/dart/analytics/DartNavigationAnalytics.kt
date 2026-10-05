// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.analytics

import com.intellij.openapi.project.Project

/** Fixed categories keep source names and paths out of navigation events. */
enum class DartNavigationAnalytics(private val id: String) {
  OVERRIDE_METHOD("dart.gutter.overrideMethod"),
  IMPLEMENT_METHOD("dart.gutter.implementMethod"),
  SUBCLASSES("dart.gutter.subclasses"),
  OVERRIDING_METHODS("dart.gutter.overridingMethods");

  fun report(project: Project) {
    Analytics.report(AnalyticsData.forAction(id, "GutterIcon", project))
  }
}
