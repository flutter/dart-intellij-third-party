package com.jetbrains.lang.dart.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;

public class DartUrlResolverTest extends DartCodeInsightFixtureTestCase {

  public void testGetDartUrlForFile() {
    myFixture.addFileToProject("pubspec.yaml", "name: my_project\n");
    myFixture.addFileToProject("lib/main.dart", "");
    final VirtualFile mainDart = myFixture.findFileInTempDir("lib/main.dart");
    
    final DartUrlResolver resolver = DartUrlResolver.getInstance(getProject(), mainDart);
    assertEquals("package:my_project/main.dart", resolver.getDartUrlForFile(mainDart));
  }
  
  public void testFindFileByDartUrl() {
    myFixture.addFileToProject("pubspec.yaml", "name: my_project\n");
    myFixture.addFileToProject("lib/main.dart", "");
    final VirtualFile mainDart = myFixture.findFileInTempDir("lib/main.dart");
    
    final DartUrlResolver resolver = DartUrlResolver.getInstance(getProject(), mainDart);
    final VirtualFile found = resolver.findFileByDartUrl("package:my_project/main.dart");
    assertNotNull(found);
    assertEquals(mainDart, found);
  }
}
