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
        return requireNotNull(dartSection.getAsJsonObject(DartLspInlayHintsConfiguration.INLAY_HINTS_KEY)) {
            "The 'dart' configuration section should contain an 'inlayHints' object, was: $dartSection"
        }
    }

    private fun assertTypeCategory(key: String, expected: Boolean) {
        val category = requireNotNull(inlayHints().getAsJsonObject(key)) {
            "inlayHints should contain an object for '$key'"
        }
        assertEquals(
            "Wrong 'enabled' value for '$key'",
            expected,
            category.get(DartLspInlayHintsConfiguration.ENABLED_KEY).asBoolean,
        )
    }

    private fun assertParameterNames(expected: String) {
        val key = DartLspInlayHintsConfiguration.PARAMETER_NAMES_KEY
        val category = requireNotNull(inlayHints().getAsJsonObject(key)) {
            "inlayHints should contain an object for '$key'"
        }
        assertEquals(
            "Wrong 'enabled' value for '$key'",
            expected,
            category.get(DartLspInlayHintsConfiguration.ENABLED_KEY).asString,
        )
    }

    fun testEverythingIsDisabledByDefault() {
        // Both parent checkboxes are off by default, so the server must not compute any hints.
        assertParameterNames("none")
        assertTypeCategory(DartLspInlayHintsConfiguration.VARIABLE_TYPES_KEY, false)
        assertTypeCategory(DartLspInlayHintsConfiguration.RETURN_TYPES_KEY, false)
        assertTypeCategory(DartLspInlayHintsConfiguration.PARAMETER_TYPES_KEY, false)
        assertTypeCategory(DartLspInlayHintsConfiguration.TYPE_ARGUMENTS_KEY, false)
        assertTypeCategory(DartLspInlayHintsConfiguration.DOT_SHORTHAND_TYPES_KEY, false)
    }

    fun testTypeCategoriesFollowTheirOptionsWhenTheParentIsOn() {
        settings().setProviderEnabled(DartTypesInlayHintsProvider.PROVIDER_ID, true)
        settings().setOptionEnabled(
            DartTypesInlayHintsProvider.RETURN_TYPES_OPTION_ID,
            DartTypesInlayHintsProvider.PROVIDER_ID,
            false,
        )

        assertTypeCategory(DartLspInlayHintsConfiguration.RETURN_TYPES_KEY, false)
        // The other options are on by default and stay on.
        assertTypeCategory(DartLspInlayHintsConfiguration.VARIABLE_TYPES_KEY, true)
        assertTypeCategory(DartLspInlayHintsConfiguration.PARAMETER_TYPES_KEY, true)
        assertTypeCategory(DartLspInlayHintsConfiguration.TYPE_ARGUMENTS_KEY, true)
        assertTypeCategory(DartLspInlayHintsConfiguration.DOT_SHORTHAND_TYPES_KEY, true)
    }

    fun testTypeOptionsAreIgnoredWhileTheParentIsOff() {
        settings().setOptionEnabled(
            DartTypesInlayHintsProvider.VARIABLE_TYPES_OPTION_ID,
            DartTypesInlayHintsProvider.PROVIDER_ID,
            true,
        )

        assertTypeCategory(DartLspInlayHintsConfiguration.VARIABLE_TYPES_KEY, false)
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
        assertTypeCategory(DartLspInlayHintsConfiguration.VARIABLE_TYPES_KEY, true)
        assertTypeCategory(DartLspInlayHintsConfiguration.RETURN_TYPES_KEY, true)
        assertTypeCategory(DartLspInlayHintsConfiguration.PARAMETER_TYPES_KEY, true)
        assertTypeCategory(DartLspInlayHintsConfiguration.TYPE_ARGUMENTS_KEY, true)
        assertTypeCategory(DartLspInlayHintsConfiguration.DOT_SHORTHAND_TYPES_KEY, true)
    }

    fun testSectionContainsExactlyTheServerSideKeys() {
        // The only place that spells the wire format out; everything else goes through the
        // constants, so a typo in one of them has to fail here.
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
