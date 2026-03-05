package com.jetbrains.lang.dart.util;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import java.io.IOException;

public class PubspecYamlUtilTest extends DartCodeInsightFixtureTestCase {

  public void testIsPubspecFile() {
    VirtualFile file = myFixture.configureByText("pubspec.yaml", "name: foo").getVirtualFile();
    assertTrue(PubspecYamlUtil.isPubspecFile(file));

    VirtualFile file2 = myFixture.configureByText("foo.yaml", "name: foo").getVirtualFile();
    assertFalse(PubspecYamlUtil.isPubspecFile(file2));
  }

  public void testGetDartProjectName() {
    VirtualFile file1 = myFixture.configureByText("pubspec.yaml", "name: my_app").getVirtualFile();
    assertEquals("my_app", PubspecYamlUtil.getDartProjectName(file1));

    VirtualFile file2 = myFixture.configureByText("pubspec2.yaml", "name: other_app").getVirtualFile();
    assertEquals("other_app", PubspecYamlUtil.getDartProjectName(file2));

    VirtualFile file3 = myFixture.configureByText("pubspec3.yaml", "invalid: yaml").getVirtualFile();
    assertNull(PubspecYamlUtil.getDartProjectName(file3));

    VirtualFile file4 = myFixture.configureByText("pubspec4.yaml", "name: \n  nested: map").getVirtualFile();
    assertNull(PubspecYamlUtil.getDartProjectName(file4));
  }

  public void testFindPubspecYamlFile() throws IOException {
    VirtualFile pubspec = myFixture.configureByText("pubspec.yaml", "name: foo").getVirtualFile();
    VirtualFile libDir = myFixture.getTempDirFixture().findOrCreateDir("lib");
    VirtualFile file = myFixture.addFileToProject("lib/foo.dart", "").getVirtualFile();

    assertEquals(pubspec, PubspecYamlUtil.findPubspecYamlFile(getProject(), file));
    assertEquals(pubspec, PubspecYamlUtil.findPubspecYamlFile(getProject(), libDir));
    assertEquals(pubspec, PubspecYamlUtil.findPubspecYamlFile(getProject(), pubspec));

    WriteAction.run(() -> pubspec.delete(this));
    assertNull(PubspecYamlUtil.findPubspecYamlFile(getProject(), libDir));
  }
}
