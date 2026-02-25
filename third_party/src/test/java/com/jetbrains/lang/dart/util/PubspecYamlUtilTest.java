package com.jetbrains.lang.dart.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;

public class PubspecYamlUtilTest extends DartCodeInsightFixtureTestCase {

  public void testGetDartProjectName() {
    myFixture.addFileToProject("pubspec.yaml", "name: my_project\n");
    final VirtualFile pubspec = myFixture.findFileInTempDir("pubspec.yaml");
    assertEquals("my_project", PubspecYamlUtil.getDartProjectName(pubspec));
  }

  public void testIsPubspecFile() {
    myFixture.addFileToProject("pubspec.yaml", "");
    final VirtualFile pubspec = myFixture.findFileInTempDir("pubspec.yaml");
    assertTrue(PubspecYamlUtil.isPubspecFile(pubspec));
    
    myFixture.addFileToProject("other.yaml", "");
    final VirtualFile other = myFixture.findFileInTempDir("other.yaml");
    assertFalse(PubspecYamlUtil.isPubspecFile(other));
  }

  public void testFindPubspecYamlFile() {
    myFixture.addFileToProject("pubspec.yaml", "name: my_project\n");
    myFixture.addFileToProject("lib/main.dart", "");
    
    final VirtualFile mainDart = myFixture.findFileInTempDir("lib/main.dart");
    final VirtualFile pubspec = PubspecYamlUtil.findPubspecYamlFile(getProject(), mainDart);
    assertNotNull(pubspec);
    assertEquals("pubspec.yaml", pubspec.getName());
  }
}
