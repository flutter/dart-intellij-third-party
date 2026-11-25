// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.ide.runner.server.ui;

import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.ide.runner.server.ui.DartRemoteDebugConfigurationEditor.NameAndPath;

import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;

public class DartRemoteDebugComboRendererTest extends BasePlatformTestCase {

  public void testDartProjectComboRendererFormatsNamedUnnamedAndNullItems() {
    final ListCellRenderer<NameAndPath> renderer =
      DartRemoteDebugConfigurationEditor.createDartProjectComboRenderer();
    final JList<NameAndPath> list = new JList<>();
    final String sysPath = FileUtil.toSystemDependentName("/workspace/my_app");

    final NameAndPath named = new NameAndPath("my_app", "/workspace/my_app");
    final Component namedComp = renderer.getListCellRendererComponent(list, named, 0, false, false);
    assertEquals("my_app (" + sysPath + ")", namedComp.getAccessibleContext().getAccessibleName());

    final Component nullComp = renderer.getListCellRendererComponent(list, null, -1, false, false);
    assertEquals("", nullComp.getAccessibleContext().getAccessibleName());

    final NameAndPath unnamed = new NameAndPath(null, "/workspace/my_app");
    final Component unnamedComp = renderer.getListCellRendererComponent(list, unnamed, 1, false, false);
    assertEquals(sysPath, unnamedComp.getAccessibleContext().getAccessibleName());
  }

  public void testEditorInitializesComponentWithoutError() {
    final DartRemoteDebugConfigurationEditor editor =
      new DartRemoteDebugConfigurationEditor(ProjectManager.getInstance().getDefaultProject());
    try {
      assertNotNull(editor.getComponent());
    }
    finally {
      Disposer.dispose(editor);
    }
  }
}
