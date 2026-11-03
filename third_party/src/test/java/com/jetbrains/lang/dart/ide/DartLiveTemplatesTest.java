package com.jetbrains.lang.dart.ide;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.LiveTemplateContextService;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.ide.template.DartTemplateContextType;
import com.jetbrains.lang.dart.util.DartTestUtils;

import java.lang.reflect.Constructor;

/**
 * Test the Dart live templates.
 * <p>
 * This class tests the Dart live templates, which are used to insert common code snippets into the editor.
 */
public class DartLiveTemplatesTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return DartTestUtils.BASE_TEST_DATA_PATH + getBasePath();
  }

  @Override
  protected String getBasePath() {
    return FileUtil.toSystemDependentName("/liveTemplates/");
  }

  // Added setUp to make sure the files that the test needs are available during tests
  //
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(getModule(), myFixture.getProjectDisposable(), false);
  }
  public void expandTemplate(final Editor editor) {
    final Project project = editor.getProject();
    assertNotNull(project);
    new ListTemplatesAction().actionPerformedImpl(project, editor);
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    assertNotNull(lookup);
    lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    TemplateState template = TemplateManagerImpl.getTemplateState(editor);
    if (template != null) {
      Disposer.dispose(template);
    }
  }

  private void doTest() {
    doTest(getTestName(false) + ".dart");
  }

  private void doTest(String... files) {
    myFixture.configureByFiles(files);
    expandTemplate(myFixture.getEditor());
    WriteCommandAction.runWriteCommandAction(null, () -> {
      CodeStyleManager.getInstance(myFixture.getProject()).reformat(myFixture.getFile());
    });
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResultByFile(getTestName(false) + ".after.dart");
  }

  public void testItar1() {
    doTest();
  }

  public void testItar2() {
    doTest();
  }

  public void testIter() {
    doTest();
  }

  public void testSout() {
    doTest();
  }

  public void testSoutm() {
    doTest();
  }

  public void testSoutm2() {
    doTest();
  }

  public void testDartTemplateContextTypesRegisteredDeclaratively() {
    final Constructor<?>[] constructors = DartTemplateContextType.class.getDeclaredConstructors();
    assertEquals(1, constructors.length);
    assertEquals(1, constructors[0].getParameterCount());
    assertEquals(String.class, constructors[0].getParameterTypes()[0]);

    final LiveTemplateContextService service = LiveTemplateContextService.getInstance();
    final TemplateContextType dartContext = service.getTemplateContextType("DART");
    final TemplateContextType statementContext = service.getTemplateContextType("DART_STATEMENT");
    assertInstanceOf(dartContext, DartTemplateContextType.Generic.class);
    assertInstanceOf(statementContext, DartTemplateContextType.Statement.class);
    assertSame(dartContext, statementContext.getBaseContextType());

    myFixture.configureByText("in_block.dart", "void main() {\n  <caret>\n}\n");
    final TemplateActionContext blockActionContext = TemplateActionContext.expanding(myFixture.getFile(), myFixture.getEditor());
    assertTrue(dartContext.isInContext(blockActionContext));
    assertTrue(statementContext.isInContext(blockActionContext));

    myFixture.configureByText("top_level.dart", "<caret>\nvoid main() {}\n");
    final TemplateActionContext topLevelActionContext = TemplateActionContext.expanding(myFixture.getFile(), myFixture.getEditor());
    assertTrue(dartContext.isInContext(topLevelActionContext));
    assertFalse(statementContext.isInContext(topLevelActionContext));
  }
}
