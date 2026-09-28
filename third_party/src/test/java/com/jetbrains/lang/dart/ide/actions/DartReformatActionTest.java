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

  public void testLspDelegatesToStandardReformatPreservingSelection() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    RecordingFormatter formatter = new RecordingFormatter();
    RecordingReformatAction action = new RecordingReformatAction(formatter);
    AnActionEvent event = event(ActionPlaces.EDITOR_POPUP, true);
    action.update(event);
    assertTrue(event.getPresentation().isEnabled());

    myFixture.getEditor().getSelectionModel().setSelection(12, 26);
    action.actionPerformed(event);
    assertSame(event, action.lastEvent);
    assertEquals(List.of(new TextRange(12, 26)), action.selections);

    myFixture.getEditor().getSelectionModel().removeSelection();
    action.actionPerformed(event);
    assertEquals(2, action.selections.size());
    assertNull("No selection must remain distinct from Select All", action.selections.get(1));

    TextRange wholeDocument = TextRange.from(0, myFixture.getEditor().getDocument().getTextLength());
    myFixture.getEditor().getSelectionModel().setSelection(wholeDocument.getStartOffset(), wholeDocument.getEndOffset());
    action.actionPerformed(event);
    assertEquals(3, action.selections.size());
    assertEquals(wholeDocument, action.selections.get(2));
  }

  public void testDirectProjectInvocationCannotReachAvailableLspFormatter() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    RecordingFormatter formatter = new RecordingFormatter();
    RecordingReformatAction action = new RecordingReformatAction(formatter);
    action.actionPerformed(event(ActionPlaces.PROJECT_VIEW_POPUP, true, myFixture.getFile().getVirtualFile()));
    assertTrue(action.selections.isEmpty());
    assertNull(action.lastEvent);
  }

  private static class RecordingReformatAction extends DartReformatAction {
    private final FormattingService formatter;
    private final List<TextRange> selections = new ArrayList<>();
    private AnActionEvent lastEvent;

    private RecordingReformatAction(FormattingService formatter) {
      this.formatter = formatter;
    }

    @Override FormattingService getLspFormattingService() { return formatter; }

    @Override void performStandardReformat(AnActionEvent event) {
      lastEvent = event;
      var editor = event.getData(CommonDataKeys.EDITOR);
      assertNotNull(editor);
      var selection = editor.getSelectionModel();
      selections.add(selection.hasSelection() ? new TextRange(selection.getSelectionStart(), selection.getSelectionEnd()) : null);
    }
  }

  /** Makes LSP formatting available without allowing the action to bypass standard reformat. */
  private static class RecordingFormatter extends AbstractDocumentFormattingService {
    @Override public Set<Feature> getFeatures() { return Set.of(Feature.FORMAT_FRAGMENTS); }
    @Override public boolean canFormat(PsiFile file) { return true; }
    @Override public void formatDocument(Document document, List<TextRange> ranges, FormattingContext context,
                                         boolean canChangeWhiteSpaceOnly, boolean quickFormat) {
      junit.framework.Assert.fail("The Dart action must delegate to standard reformat, not invoke the formatting service directly");
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
