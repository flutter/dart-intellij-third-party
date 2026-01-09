package com.jetbrains.lang.dart.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class DartBuildFileUtilTest extends DartCodeInsightFixtureTestCase {

  public void testFindPackageRootBuildFile() throws IOException {
    VirtualFile buildFile = myFixture.configureByText("BUILD", "").getVirtualFile();
    VirtualFile libDir = myFixture.getTempDirFixture().findOrCreateDir("lib");
    VirtualFile file = myFixture.addFileToProject("lib/foo.dart", "").getVirtualFile();

    // The current fixture setup puts BUILD in the root of the temp content.
    // getDartProjectName depends on parent name.

    // Let's verify findPackageRootBuildFile finds the BUILD file for files in lib
    assertEquals(buildFile, DartBuildFileUtil.findPackageRootBuildFile(getProject(), file));

    // Context file is the lib directory itself
    assertEquals(buildFile, DartBuildFileUtil.findPackageRootBuildFile(getProject(), libDir));

    // Context file is the BUILD file itself
    assertEquals(buildFile, DartBuildFileUtil.findPackageRootBuildFile(getProject(), buildFile));

    // Test specific logic: build file ignored if its parent's parent has 'lib'
    // (simulating nested package inside lib?)
    // But honestly, the logic is: return file if parent2 == null OR
    // parent2.findChild("lib") == null.
    // In our case parent is src, parent2 is temp. temp usually doesn't have lib
    // unless we created it?
    // Actually in myFixture structure, we are usually in isolated temp dir.
  }

  public void testGetDartProjectName() {
    VirtualFile buildFile = myFixture.configureByText("BUILD", "").getVirtualFile();
    // The name of the project is the name of the parent directory of BUILD file.
    // In test fixture, this is usually 'src' or 'temp' depending on where we
    // created it.
    // configureByText creates in src path of fixture.

    String parentName = buildFile.getParent().getName();
    assertEquals(parentName, DartBuildFileUtil.getDartProjectName(buildFile));
  }
}
