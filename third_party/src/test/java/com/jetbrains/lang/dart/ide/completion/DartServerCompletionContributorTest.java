// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import org.dartlang.analysis.server.protocol.CompletionSuggestion;
import org.dartlang.analysis.server.protocol.CompletionSuggestionKind;
import org.dartlang.analysis.server.protocol.Element;
import org.dartlang.analysis.server.protocol.ElementKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("deprecation")
public class DartServerCompletionContributorTest extends DartCodeInsightFixtureTestCase {

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

  public void testDoNotEatNewExpressionParenOnTabWithLspCompletion() throws Exception {
    doTabCompletionTestWithLspEnabled(
      """
        main() {
          var o;
          new DateTime.fromMicrosecondsSinceEpoch(o.<caret>);
        }""",
      "hashCode",
      """
        main() {
          var o;
          new DateTime.fromMicrosecondsSinceEpoch(o.hashCode<caret>);
        }"""
    );
  }

  public void testDoNotEatParenthesizedExpressionParenOnTabWithLspCompletion() throws Exception {
    doTabCompletionTestWithLspEnabled(
      """
        main() {
          var o;
          var x = (o.<caret>);
        }""",
      "hashCode",
      """
        main() {
          var o;
          var x = (o.hashCode<caret>);
        }"""
    );
  }

  public void testDoNotEatListBracketOnTabWithLspCompletion() throws Exception {
    doTabCompletionTestWithLspEnabled(
      """
        main() {
          return [
            Container(color: Colors.<caret>)
          ];
        }""",
      "red",
      """
        main() {
          return [
            Container(color: Colors.red<caret>)
          ];
        }"""
    );
  }

  public void testDoNotEatSetOrMapBraceOnTabWithLspCompletion() throws Exception {
    doTabCompletionTestWithLspEnabled(
      """
        main() {
          return {
            'a': Container(color: Colors.<caret>)
          };
        }""",
      "red",
      """
        main() {
          return {
            'a': Container(color: Colors.red<caret>)
          };
        }"""
    );
  }

  public void testDoNotEatClosureBraceOnTabWithLspCompletion() throws Exception {
    doTabCompletionTestWithLspEnabled(
      "Function get goo => (() {int.<caret>});",
      "parse",
      "Function get goo => (() {int.parse<caret>});"
    );
  }

  public void testDoNotEatArrayAccessBracketOnTabWithLspCompletion() throws Exception {
    doTabCompletionTestWithLspEnabled(
      """
        main() {
          var list = [1, 2];
          var o;
          var x = list[o.<caret>];
        }""",
      "hashCode",
      """
        main() {
          var list = [1, 2];
          var o;
          var x = list[o.hashCode<caret>];
        }"""
    );
  }

  public void testDoNotEatStringInterpolationBraceOnTabWithLspCompletion() throws Exception {
    doTabCompletionTestWithLspEnabled(
      """
        main() {
          var o;
          var s = 'Hello ${o.<caret>}';
        }""",
      "hashCode",
      """
        main() {
          var o;
          var s = 'Hello ${o.hashCode<caret>}';
        }"""
    );
  }

  public void testDoNotEatDotInIncompleteQualifiedReferenceOnTabWithLspCompletion() throws Exception {
    doTabCompletionTestWithLspEnabled(
      """
        main() {
          foo<caret>Baz.
        }""",
      "fooBar",
      """
        main() {
          fooBar<caret>.
        }"""
    );
  }

  public void testUriCompletionByTabWithLspCompletion() throws Exception {
    doTabCompletionTestWithLspEnabled(
      "import '<caret>old_uri.dart';",
      "package:foo/bar.dart",
      "import 'package:foo/bar.dart<caret>';"
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

  private void doTabCompletionTestWithLspEnabled(@NotNull String beforeText,
                                                 @NotNull String itemToSelect,
                                                 @NotNull String expectedAfterText) throws Exception {
    DartSdk sdk = DartSdk.getDartSdk(getProject());
    assertNotNull(sdk);
    Field versionField = DartSdk.class.getDeclaredField("myVersion");
    versionField.setAccessible(true);
    String originalVersion = sdk.getVersion();

    try {
      versionField.set(sdk, "3.15.0");
      DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
      assertTrue(DartAnalysisServerService.isLspCompletionEnabled(getProject()));

      CompletionContributorEP ep = new CompletionContributorEP(
        DartLanguage.INSTANCE.getID(),
        StubLookupCompletionContributor.class.getName(),
        new DefaultPluginDescriptor("test")
      );
      CompletionContributor.EP.getPoint().registerExtension(ep, getTestRootDisposable());

      myFixture.configureByText("test.dart", beforeText);
      myFixture.complete(CompletionType.BASIC);

      LookupEx activeLookup = LookupManager.getActiveLookup(myFixture.getEditor());
      assertNotNull("Expected active lookup popup", activeLookup);
      LookupElement target = ContainerUtil.find(activeLookup.getItems(), item -> itemToSelect.equals(item.getLookupString()));
      assertNotNull("Expected '" + itemToSelect + "' in lookup items", target);

      activeLookup.setCurrentItem(target);
      myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);
      myFixture.checkResult(expectedAfterText);
    }
    finally {
      DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), false);
      versionField.set(sdk, originalVersion);
    }
  }

  public static final class StubLookupCompletionContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
      result.addElement(LookupElementBuilder.create("hashCode"));
      result.addElement(LookupElementBuilder.create("runtimeType"));
      result.addElement(LookupElementBuilder.create("red"));
      result.addElement(LookupElementBuilder.create("parse"));
      result.addElement(LookupElementBuilder.create("fooBar"));
      result.addElement(LookupElementBuilder.create("fooOther"));
      result.addElement(LookupElementBuilder.create("package:foo/bar.dart"));
      result.addElement(LookupElementBuilder.create("package:foo/baz.dart"));
    }
  }
}
