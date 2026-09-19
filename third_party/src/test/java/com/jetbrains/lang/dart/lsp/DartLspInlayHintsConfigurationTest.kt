/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.gson.JsonObject
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.hints.DartParameterNamesInlayHintsProvider
import com.jetbrains.lang.dart.hints.DartTypesInlayHintsProvider

class DartLspInlayHintsConfigurationTest : DartCodeInsightFixtureTestCase() {

    override fun tearDown() {
        try {
            // DeclarativeInlayHintsSettings is an application-level service; reset it so that
            // enabled providers and options do not leak into other tests.
            DeclarativeInlayHintsSettings.getInstance().loadState(DeclarativeInlayHintsSettings.HintsState())
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    private fun settings(): DeclarativeInlayHintsSettings = DeclarativeInlayHintsSettings.getInstance()

    private fun inlayHints(): JsonObject {
        val dartSection = DartLspInlayHintsConfiguration.buildDartSection()
        return requireNotNull(dartSection.getAsJsonObject("inlayHints")) {
            "The 'dart' configuration section should contain an 'inlayHints' object, was: $dartSection"
        }
    }

    private fun assertTypeCategory(key: String, expected: Boolean) {
        val category = requireNotNull(inlayHints().getAsJsonObject(key)) {
            "inlayHints should contain an object for '$key'"
        }
        assertEquals("Wrong 'enabled' value for '$key'", expected, category.get("enabled").asBoolean)
    }

    private fun assertParameterNames(expected: String) {
        val category = requireNotNull(inlayHints().getAsJsonObject("parameterNames")) {
            "inlayHints should contain an object for 'parameterNames'"
        }
        assertEquals("Wrong 'enabled' value for 'parameterNames'", expected, category.get("enabled").asString)
    }

    fun testEverythingIsDisabledByDefault() {
        // Both parent checkboxes are off by default, so the server must not compute any hints.
        assertParameterNames("none")
        assertTypeCategory("variableTypes", false)
        assertTypeCategory("returnTypes", false)
        assertTypeCategory("parameterTypes", false)
        assertTypeCategory("typeArguments", false)
        assertTypeCategory("dotShorthandTypes", false)
    }

    fun testTypeCategoriesFollowTheirOptionsWhenTheParentIsOn() {
        settings().setProviderEnabled(DartTypesInlayHintsProvider.PROVIDER_ID, true)
        settings().setOptionEnabled(
            DartTypesInlayHintsProvider.RETURN_TYPES_OPTION_ID,
            DartTypesInlayHintsProvider.PROVIDER_ID,
            false,
        )

        assertTypeCategory("returnTypes", false)
        // The other options are on by default and stay on.
        assertTypeCategory("variableTypes", true)
        assertTypeCategory("parameterTypes", true)
        assertTypeCategory("typeArguments", true)
        assertTypeCategory("dotShorthandTypes", true)
    }

    fun testTypeOptionsAreIgnoredWhileTheParentIsOff() {
        settings().setOptionEnabled(
            DartTypesInlayHintsProvider.VARIABLE_TYPES_OPTION_ID,
            DartTypesInlayHintsProvider.PROVIDER_ID,
            true,
        )

        assertTypeCategory("variableTypes", false)
    }

    fun testParameterNamesIsAllWhenOnlyTheParentIsOn() {
        settings().setProviderEnabled(DartParameterNamesInlayHintsProvider.PROVIDER_ID, true)

        assertParameterNames("all")
    }

    fun testParameterNamesIsLiteralWhenTheLiteralOptionIsOn() {
        settings().setProviderEnabled(DartParameterNamesInlayHintsProvider.PROVIDER_ID, true)
        settings().setOptionEnabled(
            DartParameterNamesInlayHintsProvider.ONLY_LITERAL_OPTION_ID,
            DartParameterNamesInlayHintsProvider.PROVIDER_ID,
            true,
        )

        assertParameterNames("literal")
    }

    fun testParameterNamesIsNoneWhileTheParentIsOff() {
        settings().setOptionEnabled(
            DartParameterNamesInlayHintsProvider.ONLY_LITERAL_OPTION_ID,
            DartParameterNamesInlayHintsProvider.PROVIDER_ID,
            true,
        )

        assertParameterNames("none")
    }

    fun testBothParentsOnProducesTheFullSection() {
        settings().setProviderEnabled(DartParameterNamesInlayHintsProvider.PROVIDER_ID, true)
        settings().setProviderEnabled(DartTypesInlayHintsProvider.PROVIDER_ID, true)

        assertParameterNames("all")
        assertTypeCategory("variableTypes", true)
        assertTypeCategory("returnTypes", true)
        assertTypeCategory("parameterTypes", true)
        assertTypeCategory("typeArguments", true)
        assertTypeCategory("dotShorthandTypes", true)
    }

    fun testSectionContainsExactlyTheServerSideKeys() {
        assertEquals(setOf("inlayHints"), DartLspInlayHintsConfiguration.buildDartSection().keySet())
        assertEquals(
            setOf(
                "parameterNames",
                "variableTypes",
                "returnTypes",
                "parameterTypes",
                "typeArguments",
                "dotShorthandTypes",
            ),
            inlayHints().keySet(),
        )
    }
}
