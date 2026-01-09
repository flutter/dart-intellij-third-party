package com.jetbrains.lang.dart.util;

import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class DartUrlResolverTest extends DartCodeInsightFixtureTestCase {

  public void testDartUrl() {
    final String dartCoreUrl = "dart:core";
    final DartUrlResolver resolver = DartUrlResolver.getInstance(getProject(),
        ModuleRootManager.getInstance(getModule()).getContentRoots()[0]);
    final VirtualFile file = resolver.findFileByDartUrl(dartCoreUrl);
    assertNotNull("dart:core not found", file);
    assertEquals(dartCoreUrl, resolver.getDartUrlForFile(file));
  }

  public void testFileUrl() throws java.io.IOException {
    // DartUrlResolverImpl uses LocalFileSystem, so we must use a real file on disk.
    final java.io.File tempFile = java.io.File.createTempFile("test_file", ".dart");
    tempFile.deleteOnExit();
    final VirtualFile file = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        .refreshAndFindFileByIoFile(tempFile);
    assertNotNull(file);

    final String fileUrl = tempFile.toURI().toString();
    final DartUrlResolver resolver = DartUrlResolver.getInstance(getProject(), file);

    assertEquals(file, resolver.findFileByDartUrl(fileUrl));

    // Also verify getDartUrlForFile returns the file: url
    assertEquals(fileUrl, resolver.getDartUrlForFile(file));
  }

  public void testPackageUrl() {
    myFixture.addFileToProject("pubspec.yaml", "name: my_app\ndependencies:\n");
    final VirtualFile file = myFixture.addFileToProject("lib/my_file.dart", "").getVirtualFile();
    final String packageUrl = "package:my_app/my_file.dart";

    final DartUrlResolver resolver = DartUrlResolver.getInstance(getProject(), file);

    // We need to ensure the resolver sees the pubspec.
    // DartUrlResolverImpl initializes with the finding the pubspec.

    final VirtualFile resolvedFile = resolver.findFileByDartUrl(packageUrl);
    assertNotNull("Should resolve package:my_app/my_file.dart", resolvedFile);
    assertEquals(file, resolvedFile);

    assertEquals(packageUrl, resolver.getDartUrlForFile(file));
  }
}
