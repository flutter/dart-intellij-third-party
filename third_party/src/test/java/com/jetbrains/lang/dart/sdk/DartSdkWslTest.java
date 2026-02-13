// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.sdk;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslPath;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for WSL-related functionality in {@link DartSdk}.
 * <p>
 * WSL tests require a real Dart SDK installed inside a WSL distribution.
 * Set the {@code WSL_DART_SDK} environment variable to the Windows UNC path
 * of the Dart SDK (e.g., {@code \\wsl$\Ubuntu\ usr\lib\dart}) to run WSL tests.
 * When not set, WSL tests are skipped via {@link Assume#assumeTrue}.
 */
public class DartSdkWslTest {

  // Sample WSL UNC paths for testing
  private static final String WSL_UBUNTU_PATH = "\\\\wsl$\\Ubuntu\\usr\\lib\\dart";
  private static final String WSL_LOCALHOST_PATH = "\\\\wsl.localhost\\Ubuntu\\usr\\lib\\dart";

  // Sample Windows paths
  private static final String WINDOWS_PATH = "C:\\dart-sdk";

  // Sample Linux paths
  private static final String LINUX_PATH = "/usr/lib/dart";

  private DartSdk nonWslSdk;
  private DartSdk wslSdk;
  private WSLDistribution wslDistribution;

  @Before
  public void setUp() {
    nonWslSdk = DartSdk.forPath(DartTestUtils.SDK_HOME_PATH);

    // WSL SDK from environment variable (Windows UNC path, e.g. \\wsl$\Ubuntu\ usr\lib\dart)
    String wslSdkPath = System.getProperty("wsl.dart.sdk");
    if (wslSdkPath == null) {
      wslSdkPath = System.getenv("WSL_DART_SDK");
    }
    if (wslSdkPath != null) {
      wslSdk = DartSdk.forPath(wslSdkPath);
      if (wslSdk != null) {
        WslPath wslPath = WslPath.parseWindowsUncPath(wslSdkPath);
        if (wslPath != null) {
          wslDistribution = wslPath.getDistribution();
        }
      }
    }
  }

  /**
   * Skips the current test if no WSL SDK is available.
   * Tests calling this will show as "ignored" (not "failed") when WSL_DART_SDK is not set.
   */
  private void requireWslSdk() {
    Assume.assumeTrue(
      "WSL_DART_SDK not set. Set to Windows UNC path of Dart SDK in WSL to run WSL tests.",
      wslSdk != null
    );
  }

  // ==================== WslPath Utility Tests ====================

  /**
   * Tests that WSL UNC paths are correctly identified by WslPath utility.
   */
  @Test
  public void testWslPathDetection() {
    if (!SystemInfo.isWindows) {
      assertNull(WslPath.parseWindowsUncPath(WSL_UBUNTU_PATH));
      return;
    }

    assertNotNull("Ubuntu WSL path should be detected", WslPath.parseWindowsUncPath(WSL_UBUNTU_PATH));
    assertNotNull("wsl.localhost path should be detected", WslPath.parseWindowsUncPath(WSL_LOCALHOST_PATH));
    assertNull("Windows path should not be detected as WSL", WslPath.parseWindowsUncPath(WINDOWS_PATH));
    assertNull("Linux path should not be detected as WSL", WslPath.parseWindowsUncPath(LINUX_PATH));
  }

  /**
   * Tests that WslPath.isWslUncPath correctly identifies WSL UNC paths.
   */
  @Test
  public void testIsWslUncPath() {
    if (!SystemInfo.isWindows) {
      assertFalse(WslPath.isWslUncPath(WSL_UBUNTU_PATH));
      return;
    }

    assertTrue("Should detect \\\\wsl$ path", WslPath.isWslUncPath(WSL_UBUNTU_PATH));
    assertTrue("Should detect \\\\wsl.localhost path", WslPath.isWslUncPath(WSL_LOCALHOST_PATH));
    assertFalse("Should not detect Windows path as WSL", WslPath.isWslUncPath(WINDOWS_PATH));
    assertFalse("Should not detect Linux path as WSL", WslPath.isWslUncPath(LINUX_PATH));
  }

  // ==================== Non-WSL SDK Tests ====================

  /**
   * Tests that a non-WSL SDK correctly reports isWsl() as false.
   */
  @Test
  public void testNonWslSdkIsWsl() {
    Assume.assumeNotNull(nonWslSdk);
    assertFalse("Non-WSL SDK should return false for isWsl()", nonWslSdk.isWsl());
  }

  /**
   * Tests that getLocalFilePath returns the correct format for non-WSL SDKs.
   */
  @Test
  public void testGetLocalFilePathNonWsl() {
    Assume.assumeNotNull(nonWslSdk);

    String inputPath = "/home/user/project/lib/main.dart";
    String result = nonWslSdk.getLocalFilePath(inputPath);

    if (SystemInfo.isWindows) {
      assertEquals("Should convert to Windows path format",
                   FileUtil.toSystemDependentName(inputPath), result);
    } else {
      assertEquals("Should preserve Unix path format", inputPath, result);
    }
  }

  /**
   * Tests that getLocalFilePath handles Windows paths correctly on non-WSL SDKs.
   */
  @Test
  public void testGetLocalFilePathWindowsPath() {
    Assume.assumeNotNull(nonWslSdk);

    String windowsPath = "C:/Users/user/project/lib/main.dart";
    String result = nonWslSdk.getLocalFilePath(windowsPath);

    assertEquals("Should convert to system-dependent format",
                 FileUtil.toSystemDependentName(windowsPath), result);
  }

  /**
   * Tests that getIDEFilePath returns the path unchanged for non-WSL SDKs.
   */
  @Test
  public void testGetIDEFilePathNonWsl() {
    Assume.assumeNotNull(nonWslSdk);

    String path = "/home/user/project/lib/main.dart";
    String result = nonWslSdk.getIDEFilePath(path);

    assertEquals("Non-WSL SDK should return path unchanged", path, result);
  }

  /**
   * Tests that getIDEFilePath handles null input gracefully.
   */
  @Test
  public void testGetIDEFilePathNull() {
    Assume.assumeNotNull(nonWslSdk);

    String result = nonWslSdk.getIDEFilePath(null);
    assertNull("Should return null for null input", result);
  }

  /**
   * Tests that getDartExePath returns the correct executable path for non-WSL SDKs.
   */
  @Test
  public void testGetDartExePathNonWsl() {
    Assume.assumeNotNull(nonWslSdk);

    String dartExePath = nonWslSdk.getDartExePath();
    assertNotNull("getDartExePath should not return null", dartExePath);

    if (SystemInfo.isWindows) {
      assertTrue("Windows dart exe should end with .exe", dartExePath.endsWith("/bin/dart.exe"));
    } else {
      assertTrue("Unix dart exe should end with /bin/dart", dartExePath.endsWith("/bin/dart"));
      assertFalse("Unix dart exe should not have .exe extension", dartExePath.endsWith(".exe"));
    }
  }

  /**
   * Tests that getFullDartExePath returns the correct path for non-WSL SDKs.
   */
  @Test
  public void testGetFullDartExePathNonWsl() {
    Assume.assumeNotNull(nonWslSdk);

    String fullPath = nonWslSdk.getFullDartExePath();
    assertNotNull("getFullDartExePath should not return null", fullPath);

    assertEquals("For non-WSL SDK, getDartExePath and getFullDartExePath should match",
                 nonWslSdk.getDartExePath(), fullPath);
  }

  /**
   * Tests that getPubPath returns the correct path for non-WSL SDKs.
   */
  @Test
  public void testGetPubPathNonWsl() {
    Assume.assumeNotNull(nonWslSdk);

    String pubPath = nonWslSdk.getPubPath();
    assertNotNull("getPubPath should not return null", pubPath);

    if (SystemInfo.isWindows) {
      assertTrue("Windows pub should end with .bat", pubPath.endsWith("/bin/pub.bat"));
    } else {
      assertTrue("Unix pub should be in bin directory", pubPath.endsWith("/bin/pub"));
      assertFalse("Unix pub should not have .bat extension", pubPath.endsWith(".bat"));
    }
  }

  /**
   * Tests that getFullPubPath returns the correct path for non-WSL SDKs.
   */
  @Test
  public void testGetFullPubPathNonWsl() {
    Assume.assumeNotNull(nonWslSdk);

    String fullPubPath = nonWslSdk.getFullPubPath();
    assertNotNull("getFullPubPath should not return null", fullPubPath);

    assertEquals("For non-WSL SDK, getPubPath and getFullPubPath should match",
                 nonWslSdk.getPubPath(), fullPubPath);
  }

  /**
   * Tests that getHomePath returns the SDK home path.
   */
  @Test
  public void testGetHomePath() {
    Assume.assumeNotNull(nonWslSdk);

    String homePath = nonWslSdk.getHomePath();
    assertNotNull("getHomePath should not return null", homePath);
    assertEquals("Home path should match the path used to create SDK",
                 DartTestUtils.SDK_HOME_PATH, homePath);
  }

  /**
   * Tests that getVersion returns a non-null version string.
   */
  @Test
  public void testGetVersion() {
    Assume.assumeNotNull(nonWslSdk);

    String version = nonWslSdk.getVersion();
    assertNotNull("getVersion should not return null", version);
    assertFalse("Version should not be empty", version.isEmpty());
  }

  /**
   * Tests that DartSdk.forPath returns null for an invalid path.
   */
  @Test
  public void testForPathInvalidPath() {
    DartSdk sdk = DartSdk.forPath("/non/existent/path/to/dart/sdk");
    assertNull("forPath should return null for invalid SDK path", sdk);
  }

  /**
   * Tests that DartSdk.forPath returns null for an empty path.
   */
  @Test
  public void testForPathEmptyPath() {
    DartSdk sdk = DartSdk.forPath("");
    assertNull("forPath should return null for empty path", sdk);
  }

  /**
   * Tests getLocalFileUri for non-WSL SDK.
   */
  @Test
  public void testGetLocalFileUri() {
    Assume.assumeNotNull(nonWslSdk);

    String path = "/home/user/project/lib/main.dart";
    String result = nonWslSdk.getLocalFileUri(path);
    assertNotNull("getLocalFileUri should not return null", result);

    assertTrue("Result should contain the path components",
               result.contains("home") && result.contains("user") && result.contains("main.dart"));
  }

  /**
   * Tests that getDartCommandList returns a non-empty list for non-WSL SDKs.
   */
  @Test
  public void testGetDartCommandListNonWsl() {
    Assume.assumeNotNull(nonWslSdk);

    var commandList = nonWslSdk.getDartCommandList();
    assertNotNull("getDartCommandList should not return null", commandList);
    assertFalse("Command list should not be empty", commandList.isEmpty());

    assertTrue("Command list should contain dart executable",
               commandList.get(0).contains("dart"));
  }

  /**
   * Tests that getDartSimpleCommandList returns a non-empty list for non-WSL SDKs.
   */
  @Test
  public void testGetDartSimpleCommandListNonWsl() {
    Assume.assumeNotNull(nonWslSdk);

    var commandList = nonWslSdk.getDartSimpleCommandList();
    assertNotNull("getDartSimpleCommandList should not return null", commandList);
    assertFalse("Command list should not be empty", commandList.isEmpty());

    String firstCommand = commandList.get(0);
    assertTrue("Command should be dart executable for non-WSL SDK",
               firstCommand.contains("dart"));
  }

  // ==================== WSL SDK Tests ====================

  /**
   * Tests that a WSL SDK correctly reports isWsl() as true.
   */
  @Test
  public void testWslSdkIsWsl() {
    requireWslSdk();

    assertTrue("WSL SDK should return true for isWsl()", wslSdk.isWsl());
  }

  /**
   * Tests that WSL SDK returns the correct home path.
   */
  @Test
  public void testWslSdkGetHomePath() {
    requireWslSdk();

    String homePath = wslSdk.getHomePath();
    assertNotNull("WSL SDK home path should not be null", homePath);
  }

  /**
   * Tests that WSL SDK returns a valid version.
   */
  @Test
  public void testWslSdkGetVersion() {
    requireWslSdk();

    String version = wslSdk.getVersion();
    assertNotNull("WSL SDK version should not be null", version);
    assertFalse("WSL SDK version should not be empty", version.isEmpty());
  }

  /**
   * Tests that getDartExePath returns a Linux path for WSL SDKs.
   */
  @Test
  public void testGetDartExePathWsl() {
    requireWslSdk();

    String dartExePath = wslSdk.getDartExePath();
    assertNotNull("getDartExePath should not return null for WSL SDK", dartExePath);

    // WSL SDK should return a Linux-style path (converted by WSLDistribution)
    assertTrue("WSL dart exe path should be a Linux path (start with /)",
               dartExePath.startsWith("/"));
    assertTrue("WSL dart exe path should end with /bin/dart",
               dartExePath.endsWith("/bin/dart"));
    assertFalse("WSL dart exe should not have .exe extension",
                dartExePath.endsWith(".exe"));
  }

  /**
   * Tests that getFullDartExePath returns the Windows UNC path for WSL SDKs.
   */
  @Test
  public void testGetFullDartExePathWsl() {
    requireWslSdk();

    String fullPath = wslSdk.getFullDartExePath();
    assertNotNull("getFullDartExePath should not return null for WSL SDK", fullPath);

    // For WSL SDK, getFullDartExePath returns the UNC path (for display/file access)
    assertTrue("WSL full dart exe path should end with /bin/dart",
               fullPath.endsWith("/bin/dart"));
  }

  /**
   * Tests that getPubPath returns a Linux path for WSL SDKs.
   */
  @Test
  public void testGetPubPathWsl() {
    requireWslSdk();

    String pubPath = wslSdk.getPubPath();
    assertNotNull("getPubPath should not return null for WSL SDK", pubPath);

    // WSL SDK should return a Linux-style path
    assertTrue("WSL pub path should be a Linux path (start with /)",
               pubPath.startsWith("/"));
    assertTrue("WSL pub path should end with /bin/pub",
               pubPath.endsWith("/bin/pub"));
    assertFalse("WSL pub should not have .bat extension",
                pubPath.endsWith(".bat"));
  }

  /**
   * Tests that getFullPubPath returns the Windows UNC path for WSL SDKs.
   */
  @Test
  public void testGetFullPubPathWsl() {
    requireWslSdk();

    String fullPubPath = wslSdk.getFullPubPath();
    assertNotNull("getFullPubPath should not return null for WSL SDK", fullPubPath);

    assertTrue("WSL full pub path should end with /bin/pub",
               fullPubPath.endsWith("/bin/pub"));
  }

  /**
   * Tests that getIDEFilePath converts Linux paths to Windows UNC paths for WSL SDKs.
   */
  @Test
  public void testGetIDEFilePathWsl() {
    requireWslSdk();
    assertNotNull("wslDistribution should not be null", wslDistribution);

    String linuxPath = "/home/user/.pub-cache/hosted/pub.dev/meta-1.9.1";
    String result = wslSdk.getIDEFilePath(linuxPath);

    assertNotNull("getIDEFilePath should not return null for WSL SDK", result);

    // The result should be a Windows UNC path
    String expected = wslDistribution.getWindowsPath(linuxPath);
    assertEquals("getIDEFilePath should convert Linux path to Windows UNC path", expected, result);
  }

  /**
   * Tests that getLocalFilePath converts WSL UNC paths to Linux paths for WSL SDKs.
   */
  @Test
  public void testGetLocalFilePathWsl() {
    requireWslSdk();
    assertNotNull("wslDistribution should not be null", wslDistribution);

    // Test with a WSL UNC path
    String wslUncPath = "\\\\wsl$\\Ubuntu\\home\\user\\project\\lib\\main.dart";
    String result = wslSdk.getLocalFilePath(wslUncPath);

    assertNotNull("getLocalFilePath should not return null for WSL SDK", result);

    // The result should be a Linux path
    assertTrue("WSL getLocalFilePath should return a Linux path (start with /)",
               result.startsWith("/"));
  }

  /**
   * Tests that getLocalFilePath returns non-WSL paths unchanged for WSL SDKs.
   */
  @Test
  public void testGetLocalFilePathWslNonWslInput() {
    requireWslSdk();

    // Test with a non-WSL path (already a Linux path)
    String linuxPath = "/home/user/project/lib/main.dart";
    String result = wslSdk.getLocalFilePath(linuxPath);

    assertEquals("WSL getLocalFilePath should return Linux path unchanged",
                 linuxPath, result);
  }

  /**
   * Tests that getDartCommandList includes WSL wrapper commands for WSL SDKs.
   */
  @Test
  public void testGetDartCommandListWsl() {
    requireWslSdk();

    var commandList = wslSdk.getDartCommandList();
    assertNotNull("getDartCommandList should not return null for WSL SDK", commandList);
    assertFalse("Command list should not be empty", commandList.isEmpty());

    // WSL command list should include wsl.exe or similar wrapper
    String commands = String.join(" ", commandList);
    assertTrue("WSL command list should include wsl",
               commands.toLowerCase().contains("wsl"));
  }

  /**
   * Tests that getDartSimpleCommandList includes WSL executable for WSL SDKs.
   */
  @Test
  public void testGetDartSimpleCommandListWsl() {
    requireWslSdk();

    var commandList = wslSdk.getDartSimpleCommandList();
    assertNotNull("getDartSimpleCommandList should not return null for WSL SDK", commandList);
    assertFalse("Command list should not be empty", commandList.isEmpty());

    // First command should be wsl.exe path
    String firstCommand = commandList.get(0).toLowerCase();
    assertTrue("First command should be wsl executable",
               firstCommand.contains("wsl"));
  }
}
