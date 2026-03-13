package com.jetbrains.dart.analysisServer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.refactoring.ServerInlineMethodRefactoring;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.SourceChange;
import org.jetbrains.annotations.NotNull;

public class DartLspInlineMethodRefactoringTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get("dart.use.lsp.client").setValue(true, myFixture.getTestRootDisposable());
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    DartAnalysisServerService.getInstance(getProject()).serverReadyForRequest();
    myFixture.setTestDataPath(DartTestUtils.BASE_TEST_DATA_PATH + getBasePath());
  }

  @Override
  protected String getBasePath() {
    return "/analysisServer/refactoring/inline/method";
  }

  public void testFunctionSingle() {
    doTest(getTestName(false) + ".dart", false);
  }

  public void testFunctionAll() {
    doTest(getTestName(false) + ".dart", true);
  }

  @NotNull
  private ServerInlineMethodRefactoring createRefactoring(String filePath) {
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
    PsiFile psiFile = myFixture.configureByFile(filePath);
    myFixture.doHighlighting();
    int offset = getEditor().getCaretModel().getOffset();
    return new ServerInlineMethodRefactoring(getProject(), psiFile.getVirtualFile(), offset, 0);
  }

  private void doTest(String filePath, boolean inlineAll) {
    ServerInlineMethodRefactoring refactoring = createRefactoring(filePath);

    RefactoringStatus initialConditions = refactoring.checkInitialConditions();
    assertNotNull(initialConditions);
    assertTrue(initialConditions.isOK());

    if (inlineAll) {
      refactoring.setInlineAll(true);
      refactoring.setDeleteSource(true);
    }

    RefactoringStatus finalConditions = refactoring.checkFinalConditions();
    assertNotNull(finalConditions);
    assertTrue(finalConditions.isOK());

    SourceChange change = refactoring.getChange();
    assertNotNull(change);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        AssistUtils.applySourceChange(myFixture.getProject(), change, false);
      }
      catch (DartSourceEditException e) {
        fail(e.getMessage());
      }
    });

    myFixture.checkResultByFile(getTestName(false) + ".after.dart");
  }
}
