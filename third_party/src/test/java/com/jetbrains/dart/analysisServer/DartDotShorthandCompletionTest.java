package com.jetbrains.dart.analysisServer;

import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.util.DartTestUtils;

public class DartDotShorthandCompletionTest extends DartServerCompletionTest {
  public void testDotNewCompletion() {
    doTest("new",
           """
             class A {
               A();
             }
             void f(A a) {}
             main() {
               f(.<caret>);
             }""");
  }

  public void testDotNamedCompletion() {
    doTest("named",
           """
             class A {
               A.named();
             }
             void f(A a) {}
             main() {
               f(.<caret>);
             }""");
  }

  public void testEnumCompletion() {
    doTest("e1",
           """
             enum E {
               e1, e2
             }
             void f(E e) {}
             main() {
               f(.<caret>);
             }""");
  }

  public void testDotNamedArgCompletion() {
    doTest("name: ",
           """
             class A {
               A.named({String? name});
             }
             void f(A a) {}
             main() {
               f(.named(<caret>));
             }""");
  }

  public void testDotNewArgCompletion() {
    doTest("name: ",
           """
             class A {
               A({String? name});
             }
             void f(A a) {}
             main() {
               f(.new(<caret>));
             }""");
  }

  private void doTest(String lookupToSelect, String text) {
    myFixture.configureByText("foo.dart", text);
    myFixture.doHighlighting();
    myFixture.completeBasic();
    myFixture.type(lookupToSelect); 
    // We expect the completion to work, so we check if the item is in the list
    // The base class methods might be useful but we need custom setup for text verification or just basic completion check
    // Actually, checking if "new" or "named" is present is enough for now.
    // But verify behavior: if we type "new", it should select "new"
    
     // Let's reuse selectLookup logic if possible or just assert it exists
    if (lookupToSelect != null) {
        // Find if the lookup contains our expected element
        var lookups = myFixture.getLookupElementStrings();
        assertNotNull("Lookup list should not be null", lookups);
        assertTrue("Likely missing completion item: " + lookupToSelect + ". Found: " + lookups, lookups.contains(lookupToSelect));
    }
  }
}
