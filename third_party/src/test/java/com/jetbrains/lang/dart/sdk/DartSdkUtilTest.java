// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.sdk;

import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class DartSdkUtilTest extends TestCase {

  private File mySdkHome;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySdkHome = FileUtil.createTempDirectory("dart-sdk", null);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileUtil.delete(mySdkHome);
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
    writeVersionFile("3.14.0-137.0.dev\n");
    assertEquals("3.14.0-137.0.dev", DartSdkUtil.getSdkVersion(mySdkHome.getPath()));
    // The second call is served from the cache.
    assertEquals("3.14.0-137.0.dev", DartSdkUtil.getSdkVersion(mySdkHome.getPath()));
  }

  public void testGetSdkVersionReturnsNullWithoutVersionFile() {
    assertNull(DartSdkUtil.getSdkVersion(mySdkHome.getPath()));
  }

  private void writeVersionFile(String content) throws IOException {
    FileUtil.writeToFile(new File(mySdkHome, "version"), content);
  }
}
