package com.jetbrains.dart.analysisServer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.refactoring.ServerExtractLocalVariableRefactoring;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.SourceChange;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class DartLspExtractLocalVariableRefactoringTest extends CodeInsightFixtureTestCase {
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
    return "/analysisServer/refactoring/extract/localVariable";
  }

  public void testExpressionSingle() {
    doTest(getTestName(false) + ".dart", false);
  }

  @NotNull
  private ServerExtractLocalVariableRefactoring createRefactoring(String filePath) {
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
    PsiFile psiFile = myFixture.configureByFile(filePath);

    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        VfsUtil.saveText(psiFile.getVirtualFile(), StringUtil.convertLineSeparators(psiFile.getText(), "\r\n"));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    myFixture.doHighlighting();
    int offset = getEditor().getSelectionModel().getSelectionStart();
    int length = getEditor().getSelectionModel().getSelectionEnd() - offset;
    return new ServerExtractLocalVariableRefactoring(getProject(), psiFile.getVirtualFile(), offset, length);
  }

  private void doTest(String filePath, boolean extractAll) {
    ServerExtractLocalVariableRefactoring refactoring = createRefactoring(filePath);

    RefactoringStatus initialConditions = refactoring.checkInitialConditions();
    assertNotNull(initialConditions);
    assertTrue(initialConditions.isOK());

    refactoring.setExtractAll(extractAll);

    RefactoringStatus finalConditions = refactoring.checkFinalConditions();
    assertNotNull(finalConditions);
    assertTrue(finalConditions.isOK());

    SourceChange change = refactoring.getChange();
    assertNotNull(change);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        AssistUtils.applySourceChange(myFixture.getProject(), change, true);
      }
      catch (DartSourceEditException e) {
        fail(e.getMessage());
      }
    });
    myFixture.checkResultByFile(getTestName(false) + ".after.dart");
  }
}
