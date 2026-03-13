// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.refactoring;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.analyzer.lsp.LspRenameRefactoringResult;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatusEntry;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatusSeverity;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The LTK wrapper around the Analysis Server 'Rename' refactoring.
 */
public class ServerRenameRefactoring extends ServerRefactoring {
  private RenameOptions options;
  private String elementKindName;
  private String oldName;
  private final Object myLspStateLock = new Object();
  private @Nullable ServerRefactoringListener myLspListener;
  private @Nullable RefactoringStatus myLspServerErrorStatus;
  private @Nullable RefactoringStatus myLspInitialStatus;
  private @Nullable RefactoringStatus myLspOptionsStatus;
  private @Nullable RefactoringStatus myLspFinalStatus;
  private @Nullable SourceChange myLspChange;
  private final @NotNull Set<String> myLspPotentialEdits = new HashSet<>();
  private boolean myHasPendingLspRequests;
  private int myLastLspValidationId;

  public ServerRenameRefactoring(final @NotNull Project project, final @NotNull VirtualFile file, final int offset, final int length) {
    super(project, DartBundle.message("progress.title.rename"), RefactoringKind.RENAME, file, offset, length);
  }

  public @NotNull String getElementKindName() {
    return elementKindName;
  }

  public @NotNull String getOldName() {
    return oldName;
  }

  @Override
  public @Nullable RefactoringStatus checkFinalConditions() {
    if (!useNativeLspRename()) {
      return super.checkFinalConditions();
    }

    ProgressManager.getInstance().run(new Task.Modal(null, DartBundle.message("progress.title.rename"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(DartBundle.message("progress.text.validating.the.specified.parameters"));
        indicator.setIndeterminate(true);
        runNativeRenameRequest(false, getRequestedNewName());
      }
    });

    synchronized (myLspStateLock) {
      if (myLspServerErrorStatus != null) {
        return myLspServerErrorStatus;
      }
      if (myLspFinalStatus == null) {
        return null;
      }
      RefactoringStatus result = new RefactoringStatus();
      result.merge(myLspOptionsStatus);
      result.merge(myLspFinalStatus);
      return result;
    }
  }

  @Override
  public @Nullable RefactoringStatus checkInitialConditions() {
    if (!useNativeLspRename()) {
      return super.checkInitialConditions();
    }

    ProgressManager.getInstance().run(new Task.Modal(null, DartBundle.message("progress.title.rename"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(DartBundle.message("progress.text.checking.availability.at.the.selection"));
        indicator.setIndeterminate(true);
        runNativeRenameRequest(true, null);
      }
    });

    synchronized (myLspStateLock) {
      if (myLspServerErrorStatus != null) {
        return myLspServerErrorStatus;
      }
      return myLspInitialStatus;
    }
  }

  @Override
  public @Nullable SourceChange getChange() {
    if (!useNativeLspRename()) {
      return super.getChange();
    }
    synchronized (myLspStateLock) {
      return myLspChange;
    }
  }

  @Override
  protected @Nullable RefactoringOptions getOptions() {
    return options;
  }

  @Override
  public @NotNull Set<String> getPotentialEdits() {
    if (!useNativeLspRename()) {
      return super.getPotentialEdits();
    }
    synchronized (myLspStateLock) {
      return new HashSet<>(myLspPotentialEdits);
    }
  }

  @Override
  protected void setFeedback(@NotNull RefactoringFeedback _feedback) {
    RenameFeedback feedback = (RenameFeedback)_feedback;
    elementKindName = StringUtil.capitalize(feedback.getElementKindName());
    oldName = feedback.getOldName();
    if (options == null) {
      options = new RenameOptions(oldName);
    }
  }

  @Override
  public void setListener(@Nullable ServerRefactoringListener listener) {
    if (!useNativeLspRename()) {
      super.setListener(listener);
      return;
    }
    synchronized (myLspStateLock) {
      myLspListener = listener;
    }
  }

  public void setNewName(@NotNull String newName) {
    if (options == null) {
      options = new RenameOptions(oldName == null ? "" : oldName);
    }
    options.setNewName(newName);
    if (!useNativeLspRename()) {
      setOptions(true, null);
      return;
    }

    final int validationId;
    synchronized (myLspStateLock) {
      validationId = ++myLastLspValidationId;
      myHasPendingLspRequests = true;
    }
    notifyLspListener();

    AppExecutorUtil.getAppExecutorService().execute(() -> {
      LspRenameRefactoringResult result = null;
      RefactoringStatus errorStatus = null;
      try {
        result = DartAnalysisServerService.getInstance(getProject()).lsp_getRenameRefactoring(getFile(), getOffset(), true, newName);
        if (result == null) {
          errorStatus = RefactoringStatus.createFatalErrorStatus("LSP rename backend is unavailable.");
        }
      }
      catch (RuntimeException e) {
        errorStatus = RefactoringStatus.createFatalErrorStatus(e.getMessage());
      }

      synchronized (myLspStateLock) {
        if (validationId != myLastLspValidationId) {
          return;
        }
        myHasPendingLspRequests = false;
        if (errorStatus != null) {
          myLspServerErrorStatus = errorStatus;
          myLspOptionsStatus = errorStatus;
        }
        else if (result != null) {
          applyLspResult(result);
        }
      }
      notifyLspListener();
    });
  }

  private void applyLspResult(@NotNull LspRenameRefactoringResult result) {
    if (result.getFeedback() != null) {
      setFeedback(result.getFeedback());
    }
    myLspServerErrorStatus = null;
    myLspInitialStatus = toRefactoringStatus(result.getInitialProblems());
    myLspOptionsStatus = toRefactoringStatus(result.getOptionsProblems());
    myLspFinalStatus = toRefactoringStatus(result.getFinalProblems());
    myLspChange = result.getChange();
    myLspPotentialEdits.clear();
    myLspPotentialEdits.addAll(result.getPotentialEdits());
  }

  private @Nullable String getRequestedNewName() {
    return options == null ? null : options.getNewName();
  }

  private void notifyLspListener() {
    final ServerRefactoringListener listener;
    final boolean hasPendingRequests;
    final RefactoringStatus optionsStatus;
    synchronized (myLspStateLock) {
      listener = myLspListener;
      hasPendingRequests = myHasPendingLspRequests;
      optionsStatus = myLspOptionsStatus != null ? myLspOptionsStatus : new RefactoringStatus();
    }
    if (listener != null) {
      listener.requestStateChanged(hasPendingRequests, optionsStatus);
    }
  }

  private void runNativeRenameRequest(boolean validateOnly, @Nullable String newName) {
    final LspRenameRefactoringResult result = DartAnalysisServerService.getInstance(getProject())
      .lsp_getRenameRefactoring(getFile(), getOffset(), validateOnly, newName);

    synchronized (myLspStateLock) {
      if (result == null) {
        final RefactoringStatus errorStatus = RefactoringStatus.createFatalErrorStatus("LSP rename backend is unavailable.");
        myLspServerErrorStatus = errorStatus;
        myLspInitialStatus = errorStatus;
        myLspOptionsStatus = errorStatus;
        myLspFinalStatus = errorStatus;
        myLspChange = null;
        myLspPotentialEdits.clear();
        return;
      }
      applyLspResult(result);
      myHasPendingLspRequests = false;
    }
    notifyLspListener();
  }

  private boolean useNativeLspRename() {
    return Registry.is("dart.use.lsp.client", false);
  }

  private static @NotNull RefactoringStatus toRefactoringStatus(@Nullable Collection<RefactoringProblem> problems) {
    RefactoringStatus status = new RefactoringStatus();
    if (problems == null) {
      return status;
    }

    for (RefactoringProblem problem : problems) {
      status.addEntry(new RefactoringStatusEntry(toProblemSeverity(problem.getSeverity()), problem.getMessage()));
    }
    return status;
  }

  private static @NotNull RefactoringStatusSeverity toProblemSeverity(@NotNull String severity) {
    if (RefactoringProblemSeverity.FATAL.equals(severity)) {
      return RefactoringStatusSeverity.FATAL;
    }
    if (RefactoringProblemSeverity.ERROR.equals(severity)) {
      return RefactoringStatusSeverity.ERROR;
    }
    if (RefactoringProblemSeverity.WARNING.equals(severity)) {
      return RefactoringStatusSeverity.WARNING;
    }
    return RefactoringStatusSeverity.OK;
  }
}
