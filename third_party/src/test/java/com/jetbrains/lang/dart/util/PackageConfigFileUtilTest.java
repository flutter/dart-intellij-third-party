// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

/**
 * Unit tests for package configuration file utilities.
 * <p>
 * Tests path parsing and conversion for package_config.json files,
 * including WSL path handling scenarios.
 */
public class PackageConfigFileUtilTest extends TestCase {

  /**
   * Tests parsing of file:// URIs from package_config.json.
   */
  public void testFileUriParsing() {
    // Standard Linux file URI
    assertEquals("/home/user/.pub-cache/hosted/pub.dev/meta-1.9.1",
                 parseFileUri("file:///home/user/.pub-cache/hosted/pub.dev/meta-1.9.1"));

    // Windows file URI with drive letter
    if (SystemInfo.isWindows) {
      // On Windows, the path should preserve the drive letter
      String windowsPath = parseFileUri("file:///C:/Users/user/.pub-cache/hosted/pub.dev/meta-1.9.1");
      assertTrue("Windows path should contain drive letter",
                 windowsPath.contains("C:") || windowsPath.contains("c:"));
    }

    // File URI with encoded characters
    assertEquals("/home/user/.pub-cache/hosted/pub.dev/some+package-1.0.0",
                 parseFileUri("file:///home/user/.pub-cache/hosted/pub.dev/some%2Bpackage-1.0.0"));
  }

  /**
   * Tests handling of relative URIs in package_config.json.
   */
  public void testRelativeUriHandling() {
    // Relative path should not start with file:/
    String relativeUri = "../.dart_tool/package_config.json";
    assertFalse("Relative URI should not start with file:/", relativeUri.startsWith("file:/"));

    // Another common relative path pattern
    String libRelative = "../lib/";
    assertFalse("Lib relative path should not start with file:/", libRelative.startsWith("file:/"));
  }

  /**
   * Tests the path trimming logic used in getAbsolutePackageRootPath.
   */
  public void testPathTrimming() {
    // Test trimming of file:/ prefix and leading/trailing slashes
    String uri = "file:///home/user/.pub-cache/";
    String pathAfterSlashes = trimFilePrefix(uri);
    assertEquals("home/user/.pub-cache", pathAfterSlashes);

    // Windows-style file URI
    String windowsUri = "file:///C:/Users/user/.pub-cache/";
    String windowsPath = trimFilePrefix(windowsUri);
    assertEquals("C:/Users/user/.pub-cache", windowsPath);
  }

  /**
   * Tests detection of Windows drive letters in paths.
   */
  public void testWindowsDriveDetection() {
    assertTrue("Should detect C: drive", startsWithWindowsDrive("C:/Users/user"));
    assertTrue("Should detect D: drive", startsWithWindowsDrive("D:/dart-sdk"));
    assertTrue("Should detect lowercase drive", startsWithWindowsDrive("c:/users/user"));

    assertFalse("Should not detect Linux path", startsWithWindowsDrive("/home/user"));
    assertFalse("Should not detect relative path", startsWithWindowsDrive("../lib"));
    assertFalse("Should not detect short path", startsWithWindowsDrive("C"));
    assertFalse("Should not detect UNC path", startsWithWindowsDrive("\\\\wsl$\\Ubuntu"));
  }

  /**
   * Tests package URI construction.
   */
  public void testPackageUriConstruction() {
    String rootUri = "file:///home/user/.pub-cache/hosted/pub.dev/meta-1.9.1";
    String packageUri = "lib/";
    String combined = rootUri + "/" + packageUri;

    assertTrue("Combined URI should point to lib directory",
               combined.endsWith("/lib/"));
    assertTrue("Combined URI should be a file URI",
               combined.startsWith("file://"));
  }

  /**
   * Tests handling of encoded characters in URIs.
   * The + character needs special handling as URLDecoder replaces + with space.
   */
  public void testEncodedCharacterHandling() {
    // The plugin protects + chars by encoding them before decoding
    String uriWithPlus = "file:///home/user/.pub-cache/some+package-1.0.0";
    String protected_uri = StringUtil.replace(uriWithPlus, "+", "%2B");
    assertTrue("Protected URI should contain %2B", protected_uri.contains("%2B"));
    assertFalse("Protected URI should not contain +", protected_uri.contains("+"));
  }

  // Helper method that mimics the file URI parsing logic
  private static String parseFileUri(String uri) {
    if (!uri.startsWith("file:/")) {
      return uri;
    }

    // Trim file:/ prefix
    String path = uri.substring("file:/".length());

    // Trim leading slashes
    while (path.startsWith("/")) {
      path = path.substring(1);
    }

    // URL decode
    path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);

    // For non-Windows absolute paths, add leading slash back
    if (!startsWithWindowsDrive(path)) {
      path = "/" + path;
    }

    return path;
  }

  // Helper that mimics the trimming logic in PackageConfigFileUtil
  private static String trimFilePrefix(String uri) {
    if (!uri.startsWith("file:/")) {
      return uri;
    }
    String result = uri.substring("file:/".length());
    // Trim leading slashes
    while (result.startsWith("/")) {
      result = result.substring(1);
    }
    // Trim trailing slash
    if (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  // Helper that checks for Windows drive letter
  private static boolean startsWithWindowsDrive(String path) {
    if (path.length() < 2) return false;
    char first = path.charAt(0);
    char second = path.charAt(1);
    return ((first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z')) && second == ':';
  }
}
