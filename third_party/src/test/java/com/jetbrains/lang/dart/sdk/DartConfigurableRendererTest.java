// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.sdk;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.jetbrains.lang.dart.DartBundle;

import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.util.concurrent.atomic.AtomicBoolean;

public class DartConfigurableRendererTest extends BasePlatformTestCase {

  public void testSdkUpdateChannelRenderer() {
    final ListCellRenderer<DartSdkUpdateOption> renderer = DartConfigurable.createSdkUpdateChannelRenderer();
    final JList<DartSdkUpdateOption> list = new JList<>(DartSdkUpdateOption.OPTIONS_TO_SHOW_IN_COMBO);

    for (DartSdkUpdateOption option : DartSdkUpdateOption.OPTIONS_TO_SHOW_IN_COMBO) {
      final Component rendered = renderer.getListCellRendererComponent(list, option, 0, false, false);
      assertFalse(option.getPresentableName().isEmpty());
      assertEquals(option.getPresentableName(), rendered.getAccessibleContext().getAccessibleName());
    }

    final Component nullRendered = renderer.getListCellRendererComponent(list, null, -1, false, false);
    assertEquals("", nullRendered.getAccessibleContext().getAccessibleName());
  }

  public void testModulesTreeRendererForProjectNode() {
    final AtomicBoolean dartSupportEnabled = new AtomicBoolean(false);
    final CheckboxTree.CheckboxTreeCellRenderer renderer =
      DartConfigurable.createModulesCheckboxTreeCellRenderer(dartSupportEnabled::get);
    final JTree tree = new JTree();
    final CheckedTreeNode projectNode = new CheckedTreeNode(getProject());
    final String expectedLabel = DartBundle.message("project.0", getProject().getName());

    renderer.getTreeCellRendererComponent(tree, projectNode, false, true, false, 0, false);
    assertFalse(renderer.getThreeStateCheckBox().isEnabled());
    assertFalse(renderer.getTextRenderer().isEnabled());
    assertEquals(expectedLabel, renderer.getTextRenderer().getCharSequence(false).toString());

    dartSupportEnabled.set(true);
    renderer.getThreeStateCheckBox().setEnabled(false);
    renderer.customizeRenderer(tree, projectNode, false, true, false, 0, false);
    // Project nodes preserve partial-selection checkbox state when Dart support is enabled.
    assertFalse(renderer.getThreeStateCheckBox().isEnabled());
    assertTrue(renderer.getTextRenderer().isEnabled());
  }

  public void testModulesTreeRendererForModuleAndPlainNodes() {
    final AtomicBoolean dartSupportEnabled = new AtomicBoolean(false);
    final CheckboxTree.CheckboxTreeCellRenderer renderer =
      DartConfigurable.createModulesCheckboxTreeCellRenderer(dartSupportEnabled::get);
    final JTree tree = new JTree();
    final CheckedTreeNode moduleNode = new CheckedTreeNode(getModule());

    renderer.getTreeCellRendererComponent(tree, moduleNode, false, false, true, 1, false);
    assertFalse(renderer.getThreeStateCheckBox().isEnabled());
    assertFalse(renderer.getTextRenderer().isEnabled());
    assertNotNull(renderer.getTextRenderer().getIcon());
    assertEquals(getModule().getName(), renderer.getTextRenderer().getCharSequence(false).toString());

    dartSupportEnabled.set(true);
    renderer.getTreeCellRendererComponent(tree, moduleNode, false, false, true, 1, false);
    assertTrue(renderer.getThreeStateCheckBox().isEnabled());
    assertTrue(renderer.getTextRenderer().isEnabled());
    assertNotNull(renderer.getTextRenderer().getIcon());
    assertEquals(getModule().getName(), renderer.getTextRenderer().getCharSequence(false).toString());

    renderer.getTreeCellRendererComponent(tree, "nonCheckedNode", false, false, true, 2, false);
    assertEquals("", renderer.getTextRenderer().getCharSequence(false).toString());
  }

  public void testConfigurableCreatesAndResetsUiWithoutError() {
    final DartConfigurable configurable = new DartConfigurable(getProject());
    try {
      assertNotNull(configurable.createComponent());
      configurable.reset();
    }
    finally {
      Disposer.dispose(configurable);
    }
  }
}
