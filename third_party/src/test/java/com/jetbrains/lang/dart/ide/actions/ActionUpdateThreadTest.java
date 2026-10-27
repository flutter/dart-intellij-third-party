// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.ide.errorTreeView.AnalysisServerDiagnosticsAction;
import com.jetbrains.lang.dart.ide.errorTreeView.ReanalyzeDartSourcesAction;
import com.jetbrains.lang.dart.ide.errorTreeView.RestartDartAnalysisServerAction;
import com.jetbrains.lang.dart.ide.generation.DartGenerateConstructorAction;
import com.jetbrains.lang.dart.ide.generation.DartGenerateEqualsAndHashcodeAction;
import com.jetbrains.lang.dart.ide.generation.DartGenerateGetterAction;
import com.jetbrains.lang.dart.ide.generation.DartGenerateGetterSetterAction;
import com.jetbrains.lang.dart.ide.generation.DartGenerateNamedConstructorAction;
import com.jetbrains.lang.dart.ide.generation.DartGenerateSetterAction;
import com.jetbrains.lang.dart.ide.generation.DartGenerateToStringAction;
import com.jetbrains.lang.dart.ide.runner.actions.DartPopFrameAction;
import com.jetbrains.lang.dart.ide.runner.server.OpenDartObservatoryUrlAction;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartCopyDtdUriAction;
import com.jetbrains.lang.dart.pubServer.StopDartWebdevServerAction;

public class ActionUpdateThreadTest extends BasePlatformTestCase {

  public void testRegisteredPluginActions_specifyExplicitUpdateThread() {
    final String[] registeredActionIds = {
      "Dart.stop.dart.webdev.server",
      "Dart.NewDartFile",
      "Dart.Reanalyze",
      "Dart.Restart.Analysis.Server",
      "Dart.Analyzer.Diagnostics",
      "Dart.DartStyle",
      "Dart.DartSortMembers",
      "Generate.Constructor.Dart",
      "Generate.Named.Constructor.Dart",
      "Generate.GetAccessor.Dart",
      "Generate.SetAccessor.Dart",
      "Generate.GetSetAccessor.Dart",
      "Generate.ToString.Dart",
      "Generate.EqualsAndHashcode.Dart",
      "Dart.pub.get",
      "Dart.pub.upgrade",
      "Dart.build",
      "Dart.pub.outdated",
      "Dart.pub.cache.repair",
      "DartCopyDtdUriAction"
    };

    final ActionManager actionManager = ActionManager.getInstance();
    for (String actionId : registeredActionIds) {
      final AnAction action = actionManager.getAction(actionId);
      assertNotNull("Action with id '" + actionId + "' must be registered in ActionManager", action);
      assertActionUpdateThread(ActionUpdateThread.BGT, action);
    }
  }

  public void testCoreActions_specifyExplicitUpdateThread() {
    assertActionUpdateThread(ActionUpdateThread.BGT, new CreateDartFileAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new ReanalyzeDartSourcesAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new RestartDartAnalysisServerAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new AnalysisServerDiagnosticsAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartPopFrameAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new OpenDartObservatoryUrlAction(null, () -> true));
    assertActionUpdateThread(ActionUpdateThread.BGT, new StopDartWebdevServerAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartCopyDtdUriAction());
  }

  public void testSubclassesInheritFromBase() {
    // AbstractDartFileProcessingAction subclasses
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartStyleAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartSortMembersAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartFixAction());

    // BaseDartGenerateAction subclasses
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartGenerateConstructorAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartGenerateNamedConstructorAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartGenerateGetterAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartGenerateSetterAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartGenerateGetterSetterAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartGenerateToStringAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartGenerateEqualsAndHashcodeAction());

    // DartPubActionBase subclasses
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartPubGetAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartPubUpgradeAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartPubBuildAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartPubOutdatedAction());
    assertActionUpdateThread(ActionUpdateThread.BGT, new DartPubCacheRepairAction());
  }

  private static void assertActionUpdateThread(ActionUpdateThread expected, AnAction action) {
    assertNotNull("Action instance must not be null", action);
    assertEquals("Action " + action.getClass().getSimpleName() + " must explicitly specify " + expected,
                 expected,
                 action.getActionUpdateThread());
  }
}
