package com.jetbrains.dart.analysisServer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.refactoring.moveFile.MoveFileRefactoring;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.SourceChange;

public class DartLspMoveFileRefactoringTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get("dart.use.lsp.client").setValue(true, myFixture.getTestRootDisposable());
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    DartAnalysisServerService.getInstance(getProject()).serverReadyForRequest();
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
  }

  public void testFileMove() {
    final PsiFile fooFile = myFixture.addFileToProject("web/src/foo.dart", "import \"bar.dart\";");
    myFixture.addFileToProject("web/src/bar.dart", "");
    final MoveFileRefactoring refactoring = createRefactoring(fooFile, "web/foo.dart");
    applyRefactoring(refactoring);
    myFixture.openFileInEditor(fooFile.getVirtualFile());
    myFixture.checkResult("import \"src/bar.dart\";");
  }

  public void testTargetFileMove() {
    final PsiFile fooFile = myFixture.addFileToProject("web/src/foo.dart", "import \"bar.dart\";");
    final PsiFile barFile = myFixture.addFileToProject("web/src/bar.dart", "");
    myFixture.openFileInEditor(fooFile.getVirtualFile());
    myFixture.doHighlighting();
    final MoveFileRefactoring refactoring = createRefactoring(barFile, "web/bar.dart");
    applyRefactoring(refactoring);
    myFixture.openFileInEditor(fooFile.getVirtualFile());
    myFixture.checkResult("import \"../bar.dart\";");
  }

  private MoveFileRefactoring createRefactoring(PsiFile file, String newFilePath) {
    final String currentPath = file.getVirtualFile().getPath();
    final int webRootIndex = currentPath.indexOf("/web/");
    assertTrue("Expected test file to live under a web/ root: " + currentPath, webRootIndex >= 0);
    final String testContentRoot = currentPath.substring(0, webRootIndex);
    return new MoveFileRefactoring(getProject(), file.getVirtualFile(), testContentRoot + "/" + newFilePath);
  }

  private void applyRefactoring(MoveFileRefactoring refactoring) {
    final RefactoringStatus initialConditions = refactoring.checkInitialConditions();
    assertNotNull(initialConditions);
    assertTrue(initialConditions.isOK());

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
  }
}
