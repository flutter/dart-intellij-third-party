// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.dart.analysisServer;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.completion.DartServerStatementCompletionProcessor;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DartServerStatementCompletionTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    DartAnalysisServerService.getInstance(getProject()).serverReadyForRequest();
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
  }

  public void doTest(@NotNull final String before, @NotNull final String after) {
    myFixture.configureByText("complete.dart", before);
    // Apparently, highlighting causes the editor and analysis server to be in sync with the file system.
    myFixture.doHighlighting(); // Basically, this is magic to de-flake tests.
    List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add,
                                 () -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT));
    myFixture.checkResult(after);
    assertCompletionEvent(events, "application_completed");
  }

  private void assertCompletionEvent(List<AnalyticsData> events, String outcome) {
    assertEquals(1, events.size());
    AnalyticsData event = events.getFirst();
    assertSame(getProject(), event.getProject());
    assertEquals(Set.of("id", "type", "place", "outcome", "duration_ms"), event.getData().keySet());
    assertEquals("dart.completeStatement", event.getData().get("id"));
    assertEquals("action", event.getData().get("type"));
    assertEquals(ActionPlaces.UNKNOWN, event.getData().get("place"));
    assertEquals(outcome, event.getData().get("outcome"));
    Object duration = event.getData().get("duration_ms");
    assertInstanceOf(duration, Integer.class);
    assertTrue((Integer)duration >= 0);
  }

  public void testNonLocalFileHasNoResult() {
    myFixture.configureFromExistingVirtualFile(new LightVirtualFile("complete.dart", DartFileType.INSTANCE, "main() {}"));
    myFixture.getEditor().getCaretModel().moveToOffset(8);
    assertFalse(myFixture.getFile().getVirtualFile().isInLocalFileSystem());
    List<AnalyticsData> events = new ArrayList<>();
    // Invoke the processor directly: the editor action may perform fallback edits when it returns false.
    Analytics.withReportObserver(events::add, () -> assertFalse(new DartServerStatementCompletionProcessor()
      .process(getProject(), myFixture.getEditor(), myFixture.getFile())));
    myFixture.checkResult("main() {<caret>}");
    assertCompletionEvent(events, "no_result");
  }

  public void testIfBlock() {
    doTest(
      """
        main() {
          if (true)<caret>
        }""",
      """
        main() {
          if (true) {
            <caret>
          }
        }"""
    );
  }

  public void testWhileBlock() {
    doTest(
      """
        main() {
          while (true)<caret>
        }""",
      """
        main() {
          while (true) {
            <caret>
          }
        }"""
    );
  }
}
