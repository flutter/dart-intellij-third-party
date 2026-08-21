/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.hints.DartParameterNamesInlayHintsProvider
import com.jetbrains.lang.dart.hints.DartTypesInlayHintsProvider
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintKind

class DartLspInlayHintSupportTest : DartCodeInsightFixtureTestCase() {

    override fun tearDown() {
        try {
            // DeclarativeInlayHintsSettings is an application-level service; reset it so that
            // enabled providers do not leak into other tests.
            DeclarativeInlayHintsSettings.getInstance().loadState(DeclarativeInlayHintsSettings.HintsState())
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    private fun dartFile(): VirtualFile = myFixture.configureByText("test.dart", "").virtualFile

    private fun parameterHint(): InlayHint = InlayHint().apply { kind = InlayHintKind.Parameter }

    private fun typeHint(): InlayHint = InlayHint().apply { kind = InlayHintKind.Type }

    fun testProvidersAreRegisteredForDartAndDisabledByDefault() {
        for (providerId in listOf(
            DartParameterNamesInlayHintsProvider.PROVIDER_ID,
            DartTypesInlayHintsProvider.PROVIDER_ID,
        )) {
            val providerInfo = requireNotNull(
                InlayHintsProviderFactory.getProviderInfo(DartLanguage.INSTANCE, providerId)
            ) { "Provider '$providerId' should be registered for Dart in plugin.xml" }
            assertFalse("Provider '$providerId' should be off by default", providerInfo.isEnabledByDefault)
        }
    }

    fun testHintsHiddenAndServerNotAskedByDefault() {
        val support = DartLspInlayHintSupport()
        val file = dartFile()

        assertFalse(support.shouldAskServerForInlayHints(file))
        assertFalse(support.shouldDisplayInlayHint(file, parameterHint()))
        assertFalse(support.shouldDisplayInlayHint(file, typeHint()))
    }

    fun testParameterNamesCheckboxControlsParameterHints() {
        DeclarativeInlayHintsSettings.getInstance()
            .setProviderEnabled(DartParameterNamesInlayHintsProvider.PROVIDER_ID, true)
        val support = DartLspInlayHintSupport()
        val file = dartFile()

        assertTrue(support.shouldAskServerForInlayHints(file))
        assertTrue(support.shouldDisplayInlayHint(file, parameterHint()))
        assertFalse(support.shouldDisplayInlayHint(file, typeHint()))
    }

    fun testTypesCheckboxControlsTypeHints() {
        DeclarativeInlayHintsSettings.getInstance()
            .setProviderEnabled(DartTypesInlayHintsProvider.PROVIDER_ID, true)
        val support = DartLspInlayHintSupport()
        val file = dartFile()

        assertTrue(support.shouldAskServerForInlayHints(file))
        assertTrue(support.shouldDisplayInlayHint(file, typeHint()))
        assertFalse(support.shouldDisplayInlayHint(file, parameterHint()))
    }

    fun testHintWithoutKindIsDisplayed() {
        DeclarativeInlayHintsSettings.getInstance()
            .setProviderEnabled(DartTypesInlayHintsProvider.PROVIDER_ID, true)
        val support = DartLspInlayHintSupport()
        val file = dartFile()

        // The Dart Analysis Server sets a kind on every hint; a hint without a kind cannot be
        // attributed to either checkbox, so it is shown as long as hints were requested at all.
        assertTrue(support.shouldDisplayInlayHint(file, InlayHint()))
        assertFalse(support.shouldDisplayInlayHint(file, parameterHint()))
    }
}
