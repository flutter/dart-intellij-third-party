/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.customization.LspCodeActionsDisabled
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk
import java.lang.reflect.Proxy
import org.eclipse.lsp4j.CodeAction

class DartLspCodeActionsSupportTest : DartCodeInsightFixtureTestCase() {

    private fun stubLspServer(): LspServer =
        Proxy.newProxyInstance(
            LspServer::class.java.classLoader,
            arrayOf(LspServer::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getProject" -> project
                "getInitializeResult" -> null
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        } as LspServer

    private fun codeAction(title: String, kind: String?): CodeAction =
        CodeAction(title).apply {
            this.kind = kind
        }

    fun testCreateIntentionActionExcludesSourceKinds() {
        val lspServer = stubLspServer()
        val excludedKinds = listOf(
            "source",
            "source.organizeImports",
            "source.sortMembers",
            "source.fixAll",
            "source.custom.subkind",
        )

        for (kind in excludedKinds) {
            val action = codeAction("Action ($kind)", kind)
            assertNull(
                "Expected createIntentionAction to return null for kind '$kind'",
                DartLspCodeActionsSupport.createIntentionAction(lspServer, action),
            )
        }
    }

    fun testCreateIntentionActionRetainsNonSourceKinds() {
        val lspServer = stubLspServer()
        val allowedKinds = listOf(
            "refactor",
            "refactor.extract",
            "refactor.flutter.wrap.generic",
            null,
            "sourcefoo",
            "source_action",
            "",
            "SOURCE",
        )

        for (kind in allowedKinds) {
            val title = "Action ($kind)"
            val intention = DartLspCodeActionsSupport.createIntentionAction(lspServer, codeAction(title, kind))
            assertNotNull("Expected non-null LspIntentionAction for kind '$kind'", intention)
            assertEquals(title, intention?.text)
        }
    }

    fun testCreateQuickFixIsNotFiltered() {
        val lspServer = stubLspServer()
        val quickFixKinds = listOf(
            "quickfix",
            "quickfix.import.librarySdk",
            "quickfix.fixAll",
            "source",
            "source.fixAll",
        )

        for (kind in quickFixKinds) {
            val title = "Fix ($kind)"
            val quickFix = DartLspCodeActionsSupport.createQuickFix(lspServer, codeAction(title, kind))
            assertNotNull("Expected createQuickFix to remain unfiltered for kind '$kind'", quickFix)
            assertEquals(title, quickFix?.text)
        }
    }

    fun testDescriptorCodeActionsCustomizerReflectsLspCodeActionsEnabledState() {
        val sdk = requireNotNull(DartSdk.getDartSdk(project)) { "Dart SDK should be configured in test fixture" }
        val versionField = DartSdk::class.java.getDeclaredField("myVersion").apply { isAccessible = true }
        val originalVersion = sdk.version
        val descriptor = DartLspServerDescriptor(project)

        try {
            // 1. Sufficient SDK version + experimental LSP enabled -> DartLspCodeActionsSupport
            versionField.set(sdk, DartAnalysisServerService.MIN_LSP_CODE_ACTIONS_SDK_VERSION)
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
            assertTrue(DartConfigurable.isLspCodeActionsEnabled(project))
            assertSame(DartLspCodeActionsSupport, descriptor.lspCustomization.codeActionsCustomizer)

            // 2. Experimental LSP disabled -> LspCodeActionsDisabled
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
            assertFalse(DartConfigurable.isLspCodeActionsEnabled(project))
            assertSame(LspCodeActionsDisabled, descriptor.lspCustomization.codeActionsCustomizer)

            // 3. Experimental LSP enabled, but SDK older than MIN_LSP_CODE_ACTIONS_SDK_VERSION -> LspCodeActionsDisabled
            versionField.set(sdk, "3.13.0")
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
            assertFalse(DartConfigurable.isLspCodeActionsEnabled(project))
            assertSame(LspCodeActionsDisabled, descriptor.lspCustomization.codeActionsCustomizer)
        } finally {
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
            versionField.set(sdk, originalVersion)
        }
    }
}
