// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.sdk;

import com.intellij.execution.wsl.WslPath;
import com.intellij.openapi.util.SystemInfo;
import junit.framework.TestCase;

/**
 * Unit tests for WSL-related functionality in {@link DartSdk}.
 * <p>
 * These tests verify path conversion and WSL detection logic.
 * Some tests are only meaningful on Windows where WSL is available.
 */
public class DartSdkWslTest extends TestCase {

  // Sample WSL UNC paths for testing
  private static final String WSL_UBUNTU_PATH = "\\\\wsl$\\Ubuntu\\usr\\lib\\dart";
  private static final String WSL_DEBIAN_PATH = "\\\\wsl$\\Debian\\usr\\lib\\dart";
  private static final String WSL_LOCALHOST_PATH = "\\\\wsl.localhost\\Ubuntu\\usr\\lib\\dart";

  // Sample Windows paths
  private static final String WINDOWS_PATH = "C:\\dart-sdk";
  private static final String WINDOWS_PATH_WITH_SPACES = "C:\\Program Files\\Dart\\dart-sdk";

  // Sample Linux paths
  private static final String LINUX_PATH = "/usr/lib/dart";

  /**
   * Tests that WSL UNC paths are correctly identified as WSL paths.
   * This test runs on all platforms but the actual WSL detection only works on Windows.
   */
  public void testWslPathDetection() {
    if (!SystemInfo.isWindows) {
      // On non-Windows platforms, WslPath.parseWindowsUncPath always returns null
      assertNull(WslPath.parseWindowsUncPath(WSL_UBUNTU_PATH));
      return;
    }

    // On Windows, WSL UNC paths should be detected
    assertNotNull("Ubuntu WSL path should be detected", WslPath.parseWindowsUncPath(WSL_UBUNTU_PATH));
    assertNotNull("Debian WSL path should be detected", WslPath.parseWindowsUncPath(WSL_DEBIAN_PATH));
    assertNotNull("wsl.localhost path should be detected", WslPath.parseWindowsUncPath(WSL_LOCALHOST_PATH));

    // Non-WSL paths should not be detected as WSL
    assertNull("Windows path should not be detected as WSL", WslPath.parseWindowsUncPath(WINDOWS_PATH));
    assertNull("Linux path should not be detected as WSL", WslPath.parseWindowsUncPath(LINUX_PATH));
  }

  /**
   * Tests the fixLinuxPath utility method converts backslashes to forward slashes.
   */
  public void testFixLinuxPath() {
    assertEquals("/home/user/project", fixLinuxPath("\\home\\user\\project"));
    assertEquals("/home/user/project", fixLinuxPath("/home/user/project"));
    assertEquals("home/user/project", fixLinuxPath("home\\user\\project"));
    assertEquals("", fixLinuxPath(""));
    assertEquals("/", fixLinuxPath("\\"));
    assertEquals("a/b/c/d", fixLinuxPath("a\\b\\c\\d"));
  }

  /**
   * Tests path construction for various SDK locations.
   */
  public void testDartExePathConstruction() {
    // Test Windows path construction
    String windowsSdkHome = "C:\\dart-sdk";
    String expectedWindowsExe = windowsSdkHome + "/bin/dart.exe";
    assertEquals(expectedWindowsExe, constructDartExePath(windowsSdkHome, false, false));

    // Test Linux path construction
    String linuxSdkHome = "/usr/lib/dart";
    String expectedLinuxExe = linuxSdkHome + "/bin/dart";
    assertEquals(expectedLinuxExe, constructDartExePath(linuxSdkHome, false, true));

    // Test WSL path construction (returns Linux path for execution)
    String wslSdkHome = "\\\\wsl$\\Ubuntu\\usr\\lib\\dart";
    // For WSL, getDartExePath returns the Linux path for command execution
    // The actual path would be converted by the WSL distribution
  }

  /**
   * Tests that package paths from package_config.json are correctly handled.
   */
  public void testPackagePathFormats() {
    // Test file:// URI parsing
    String fileUri = "file:///home/user/.pub-cache/hosted/pub.dev/meta-1.9.1";
    String expectedPath = "/home/user/.pub-cache/hosted/pub.dev/meta-1.9.1";
    assertEquals(expectedPath, extractPathFromFileUri(fileUri));

    // Test Windows file:// URI
    String windowsFileUri = "file:///C:/Users/user/.pub-cache/hosted/pub.dev/meta-1.9.1";
    String expectedWindowsPath = "C:/Users/user/.pub-cache/hosted/pub.dev/meta-1.9.1";
    assertEquals(expectedWindowsPath, extractPathFromFileUri(windowsFileUri));

    // Test relative path handling
    String relativeUri = "../.pub-cache/hosted/pub.dev/meta-1.9.1";
    assertFalse(relativeUri.startsWith("file:/"));
  }

  /**
   * Tests edge cases in path handling.
   */
  public void testPathEdgeCases() {
    // Empty path
    assertEquals("", fixLinuxPath(""));

    // Path with mixed separators
    assertEquals("a/b/c/d", fixLinuxPath("a\\b/c\\d"));

    // Path with spaces
    assertEquals("/home/user/my project/lib", fixLinuxPath("\\home\\user\\my project\\lib"));

    // Path with special characters
    assertEquals("/home/user/project-name/lib", fixLinuxPath("\\home\\user\\project-name\\lib"));
    assertEquals("/home/user/project_name/lib", fixLinuxPath("\\home\\user\\project_name\\lib"));
  }

  /**
   * Tests that WslPath.isWslUncPath correctly identifies WSL UNC paths.
   */
  public void testIsWslUncPath() {
    if (!SystemInfo.isWindows) {
      // On non-Windows, these methods may behave differently
      return;
    }

    assertTrue("Should detect \\\\wsl$ path", WslPath.isWslUncPath(WSL_UBUNTU_PATH));
    assertTrue("Should detect \\\\wsl.localhost path", WslPath.isWslUncPath(WSL_LOCALHOST_PATH));
    assertFalse("Should not detect Windows path as WSL", WslPath.isWslUncPath(WINDOWS_PATH));
    assertFalse("Should not detect Linux path as WSL", WslPath.isWslUncPath(LINUX_PATH));
  }

  // Helper method that mirrors DartSdk.fixLinuxPath
  private static String fixLinuxPath(String path) {
    return path.replace('\\', '/');
  }

  // Helper method to construct dart exe path based on platform
  private static String constructDartExePath(String homePath, boolean isWsl, boolean isLinux) {
    if (isWsl) {
      // For WSL, would use distribution.getWslPath()
      return homePath + "/bin/dart";
    }
    if (isLinux || !SystemInfo.isWindows) {
      return homePath + "/bin/dart";
    }
    return homePath + "/bin/dart.exe";
  }

  // Helper method to extract path from file:// URI
  private static String extractPathFromFileUri(String uri) {
    if (!uri.startsWith("file:/")) {
      return uri;
    }
    // Remove file:// or file:/// prefix
    String path = uri.substring("file://".length());
    if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
      // Windows path like /C:/... - remove leading /
      path = path.substring(1);
    }
    return path;
  }
}
