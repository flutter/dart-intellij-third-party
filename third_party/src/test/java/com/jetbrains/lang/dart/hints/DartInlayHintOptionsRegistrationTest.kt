/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.InlayOptionInfo
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.DartLanguage

/**
 * Checks the `<option>` sub-checkboxes that plugin.xml registers for the declarative Dart inlay
 * hint providers: their ids, their defaults and their names.
 *
 * Those ids and defaults are the contract that
 * [com.jetbrains.lang.dart.lsp.DartLspInlayHintsConfiguration] reads at runtime to build the
 * configuration for the Dart Analysis Server, so they are asserted verbatim here. Which hint
 * category of the server an option stands for is not checked here - only the server knows that.
 */
class DartInlayHintOptionsRegistrationTest : DartCodeInsightFixtureTestCase() {

  private fun optionsOf(providerId: String): Collection<InlayOptionInfo> {
    val providerInfo = requireNotNull(
      InlayHintsProviderFactory.getProviderInfo(DartLanguage.INSTANCE, providerId)
    ) { "Provider '$providerId' should be registered for Dart in plugin.xml" }
    return providerInfo.options
  }

  /**
   * Asserts the registered option ids. The platform hands out the options as a `Set`, so it folds
   * two `<option>` entries that are equal in every attribute into one; the size assertion catches
   * the copy-paste mistake that matters, an id registered twice with a different default or name.
   */
  private fun assertOptionIds(providerId: String, vararg expectedIds: String) {
    val ids = optionsOf(providerId).map { it.id }
    assertEquals("Wrong number of <option> registrations under provider '$providerId': $ids", expectedIds.size, ids.size)
    assertEquals(expectedIds.toSet(), ids.toSet())
  }

  private fun assertOption(providerId: String, optionId: String, enabledByDefault: Boolean, name: String) {
    val option = requireNotNull(optionsOf(providerId).firstOrNull { it.id == optionId }) {
      "Option '$optionId' should be registered under provider '$providerId' in plugin.xml"
    }
    assertEquals("Wrong default for option '$optionId'", enabledByDefault, option.isEnabledByDefault)
    assertEquals("Wrong name for option '$optionId'", name, option.name)
  }

  /** The description is shown below the checkbox, so a missing bundle key would be visible. */
  private fun assertDescription(key: String) {
    val description = DartBundle.message(key)
    assertTrue("Bundle key '$key' should resolve to a description, was: '$description'", description.isNotBlank())
    assertFalse("Bundle key '$key' should resolve to a description, was: '$description'", description.contains(key))
  }

  fun testParameterNamesOptionsAreRegistered() {
    val providerId = DartParameterNamesInlayHintsProvider.PROVIDER_ID
    assertOptionIds(providerId, "dart.parameter.names.only.literal")
    assertOption(
      providerId,
      DartParameterNamesInlayHintsProvider.ONLY_LITERAL_OPTION_ID,
      false,
      "Only for literal arguments",
    )
  }

  fun testTypesOptionsAreRegistered() {
    val providerId = DartTypesInlayHintsProvider.PROVIDER_ID
    assertOptionIds(
      providerId,
      "dart.types.variable",
      "dart.types.return",
      "dart.types.parameter",
      "dart.types.type.arguments",
      "dart.types.dot.shorthand",
    )
    assertOption(providerId, DartTypesInlayHintsProvider.VARIABLE_TYPES_OPTION_ID, true, "Variable types")
    assertOption(providerId, DartTypesInlayHintsProvider.RETURN_TYPES_OPTION_ID, true, "Return types")
    assertOption(providerId, DartTypesInlayHintsProvider.PARAMETER_TYPES_OPTION_ID, true, "Parameter types")
    assertOption(providerId, DartTypesInlayHintsProvider.TYPE_ARGUMENTS_OPTION_ID, true, "Type arguments")
    assertOption(providerId, DartTypesInlayHintsProvider.DOT_SHORTHAND_TYPES_OPTION_ID, true, "Dot shorthand types")
  }

  fun testOptionDescriptionsAreInTheBundle() {
    assertDescription("dart.inlay.hints.parameter.names.only.literal.description")
    assertDescription("dart.inlay.hints.types.variable.description")
    assertDescription("dart.inlay.hints.types.return.description")
    assertDescription("dart.inlay.hints.types.parameter.description")
    assertDescription("dart.inlay.hints.types.type.arguments.description")
    assertDescription("dart.inlay.hints.types.dot.shorthand.description")
  }
}
