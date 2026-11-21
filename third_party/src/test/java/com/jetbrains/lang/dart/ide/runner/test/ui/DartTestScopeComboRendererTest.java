// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.ide.runner.test.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.ide.runner.test.DartTestRunnerParameters.Scope;

import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;

public class DartTestScopeComboRendererTest extends BasePlatformTestCase {

  public void testScopeComboRendererFormatsScopesAndNull() {
    final ListCellRenderer<Scope> renderer = DartTestConfigurationEditorForm.createScopeComboRenderer();
    final Scope[] comboScopes = {Scope.FOLDER, Scope.FILE, Scope.GROUP_OR_TEST_BY_NAME};
    final JList<Scope> list = new JList<>(comboScopes);

    for (Scope scope : comboScopes) {
      final Component rendered = renderer.getListCellRendererComponent(list, scope, 0, false, false);
      assertFalse(scope.getPresentableName().isEmpty());
      assertEquals(scope.getPresentableName(), rendered.getAccessibleContext().getAccessibleName());
    }

    final Component nullRendered = renderer.getListCellRendererComponent(list, null, -1, false, false);
    assertEquals("", nullRendered.getAccessibleContext().getAccessibleName());
  }

  public void testEditorInitializesComponentWithoutError() {
    final DartTestConfigurationEditorForm editor = new DartTestConfigurationEditorForm(getProject());
    try {
      assertNotNull(editor.getComponent());
    }
    finally {
      Disposer.dispose(editor);
    }
  }
}
