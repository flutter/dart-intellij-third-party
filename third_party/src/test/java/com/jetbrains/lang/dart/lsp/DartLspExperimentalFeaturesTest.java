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

import java.util.concurrent.atomic.AtomicInteger;

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

  public void testFormattingRoutingFollowsEachAppliedFeatureFlagTransition() {
    final DartLspServerDescriptor descriptor = new DartLspServerDescriptor(getProject());
    final var file = myFixture.configureByText("formatted.dart", "void main() {}").getVirtualFile();
    final AtomicInteger restartCount = new AtomicInteger();
    final boolean[] persistedValue = {false};

    assertFalse(DartLspFeatureToggleLifecycle.applyIfChanged(false, false, value -> persistedValue[0] = value,
                                                              restartCount::incrementAndGet));
    assertFalse(persistedValue[0]);
    assertEquals(0, restartCount.get());

    for (boolean enabled : new boolean[]{true, false, true}) {
      assertTrue(DartLspFeatureToggleLifecycle.applyIfChanged(!enabled, enabled, value -> persistedValue[0] = value,
                                                               restartCount::incrementAndGet));
      assertEquals(enabled, persistedValue[0]);
      PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", enabled, true);
      assertEquals(enabled,
                   descriptor.getLspCustomization().getFormattingCustomizer() instanceof LspFormattingSupport);
      if (enabled) {
        assertTrue(((LspFormattingSupport)descriptor.getLspCustomization().getFormattingCustomizer())
                     .shouldFormatThisFileExclusivelyByServer(file, true, false));
      }
    }

    assertEquals(3, restartCount.get());
  }

  public void testLspMethodsExperimentalStatus() {
    assertFalse(LspMethod.DIAGNOSTIC_SERVER.isExperimental());
    assertFalse(LspMethod.HOVER.isExperimental());
    assertFalse(LspMethod.TYPE_DEFINITION.isExperimental());
    assertTrue(LspMethod.DEFINITION.isExperimental());
    assertTrue(LspMethod.DOCUMENT_HIGHLIGHT.isExperimental());

    assertFalse(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.DIAGNOSTIC_SERVER));
    assertFalse(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.HOVER));
  }

  public void testFormattingMethodsAreExperimentalAndDescribed() {
    assertTrue(LspMethod.FORMATTING.isExperimental());
    assertEquals("formatting", LspMethod.FORMATTING.getPresentableName());
    assertTrue(LspMethod.RANGE_FORMATTING.isExperimental());
    assertEquals("range formatting", LspMethod.RANGE_FORMATTING.getPresentableName());

    assertTrue(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.FORMATTING));
    assertTrue(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.RANGE_FORMATTING));
  }

  public void testFormattingRoutingIsExclusiveOnlyWhileExperimentalLspIsEnabled() {
    final DartLspServerDescriptor descriptor = new DartLspServerDescriptor(getProject());
    final var file = myFixture.configureByText("formatted.dart", "void main() {}").getVirtualFile();

    final var enabledCustomizer = descriptor.getLspCustomization().getFormattingCustomizer();
    assertTrue(enabledCustomizer instanceof LspFormattingSupport);
    assertTrue(((LspFormattingSupport)enabledCustomizer).shouldFormatThisFileExclusivelyByServer(file, true, false));

    PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", false, true);
    assertSame(LspFormattingDisabled.INSTANCE, descriptor.getLspCustomization().getFormattingCustomizer());
  }

  public void testOnlyLspOwnedEditorFormattingYieldsToTheServer() {
    final var file = myFixture.configureByText("formatted.dart", "void main() {}").getVirtualFile();

    assertTrue(DartLspFormattingRouting.isLspOwnedEditorFormatting(getProject(), file));

    PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", false, true);
    assertFalse(DartLspFormattingRouting.isLspOwnedEditorFormatting(getProject(), file));

    PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", true, true);
    assertFalse(DartLspFormattingRouting.isLspOwnedEditorFormatting(getProject(), new LightVirtualFile("injected.dart", "")));
  }

  public void testDisabledFormattingRoutingDeclinesLocalDartBeforeAnyBridgeRequest() {
    final var file = myFixture.configureByText("legacy.dart", "void main(){print('legacy');}").getVirtualFile();

    PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", false, true);

    assertFalse(DartLspFormattingRouting.isLspOwnedEditorFormatting(getProject(), file));
    assertSame(LspFormattingDisabled.INSTANCE, new DartLspServerDescriptor(getProject()).getLspCustomization().getFormattingCustomizer());
  }

  public void testFormattingRoutingAcceptsOnlyLocalDartFiles() {
    final DartLspServerDescriptor descriptor = new DartLspServerDescriptor(getProject());
    final var localDartFile = myFixture.configureByText("formatted.DART", "void main() {}").getVirtualFile();

    assertTrue(DartLspFormattingRouting.isFormattingEligible(localDartFile));

    final var customizer = (LspFormattingSupport)descriptor.getLspCustomization().getFormattingCustomizer();
    assertTrue(customizer.shouldFormatThisFileExclusivelyByServer(localDartFile, true, false));
    for (String fileName : new String[]{"pubspec.yaml", "page.html", "AndroidManifest.xml", "README.md"}) {
      final var file = myFixture.configureByText(fileName, "content").getVirtualFile();
      assertFalse(fileName, DartLspFormattingRouting.isFormattingEligible(file));
      assertFalse(fileName, customizer.shouldFormatThisFileExclusivelyByServer(file, true, true));
    }

    final var nonLocalDartFile = new LightVirtualFile("injected.dart", "void main() {}");
    assertFalse(DartLspFormattingRouting.isFormattingEligible(nonLocalDartFile));
    assertFalse(customizer.shouldFormatThisFileExclusivelyByServer(nonLocalDartFile, true, true));
  }

  public void testFormattingRejectsDocumentationLikeFilesUnderBothSettings() {
    final DartLspServerDescriptor descriptor = new DartLspServerDescriptor(getProject());
    final String[] rejectedFileNames = {"requirements.txt", "CMakeLists.txt", "example.mdx", "README.sh"};

    for (boolean lspEnabled : new boolean[]{false, true}) {
      PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", lspEnabled, true);
      for (String fileName : rejectedFileNames) {
        assertFalse(fileName + " must not be admitted when LSP is " + lspEnabled,
                    descriptor.isSupportedFile(myFixture.configureByText(fileName, "content").getVirtualFile()));
      }
    }
  }
}
