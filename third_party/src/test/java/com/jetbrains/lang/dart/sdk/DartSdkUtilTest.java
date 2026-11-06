// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.sdk;

import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class DartSdkUtilTest extends TestCase {

  private File myTempRoot;
  private File mySdkHome;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempRoot = FileUtil.createTempDirectory("dart-sdk", null);
    // getSdkVersion caches by (version file, lastModified) in a static map. Use a per-test
    // directory so a cached value from another test can't be served for the same path and
    // timestamp, even if the temp root is reused.
    mySdkHome = new File(myTempRoot, getName());
    assertTrue(mySdkHome.mkdirs());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileUtil.delete(myTempRoot);
    }
    finally {
      super.tearDown();
    }
  }

  public void testGetSdkVersionTrimsTrailingNewline() throws IOException {
    writeVersionFile("3.13.4\n");
    assertEquals("3.13.4", DartSdkUtil.getSdkVersion(mySdkHome.getPath()));
  }

  public void testGetSdkVersionTrimsTrailingCrLf() throws IOException {
    writeVersionFile("3.13.4\r\n");
    assertEquals("3.13.4", DartSdkUtil.getSdkVersion(mySdkHome.getPath()));
  }

  public void testGetSdkVersionCachedValueIsTrimmed() throws IOException {
    final File versionFile = writeVersionFile("3.14.0-137.0.dev\n");
    assertEquals("3.14.0-137.0.dev", DartSdkUtil.getSdkVersion(mySdkHome.getPath()));

    // Change the contents but keep the timestamp, so the cache key is unchanged and the
    // second call must be served from the cache rather than re-reading the file.
    final long lastModified = versionFile.lastModified();
    FileUtil.writeToFile(versionFile, "9.9.9\n");
    assertTrue(versionFile.setLastModified(lastModified));

    assertEquals("3.14.0-137.0.dev", DartSdkUtil.getSdkVersion(mySdkHome.getPath()));
  }

  public void testGetSdkVersionReturnsNullWithoutVersionFile() {
    assertNull(DartSdkUtil.getSdkVersion(mySdkHome.getPath()));
  }

  private File writeVersionFile(String content) throws IOException {
    final File versionFile = new File(mySdkHome, "version");
    FileUtil.writeToFile(versionFile, content);
    return versionFile;
  }
}
