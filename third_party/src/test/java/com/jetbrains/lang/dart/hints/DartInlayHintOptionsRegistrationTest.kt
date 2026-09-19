/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.InlayOptionInfo
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.DartLanguage

/**
 * Checks that every hint category reported by the Dart Analysis Server is registered as an
 * `<option>` sub-checkbox of the matching declarative inlay hints provider in plugin.xml.
 *
 * The option ids and their defaults are the contract that
 * [com.jetbrains.lang.dart.lsp.DartLspInlayHintSupport] reads at runtime, so they are asserted
 * verbatim here.
 */
class DartInlayHintOptionsRegistrationTest : DartCodeInsightFixtureTestCase() {

  private fun optionsOf(providerId: String): Map<String, InlayOptionInfo> {
    val providerInfo = requireNotNull(
      InlayHintsProviderFactory.getProviderInfo(DartLanguage.INSTANCE, providerId)
    ) { "Provider '$providerId' should be registered for Dart in plugin.xml" }
    return providerInfo.options.associateBy { it.id }
  }

  private fun assertOption(providerId: String, optionId: String, enabledByDefault: Boolean, name: String) {
    val option = requireNotNull(optionsOf(providerId)[optionId]) {
      "Option '$optionId' should be registered under provider '$providerId' in plugin.xml"
    }
    assertEquals("Wrong default for option '$optionId'", enabledByDefault, option.isEnabledByDefault)
    assertEquals("Wrong name for option '$optionId'", name, option.name)
  }

  fun testParameterNamesOptionsAreRegistered() {
    val providerId = DartParameterNamesInlayHintsProvider.PROVIDER_ID
    assertEquals(setOf("dart.parameter.names.only.literal"), optionsOf(providerId).keys)
    assertOption(
      providerId,
      DartParameterNamesInlayHintsProvider.ONLY_LITERAL_OPTION_ID,
      false,
      "Only for literal arguments",
    )
  }

  fun testTypesOptionsAreRegistered() {
    val providerId = DartTypesInlayHintsProvider.PROVIDER_ID
    assertEquals(
      setOf(
        "dart.types.variable",
        "dart.types.return",
        "dart.types.parameter",
        "dart.types.type.arguments",
        "dart.types.dot.shorthand",
      ),
      optionsOf(providerId).keys,
    )
    assertOption(providerId, DartTypesInlayHintsProvider.VARIABLE_TYPES_OPTION_ID, true, "Variable types")
    assertOption(providerId, DartTypesInlayHintsProvider.RETURN_TYPES_OPTION_ID, true, "Return types")
    assertOption(providerId, DartTypesInlayHintsProvider.PARAMETER_TYPES_OPTION_ID, true, "Parameter types")
    assertOption(providerId, DartTypesInlayHintsProvider.TYPE_ARGUMENTS_OPTION_ID, true, "Type arguments")
    assertOption(providerId, DartTypesInlayHintsProvider.DOT_SHORTHAND_TYPES_OPTION_ID, true, "Dot shorthand types")
  }
}
