// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.lsp;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.platform.dartlsp.api.customization.LspFormattingDisabled;
import com.intellij.platform.dartlsp.api.customization.LspFormattingSupport;
import com.intellij.testFramework.LightVirtualFile;
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

  public void testFormattingMethodsHaveOneSettingsLabel() {
    final var formatting = LspMethod.valueOf("FORMATTING");
    final var rangeFormatting = LspMethod.valueOf("RANGE_FORMATTING");
    assertTrue(formatting.isExperimental());
    assertTrue(rangeFormatting.isExperimental());
    assertEquals("formatting", formatting.getPresentableName());
    assertNull(rangeFormatting.getPresentableName());
    assertTrue(LspMethod.Companion.getExperimentalFeatures().contains(formatting));
    assertTrue(LspMethod.Companion.getExperimentalFeatures().contains(rangeFormatting));
  }

  public void testFormattingDescriptorFollowsFlagTransitions() {
    final var descriptor = new DartLspServerDescriptor(getProject());
    final var enabledCustomizer = descriptor.getLspCustomization().getFormattingCustomizer();
    for (boolean enabled : new boolean[]{true, false, true}) {
      PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", enabled, true);
      final var customizer = descriptor.getLspCustomization().getFormattingCustomizer();
      if (enabled) {
        assertTrue(customizer instanceof LspFormattingSupport);
        assertSame(enabledCustomizer, customizer);
      } else {
        assertSame(LspFormattingDisabled.INSTANCE, customizer);
      }
    }
  }

  public void testFormattingDescriptorAcceptsOnlyLocalDartFiles() {
    final var customizer = new DartLspServerDescriptor(getProject()).getLspCustomization().getFormattingCustomizer();
    final var support = (LspFormattingSupport)customizer;
    for (String name : new String[]{"format.dart", "format.DART"}) {
      final var file = myFixture.getTempDirFixture().createFile(name);
      assertTrue(file.isInLocalFileSystem());
      assertTrue(support.shouldFormatThisFileExclusivelyByServer(file, true, false));
      assertTrue(support.shouldFormatThisFileExclusivelyByServer(file, false, true));
    }
    for (String name : new String[]{"pubspec.yaml", "page.html", "README", "format.dart.txt"}) {
      assertFalse(name, support.shouldFormatThisFileExclusivelyByServer(
        myFixture.getTempDirFixture().createFile(name), true, true));
    }
    assertFalse(support.shouldFormatThisFileExclusivelyByServer(
      new LightVirtualFile("injected.dart", ""), false, true));
  }

  public void testLspMethodsExperimentalStatus() {
    assertFalse(LspMethod.CONNECT_TO_DTD.isExperimental());
    assertFalse(LspMethod.DIAGNOSTIC_SERVER.isExperimental());
    assertFalse(LspMethod.DOCUMENT_HIGHLIGHT.isExperimental());
    assertFalse(LspMethod.HOVER.isExperimental());
    assertFalse(LspMethod.TYPE_DEFINITION.isExperimental());
    assertTrue(LspMethod.CODE_ACTION.isExperimental());
    assertTrue(LspMethod.DEFINITION.isExperimental());
    assertTrue(LspMethod.PUBLISH_DIAGNOSTICS.isExperimental());
    assertTrue(LspMethod.SEMANTIC_TOKENS_FULL.isExperimental());
    assertTrue(LspMethod.DOCUMENT_SYMBOL.isExperimental());
    assertTrue(LspMethod.RENAME.isExperimental());
    assertTrue(LspMethod.SIGNATURE_HELP.isExperimental());

    assertFalse(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.CONNECT_TO_DTD));
    assertFalse(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.DIAGNOSTIC_SERVER));
    assertFalse(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.DOCUMENT_HIGHLIGHT));
    assertFalse(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.HOVER));
    assertTrue(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.CODE_ACTION));
    assertTrue(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.PUBLISH_DIAGNOSTICS));
    assertTrue(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.SEMANTIC_TOKENS_FULL));
    assertTrue(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.SIGNATURE_HELP));
  }
}
