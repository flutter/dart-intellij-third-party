/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.ide.refactoring.moveFile.DartServerMoveDartFileHandler
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil

class DartLspRenamePsiElementProcessorTest : DartCodeInsightFixtureTestCase() {

    fun testCanProcessElementGatedByExperimentalFlag() {
        val processor = DartLspRenamePsiElementProcessor()
        val file = myFixture.addFileToProject("lib/a.dart", "class A {}")
        val dir = file.containingDirectory

        // Disabled by default
        assertFalse(DartConfigurable.isExperimentalLspFeaturesEnabled(project))
        assertFalse(processor.canProcessElement(file))
        assertFalse(processor.canProcessElement(dir))

        // Enable experimental LSP
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        try {
            assertTrue(processor.canProcessElement(file))
            assertTrue(processor.canProcessElement(dir))
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        }
    }

    fun testCanProcessElementNonDartFileReturnsFalse() {
        val processor = DartLspRenamePsiElementProcessor()
        val textFile = myFixture.addFileToProject("lib/readme.txt", "hello")

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        try {
            assertFalse(processor.canProcessElement(textFile))
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        }
    }

    fun testCanProcessElementOutsideContentReturnsFalse() {
        val processor = DartLspRenamePsiElementProcessor()
        val lightFile = LightVirtualFile("outside.dart", "class Outside {}")
        val psiLightFile = checkNotNull(psiManager.findFile(lightFile))

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        try {
            assertFalse(processor.canProcessElement(psiLightFile))
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        }
    }

    fun testCanProcessElementDirectoryInNonDartModuleReturnsFalse() {
        val processor = DartLspRenamePsiElementProcessor()
        val file = myFixture.addFileToProject("lib/c.dart", "class C {}")
        val dir = file.containingDirectory

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        try {
            assertTrue(processor.canProcessElement(dir))
            assertTrue(processor.canProcessElement(file))

            WriteAction.run<Throwable> {
                DartSdkLibUtil.disableDartSdk(listOf(module))
            }
            assertFalse(DartSdkLibUtil.isDartSdkEnabled(module))
            assertNotNull(DartSdk.getDartSdk(project))

            // Directory in a non-Dart module should return false to prevent hijacking directory renames
            assertFalse(processor.canProcessElement(dir))
            // Dart files within project content are still handled
            assertTrue(processor.canProcessElement(file))
        } finally {
            WriteAction.run<Throwable> {
                DartSdkLibUtil.enableDartSdk(module)
            }
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        }
    }

    fun testFindReferencesReturnsEmptyToPreventLegacyDoubleEdits() {
        val processor = DartLspRenamePsiElementProcessor()
        val file = myFixture.addFileToProject("lib/b.dart", "class B {}")
        val references = processor.findReferences(file, file.resolveScope, true)
        assertTrue("findReferences should return empty to prevent legacy PSI string double-edits", references.isEmpty())
    }

    fun testMoveFileHandlerYieldsWhenExperimentalLspEnabled() {
        val moveHandler = DartServerMoveDartFileHandler()
        val file = myFixture.addFileToProject("lib/move_target.dart", "class MoveTarget {}")

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        try {
            assertFalse(
                "DartServerMoveDartFileHandler should yield to LspWillRenameFilesHandler when experimental LSP is enabled",
                moveHandler.canProcessElement(file)
            )
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        }
    }
}
