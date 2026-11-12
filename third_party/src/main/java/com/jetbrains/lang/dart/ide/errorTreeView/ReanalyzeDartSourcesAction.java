// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.errorTreeView;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.platform.dartlsp.api.LspServerManager;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider;
import com.jetbrains.lang.dart.lsp.DartLspService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReanalyzeDartSourcesAction extends AnAction implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (isApplicable(project)) {
      DartAnalysisServerService das = DartAnalysisServerService.getInstance(project);
      if (das.isServerProcessActive()) {
        String sdkVersion = das.getSdkVersion();
        if (!sdkVersion.isEmpty() && DartAnalysisServerService.isDartSdkVersionSufficientForLspReanalyze(sdkVersion)) {
          DartLspService.reanalyze(project)
                  .thenAccept(succeeded -> {
                    if (!project.isDisposed() && !Boolean.TRUE.equals(succeeded)) {
                      das.analysis_reanalyze();
                    }
                  })
                  .exceptionally(ex ->{
                    if (!project.isDisposed()) {
                      das.analysis_reanalyze();
                    }
                    return null;
                  });
        } else {
          das.analysis_reanalyze();
        }
      }
      else {
        // Ensure the server is properly stopped.
        LspServerManager.getInstance(project).stopAndRestartIfNeeded(DartLspServerSupportProvider.class);
        das.restartServer();
      }
    }

    Analytics.report(AnalyticsData.forAction(this, e));
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isApplicable(e.getProject()));
  }

  private static boolean isApplicable(final @Nullable Project project) {
    return project != null && ToolWindowManager.getInstance(project).getToolWindow(DartProblemsView.TOOLWINDOW_ID) != null;
  }
}
