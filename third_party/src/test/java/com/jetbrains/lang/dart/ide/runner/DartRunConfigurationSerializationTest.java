/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.runner;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunConfiguration;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunConfigurationType;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunnerParameters;
import com.jetbrains.lang.dart.ide.runner.server.DartRemoteDebugConfiguration;
import com.jetbrains.lang.dart.ide.runner.server.DartRemoteDebugConfigurationType;
import com.jetbrains.lang.dart.ide.runner.test.DartTestRunConfiguration;
import com.jetbrains.lang.dart.ide.runner.test.DartTestRunConfigurationType;
import com.jetbrains.lang.dart.ide.runner.test.DartTestRunnerParameters;
import org.jdom.Element;

import java.util.LinkedHashMap;
import java.util.Map;

public class DartRunConfigurationSerializationTest extends BasePlatformTestCase {

  public void testCommandLineSkipsDefaultValuesAndRoundTripsNonDefaults() {
    ConfigurationFactory factory = DartCommandLineRunConfigurationType.getInstance().getConfigurationFactories()[0];
    DartCommandLineRunConfiguration defaultConfig =
      (DartCommandLineRunConfiguration)factory.createTemplateConfiguration(getProject());

    Element defaultElement = new Element("configuration");
    defaultConfig.writeExternal(defaultElement);
    assertEmpty(defaultElement.getChildren("option"));
    assertEmpty(defaultElement.getChildren("envs"));

    DartCommandLineRunConfiguration configured =
      (DartCommandLineRunConfiguration)factory.createTemplateConfiguration(getProject());
    DartCommandLineRunnerParameters params = configured.getRunnerParameters();
    params.setFilePath("/workspace/app/bin/main.dart");
    params.setVMOptions("--enable-experiment=macros");
    params.setArguments("--verbose");
    params.setWorkingDirectory("/workspace/app");
    Map<String, String> envs = new LinkedHashMap<>();
    envs.put("DART_ENV", "test");
    params.setEnvs(envs);
    params.setIncludeParentEnvs(false);

    Element serialized = new Element("configuration");
    configured.writeExternal(serialized);
    assertFalse(serialized.getChildren("option").isEmpty());

    DartCommandLineRunConfiguration restored =
      (DartCommandLineRunConfiguration)factory.createTemplateConfiguration(getProject());
    restored.readExternal(serialized);
    DartCommandLineRunnerParameters restoredParams = restored.getRunnerParameters();
    assertEquals("/workspace/app/bin/main.dart", restoredParams.getFilePath());
    assertEquals("--enable-experiment=macros", restoredParams.getVMOptions());
    assertEquals("--verbose", restoredParams.getArguments());
    assertEquals("/workspace/app", restoredParams.getWorkingDirectory());
    assertEquals(envs, restoredParams.getEnvs());
    assertFalse(restoredParams.isIncludeParentEnvs());
  }

  public void testTestConfigurationSkipsDefaultValuesAndRoundTripsNonDefaults() {
    ConfigurationFactory factory = DartTestRunConfigurationType.getInstance().getConfigurationFactories()[0];
    DartTestRunConfiguration defaultConfig =
      (DartTestRunConfiguration)factory.createTemplateConfiguration(getProject());

    Element defaultElement = new Element("configuration");
    defaultConfig.writeExternal(defaultElement);
    assertEmpty(defaultElement.getChildren("option"));
    assertEmpty(defaultElement.getChildren("envs"));

    DartTestRunConfiguration configured =
      (DartTestRunConfiguration)factory.createTemplateConfiguration(getProject());
    DartTestRunnerParameters params = configured.getRunnerParameters();
    params.setFilePath("/workspace/app/test/widget_test.dart");
    params.setIncludeParentEnvs(false);
    params.setScope(DartTestRunnerParameters.Scope.GROUP_OR_TEST_BY_NAME);
    params.setTestName("counter increments");
    params.setTargetName("counter");
    params.setTestRunnerOptions("--platform vm");

    Element serialized = new Element("configuration");
    configured.writeExternal(serialized);
    assertFalse(serialized.getChildren("option").isEmpty());

    DartTestRunConfiguration restored =
      (DartTestRunConfiguration)factory.createTemplateConfiguration(getProject());
    restored.readExternal(serialized);
    DartTestRunnerParameters restoredParams = restored.getRunnerParameters();
    assertEquals("/workspace/app/test/widget_test.dart", restoredParams.getFilePath());
    assertFalse(restoredParams.isIncludeParentEnvs());
    assertEquals(DartTestRunnerParameters.Scope.GROUP_OR_TEST_BY_NAME, restoredParams.getScope());
    assertEquals("counter increments", restoredParams.getTestName());
    assertEquals("counter", restoredParams.getTargetName());
    assertEquals("--platform vm", restoredParams.getTestRunnerOptions());
  }

  public void testRemoteDebugSkipsDefaultValuesAndRoundTripsNonDefaults() {
    ConfigurationFactory factory = DartRemoteDebugConfigurationType.getInstance().getConfigurationFactories()[0];
    DartRemoteDebugConfiguration defaultConfig =
      (DartRemoteDebugConfiguration)factory.createTemplateConfiguration(getProject());

    Element defaultElement = new Element("configuration");
    defaultConfig.writeExternal(defaultElement);
    assertEmpty(defaultElement.getChildren("option"));

    DartRemoteDebugConfiguration configured =
      (DartRemoteDebugConfiguration)factory.createTemplateConfiguration(getProject());
    configured.getParameters().setDartProjectPath("/workspace/remote_app");

    Element serialized = new Element("configuration");
    configured.writeExternal(serialized);
    assertFalse(serialized.getChildren("option").isEmpty());

    DartRemoteDebugConfiguration restored =
      (DartRemoteDebugConfiguration)factory.createTemplateConfiguration(getProject());
    restored.readExternal(serialized);
    assertEquals("/workspace/remote_app", restored.getParameters().getDartProjectPath());
  }
}
