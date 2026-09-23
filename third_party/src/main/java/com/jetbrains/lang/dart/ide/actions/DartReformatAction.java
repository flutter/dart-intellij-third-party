// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.ide.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.formatting.FormatTextRanges;
import com.intellij.formatting.service.FormattingService;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.platform.dartlsp.impl.features.formatter.LspFormattingService;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.lsp.DartLspFormattingRouting;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Selects independent LSP and legacy implementations for the existing Dart action ID. */
public class DartReformatAction extends AnAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      event.getPresentation().setEnabledAndVisible(false);
      return;
    }
    if (!DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
      event.getPresentation().setDescription(DartBundle.message("action.Dart.DartStyle.description"));
      new DartStyleAction().update(event);
      return;
    }
    // CodeFormatGroup already contains the standard Reformat Code action.
    PsiFile file = ActionPlaces.MAIN_MENU.equals(event.getPlace()) ? null : getLspEditorFile(event);
    event.getPresentation().setText(reformatText());
    event.getPresentation().setDescription(ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_REFORMAT)
                                           .getTemplatePresentation().getDescription());
    event.getPresentation().setVisible(file != null);
    FormattingService service = file == null ? null : getLspFormattingService();
    event.getPresentation().setEnabled(service != null && service.canFormat(file));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) return;
    if (!DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
      new DartStyleAction().actionPerformed(event);
      return;
    }
    PsiFile file = getLspEditorFile(event);
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (file == null || editor == null) return; // Also guards project popups carrying an editor.
    Analytics.report(AnalyticsData.forAction(this, event));
    formatWithLsp(project, editor, file);
  }

  private void formatWithLsp(Project project, Editor editor, PsiFile file) {
    FormattingService service = getLspFormattingService();
    if (service == null || !service.canFormat(file)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed() || editor.isDisposed() || !editor.getComponent().isShowing()) return;
        HintManager.getInstance().showErrorHint(editor, DartBundle.message("dart.lsp.formatting.unavailable"));
      }, ModalityState.nonModal(), project.getDisposed());
      return;
    }
    if (!ReadonlyStatusHandler.ensureDocumentWritable(project, editor.getDocument())) return;
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    var selection = editor.getSelectionModel();
    TextRange range = selection.hasSelection()
                      ? new TextRange(selection.getSelectionStart(), selection.getSelectionEnd())
                      : TextRange.from(0, editor.getDocument().getTextLength());
    // Invoke only the LSP service: generic formatter selection could fall back to IDE/legacy formatting.
    // Its async document pipeline owns applying edits and undo, just as for standard Reformat Code.
    WriteCommandAction.runWriteCommandAction(project, reformatText(), null,
      () -> service.formatRanges(file, new FormatTextRanges(range, true), false, false), file);
  }

  @Nullable FormattingService getLspFormattingService() {
    return FormattingServiceUtil.findService(LspFormattingService.class);
  }

  private static @Nullable PsiFile getLspEditorFile(AnActionEvent event) {
    Project project = event.getProject();
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (project == null || editor == null || ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace())) return null;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return file != null && file.getVirtualFile() != null &&
           DartLspFormattingRouting.isLspOwnedEditorFormatting(project, file.getVirtualFile()) ? file : null;
  }

  private static String reformatText() {
    return ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_REFORMAT).getTemplatePresentation().getText();
  }
}
