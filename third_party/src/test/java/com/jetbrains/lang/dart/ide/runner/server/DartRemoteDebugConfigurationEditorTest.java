// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.runner.server;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.ide.runner.server.ui.DartRemoteDebugConfigurationEditor;

import java.lang.reflect.Field;

public class DartRemoteDebugConfigurationEditorTest extends BasePlatformTestCase {

  public void testApplyEditorToPreservesManualTypedPath() throws Exception {
    final DartRemoteDebugConfiguration configuration =
      new DartRemoteDebugConfiguration(getProject(), new DartRemoteDebugConfigurationType(), "Dart Remote");
    final DartRemoteDebugConfigurationEditor editor = new DartRemoteDebugConfigurationEditor(getProject());
    editor.getComponent();

    final ComboBox<?> comboBox = getProjectCombo(editor);
    comboBox.setSelectedItem("  /manual/project/path  ");

    editor.applyTo(configuration);

    assertEquals("/manual/project/path", configuration.getParameters().getDartProjectPath());
  }

  public void testResetEditorKeepsRawPathInEditableField() throws Exception {
    final DartRemoteDebugConfiguration configuration =
      new DartRemoteDebugConfiguration(getProject(), new DartRemoteDebugConfigurationType(), "Dart Remote");
    final DartRemoteDebugConfigurationEditor editor = new DartRemoteDebugConfigurationEditor(getProject());
    editor.getComponent();

    configuration.getParameters().setDartProjectPath("/manual/project/path");
    editor.resetFrom(configuration);

    final ComboBox<?> comboBox = getProjectCombo(editor);
    assertEquals("/manual/project/path", String.valueOf(comboBox.getEditor().getItem()));
  }

  private static ComboBox<?> getProjectCombo(final DartRemoteDebugConfigurationEditor editor) throws Exception {
    final Field field = DartRemoteDebugConfigurationEditor.class.getDeclaredField("myDartProjectCombo");
    field.setAccessible(true);
    final Object combo = field.get(editor);
    assertInstanceOf(combo, ComboBox.class);
    return (ComboBox<?>)combo;
  }
}
