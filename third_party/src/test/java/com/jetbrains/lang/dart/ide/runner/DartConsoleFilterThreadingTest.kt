package com.jetbrains.lang.dart.ide.runner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

class DartConsoleFilterThreadingTest : BasePlatformTestCase() {

  fun testDartConsoleFilterPackageUrlOnBackgroundThread() {
    myFixture.addFileToProject("pubspec.yaml", "name: my_project\n")
    myFixture.addFileToProject("lib/foo.dart", "void foo() {}\n")

    val filter = DartConsoleFilter(project)
    val line = "package:my_project/foo.dart:1:1"

    val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
      filter.applyFilter(line, line.length)
    })

    val result = future.get(10, TimeUnit.SECONDS)
    assertNotNull("Filter should resolve package URL", result)
  }

  fun testDartConsoleFilterFileUrlOnBackgroundThread() {
    val tempDir = FileUtil.createTempDirectory("dart_file_test", null)
    try {
      val testFile = File(tempDir, "my_file.dart")
      testFile.writeText("void main() {}\n")
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(testFile)

      val filter = DartConsoleFilter(project)
      val normalizedPath = testFile.path.replace('\\', '/')
      val line = "file:///$normalizedPath:1:1"

      val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
        filter.applyFilter(line, line.length)
      })

      val result = future.get(10, TimeUnit.SECONDS)
      assertNotNull("Filter should resolve file URL", result)
    } finally {
      FileUtil.delete(tempDir)
    }
  }

  fun testDartRelativePathsConsoleFilterOnBackgroundThread() {
    val tempDir = FileUtil.createTempDirectory("dart_rel_test", null)
    try {
      val testDir = File(tempDir, "test")
      testDir.mkdirs()
      val testFile = File(testDir, "my_test.dart")
      testFile.writeText("void main() {}\n")
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(testFile)

      val filter = DartRelativePathsConsoleFilter(project, tempDir.path.replace('\\', '/'))
      val line = "test/my_test.dart:1:1"

      val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
        filter.applyFilter(line, line.length)
      })

      val result = future.get(10, TimeUnit.SECONDS)
      assertNotNull("Filter should resolve relative path", result)
    } finally {
      FileUtil.delete(tempDir)
    }
  }
}
