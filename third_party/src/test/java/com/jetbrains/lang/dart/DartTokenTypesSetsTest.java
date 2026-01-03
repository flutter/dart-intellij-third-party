package com.jetbrains.lang.dart;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class DartTokenTypesSetsTest extends BasePlatformTestCase {
  public void testStrings() {
    assertTrue(DartTokenTypesSets.STRINGS.contains(DartTokenTypes.RAW_SINGLE_QUOTED_STRING));
    assertTrue(DartTokenTypesSets.STRINGS.contains(DartTokenTypes.RAW_TRIPLE_QUOTED_STRING));
    assertTrue(DartTokenTypesSets.STRINGS.contains(DartTokenTypes.OPEN_QUOTE));
    assertTrue(DartTokenTypesSets.STRINGS.contains(DartTokenTypes.CLOSING_QUOTE));
    assertTrue(DartTokenTypesSets.STRINGS.contains(DartTokenTypes.REGULAR_STRING_PART));
  }

  public void testReservedWords() {
    assertTrue(DartTokenTypesSets.RESERVED_WORDS.contains(DartTokenTypes.ASSERT));
    assertTrue(DartTokenTypesSets.RESERVED_WORDS.contains(DartTokenTypes.CLASS));
    assertTrue(DartTokenTypesSets.RESERVED_WORDS.contains(DartTokenTypes.IF));
    assertTrue(DartTokenTypesSets.RESERVED_WORDS.contains(DartTokenTypes.VOID));
  }

  public void testComments() {
    assertTrue(DartTokenTypesSets.COMMENTS.contains(DartTokenTypesSets.SINGLE_LINE_COMMENT));
    assertTrue(DartTokenTypesSets.COMMENTS.contains(DartTokenTypesSets.MULTI_LINE_COMMENT));
    assertTrue(DartTokenTypesSets.COMMENTS.contains(DartTokenTypesSets.SINGLE_LINE_DOC_COMMENT));
    assertTrue(DartTokenTypesSets.COMMENTS.contains(DartTokenTypesSets.MULTI_LINE_DOC_COMMENT));
  }

  public void testBuiltInIdentifiers() {
    assertTrue(DartTokenTypesSets.BUILT_IN_IDENTIFIERS.contains(DartTokenTypes.ABSTRACT));
    assertTrue(DartTokenTypesSets.BUILT_IN_IDENTIFIERS.contains(DartTokenTypes.GET));
    assertTrue(DartTokenTypesSets.BUILT_IN_IDENTIFIERS.contains(DartTokenTypes.SET));
    assertTrue(DartTokenTypesSets.BUILT_IN_IDENTIFIERS.contains(DartTokenTypes.AWAIT));
  }

  public void testOperators() {
    assertTrue(DartTokenTypesSets.OPERATORS.contains(DartTokenTypes.PLUS));
    assertTrue(DartTokenTypesSets.OPERATORS.contains(DartTokenTypes.MINUS));
    assertTrue(DartTokenTypesSets.OPERATORS.contains(DartTokenTypes.EQ_EQ));
  }
}
