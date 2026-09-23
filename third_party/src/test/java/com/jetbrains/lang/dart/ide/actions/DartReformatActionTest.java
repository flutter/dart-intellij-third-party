// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.ide.actions;

import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.service.AbstractDocumentFormattingService;
import com.intellij.formatting.service.FormattingService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.util.DartTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DartReformatActionTest extends DartCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(getModule(), myFixture.getProjectDisposable(), false);
    myFixture.configureByText("format.dart", "void main(){print('hello');}");
  }

  public void testLspProjectPopupHiddenEvenWithEditorData() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    VirtualFile file = myFixture.getFile().getVirtualFile();
    VirtualFile other = myFixture.addFileToProject("other.dart", "void f(){}").getVirtualFile();
    for (VirtualFile[] files : new VirtualFile[][]{{file}, {file, other}, {other.getParent()}}) {
      AnActionEvent event = event(ActionPlaces.PROJECT_VIEW_POPUP, true, files);
      action().update(event);
      assertFalse(event.getPresentation().isVisible());
      assertFalse(event.getPresentation().isEnabled());
    }
  }

  public void testLspEditorNameAndUnavailableServer() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    AnActionEvent event = event(ActionPlaces.EDITOR_POPUP, true);
    action().update(event);
    assertTrue(event.getPresentation().isVisible());
    assertEquals(ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_REFORMAT).getTemplatePresentation().getText(),
                 event.getPresentation().getText());
    assertFalse("No running LSP server", event.getPresentation().isEnabled());
  }

  public void testDescriptionFollowsLspToggleOnReusedPresentation() {
    AnAction action = action();
    AnActionEvent event = event(ActionPlaces.EDITOR_POPUP, true);
    String legacyDescription = action.getTemplatePresentation().getDescription();
    String lspDescription = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_REFORMAT)
      .getTemplatePresentation().getDescription();
    assertNotNull(legacyDescription);
    assertNotNull(lspDescription);
    assertFalse(legacyDescription.equals(lspDescription));

    for (boolean enabled : new boolean[]{false, true, false}) {
      DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), enabled);
      action.update(event);
      assertEquals(enabled ? lspDescription : legacyDescription, event.getPresentation().getDescription());
    }
  }

  public void testLspMainMenuDoesNotDuplicateStandardReformat() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    AnActionEvent event = event(ActionPlaces.MAIN_MENU, true);
    action().update(event);
    assertFalse(event.getPresentation().isVisible());
  }

  public void testLegacyEditorAndProjectPopupRemainAvailable() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), false);
    for (String place : new String[]{ActionPlaces.EDITOR_POPUP, ActionPlaces.PROJECT_VIEW_POPUP}) {
      AnActionEvent event = event(place, ActionPlaces.EDITOR_POPUP.equals(place), myFixture.getFile().getVirtualFile());
      action().update(event);
      assertTrue(event.getPresentation().isVisible());
      assertTrue(event.getPresentation().isEnabled());
    }
  }

  public void testLspDispatchesSelectionAndDocumentToFormattingService() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    RecordingFormatter formatter = new RecordingFormatter();
    DartReformatAction action = actionWithFormatter(formatter);
    AnActionEvent event = event(ActionPlaces.EDITOR_POPUP, true);
    action.update(event);
    assertTrue(event.getPresentation().isEnabled());

    myFixture.getEditor().getSelectionModel().setSelection(12, 26);
    action.actionPerformed(event);
    assertEquals(List.of(new TextRange(12, 26)), formatter.ranges);
    formatter.ranges.clear();
    myFixture.getEditor().getSelectionModel().removeSelection();
    action.actionPerformed(event);
    TextRange wholeDocument = TextRange.from(0, myFixture.getEditor().getDocument().getTextLength());
    assertEquals(List.of(wholeDocument), formatter.ranges);

    formatter.ranges.clear();
    myFixture.getEditor().getSelectionModel().setSelection(wholeDocument.getStartOffset(), wholeDocument.getEndOffset());
    assertTrue(myFixture.getEditor().getSelectionModel().hasSelection());
    action.actionPerformed(event);
    // Select All intentionally passes the same bounds as no selection. The platform LSP service
    // classifies these full-document bounds as document formatting, not range formatting.
    assertEquals(List.of(wholeDocument), formatter.ranges);
  }

  public void testDirectProjectInvocationCannotReachAvailableLspFormatter() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    RecordingFormatter formatter = new RecordingFormatter();
    actionWithFormatter(formatter).actionPerformed(event(ActionPlaces.PROJECT_VIEW_POPUP, true,
                                                        myFixture.getFile().getVirtualFile()));
    assertTrue(formatter.ranges.isEmpty());
  }

  private DartReformatAction actionWithFormatter(FormattingService formatter) {
    return new DartReformatAction() {
      @Override FormattingService getLspFormattingService() { return formatter; }
    };
  }

  /** Records the action/service boundary; actual LSP method forwarding is covered by bridge tests. */
  private static class RecordingFormatter extends AbstractDocumentFormattingService {
    private final List<TextRange> ranges = new ArrayList<>();
    @Override public Set<Feature> getFeatures() { return Set.of(Feature.FORMAT_FRAGMENTS); }
    @Override public boolean canFormat(PsiFile file) { return true; }
    @Override public void formatDocument(Document document, List<TextRange> ranges, FormattingContext context,
                                         boolean canChangeWhiteSpaceOnly, boolean quickFormat) {
      this.ranges.addAll(ranges);
    }
  }

  private AnAction action() {
    return ActionManager.getInstance().getAction("Dart.DartStyle");
  }

  private AnActionEvent event(String place, boolean editor, VirtualFile... files) {
    var context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files);
    if (editor) context.add(CommonDataKeys.EDITOR, myFixture.getEditor());
    return AnActionEvent.createEvent(action(), context.build(), null, place, ActionUiKind.NONE, null);
  }
}
