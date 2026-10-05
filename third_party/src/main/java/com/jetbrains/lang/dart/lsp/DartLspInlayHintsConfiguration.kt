/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.google.gson.JsonObject
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.hints.DartParameterNamesInlayHintsProvider
import com.jetbrains.lang.dart.hints.DartTypesInlayHintsProvider

/**
 * Translates the Dart checkboxes in Settings | Editor | Inlay Hints into the `dart` configuration
 * section that the Dart Analysis Server asks for via `workspace/configuration`.
 *
 * The server groups its inlay hints into the categories below and computes only the enabled ones,
 * so a category that is switched off here is never computed at all - unlike the client-side filter
 * in [DartLspInlayHintSupport], which can only drop hints after the server has produced them.
 *
 * A parent checkbox that is off disables all of its categories, regardless of the state of the
 * sub-checkboxes: the sub-checkboxes stay in the settings so that their state is remembered, but
 * they only take effect while their parent is on.
 */
object DartLspInlayHintsConfiguration {

    internal const val INLAY_HINTS_KEY = "inlayHints"
    internal const val ENABLED_KEY = "enabled"

    // The names the server gives its hint categories; they are the wire format of the `dart`
    // configuration section and are spelled out once, here.
    internal const val PARAMETER_NAMES_KEY = "parameterNames"
    internal const val VARIABLE_TYPES_KEY = "variableTypes"
    internal const val RETURN_TYPES_KEY = "returnTypes"
    internal const val PARAMETER_TYPES_KEY = "parameterTypes"
    internal const val TYPE_ARGUMENTS_KEY = "typeArguments"
    internal const val DOT_SHORTHAND_TYPES_KEY = "dotShorthandTypes"

    private const val PARAMETER_NAMES_NONE = "none"
    private const val PARAMETER_NAMES_LITERAL = "literal"
    private const val PARAMETER_NAMES_ALL = "all"

    /** The type categories of the server, paired with the option that switches them on and off. */
    private val TYPE_CATEGORIES = listOf(
        VARIABLE_TYPES_KEY to DartTypesInlayHintsProvider.VARIABLE_TYPES_OPTION_ID,
        RETURN_TYPES_KEY to DartTypesInlayHintsProvider.RETURN_TYPES_OPTION_ID,
        PARAMETER_TYPES_KEY to DartTypesInlayHintsProvider.PARAMETER_TYPES_OPTION_ID,
        TYPE_ARGUMENTS_KEY to DartTypesInlayHintsProvider.TYPE_ARGUMENTS_OPTION_ID,
        DOT_SHORTHAND_TYPES_KEY to DartTypesInlayHintsProvider.DOT_SHORTHAND_TYPES_OPTION_ID,
    )

    /** Every hint category the `inlayHints` object carries. */
    internal val SERVER_CATEGORY_KEYS: Set<String> =
        linkedSetOf(PARAMETER_NAMES_KEY) + TYPE_CATEGORIES.map { it.first }

    /**
     * Builds the whole `dart` configuration section, i.e. `{"inlayHints": {...}}`.
     */
    fun buildDartSection(): JsonObject {
        val inlayHints = JsonObject()

        val parameterNames = JsonObject()
        parameterNames.addProperty(ENABLED_KEY, parameterNamesMode())
        inlayHints.add(PARAMETER_NAMES_KEY, parameterNames)

        val typesEnabled = isProviderEnabled(DartTypesInlayHintsProvider.PROVIDER_ID)
        for ((serverKey, optionId) in TYPE_CATEGORIES) {
            val category = JsonObject()
            category.addProperty(
                ENABLED_KEY,
                typesEnabled && isOptionEnabled(optionId, DartTypesInlayHintsProvider.PROVIDER_ID),
            )
            inlayHints.add(serverKey, category)
        }

        val dartSection = JsonObject()
        dartSection.add(INLAY_HINTS_KEY, inlayHints)
        return dartSection
    }

    private fun parameterNamesMode(): String {
        val providerId = DartParameterNamesInlayHintsProvider.PROVIDER_ID
        if (!isProviderEnabled(providerId)) return PARAMETER_NAMES_NONE
        val onlyLiteral =
            isOptionEnabled(DartParameterNamesInlayHintsProvider.ONLY_LITERAL_OPTION_ID, providerId)
        return if (onlyLiteral) PARAMETER_NAMES_LITERAL else PARAMETER_NAMES_ALL
    }

    /**
     * Returns whether the checkbox of the given provider is on, falling back to the
     * `isEnabledByDefault` value of its registration in plugin.xml while the user has not toggled
     * it yet.
     */
    internal fun isProviderEnabled(providerId: String): Boolean {
        DeclarativeInlayHintsSettings.getInstance().isProviderEnabled(providerId)?.let { return it }
        return providerInfo(providerId)?.isEnabledByDefault ?: false
    }

    /**
     * Returns whether the given sub-checkbox of the given provider is on, falling back to the
     * `enabledByDefault` value of its `<option>` registration in plugin.xml while the user has not
     * toggled it yet.
     */
    private fun isOptionEnabled(optionId: String, providerId: String): Boolean {
        DeclarativeInlayHintsSettings.getInstance().isOptionEnabled(optionId, providerId)?.let { return it }
        val option = providerInfo(providerId)?.options?.firstOrNull { it.id == optionId }
        return option?.isEnabledByDefault ?: false
    }

    private fun providerInfo(providerId: String) =
        InlayHintsProviderFactory.getProviderInfo(DartLanguage.INSTANCE, providerId)
}
