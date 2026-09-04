/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.findUsages

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.util.PropertiesComponent
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartConfigurable

class DartTargetElementEvaluatorTest : DartCodeInsightFixtureTestCase() {

    override fun tearDown() {
        try {
            PropertiesComponent.getInstance(project).unsetValue("dart.lsp.experimental.enabled")
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testTargetCandidatesSuppressedWhenLspReferencesEnabled() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void helper() {}
            void main() {
              help<caret>er();
            }
            """.trimIndent()
        )

        val reference = file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Reference should be found at caret", reference)

        val evaluator = DartTargetElementEvaluator()

        // Enable experimental LSP features
        PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", true, true)

        if (DartAnalysisServerService.isLspReferencesEnabled(project)) {
            val candidates = evaluator.getTargetCandidates(reference!!)
            assertNotNull(candidates)
            assertTrue(candidates!!.isEmpty())

            val utilCandidates = TargetElementUtil.getInstance().getTargetCandidates(reference)
            assertTrue(utilCandidates.isEmpty())
        }

        // Disable experimental LSP features
        PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", false, true)
        assertFalse(DartConfigurable.isExperimentalLspFeaturesEnabled(project))
        assertNull(evaluator.getTargetCandidates(reference!!))
    }
}
