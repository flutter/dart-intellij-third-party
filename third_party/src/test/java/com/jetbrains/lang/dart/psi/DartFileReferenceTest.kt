/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.psi

import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.psi.impl.DartFileReference

class DartFileReferenceTest : DartCodeInsightFixtureTestCase() {

    fun testRelativeImportExportAndPartResolutionWithoutAnalysisServer() {
        assertFalse(DartAnalysisServerService.getInstance(project).isServerProcessActive)

        val siblingFile = myFixture.addFileToProject("lib/sibling.dart", "class Sibling {}\n")
        val nestedFile = myFixture.addFileToProject("lib/sub/nested.dart", "part of '../main.dart';\n")
        val exportedFile = myFixture.addFileToProject("lib/exported.dart", "class Exported {}\n")
        val mainFile = myFixture.addFileToProject(
            "lib/main.dart",
            """
            import 'sibling.dart';
            export 'exported.dart';
            part 'sub/nested.dart';
            """.trimIndent()
        )

        val mainUriElements = PsiTreeUtil.findChildrenOfType(mainFile, DartUriElement::class.java).toList()
        assertEquals(3, mainUriElements.size)

        assertEquals(siblingFile, resolveUriElement(mainUriElements[0]))
        assertEquals(exportedFile, resolveUriElement(mainUriElements[1]))
        assertEquals(nestedFile, resolveUriElement(mainUriElements[2]))

        val partOfUri = checkNotNull(PsiTreeUtil.findChildOfType(nestedFile, DartUriElement::class.java))
        assertEquals(mainFile, resolveUriElement(partOfUri))
    }

    fun testPackageUriResolutionWithoutAnalysisServer() {
        assertFalse(DartAnalysisServerService.getInstance(project).isServerProcessActive)

        myFixture.addFileToProject("pubspec.yaml", "name: my_app\n")
        val utilFile = myFixture.addFileToProject("lib/src/util.dart", "class Util {}\n")
        val mainFile = myFixture.addFileToProject("lib/main.dart", "import 'package:my_app/src/util.dart';\n")

        val uriElement = checkNotNull(PsiTreeUtil.findChildOfType(mainFile, DartUriElement::class.java))
        assertEquals(utilFile, resolveUriElement(uriElement))
    }

    fun testDartSdkUriResolutionWithoutAnalysisServer() {
        assertFalse(DartAnalysisServerService.getInstance(project).isServerProcessActive)

        val mainFile = myFixture.addFileToProject("lib/main.dart", "import 'dart:core';\n")
        val uriElement = checkNotNull(PsiTreeUtil.findChildOfType(mainFile, DartUriElement::class.java))
        val resolved = resolveUriElement(uriElement)
        assertNotNull("Expected dart:core to resolve locally from Dart SDK", resolved)
        assertEquals("core.dart", resolved?.name)
    }

    fun testEmptyDirectoryAndMissingUrisResolveToNull() {
        myFixture.addFileToProject("lib/sub/child.dart", "")
        val mainFile = myFixture.addFileToProject(
            "lib/main.dart",
            """
            import '';
            import 'sub';
            import 'does_not_exist.dart';
            """.trimIndent()
        )

        val uriElements = PsiTreeUtil.findChildrenOfType(mainFile, DartUriElement::class.java).toList()
        assertEquals(3, uriElements.size)

        assertNull(resolveUriElement(uriElements[0]))
        assertNull(resolveUriElement(uriElements[1]))
        assertNull(resolveUriElement(uriElements[2]))
    }

    fun testReferencesSearchFindsUnopenedFileWithoutAnalysisServer() {
        assertFalse(DartAnalysisServerService.getInstance(project).isServerProcessActive)

        val barFile = myFixture.addFileToProject("lib/bar.dart", "class Bar {}\n")
        // Leave foo.dart unopened in the editor.
        myFixture.addFileToProject("lib/foo.dart", "import 'bar.dart';\n")

        val references = ReferencesSearch.search(barFile, GlobalSearchScope.projectScope(project)).findAll()
        assertEquals(1, references.size)
        val reference = references.single()
        assertTrue(reference is DartFileReference)
        assertEquals("foo.dart", reference.element.containingFile.name)
    }

    private fun resolveUriElement(uriElement: DartUriElement): PsiFile? {
        val reference = checkNotNull(uriElement.reference)
        assertTrue(reference is DartFileReference)
        return reference.resolve() as? PsiFile
    }
}
