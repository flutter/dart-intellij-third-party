package com.jetbrains.dart.analysisServer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.refactoring.ServerInlineLocalRefactoring;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.SourceChange;
import org.jetbrains.annotations.NotNull;

public class DartLspInlineLocalRefactoringTest extends CodeInsightFixtureTestCase {
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
    return "/analysisServer/refactoring/inline/local";
  }

  public void testTest() {
    doTest(getTestName(false) + ".dart");
  }

  @NotNull
  private ServerInlineLocalRefactoring createInlineLocalRefactoring(String filePath) {
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
    PsiFile psiFile = myFixture.configureByFile(filePath);
    myFixture.doHighlighting();
    int offset = getEditor().getCaretModel().getOffset();
    return new ServerInlineLocalRefactoring(getProject(), psiFile.getVirtualFile(), offset, 0);
  }

  private void doTest(String filePath) {
    ServerInlineLocalRefactoring refactoring = createInlineLocalRefactoring(filePath);

    RefactoringStatus initialConditions = refactoring.checkInitialConditions();
    assertNotNull(initialConditions);
    assertTrue(initialConditions.isOK());

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
