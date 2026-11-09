// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.dartlang.analysis.server.protocol.CompletionSuggestion;
import org.dartlang.analysis.server.protocol.CompletionSuggestionKind;
import org.dartlang.analysis.server.protocol.Element;
import org.dartlang.analysis.server.protocol.ElementKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("deprecation")
public class DartServerCompletionContributorTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DartServerCompletionContributor.DISABLED_EXTENSIONS.clear();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      DartServerCompletionContributor.DISABLED_EXTENSIONS.clear();
    }
    finally {
      super.tearDown();
    }
  }

  public void testTypedefBaseImage() {
    Element typeAlias = new Element(ElementKind.TYPE_ALIAS, "MyTypeAlias", null, 0, null, null, null, null);
    assertEquals(AllIcons.Nodes.Annotationtype, DartServerCompletionContributor.getBaseImage(typeAlias));

    Element functionTypeAlias = new Element(ElementKind.FUNCTION_TYPE_ALIAS, "MyFunctionTypeAlias", null, 0, null, null, null, null);
    assertEquals(AllIcons.Nodes.Annotationtype, DartServerCompletionContributor.getBaseImage(functionTypeAlias));
  }

  public void testCreateLookupElementAskingExtensionsIsolatesAndSkipsFailingExtension() {
    AtomicInteger invocations = new AtomicInteger();
    ExtensionPoint<DartCompletionExtension> ep =
      ApplicationManager.getApplication().getExtensionArea().getExtensionPoint("Dart.completionExtension");
    ep.registerExtension(new DartCompletionExtension() {
      @Override
      public @Nullable LookupElementBuilder createLookupElement(@NotNull Project project, @NotNull CompletionSuggestion suggestion) {
        invocations.incrementAndGet();
        throw new NoClassDefFoundError("org/apache/commons/lang/StringUtils");
      }
    }, getTestRootDisposable());

    CompletionSuggestion suggestion = createSuggestion("myFunction");
    LookupElement firstLookup =
      DartServerCompletionContributor.createLookupElementAskingExtensions(getProject(), suggestion, null, null);
    assertEquals("myFunction", firstLookup.getLookupString());
    assertEquals(1, invocations.get());

    LookupElement secondLookup =
      DartServerCompletionContributor.createLookupElementAskingExtensions(getProject(), suggestion, null, null);
    assertEquals("myFunction", secondLookup.getLookupString());
    assertEquals(1, invocations.get());
  }

  public void testCreateLookupElementAskingExtensionsContinuesToNextExtensionAfterThrowable() {
    ExtensionPoint<DartCompletionExtension> ep =
      ApplicationManager.getApplication().getExtensionArea().getExtensionPoint("Dart.completionExtension");
    ep.registerExtension(new DartCompletionExtension() {
      @Override
      public @Nullable LookupElementBuilder createLookupElement(@NotNull Project project, @NotNull CompletionSuggestion suggestion) {
        throw new NoClassDefFoundError("org/apache/commons/lang/StringUtils");
      }
    }, getTestRootDisposable());
    ep.registerExtension(new DartCompletionExtension() {
      @Override
      public @Nullable LookupElementBuilder createLookupElement(@NotNull Project project, @NotNull CompletionSuggestion suggestion) {
        return LookupElementBuilder.create("customFromSecondExtension");
      }
    }, getTestRootDisposable());

    CompletionSuggestion suggestion = createSuggestion("myFunction");
    LookupElement lookup =
      DartServerCompletionContributor.createLookupElementAskingExtensions(getProject(), suggestion, null, null);
    assertEquals("customFromSecondExtension", lookup.getLookupString());
  }

  public void testCreateLookupElementAskingExtensionsRethrowsProcessCanceledException() {
    ExtensionPoint<DartCompletionExtension> ep =
      ApplicationManager.getApplication().getExtensionArea().getExtensionPoint("Dart.completionExtension");
    ep.registerExtension(new DartCompletionExtension() {
      @Override
      public @Nullable LookupElementBuilder createLookupElement(@NotNull Project project, @NotNull CompletionSuggestion suggestion) {
        throw new ProcessCanceledException();
      }
    }, getTestRootDisposable());

    CompletionSuggestion suggestion = createSuggestion("myFunction");
    assertThrows(
      ProcessCanceledException.class,
      () -> DartServerCompletionContributor.createLookupElementAskingExtensions(getProject(), suggestion, null, null)
    );
  }

  private static @NotNull CompletionSuggestion createSuggestion(@NotNull String completion) {
    return new CompletionSuggestion(
      CompletionSuggestionKind.INVOCATION,
      100,
      completion,
      null,
      completion.length(),
      0,
      0,
      0,
      false,
      false,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null
    );
  }
}
