package com.jetbrains.dart.analysisServer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.refactoring.ServerRenameRefactoring;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.SourceChange;
import org.jetbrains.annotations.NotNull;

public class DartLspRenameRefactoringTest extends CodeInsightFixtureTestCase {
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
    return "/analysisServer/refactoring/rename";
  }

  public void testCheckFinalConditionsNameFatalError() {
    final ServerRenameRefactoring refactoring = createRenameRefactoring();
    final RefactoringStatus initialConditions = refactoring.checkInitialConditions();
    assertNotNull(initialConditions);
    assertTrue(initialConditions.isOK());

    refactoring.setNewName("bad name");
    final RefactoringStatus finalConditions = refactoring.checkFinalConditions();
    assertNotNull(finalConditions);
    assertTrue(finalConditions.hasFatalError());
  }

  public void testCheckInitialConditionsCannotCreate() {
    final ServerRenameRefactoring refactoring = createRenameRefactoring();
    final RefactoringStatus initialConditions = refactoring.checkInitialConditions();
    assertNotNull(initialConditions);
    assertTrue(initialConditions.hasFatalError());
  }

  public void testClass() {
    doTest("NewName");
  }

  public void testConstructorDefaultToNamed() {
    doTest("newName");
  }

  public void testIgnorePotential() {
    doTest("newName");
  }

  public void testMethod() {
    doTest("newName");
  }

  public void testTypeAndImmediatelyRenameLocalVar() {
    myFixture.configureByFile(getTestName(false) + ".dart");
    myFixture.doHighlighting();
    myFixture.type('\n');
    final int offset = getEditor().getCaretModel().getOffset();
    final ServerRenameRefactoring refactoring = new ServerRenameRefactoring(getProject(), getFile().getVirtualFile(), offset, 0);
    doTest(refactoring, "newName");
  }

  private void doTest(@NotNull final String newName) {
    final ServerRenameRefactoring refactoring = createRenameRefactoring();
    doTest(refactoring, newName);
  }

  private void doTest(@NotNull final ServerRenameRefactoring refactoring, @NotNull final String newName) {
    final RefactoringStatus initialConditions = refactoring.checkInitialConditions();
    assertNotNull(initialConditions);
    assertTrue(initialConditions.isOK());

    refactoring.setNewName(newName);
    final RefactoringStatus finalConditions = refactoring.checkFinalConditions();
    assertNotNull(finalConditions);
    assertTrue(finalConditions.isOK());

    final SourceChange change = refactoring.getChange();
    assertNotNull(change);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        AssistUtils.applySourceChange(myFixture.getProject(), change, false, refactoring.getPotentialEdits());
      }
      catch (DartSourceEditException e) {
        fail(e.getMessage());
      }
    });

    myFixture.checkResultByFile(getTestName(false) + ".after.dart");
  }

  @NotNull
  private ServerRenameRefactoring createRenameRefactoring() {
    myFixture.configureByFile(getTestName(false) + ".dart");
    myFixture.doHighlighting();
    final int offset = getEditor().getCaretModel().getOffset();
    return new ServerRenameRefactoring(getProject(), getFile().getVirtualFile(), offset, 0);
  }
}
