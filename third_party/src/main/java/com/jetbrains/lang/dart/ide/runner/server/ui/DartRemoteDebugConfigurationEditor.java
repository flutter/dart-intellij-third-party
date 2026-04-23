// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.runner.server.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.lang.dart.ide.runner.server.DartRemoteDebugConfiguration;
import com.jetbrains.lang.dart.ide.runner.server.DartRemoteDebugParameters;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DartRemoteDebugConfigurationEditor extends SettingsEditor<DartRemoteDebugConfiguration> {

  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myDartProjectCombo;
  private JBLabel myHintLabel;

  public DartRemoteDebugConfigurationEditor(final @NotNull Project project) {
    myDartProjectCombo.addBrowseFolderListener(null, null, project, FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                           TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    myHintLabel.setCopyable(true);
  }

  @Override
  protected @NotNull JComponent createEditor() {
    return myMainPanel;
  }

  @Override
  protected void resetEditorFrom(final @NotNull DartRemoteDebugConfiguration config) {
    final DartRemoteDebugParameters params = config.getParameters();
    myDartProjectCombo.setText(FileUtil.toSystemDependentName(params.getDartProjectPath()));
  }

  @Override
  protected void applyEditorTo(final @NotNull DartRemoteDebugConfiguration config) {
    final DartRemoteDebugParameters params = config.getParameters();
    params.setDartProjectPath(FileUtil.toSystemIndependentName(myDartProjectCombo.getText().trim()));
  }
}

