// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.dart.analysisServer;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.util.DartTestUtils;

public class DartLspServerQuickFixTest extends CodeInsightFixtureTestCase {
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
    return "/analysisServer/quickfix";
  }

  private void doQuickFixTest(String intentionStartText) {
    myFixture.configureByFile(getTestName(false) + ".dart");

    IntentionAction quickFix = myFixture.findSingleIntention(intentionStartText);
    assertTrue(quickFix.isAvailable(getProject(), getEditor(), getFile()));

    ApplicationManager.getApplication().runWriteAction(() -> quickFix.invoke(getProject(), getEditor(), getFile()));

    myFixture.checkResultByFile(getTestName(false) + ".after.dart");
  }

  public void testCreateClass() {
    doQuickFixTest("Create class 'A'");
  }

  public void testUseEqEqNull() {
    doQuickFixTest("Use == null instead of 'is Null'");
  }

  public void testNoQuickFixes() {
    GlobalInspectionTool tool = new HighlightVisitorBasedInspection().setRunAnnotators(true);
    myFixture.enableInspections(tool);
    assertNotNull(HighlightDisplayKey.find(tool.getShortName()));
    myFixture.configureByText("foo.dart", "main(){ print(<caret>); }");
    assertEmpty(myFixture.getAvailableIntentions());
  }
}
