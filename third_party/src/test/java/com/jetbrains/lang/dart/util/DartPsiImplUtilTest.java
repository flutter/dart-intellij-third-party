package com.jetbrains.lang.dart.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;

public class DartPsiImplUtilTest extends DartCodeInsightFixtureTestCase {

  public void testGetUnquotedDartStringAndItsRange() {
    doTestUnquote("''", "", 1, 1);
    doTestUnquote("'foo'", "foo", 1, 4);
    doTestUnquote("\"bar\"", "bar", 1, 4);
    doTestUnquote("'''baz'''", "baz", 3, 6);
    doTestUnquote("\"\"\"qux\"\"\"", "qux", 3, 6);
    doTestUnquote("r'raw'", "raw", 2, 5);
    doTestUnquote("r\"raw\"", "raw", 2, 5);
    doTestUnquote("r'''raw'''", "raw", 4, 7);
    doTestUnquote("r\"\"\"raw\"\"\"", "raw", 4, 7);
    
    // Unclosed strings
    doTestUnquote("'open", "open", 1, 5);
    doTestUnquote("'''open", "open", 3, 7);
  }

  private void doTestUnquote(String input, String expectedText, int start, int end) {
    Pair<String, TextRange> result = DartPsiImplUtil.getUnquotedDartStringAndItsRange(input);
    assertEquals("Text mismatch", expectedText, result.first);
    assertEquals("Range start mismatch", start, result.second.getStartOffset());
    assertEquals("Range end mismatch", end, result.second.getEndOffset());
  }
}
