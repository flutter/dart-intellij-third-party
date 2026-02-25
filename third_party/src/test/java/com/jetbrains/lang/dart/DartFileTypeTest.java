package com.jetbrains.lang.dart;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class DartFileTypeTest extends BasePlatformTestCase {
  public void testGetName() {
    assertEquals("Dart", DartFileType.INSTANCE.getName());
  }

  public void testGetDefaultExtension() {
    assertEquals("dart", DartFileType.INSTANCE.getDefaultExtension());
  }

  public void testGetDescription() {
    assertNotNull(DartFileType.INSTANCE.getDescription());
  }

  public void testGetIcon() {
    assertNotNull(DartFileType.INSTANCE.getIcon());
  }
}
