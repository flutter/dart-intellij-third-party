// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.resolve;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.ide.runner.DartExecutionHelper;
import com.jetbrains.lang.dart.psi.DartClass;
import com.jetbrains.lang.dart.psi.DartType;
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration;
import com.jetbrains.lang.dart.util.DartResolveUtil;

import java.util.List;

/**
 * Test the Dart resolve functionality.
 * <p>
 * This class tests the Dart resolve functionality, which is used to resolve references to Dart elements, such as classes, methods, and variables.
 */
public class DartResolveTest extends DartCodeInsightFixtureTestCase {

  // Parts with imports: https://github.com/dart-lang/language/blob/main/accepted/future-releases/parts-with-imports/feature-specification.md

  public void testFindLibraryForNestedParts() {
    final PsiFile lib = myFixture.addFileToProject("lib.dart", "part 'a.dart';");
    final PsiFile a = myFixture.addFileToProject("a.dart", "part of 'lib.dart';\npart 'b.dart';");
    final PsiFile b = myFixture.addFileToProject("b.dart", "part of 'a.dart';\npart 'c.dart';");
    final PsiFile c = myFixture.addFileToProject("c.dart", "part of 'b.dart';");

    assertEquals(List.of(lib.getVirtualFile()), DartResolveUtil.findLibrary(lib));
    assertEquals(List.of(lib.getVirtualFile()), DartResolveUtil.findLibrary(a));
    assertEquals(List.of(lib.getVirtualFile()), DartResolveUtil.findLibrary(b));
    assertEquals(List.of(lib.getVirtualFile()), DartResolveUtil.findLibrary(c));

    assertTrue(DartResolveUtil.sameLibrary(c, lib));
    assertTrue(DartResolveUtil.sameLibrary(c, a));
  }

  public void testFindLibraryForUnresolvedParentOfPart() {
    final PsiFile a = myFixture.addFileToProject("a.dart", "part of 'missing.dart';\npart 'b.dart';");
    final PsiFile b = myFixture.addFileToProject("b.dart", "part of 'a.dart';");

    // The top of the resolvable chain is treated as the library.
    assertEquals(List.of(a.getVirtualFile()), DartResolveUtil.findLibrary(a));
    assertEquals(List.of(a.getVirtualFile()), DartResolveUtil.findLibrary(b));
  }

  public void testFindLibraryWithPartOfCycle() {
    final PsiFile a = myFixture.addFileToProject("a.dart", "part of 'b.dart';");
    final PsiFile b = myFixture.addFileToProject("b.dart", "part of 'a.dart';");
    final PsiFile self = myFixture.addFileToProject("self.dart", "part of 'self.dart';");

    // A file in a 'part of' cycle is treated as its own library.
    assertEquals(List.of(a.getVirtualFile()), DartResolveUtil.findLibrary(a));
    assertEquals(List.of(b.getVirtualFile()), DartResolveUtil.findLibrary(b));
    assertEquals(List.of(self.getVirtualFile()), DartResolveUtil.findLibrary(self));
    assertFalse(DartResolveUtil.sameLibrary(a, b));
  }

  public void testLibraryNameForUnresolvedParentOfPart() {
    myFixture.addFileToProject("a.dart", "part of 'missing.dart';\npart 'b.dart';");
    final PsiFile b = myFixture.addFileToProject("b.dart", "part of 'a.dart';");

    // The chain stops at 'a.dart', which has no library name, so its file name is used.
    assertEquals("a.dart", DartResolveUtil.getLibraryName(b));
  }

  public void testFindLibraryUpdatesWhenIntermediatePartOfChanges() {
    final PsiFile lib1 = myFixture.addFileToProject("lib1.dart", "part 'a.dart';");
    final PsiFile lib2 = myFixture.addFileToProject("lib2.dart", "part 'a.dart';");
    final PsiFile a = myFixture.addFileToProject("a.dart", "part of 'lib1.dart';\npart 'b.dart';");
    final PsiFile b = myFixture.addFileToProject("b.dart", "part of 'a.dart';");
    assertEquals(List.of(lib1.getVirtualFile()), DartResolveUtil.findLibrary(b));

    // The cached chain of 'b.dart' depends on the 'part of' directive of 'a.dart'.
    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(a);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.setText("part of 'lib2.dart';\npart 'b.dart';");
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    });

    assertEquals(List.of(lib2.getVirtualFile()), DartResolveUtil.findLibrary(b));
  }

  public void testFindLibraryInDumbModeMatchesSmartMode() throws Exception {
    final PsiFile lib = myFixture.addFileToProject("lib.dart", "part 'a.dart';");
    myFixture.addFileToProject("a.dart", "part of 'lib.dart';\npart 'b.dart';");
    final PsiFile b = myFixture.addFileToProject("b.dart", "part of 'a.dart';");

    // In dumb mode DartPartOfIndex is unavailable, so every file in the chain is parsed instead.
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      assertEquals(List.of(lib.getVirtualFile()), DartResolveUtil.findLibrary(b));
    });
    assertEquals(List.of(lib.getVirtualFile()), DartResolveUtil.findLibrary(b));
  }

  public void testFindLibraryDoesNotParseLibrary() throws Exception {
    // Create virtual files only, so that no PSI is loaded up front.
    final VirtualFile libFile = myFixture.getTempDirFixture().createFile("lib.dart", "part 'a.dart';");
    myFixture.getTempDirFixture().createFile("a.dart", "part of 'lib.dart';\npart 'b.dart';");
    final VirtualFile bFile = myFixture.getTempDirFixture().createFile("b.dart", "part of 'a.dart';");
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    final PsiFile b = psiManager.findFile(bFile);
    assertNotNull(b);

    assertEquals(List.of(libFile), DartResolveUtil.findLibrary(b));

    // DartPartOfIndex says 'lib.dart' isn't a part, so the walk stops there without parsing it.
    final PsiFile lib = psiManager.findFile(libFile);
    assertInstanceOf(lib, PsiFileImpl.class);
    assertFalse("lib.dart should not have been parsed", ((PsiFileImpl)lib).isContentsLoaded());
  }

  public void testExplicitDartCoreImportInLibraryReplacesImplicitImport() {
    myFixture.addFileToProject("lib.dart", """
      import 'dart:core' hide String;
      part 'a.dart';
      part 'b.dart';
      """);
    final PsiFile a = myFixture.addFileToProject("a.dart", "part of 'lib.dart';\nString aString;");
    final PsiFile b = myFixture.addFileToProject("b.dart", """
      part of 'lib.dart';
      import 'dart:core';
      String bString;
      """);

    // The library's explicit 'dart:core' import replaces the implicit one, and the part inherits it.
    assertVariableTypeUnresolved(a, "aString");
    // A part's own 'dart:core' import is visible in that part.
    assertVariableTypeResolvesTo(b, "bString", "String", "string.dart");
  }

  public void testLibraryNameForNestedParts() {
    myFixture.addFileToProject("named/lib.dart", "library my.lib;\npart 'a.dart';");
    myFixture.addFileToProject("named/a.dart", "part of 'lib.dart';\npart 'b.dart';");
    final PsiFile namedB = myFixture.addFileToProject("named/b.dart", "part of 'a.dart';");

    myFixture.addFileToProject("unnamed/lib.dart", "part 'a.dart';");
    myFixture.addFileToProject("unnamed/a.dart", "part of 'lib.dart';\npart 'b.dart';");
    final PsiFile unnamedB = myFixture.addFileToProject("unnamed/b.dart", "part of 'a.dart';");

    assertEquals("my.lib", DartResolveUtil.getLibraryName(namedB));
    assertEquals("lib.dart", DartResolveUtil.getLibraryName(unnamedB));
  }

  public void testFindLibraryForLegacyPartOfNameMatchingSeveralLibraries() {
    final PsiFile lib1 = myFixture.addFileToProject("lib1.dart", "library my.lib;\npart 'p.dart';");
    final PsiFile lib2 = myFixture.addFileToProject("lib2.dart", "library my.lib;\npart 'p.dart';");
    final PsiFile p = myFixture.addFileToProject("p.dart", "part of my.lib;");

    assertSameElements(DartResolveUtil.findLibrary(p), lib1.getVirtualFile(), lib2.getVirtualFile());
  }

  public void testTypeResolutionUsesImportsOfPartAndItsAncestors() {
    myFixture.addFileToProject("imported_by_lib.dart", "class FromLib {}");
    myFixture.addFileToProject("imported_by_a.dart", "class FromA {}");
    myFixture.addFileToProject("imported_by_b.dart", "class FromB {}");

    final PsiFile lib = myFixture.addFileToProject("lib.dart", """
      import 'imported_by_lib.dart';
      part 'a.dart';
      part 'sibling.dart';
      FromA libFromA;
      """);
    final PsiFile a = myFixture.addFileToProject("a.dart", """
      part of 'lib.dart';
      import 'imported_by_a.dart';
      part 'b.dart';
      FromLib aFromLib;
      FromA aFromA;
      FromB aFromB;
      """);
    final PsiFile b = myFixture.addFileToProject("b.dart", """
      part of 'a.dart';
      import 'imported_by_b.dart';
      FromLib bFromLib;
      FromA bFromA;
      FromB bFromB;
      """);
    final PsiFile sibling = myFixture.addFileToProject("sibling.dart", """
      part of 'lib.dart';
      FromA siblingFromA;
      """);

    // A part sees its own imports and those of its ancestors...
    assertVariableTypeResolvesTo(b, "bFromLib", "FromLib", "imported_by_lib.dart");
    assertVariableTypeResolvesTo(b, "bFromA", "FromA", "imported_by_a.dart");
    assertVariableTypeResolvesTo(b, "bFromB", "FromB", "imported_by_b.dart");
    assertVariableTypeResolvesTo(a, "aFromLib", "FromLib", "imported_by_lib.dart");
    assertVariableTypeResolvesTo(a, "aFromA", "FromA", "imported_by_a.dart");

    // ...but not those of its children or siblings.
    assertVariableTypeUnresolved(lib, "libFromA");
    assertVariableTypeUnresolved(a, "aFromB");
    assertVariableTypeUnresolved(sibling, "siblingFromA");
  }

  public void testTypeResolutionFindsDeclarationInNestedPart() {
    final PsiFile lib = myFixture.addFileToProject("lib.dart", "part 'a.dart';\nDeclaredInB fromLib;");
    myFixture.addFileToProject("a.dart", "part of 'lib.dart';\npart 'b.dart';");
    myFixture.addFileToProject("b.dart", "part of 'a.dart';\nclass DeclaredInB {}");

    assertVariableTypeResolvesTo(lib, "fromLib", "DeclaredInB", "b.dart");
  }

  public void testLibraryDeclarationShadowsImportOfPart() {
    myFixture.addFileToProject("other.dart", "class Foo {}");
    myFixture.addFileToProject("lib.dart", "part 'a.dart';\npart 'sibling.dart';");
    myFixture.addFileToProject("sibling.dart", "part of 'lib.dart';\nclass Foo {}");
    myFixture.addFileToProject("a.dart", "part of 'lib.dart';\npart 'b.dart';");
    final PsiFile b = myFixture.addFileToProject("b.dart", """
      part of 'a.dart';
      import 'other.dart';
      Foo foo;
      """);

    // Declarations anywhere in the library shadow imported names, including the part's own imports.
    assertVariableTypeResolvesTo(b, "foo", "Foo", "sibling.dart");
  }

  public void testImportOfPartShadowsImportOfAncestor() {
    myFixture.addFileToProject("outer.dart", "class Foo {}");
    myFixture.addFileToProject("inner.dart", "class Foo {}");
    final PsiFile lib = myFixture.addFileToProject("lib.dart", """
      import 'outer.dart';
      part 'a.dart';
      Foo libFoo;
      """);
    final PsiFile a = myFixture.addFileToProject("a.dart", """
      part of 'lib.dart';
      import 'inner.dart';
      Foo aFoo;
      """);

    assertVariableTypeResolvesTo(a, "aFoo", "Foo", "inner.dart");
    assertVariableTypeResolvesTo(lib, "libFoo", "Foo", "outer.dart");
  }

  public void testShowHideAndPrefixedImportsInPart() {
    myFixture.addFileToProject("show.dart", "class Shown {}\nclass NotShown {}");
    myFixture.addFileToProject("hide.dart", "class Hidden {}\nclass NotHidden {}");
    myFixture.addFileToProject("prefixed.dart", "class Prefixed {}");
    myFixture.addFileToProject("lib.dart", "part 'a.dart';");
    final PsiFile a = myFixture.addFileToProject("a.dart", """
      part of 'lib.dart';
      import 'show.dart' show Shown;
      import 'hide.dart' hide Hidden;
      import 'prefixed.dart' as p;
      Shown shown;
      NotShown notShown;
      Hidden hidden;
      NotHidden notHidden;
      Prefixed prefixed;
      """);

    assertVariableTypeResolvesTo(a, "shown", "Shown", "show.dart");
    assertVariableTypeUnresolved(a, "notShown");
    assertVariableTypeUnresolved(a, "hidden");
    assertVariableTypeResolvesTo(a, "notHidden", "NotHidden", "hide.dart");
    // Names from a prefixed import are only reachable as 'p.Prefixed'.
    assertVariableTypeUnresolved(a, "prefixed");
  }

  public void testExportInPartOfImportedLibrary() {
    myFixture.addFileToProject("exported.dart", "class Exported {}");
    myFixture.addFileToProject("other_lib.dart", "part 'other_part.dart';");
    myFixture.addFileToProject("other_part.dart", "part of 'other_lib.dart';\nexport 'exported.dart';");
    final PsiFile lib = myFixture.addFileToProject("lib.dart", "import 'other_lib.dart';\nExported exported;");

    // Exports in a part file contribute to the export namespace of its library.
    assertVariableTypeResolvesTo(lib, "exported", "Exported", "exported.dart");
  }

  public void testHidingDartCoreInPartDoesNotHideLibraryImplicitImport() {
    final PsiFile lib = myFixture.addFileToProject("lib.dart", "part 'a.dart';\nString libString;");
    final PsiFile a = myFixture.addFileToProject("a.dart", """
      part of 'lib.dart';
      import 'dart:core' hide String;
      String aString;
      """);

    // Control: 'String' comes from the implicit 'dart:core' import of the library (the mock SDK declares it in core/string.dart).
    assertVariableTypeResolvesTo(lib, "libString", "String", "string.dart");
    // The implicit 'dart:core' import belongs to the library file and is inherited by the part, so the part's own
    // 'import "dart:core" hide String' doesn't remove 'String' from scope.
    assertVariableTypeResolvesTo(a, "aString", "String", "string.dart");
  }

  public void testPrivateDeclarationInSiblingPart() {
    myFixture.addFileToProject("lib.dart", "part 'a.dart';\npart 'sibling.dart';");
    myFixture.addFileToProject("sibling.dart", "part of 'lib.dart';\nclass _Private {}");
    myFixture.addFileToProject("a.dart", "part of 'lib.dart';\npart 'b.dart';");
    final PsiFile b = myFixture.addFileToProject("b.dart", "part of 'a.dart';\n_Private private;");

    assertVariableTypeResolvesTo(b, "private", "_Private", "sibling.dart");
  }

  /**
   * Returns the declared type of the top-level variable named {@code variableName} in {@code file}.
   */
  private static DartType findVariableType(final PsiFile file, final String variableName) {
    for (DartVarAccessDeclaration declaration : PsiTreeUtil.findChildrenOfType(file, DartVarAccessDeclaration.class)) {
      if (variableName.equals(declaration.getComponentName().getName())) {
        final DartType type = declaration.getType();
        assertNotNull("Variable '" + variableName + "' in " + file.getName() + " has no declared type", type);
        return type;
      }
    }
    fail("Variable '" + variableName + "' not found in " + file.getName());
    return null;
  }

  private static DartClass resolveVariableTypeToClass(final PsiFile file, final String variableName) {
    final PsiElement resolved = findVariableType(file, variableName).resolveReference();
    final String description = "type of '" + variableName + "' in " + file.getName();
    assertNotNull(description + " should resolve", resolved);
    final DartClass dartClass = PsiTreeUtil.getParentOfType(resolved, DartClass.class, false);
    assertNotNull(description + " should resolve to a class, got " + resolved, dartClass);
    return dartClass;
  }

  private static void assertVariableTypeResolvesTo(final PsiFile file,
                                                   final String variableName,
                                                   final String expectedClassName,
                                                   final String expectedFileName) {
    final DartClass dartClass = resolveVariableTypeToClass(file, variableName);
    assertEquals(expectedClassName, dartClass.getName());
    assertEquals("file declaring the type of '" + variableName + "' in " + file.getName(),
                 expectedFileName, dartClass.getContainingFile().getName());
  }

  private static void assertVariableTypeUnresolved(final PsiFile file, final String variableName) {
    assertNull("type of '" + variableName + "' in " + file.getName() + " should not resolve",
               findVariableType(file, variableName).resolveReference());
  }

  public void testResolveAndUseScope() {
    final VirtualFile inContent = myFixture.addFileToProject("inContentOutsideDartRoot.dart", "").getVirtualFile();

    myFixture.addFileToProject("DartProject3/pubspec.yaml", "name: DartProject3");
    final VirtualFile inProject3Web = myFixture.addFileToProject("DartProject3/web/inProject3Web.dart", "").getVirtualFile();
    final VirtualFile inProject3Lib = myFixture.addFileToProject("DartProject3/lib/inProject3Lib.dart", "").getVirtualFile();

    myFixture.addFileToProject("DartProject2/pubspec.yaml", "name: DartProject2");
    final VirtualFile inProject2Web = myFixture.addFileToProject("DartProject2/web/inProject2Web.dart", "").getVirtualFile();
    final VirtualFile inProject2Lib = myFixture.addFileToProject("DartProject2/lib/inProject2Lib.dart", "").getVirtualFile();

    myFixture.addFileToProject("DartProject1/pubspec.yaml", "name: DartProject1").getVirtualFile();
    final VirtualFile inProject1Root = myFixture.addFileToProject("DartProject1/inProject1Root.dart", "").getVirtualFile();
    final VirtualFile inProject1Lib = myFixture.addFileToProject("DartProject1/lib/inLib.dart", "").getVirtualFile();
    final VirtualFile inProject1Web = myFixture.addFileToProject("DartProject1/web/inWeb.dart", "").getVirtualFile();
    final VirtualFile inProject1WebSub = myFixture.addFileToProject("DartProject1/web/sub/inWebSub.dart", "").getVirtualFile();
    final VirtualFile inProject1Test = myFixture.addFileToProject("DartProject1/test/inTest.dart", "").getVirtualFile();
    final VirtualFile inProject1Example = myFixture.addFileToProject("DartProject1/example/inExample.dart", "").getVirtualFile();

    doTestResolveScope(inContent,
                       new VirtualFile[]{inContent, inProject2Web, inProject2Lib, inProject3Web, inProject3Lib, inProject1Root,
                         inProject1Lib, inProject1Web, inProject1WebSub, inProject1Test, inProject1Example},
                       VirtualFile.EMPTY_ARRAY);
    doTestResolveScope(inProject1Lib,
                       new VirtualFile[]{inProject1Lib},
                       new VirtualFile[]{inContent, inProject2Web, inProject2Lib, inProject3Web, inProject3Lib, inProject1Root,
                         inProject1Web, inProject1WebSub, inProject1Test, inProject1Example});
    doTestResolveScope(inProject1Web,
                       new VirtualFile[]{inProject1Lib, inProject1Web, inProject1WebSub},
                       new VirtualFile[]{inContent, inProject2Web, inProject2Lib, inProject3Web, inProject3Lib, inProject1Root,
                         inProject1Test, inProject1Example});
    doTestResolveScope(inProject1WebSub,
                       new VirtualFile[]{inProject1Lib, inProject1Web, inProject1WebSub},
                       new VirtualFile[]{inContent, inProject2Web, inProject2Lib, inProject3Web, inProject3Lib, inProject1Root,
                         inProject1Test, inProject1Example});
    doTestResolveScope(inProject1Example,
                       new VirtualFile[]{inProject1Lib, inProject1Example},
                       new VirtualFile[]{inContent, inProject2Web, inProject2Lib, inProject3Web, inProject3Lib, inProject1Root,
                         inProject1Test, inProject1Web, inProject1WebSub});
    doTestResolveScope(inProject1Root,
                       new VirtualFile[]{inProject1Root, inProject1Lib, inProject1Web, inProject1WebSub, inProject1Test,
                         inProject1Example},
                       new VirtualFile[]{inContent, inProject2Web, inProject2Lib, inProject3Web, inProject3Lib});
    // DartExecutionHelper.getScopeOfFilesThatMayAffectExecution() is not called for tests in production, so we don't handle 'test' folder in a special way
    doTestResolveScope(inProject1Test,
                       new VirtualFile[]{inProject1Lib, inProject1Test},
                       new VirtualFile[]{inProject1Root, inProject1Web, inProject1WebSub, inProject1Example, inContent, inProject2Web,
                         inProject2Lib, inProject3Web, inProject3Lib});
  }

  private void doTestResolveScope(final VirtualFile contextFile,
                                  final VirtualFile[] expectedInScope,
                                  final VirtualFile[] expectedOutsideScope) {
    final GlobalSearchScope scope = DartExecutionHelper.getScopeOfFilesThatMayAffectExecution(getProject(), contextFile);

    if (scope == null) {
      assertNull("Null scope not expected for " + contextFile.getPath(), expectedInScope);
      return;
    }

    if (expectedInScope == null) {
      fail("Null scope expected for " + contextFile.getPath());
      return;
    }

    for (VirtualFile file : expectedInScope) {
      assertTrue("Expected to be in scope: " + file.getPath(), scope.contains(file));
    }

    for (VirtualFile file : expectedOutsideScope) {
      assertFalse("Expected to be out of scope: " + file.getPath(), scope.contains(file));
    }
  }
}
