// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.lang.dart.logging.PluginLogger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import org.dartlang.analysis.server.protocol.SourceEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.isWhiteSpace;

public class DartStyleAction extends AbstractDartFileProcessingAction {
  private static final Logger LOG = PluginLogger.INSTANCE.createLogger(DartStyleAction.class);

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project != null && DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
      if (event.getData(CommonDataKeys.EDITOR) != null) {
        final AnAction reformatAction = getStandardReformatAction();
        if (reformatAction == null) {
          event.getPresentation().setEnabledAndVisible(false);
          return;
        }

        super.update(event);
        if (!event.getPresentation().isEnabled()) return;

        final AnActionEvent reformatEvent = createReformatEvent(reformatAction, event);
        ActionUtil.updateAction(reformatAction, reformatEvent);
        if (!reformatEvent.getPresentation().isEnabled()) {
          event.getPresentation().setEnabled(false);
        }
        return;
      }
    }
    super.update(event);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project != null && DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
      final DartSdk sdk = DartSdk.getDartSdk(project);
      if (sdk == null || !DartAnalysisServerService.isDartSdkVersionSufficient(sdk)) return;

      final Editor editor = event.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        performEditorRedirect(project, editor, event);
      }
      else {
        performFilesRedirect(project, event);
      }
      return;
    }
    super.actionPerformed(event);
  }

  private void performEditorRedirect(@NotNull Project project, @NotNull Editor editor, @NotNull AnActionEvent event) {
    final AnAction reformatAction = getStandardReformatAction();
    if (reformatAction == null) return;
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null || !isApplicableFile(project, psiFile.getVirtualFile())) return;

    // Redirect to standard Reformat Code (which routes through LSP formatting) while preserving
    // the Dart.DartStyle menu entry, keymap bindings, and action analytics.
    Analytics.report(AnalyticsData.forAction(this, event));
    ActionUtil.performAction(reformatAction, createReformatEvent(reformatAction, event));
  }

  private void performFilesRedirect(@NotNull Project project, @NotNull AnActionEvent event) {
    final VirtualFile[] filesAndDirs = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (filesAndDirs == null || filesAndDirs.length == 0) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final List<VirtualFile> dartFiles = ActionUtil.underModalProgress(
      project,
      DartBundle.message("action.Dart.DartStyle.progress.title"),
      () -> getApplicableVirtualFiles(project, filesAndDirs)
    );
    if (dartFiles.isEmpty()) return;

    Analytics.report(AnalyticsData.forAction(this, event));
    runOverFiles(project, dartFiles);
  }

  @VisibleForTesting
  @Nullable AnAction getStandardReformatAction() {
    return ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_REFORMAT);
  }

  private static @NotNull AnActionEvent createReformatEvent(@NotNull AnAction reformatAction, @NotNull AnActionEvent event) {
    return AnActionEvent.createEvent(
      reformatAction,
      event.getDataContext(),
      null,
      event.getPlace(),
      event.getUiKind(),
      event.getInputEvent()
    );
  }

  @Override
  protected @NotNull String getActionTextForEditor() {
    return DartBundle.message("action.Dart.DartStyle.text");
  }

  @Override
  protected @NotNull String getActionTextForFiles() {
    return DartBundle.message("dart.style.action.name.ellipsis"); // because with dialog
  }

  @Override
  protected void runOverEditor(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
    reformatRange(editor, psiFile, TextRange.from(0, psiFile.getTextLength()), false);
  }

  public static TextRange reformatRangeAsPostFormatProcessor(final @NotNull PsiFile psiFile, final @NotNull TextRange range) {
    FileEditor[] fileEditors = FileEditorManager.getInstance(psiFile.getProject()).getEditors(psiFile.getVirtualFile());
    FileEditor fileEditor = fileEditors.length == 1 ? fileEditors[0] : null;
    Editor editor = fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;

    return reformatRange(editor, psiFile, range, true);
  }

  private static TextRange reformatRange(final @Nullable Editor editor,
                                         final @NotNull PsiFile psiFile,
                                         final @NotNull TextRange inputRange,
                                         final boolean runningAsPostFormatProcessor) {
    final Project project = psiFile.getProject();
    final VirtualFile file = psiFile.getVirtualFile();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (file == null || document == null) return inputRange;

    if (psiFile.getTextLength() != document.getTextLength()) {
      LOG.error("psiFile.getTextLength()=" + psiFile.getTextLength() + ", document.getTextLength()=" + document.getTextLength());
      return inputRange;
    }

    if (!ReadonlyStatusHandler.ensureDocumentWritable(project, document)) return inputRange;

    final boolean wholeFile = inputRange.equalsToRange(0, psiFile.getTextLength());
    final int caretOffset = editor != null ? editor.getCaretModel().getOffset() : 0;
    final int lineLength = getRightMargin(project);

    final DartAnalysisServerService das = DartAnalysisServerService.getInstance(project);
    das.updateFilesContent();
    final DartAnalysisServerService.FormatResult formatResult = das.edit_format(psiFile.getVirtualFile(), caretOffset, 0, lineLength);

    if (formatResult == null) {
      if (editor != null && !runningAsPostFormatProcessor) {
        showHintLater(editor, DartBundle.message("dart.style.hint.failed"), true);
      }
      return inputRange;
    }

    final List<SourceEdit> edits = formatResult.getEdits();
    if (edits == null || edits.isEmpty()) {
      if (editor != null && !runningAsPostFormatProcessor) {
        showHintLater(editor, DartBundle.message("dart.style.hint.already.good"), false);
      }
      return inputRange;
    }

    if (edits.size() > 1) {
      if (editor != null && !runningAsPostFormatProcessor) {
        showHintLater(editor, DartBundle.message("dart.style.hint.failed"), true);
      }
      LOG.warn("Unexpected response from edit_format, formatResult.getEdits().size() = " + edits.size());
      return inputRange;
    }

    final String inputText = document.getText();
    final String formattedText = StringUtil.convertLineSeparators(edits.getFirst().getReplacement());
    if (!wholeFile && countNonSpaceChars(inputText) != countNonSpaceChars(formattedText)) {
      LOG.error("dartfmt changed non-space characters for file " + file.getPath());
      return inputRange;
    }

    final TextRange rangeInFormattedText = wholeFile ? TextRange.allOf(formattedText)
                                                     : getRangeInFormattedText(inputText, inputRange, formattedText);
    final String formattedRange = rangeInFormattedText.substring(formattedText);

    WriteCommandAction
      .runWriteCommandAction(project, DartBundle.message("action.Dart.DartStyle.text"), null,
                             () -> {
                               document.replaceString(inputRange.getStartOffset(), inputRange.getEndOffset(), formattedRange);

                               if (runningAsPostFormatProcessor) {
                                 // With the 'reformat ony VCS changed text' option (which is always ON in case of reformat before commit),
                                 // this reformatRange() method is called several times for different ranges. Without committing a document
                                 // these ranges are incorrect because they are calculated based on an outdated PsiFile.
                                 PsiDocumentManager.getInstance(project).commitDocument(document);
                               }
                             },
                             psiFile);

    if (editor != null) {
      if (caretOffset > inputRange.getStartOffset()) {
        final int offset = das.getConvertedOffset(psiFile.getVirtualFile(), formatResult.getOffset())
                           + inputRange.getStartOffset() - rangeInFormattedText.getStartOffset();
        editor.getCaretModel().moveToOffset(offset);
      }
      if (!runningAsPostFormatProcessor) {
        showHintLater(editor, DartBundle.message("dart.style.hint.success"), false);
      }
    }

    return TextRange.from(inputRange.getStartOffset(), formattedRange.length());
  }

  @Override
  protected void runOverFiles(final @NotNull Project project, final @NotNull List<VirtualFile> dartFiles) {
    if (dartFiles.isEmpty()) {
      Messages
        .showInfoMessage(project, DartBundle.message("dart.style.files.no.dart.files"), DartBundle.message("action.Dart.DartStyle.text"));
      return;
    }

    if (!confirmRunOverFiles(project, dartFiles.size())) {
      return;
    }

    if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
      reformatFilesWithLsp(project, dartFiles);
    }
    else {
      runDartfmt(project, dartFiles);
    }
  }

  @VisibleForTesting
  boolean confirmRunOverFiles(@NotNull Project project, int fileCount) {
    return Messages.showOkCancelDialog(project, DartBundle.message("dart.style.files.dialog.question", fileCount),
                                       DartBundle.message("action.Dart.DartStyle.text"),
                                       CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(), null) == Messages.OK;
  }

  private void reformatFilesWithLsp(@NotNull Project project, @NotNull List<VirtualFile> dartFiles) {
    if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(dartFiles).hasReadonlyFiles()) return;

    final PsiFile[] psiFiles = ActionUtil.underModalProgress(
      project,
      DartBundle.message("action.Dart.DartStyle.progress.title"),
      () -> {
        final PsiManager psiManager = PsiManager.getInstance(project);
        final List<PsiFile> files = new ArrayList<>(dartFiles.size());
        for (VirtualFile file : dartFiles) {
          ProgressManager.checkCanceled();
          final PsiFile psiFile = psiManager.findFile(file);
          if (psiFile != null) {
            files.add(psiFile);
          }
        }
        return files.toArray(PsiFile.EMPTY_ARRAY);
      }
    );
    if (psiFiles.length > 0) {
      runReformatCodeProcessor(project, psiFiles);
    }
  }

  @VisibleForTesting
  void runReformatCodeProcessor(@NotNull Project project, @NotNull PsiFile[] psiFiles) {
    new ReformatCodeProcessor(project, psiFiles, null, false).run();
  }

  // keep public to be accessible in 3rd party plugins
  public static void runDartfmt(final @NotNull Project project, final @NotNull List<? extends VirtualFile> dartFiles) {
    final Map<VirtualFile, String> fileToNewContentMap = new HashMap<>();
    final int lineLength = getRightMargin(project);

    final Runnable runnable = () -> {
      double fraction = 0.0;
      for (final VirtualFile virtualFile : dartFiles) {
        fraction += 1.0;
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText2(FileUtil.toSystemDependentName(virtualFile.getPath()));
          if (dartFiles.size() > 1) {
            indicator.setIndeterminate(false);
            indicator.setFraction(fraction / dartFiles.size());
          }
        }

        final DartAnalysisServerService.FormatResult formatResult =
          DartAnalysisServerService.getInstance(project).edit_format(virtualFile, 0, 0, lineLength);
        if (formatResult != null && formatResult.getEdits() != null && formatResult.getEdits().size() == 1) {
          final String replacement = StringUtil.convertLineSeparators(formatResult.getEdits().getFirst().getReplacement());
          fileToNewContentMap.put(virtualFile, replacement);
        }
      }
    };

    DartAnalysisServerService.getInstance(project).updateFilesContent();

    final boolean ok = ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(runnable, DartBundle.message("action.Dart.DartStyle.progress.title"), true, project);

    if (ok) {
      final Runnable onSuccessRunnable = () -> {
        for (Map.Entry<VirtualFile, String> entry : fileToNewContentMap.entrySet()) {
          final VirtualFile file = entry.getKey();
          final Document document = FileDocumentManager.getInstance().getDocument(file);
          final String newContent = entry.getValue();

          if (document != null && newContent != null) {
            document.setText(newContent);
          }
        }
      };

      ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance()
        .executeCommand(project, onSuccessRunnable, DartBundle.message("action.Dart.DartStyle.text"), null));
    }
  }

  private static int getRightMargin(@NotNull Project project) {
    return CodeStyle.getSettings(project).getCommonSettings(DartLanguage.INSTANCE).RIGHT_MARGIN;
  }

  /**
   * <code>inputText</code> and <code>outputText</code> may differ only in whitespaces, tabs and line separators!
   */
  public static TextRange getRangeInFormattedText(@NotNull String inputText,
                                                  @NotNull TextRange rangeInInputText,
                                                  @NotNull String formattedText) {
    int startOffset = rangeInInputText.getStartOffset();
    int endOffset = rangeInInputText.getEndOffset();
    assert startOffset >= 0 && endOffset > startOffset && endOffset <= inputText.length()
      : startOffset + ", " + endOffset + ", " + inputText.length();

    int nonSpaceBeforeStartOffset = countNonSpaceChars(inputText.substring(0, startOffset));
    int nonSpaceWithinRange = countNonSpaceChars(rangeInInputText.substring(inputText));

    int resultStartOffset = getOffsetAfterNonSpaceChars(formattedText, nonSpaceBeforeStartOffset);
    int resultEndOffset = resultStartOffset + getOffsetAfterNonSpaceChars(formattedText.substring(resultStartOffset), nonSpaceWithinRange);

    return TextRange.create(adjustResultOffset(inputText, startOffset, formattedText, resultStartOffset),
                            adjustResultOffset(inputText, endOffset, formattedText, resultEndOffset));
  }

  private static int adjustResultOffset(@NotNull String inputText, int offsetInInput, @NotNull String resultText, int offsetInResult) {
    assert offsetInResult == 0 || !isWhiteSpace(resultText.charAt(offsetInResult - 1));

    // 1. if initial offset was right after non-space char then resulting offset will be right after non-space char as well
    if (offsetInInput == 0 || !isWhiteSpace(inputText.charAt(offsetInInput - 1))) {
      return offsetInResult;
    }

    // 2. if initial offset was right *before* non-space char then resulting offset will be right *before* non-space char as well
    if (offsetInInput == inputText.length() || !isWhiteSpace(inputText.charAt(offsetInInput))) {
      while (offsetInResult < resultText.length() && isWhiteSpace(resultText.charAt(offsetInResult))) {
        offsetInResult++;
      }
      return offsetInResult;
    }

    // 3. Initial offset is *inside* a whitespace sequence. This is unlikely to happen, as IntelliJ Platform formatter normalizes range before reformatting.
    // Current solution may not be optimal. Will be improved if/when real use cases are known.
    int spaceInInputBeforeOffset = countSpaceCharsBeforeOffset(inputText, offsetInInput);
    int spaceInResultAfterOffset = countSpaceCharsAfterOffset(resultText, offsetInResult);
    offsetInResult += Math.min(spaceInInputBeforeOffset, spaceInResultAfterOffset);
    return offsetInResult;
  }

  private static int countSpaceCharsBeforeOffset(@NotNull String text, int offset) {
    int result = 0;
    offset--;
    while (offset > 0) {
      if (isWhiteSpace(text.charAt(offset))) {
        result++;
        offset--;
      }
      else {
        break;
      }
    }
    return result;
  }

  private static int countSpaceCharsAfterOffset(@NotNull String text, int offset) {
    int result = 0;
    while (offset < text.length()) {
      if (isWhiteSpace(text.charAt(offset))) {
        result++;
        offset++;
      }
      else {
        break;
      }
    }
    return result;
  }

  private static int countNonSpaceChars(@NotNull String text) {
    int nonSpace = 0;
    int offset = 0;

    while (offset < text.length()) {
      if (!isWhiteSpace(text.charAt(offset))) {
        nonSpace++;
      }
      offset++;
    }

    return nonSpace;
  }

  private static int getOffsetAfterNonSpaceChars(@NotNull String text, int nonSpaceChars) {
    if (nonSpaceChars == 0) return 0;

    int nonSpace = 0;
    int offset = 0;

    while (offset < text.length()) {
      if (!isWhiteSpace(text.charAt(offset))) {
        nonSpace++;
        if (nonSpace == nonSpaceChars) {
          return offset + 1;
        }
      }
      offset++;
    }

    throw new IllegalArgumentException("not enough non-space chars in text");
  }
}
