package com.jetbrains.lang.dart.util;

import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import java.util.Collection;
import java.util.Arrays;

public class DartNameSuggesterUtilTest extends DartCodeInsightFixtureTestCase {

  public void testGenerateNames() {
    assertContainsOrdered(DartNameSuggesterUtil.generateNames("myVariable"), "variable", "myVariable");
    assertContainsOrdered(DartNameSuggesterUtil.generateNames("getFooBar"), "bar", "fooBar");
    assertContainsOrdered(DartNameSuggesterUtil.generateNames("isReady"), "ready");
    assertContainsOrdered(DartNameSuggesterUtil.generateNames("foo_bar_baz"), "baz", "barBaz", "fooBarBaz");
    assertContainsOrdered(DartNameSuggesterUtil.generateNames("foo.bar"), "bar", "fooBar");
    assertContainsOrdered(DartNameSuggesterUtil.generateNames("'quoted'"), "quoted");
    assertContainsOrdered(DartNameSuggesterUtil.generateNames("_private"), "private");
  }

  private void assertContainsOrdered(Collection<String> actual, String... expected) {
    assertEquals(Arrays.asList(expected), actual);
  }
}
