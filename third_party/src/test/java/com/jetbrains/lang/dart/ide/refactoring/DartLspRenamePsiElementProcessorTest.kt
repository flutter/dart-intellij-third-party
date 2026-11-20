/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil

class DartLspRenamePsiElementProcessorTest : DartCodeInsightFixtureTestCase() {

    fun testCanProcessElementRequiresRunningLspServerNotExperimentalFlag() {
        val file = myFixture.addFileToProject("lib/a.dart", "class A {}")
        val dir = file.containingDirectory

        // Experimental LSP disabled by default, but processor depends only on running LSP server.
        assertFalse(DartConfigurable.isExperimentalLspFeaturesEnabled(project))

        val stoppedProcessor = DartLspRenamePsiElementProcessor { false }
        assertFalse(stoppedProcessor.canProcessElement(file))
        assertFalse(stoppedProcessor.canProcessElement(dir))

        val runningProcessor = DartLspRenamePsiElementProcessor { true }
        assertTrue(runningProcessor.canProcessElement(file))
        assertTrue(runningProcessor.canProcessElement(dir))
    }

    fun testCanProcessElementNonDartFileReturnsFalse() {
        val processor = DartLspRenamePsiElementProcessor { true }
        val textFile = myFixture.addFileToProject("lib/readme.txt", "hello")
        assertFalse(processor.canProcessElement(textFile))
    }

    fun testCanProcessElementOutsideContentReturnsFalse() {
        val processor = DartLspRenamePsiElementProcessor { true }
        val lightFile = LightVirtualFile("outside.dart", "class Outside {}")
        val psiLightFile = checkNotNull(psiManager.findFile(lightFile))
        assertFalse(processor.canProcessElement(psiLightFile))
    }

    fun testCanProcessElementDirectoryInNonDartModuleReturnsFalse() {
        val processor = DartLspRenamePsiElementProcessor { true }
        val file = myFixture.addFileToProject("lib/c.dart", "class C {}")
        val dir = file.containingDirectory

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
        }
    }

    fun testFindReferencesReturnsEmptyToPreventLegacyDoubleEdits() {
        val processor = DartLspRenamePsiElementProcessor { true }
        val file = myFixture.addFileToProject("lib/b.dart", "class B {}")
        val references = processor.findReferences(file, file.resolveScope, true)
        assertTrue("findReferences should return empty to prevent legacy PSI string double-edits", references.isEmpty())
    }
}
