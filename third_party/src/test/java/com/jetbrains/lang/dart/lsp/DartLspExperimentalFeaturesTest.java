// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.lsp;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.sdk.DartConfigurable;

public class DartLspExperimentalFeaturesTest extends CodeInsightFixtureTestCase {

  @Override
  public void tearDown() throws Exception {
    try {
      PropertiesComponent.getInstance(getProject()).unsetValue("dart.lsp.experimental.enabled");
    } catch (Throwable e) {
      addSuppressedException(e);
    } finally {
      super.tearDown();
    }
  }

  public void testExperimentalFeaturesSettingDefault() {
    assertTrue(DartConfigurable.isExperimentalLspFeaturesEnabled(getProject()));
  }

  public void testToggleExperimentalFeaturesSetting() {
    assertTrue(DartConfigurable.isExperimentalLspFeaturesEnabled(getProject()));

    PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", false, true);
    assertFalse(DartConfigurable.isExperimentalLspFeaturesEnabled(getProject()));

    PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", true, true);
    assertTrue(DartConfigurable.isExperimentalLspFeaturesEnabled(getProject()));
  }
}
