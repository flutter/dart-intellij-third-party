// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.pubServer

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PubServerPathHandlerTest : BasePlatformTestCase() {

  fun testServedDirAndPathForWebFolder() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("web/index.html", "<html></html>")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNotNull("Result should not be null for web folder", result)
    assertEquals("web", result!!.first.name)
    assertEquals("/index.html", result.second)
  }

  fun testServedDirAndPathForExampleFolder() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("example/main.dart", "void main() {}")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNotNull("Result should not be null for example folder", result)
    assertEquals("example", result!!.first.name)
    assertEquals("/main.dart", result.second)
  }

  fun testLibFolderReturnsNull() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("lib/foo.dart", "class Foo {}")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNull("Result should be null for files inside lib folder", result)
  }

  fun testBuildFolderReturnsNull() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("build/out.js", "// compiled JS")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNull("Result should be null for files inside build folder", result)
  }

  fun testPackagesFolderReturnsNull() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("packages/pkg.dart", "// packages")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNull("Result should be null for files inside packages folder", result)
  }

  fun testRootLevelFileReturnsNull() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("README.md", "# Test")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNull("Result should be null for files directly in Dart project root", result)
  }

  fun testEscapedUrlPath() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("web/my page.html", "<html></html>")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNotNull("Result should not be null for file with space in path", result)
    assertEquals("web", result!!.first.name)
    assertEquals("/my%20page.html", result.second)
  }
}
