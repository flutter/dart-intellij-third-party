// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.dart.analysisServer;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.SourceChange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DartLspServerIntentionsTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get("dart.use.lsp.client").setValue(true, myFixture.getTestRootDisposable());
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    DartAnalysisServerService.getInstance(getProject()).serverReadyForRequest();
    myFixture.setTestDataPath(DartTestUtils.BASE_TEST_DATA_PATH + getBasePath());
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
  }

  @Override
  protected String getBasePath() {
    return "/analysisServer/intentions";
  }

  private void doTest(@NotNull String intentionName) {
    myFixture.configureByFile(getTestName(false) + ".dart");
    IntentionAction intention = myFixture.findSingleIntention(intentionName);

    ApplicationManager.getApplication().runWriteAction(() -> intention.invoke(getProject(), getEditor(), getFile()));

    myFixture.checkResultByFile(getTestName(false) + ".after.dart");
  }

  public void testIntroduceVariableNoSelection() {
    doTest("Assign value to new local variable");
  }

  public void testSurroundWithTryCatch() {
    doTest("Surround with 'try-catch'");
  }

  public void testIntentionsOrder() {
    myFixture.configureByText("foo.dart", """
      void f() {
        <selection><caret>var x = 3;</selection>
      }
      """);
    final List<String> intentions = ContainerUtil.map(myFixture.getAvailableIntentions(), IntentionAction::getText);
    assertOrderedEquals(intentions,
                        "Surround with block",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'if'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'while'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'for-in'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'for'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'do-while'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'try-catch'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'try-finally'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Adjust code style settings",
                        "Assign shortcut…");
  }

  public void testIntentionPreview() {
    myFixture.configureByText("foo.dart", """
      main ( )  {
       Foo<caret>
         }""");
    IntentionAction action = myFixture.findSingleIntention("Assign value to new local variable");
    assertEquals("""
                   main ( )  {
                    var foo = Foo
                      }""", myFixture.getIntentionPreviewText(action));
  }

  public void testAssistRequestDoesNotReturnQuickFixes() {
    myFixture.configureByText("foo.dart", "<caret>ServerSocket f;\nclass ServerSockets {}");

    DartAnalysisServerService service = DartAnalysisServerService.getInstance(getProject());
    service.updateFilesContent();

    List<SourceChange> assists = service.edit_getAssists(getFile().getVirtualFile(), getEditor().getCaretModel().getOffset(), 0);
    assertEmpty(ContainerUtil.map(assists, SourceChange::getMessage));
  }
}
