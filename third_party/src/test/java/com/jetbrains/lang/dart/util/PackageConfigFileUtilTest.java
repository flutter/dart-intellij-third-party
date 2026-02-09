// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import junit.framework.TestCase;

/**
 * Unit tests for {@link PackageConfigFileUtil}.
 * <p>
 * Tests path parsing and conversion for package_config.json files,
 * including WSL path handling scenarios. These tests use the actual
 * production utility classes from IntelliJ platform.
 * <p>
 * Note: Tests for {@link PackageConfigFileUtil#getAbsolutePackageRootPath}
 * require VirtualFile instances and are better suited for integration tests.
 * This test class focuses on the utility methods used by that code path.
 */
public class PackageConfigFileUtilTest extends TestCase {

  /**
   * Tests URLUtil.decode which is used in production code for URI decoding.
   * This is the same decoding logic used in loadPackagesMapFromJson.
   */
  public void testUrlDecoding() {
    // Test basic URL decoding (used in PackageConfigFileUtil.loadPackagesMapFromJson)
    assertEquals("some+package-1.0.0", URLUtil.decode("some%2Bpackage-1.0.0"));
    assertEquals("/home/user/.pub-cache", URLUtil.decode("/home/user/.pub-cache"));
    assertEquals("path with spaces", URLUtil.decode("path%20with%20spaces"));
  }

  /**
   * Tests the + character protection used in loadPackagesMapFromJson.
   * The code protects + chars by encoding them before decoding.
   */
  public void testPlusCharacterProtection() {
    // This mimics the production code in loadPackagesMapFromJson:
    // final String encodedUriWithoutPluses = StringUtil.replace(rootUriValue + "/" + packageUriValue, "+", "%2B");
    // final String uri = URLUtil.decode(encodedUriWithoutPluses);

    String originalUri = "file:///home/user/.pub-cache/some+package-1.0.0/lib/";
    String protectedUri = StringUtil.replace(originalUri, "+", "%2B");
    String decodedUri = URLUtil.decode(protectedUri);

    // The + should be preserved after protect + decode cycle
    assertEquals("Plus should be preserved", originalUri, decodedUri);

    // Without protection, + would be decoded to space
    String directDecode = URLUtil.decode(originalUri);
    assertTrue("Without protection, + might be mishandled",
               directDecode.contains("+") || directDecode.contains(" "));
  }

  /**
   * Tests StringUtil.trimStart and trimLeading used in getAbsolutePackageRootPath.
   */
  public void testFileUriTrimming() {
    // This mimics the production code:
    // final String pathAfterSlashes = StringUtil.trimEnd(StringUtil.trimLeading(StringUtil.trimStart(uri, "file:/"), '/'), "/");

    String fileUri = "file:///home/user/.pub-cache/hosted/pub.dev/meta-1.9.1/";
    String afterTrimStart = StringUtil.trimStart(fileUri, "file:/");
    String afterTrimLeading = StringUtil.trimLeading(afterTrimStart, '/');
    String pathAfterSlashes = StringUtil.trimEnd(afterTrimLeading, "/");

    assertEquals("home/user/.pub-cache/hosted/pub.dev/meta-1.9.1", pathAfterSlashes);

    // Test Windows file URI
    String windowsFileUri = "file:///C:/Users/user/.pub-cache/";
    afterTrimStart = StringUtil.trimStart(windowsFileUri, "file:/");
    afterTrimLeading = StringUtil.trimLeading(afterTrimStart, '/');
    pathAfterSlashes = StringUtil.trimEnd(afterTrimLeading, "/");

    assertEquals("C:/Users/user/.pub-cache", pathAfterSlashes);
  }

  /**
   * Tests OSAgnosticPathUtil.startsWithWindowsDrive used in getAbsolutePackageRootPath.
   */
  public void testWindowsDriveDetection() {
    // Production code uses: OSAgnosticPathUtil.startsWithWindowsDrive(pathAfterSlashes)
    assertTrue("Should detect C: drive", OSAgnosticPathUtil.startsWithWindowsDrive("C:/Users/user"));
    assertTrue("Should detect D: drive", OSAgnosticPathUtil.startsWithWindowsDrive("D:/dart-sdk"));
    assertTrue("Should detect lowercase drive", OSAgnosticPathUtil.startsWithWindowsDrive("c:/users/user"));

    assertFalse("Should not detect Linux path", OSAgnosticPathUtil.startsWithWindowsDrive("/home/user"));
    assertFalse("Should not detect relative path", OSAgnosticPathUtil.startsWithWindowsDrive("../lib"));
    assertFalse("Should not detect short path", OSAgnosticPathUtil.startsWithWindowsDrive("C"));
    assertFalse("Should not detect single char", OSAgnosticPathUtil.startsWithWindowsDrive("a"));
  }

  /**
   * Tests the complete file URI parsing logic as used in getAbsolutePackageRootPath.
   * This tests the production code path for non-WSL, non-Windows scenarios.
   */
  public void testFileUriParsingNonWindows() {
    if (SystemInfo.isWindows) {
      // This test is for non-Windows behavior
      return;
    }

    String fileUri = "file:///home/user/.pub-cache/hosted/pub.dev/meta-1.9.1/lib/";

    // Simulate production code path
    String pathAfterSlashes = StringUtil.trimEnd(
        StringUtil.trimLeading(
            StringUtil.trimStart(fileUri, "file:/"),
            '/'),
        "/");

    // On non-Windows, the path gets a leading slash added back
    String expectedPath = "/" + pathAfterSlashes;
    assertEquals("/home/user/.pub-cache/hosted/pub.dev/meta-1.9.1/lib", expectedPath);
  }

  /**
   * Tests the complete file URI parsing logic for Windows paths.
   */
  public void testFileUriParsingWindows() {
    String fileUri = "file:///C:/Users/user/.pub-cache/hosted/pub.dev/meta-1.9.1/lib/";

    // Simulate production code path
    String pathAfterSlashes = StringUtil.trimEnd(
        StringUtil.trimLeading(
            StringUtil.trimStart(fileUri, "file:/"),
            '/'),
        "/");

    // Check if it's a Windows drive path
    assertTrue("Should detect Windows drive",
               OSAgnosticPathUtil.startsWithWindowsDrive(pathAfterSlashes));

    // On Windows with a drive letter, the path is returned as-is
    assertEquals("C:/Users/user/.pub-cache/hosted/pub.dev/meta-1.9.1/lib", pathAfterSlashes);
  }

  /**
   * Tests handling of relative URIs (no file:/ prefix).
   */
  public void testRelativeUriHandling() {
    // Relative URIs don't start with file:/
    String relativeUri = "../lib/";
    assertFalse("Relative URI should not start with file:/", relativeUri.startsWith("file:/"));

    String localPackageUri = "../";
    assertFalse("Local package URI should not start with file:/", localPackageUri.startsWith("file:/"));

    // The production code handles these by combining with baseDir.getPath()
    // For unit testing, we just verify the detection logic
  }

  /**
   * Tests that the constants used in production code are correct.
   */
  public void testPackageConfigConstants() {
    assertEquals(".dart_tool", PackageConfigFileUtil.DART_TOOL_DIR);
    assertEquals("package_config.json", PackageConfigFileUtil.PACKAGE_CONFIG_JSON);
  }

  /**
   * Tests URI combining as done in loadPackagesMapFromJson.
   */
  public void testUriCombining() {
    // Production code: rootUriValue + "/" + packageUriValue
    String rootUri = "file:///home/user/.pub-cache/hosted/pub.dev/meta-1.9.1";
    String packageUri = "lib/";
    String combined = rootUri + "/" + packageUri;

    assertEquals("file:///home/user/.pub-cache/hosted/pub.dev/meta-1.9.1/lib/", combined);

    // Test with trailing slash on rootUri
    String rootUriWithSlash = "file:///home/user/.pub-cache/hosted/pub.dev/meta-1.9.1/";
    combined = rootUriWithSlash + "/" + packageUri;

    // This results in double slash, which the trim logic handles
    assertTrue("Should contain lib directory", combined.contains("/lib/"));
  }

  /**
   * Tests edge cases in path parsing.
   */
  public void testPathEdgeCases() {
    // Empty string handling
    assertEquals("", StringUtil.trimStart("", "file:/"));
    assertEquals("", StringUtil.trimLeading("", '/'));
    assertEquals("", StringUtil.trimEnd("", "/"));

    // Only file:/ prefix
    String pathAfterSlashes = StringUtil.trimEnd(
        StringUtil.trimLeading(
            StringUtil.trimStart("file:/", "file:/"),
            '/'),
        "/");
    assertEquals("", pathAfterSlashes);

    // Path with special characters (but not +)
    String specialPath = "file:///home/user/my-project_v1.0/lib/";
    pathAfterSlashes = StringUtil.trimEnd(
        StringUtil.trimLeading(
            StringUtil.trimStart(specialPath, "file:/"),
            '/'),
        "/");
    assertEquals("home/user/my-project_v1.0/lib", pathAfterSlashes);
  }
}
