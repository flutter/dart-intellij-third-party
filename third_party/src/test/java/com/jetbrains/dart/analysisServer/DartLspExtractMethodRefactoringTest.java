package com.jetbrains.dart.analysisServer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.refactoring.ServerExtractMethodRefactoring;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.SourceChange;
import org.jetbrains.annotations.NotNull;

public class DartLspExtractMethodRefactoringTest extends CodeInsightFixtureTestCase {
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
    return "/analysisServer/refactoring/extract/method";
  }

  public void testMethodSingle() {
    doTest(getTestName(false) + ".dart");
  }

  @NotNull
  private ServerExtractMethodRefactoring createRefactoring(String filePath) {
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
    final PsiFile psiFile = myFixture.configureByFile(filePath);
    myFixture.doHighlighting();
    final SelectionModel selectionModel = getEditor().getSelectionModel();
    final int offset = selectionModel.getSelectionStart();
    final int length = selectionModel.getSelectionEnd() - offset;
    return new ServerExtractMethodRefactoring(getProject(), psiFile.getVirtualFile(), offset, length);
  }

  private void doTest(String filePath) {
    final ServerExtractMethodRefactoring refactoring = createRefactoring(filePath);
    final RefactoringStatus initialConditions = refactoring.checkInitialConditions();
    assertNotNull(initialConditions);
    assertTrue(initialConditions.isOK());

    refactoring.setName("test");

    final RefactoringStatus finalConditions = refactoring.checkFinalConditions();
    assertNotNull(finalConditions);
    assertTrue(finalConditions.isOK());

    final SourceChange change = refactoring.getChange();
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
