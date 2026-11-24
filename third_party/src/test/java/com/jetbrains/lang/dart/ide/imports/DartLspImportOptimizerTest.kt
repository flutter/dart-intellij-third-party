/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.imports

import com.intellij.platform.dartlsp.api.customization.LspOptimizeImportsDisabled
import com.intellij.platform.dartlsp.api.customization.LspOptimizeImportsSupport
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.lsp.DartLspServerDescriptor
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk

class DartLspImportOptimizerTest : DartCodeInsightFixtureTestCase() {

    fun testLegacyOptimizerIsUsedWhenExperimentalLspIsOff() {
        val file = myFixture.addFileToProject("lib/off.dart", "")
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)

        assertTrue(DartImportOptimizer().supports(file))
        val descriptor = DartLspServerDescriptor(project)
        assertSame(LspOptimizeImportsDisabled, descriptor.lspCustomization.optimizeImportsCustomizer)
    }

    fun testLegacyOptimizerIsUsedWhenSdkIsTooOldForLspCodeActions() {
        // The test SDK (3.14.0-65.0.dev) is older than the code actions gate (3.14.0-137.0.dev).
        val file = myFixture.addFileToProject("lib/old_sdk.dart", "")
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        try {
            assertTrue(DartImportOptimizer().supports(file))
            val descriptor = DartLspServerDescriptor(project)
            assertSame(LspOptimizeImportsDisabled, descriptor.lspCustomization.optimizeImportsCustomizer)
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        }
    }

    fun testLspOptimizerIsUsedWhenLspCodeActionsAreEnabled() {
        val file = myFixture.addFileToProject("lib/enabled.dart", "")
        val sdk = requireNotNull(DartSdk.getDartSdk(project))
        val versionField = DartSdk::class.java.getDeclaredField("myVersion").apply { isAccessible = true }
        val originalVersion = sdk.version
        try {
            versionField.set(sdk, "3.14.0")
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)

            assertFalse(DartImportOptimizer().supports(file))
            val descriptor = DartLspServerDescriptor(project)
            assertTrue(descriptor.lspCustomization.optimizeImportsCustomizer is LspOptimizeImportsSupport)
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
            versionField.set(sdk, originalVersion)
        }
    }
}
