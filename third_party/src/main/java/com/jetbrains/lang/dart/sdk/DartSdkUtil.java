// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.sdk;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.jetbrains.lang.dart.DartBundle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DartSdkUtil {
  private static final Map<Pair<File, Long>, String> ourVersions = new HashMap<>();
  private static final String DART_SDK_KNOWN_PATHS = "DART_SDK_KNOWN_PATHS";

  public static @Nullable @NlsSafe String getSdkVersion(final @NotNull String sdkHomePath) {
    final File versionFile = new File(sdkHomePath + "/version");
    if (!versionFile.isFile()) {
      return null;
    }

    final Pair<File, Long> versionPair = Pair.create(versionFile, versionFile.lastModified());
    final String cachedVersion = ourVersions.get(versionPair);
    if (cachedVersion != null) {
      return cachedVersion;
    }

    final String version = FileUtil.loadFileOrNull(versionFile);
    if (version != null) {
      ourVersions.put(versionPair, version);
      return version;
    }

    return null;
  }

  @Contract("null->false")
  public static boolean isDartSdkHome(final @Nullable String path) {
    return Strings.isNotEmpty(path) && new File(path + "/lib/core/core.dart").isFile();
  }

  private static @NotNull String getItemFromCombo(final @NotNull JComboBox combo) {
    return combo.getEditor().getItem().toString().trim();
  }

  public static @Nullable String getFirstKnownDartSdkPath() {
    List<String> knownPaths = PropertiesComponent.getInstance().getList(DART_SDK_KNOWN_PATHS);
    if (knownPaths != null && !knownPaths.isEmpty() && isDartSdkHome(knownPaths.getFirst())) {
      return knownPaths.getFirst();
    }
    return null;
  }

  public static void addKnownSDKPathsToCombo(final @NotNull JComboBox<String> combo) {
    final SmartList<String> validPathsForUI = new SmartList<>();

    final String currentPath = getItemFromCombo(combo);
    if (!currentPath.isEmpty()) {
      validPathsForUI.add(currentPath);
    }

    List<String> knownPaths = PropertiesComponent.getInstance().getList(DART_SDK_KNOWN_PATHS);
    if (knownPaths != null && !knownPaths.isEmpty()) {
      for (String path : knownPaths) {
        final String pathSD = FileUtil.toSystemDependentName(path);
        if (!pathSD.equals(currentPath) && isDartSdkHome(path)) {
          validPathsForUI.add(pathSD);
        }
      }
    }

    combo.setModel(new DefaultComboBoxModel<>(ArrayUtilRt.toStringArray(validPathsForUI)));
  }

  public static void updateKnownSdkPaths(final @NotNull Project project, final @NotNull String newSdkPath) {
    final DartSdk oldSdk = DartSdk.getDartSdk(project);
    final List<String> knownPaths = new ArrayList<>();

    List<String> oldKnownPaths = PropertiesComponent.getInstance().getList(DART_SDK_KNOWN_PATHS);
    if (oldKnownPaths != null) {
      knownPaths.addAll(oldKnownPaths);
    }

    if (oldSdk != null) {
      knownPaths.remove(oldSdk.getHomePath());
      knownPaths.addFirst(oldSdk.getHomePath());
    }

    knownPaths.remove(newSdkPath);
    knownPaths.addFirst(newSdkPath);

    PropertiesComponent.getInstance().setList(DART_SDK_KNOWN_PATHS, knownPaths);
  }

  public static @Nullable @NlsContexts.Label String getErrorMessageIfWrongSdkRootPath(final @NotNull String sdkRootPath) {
    if (sdkRootPath.isEmpty()) return DartBundle.message("error.path.to.sdk.not.specified");

    final File sdkRoot = new File(sdkRootPath);
    if (!sdkRoot.isDirectory()) return DartBundle.message("error.folder.specified.as.sdk.not.exists");

    if (!isDartSdkHome(sdkRootPath)) return DartBundle.message("error.sdk.not.found.in.specified.location");

    return null;
  }

  public static @NotNull String getDartExePath(@NotNull DartSdk sdk) {
    return getDartExePath(sdk.getHomePath());
  }

  public static @NotNull String getDartExePath(@NotNull String sdkRoot) {
    return sdkRoot + (SystemInfo.isWindows ? "/bin/dart.exe" : "/bin/dart");
  }
}
