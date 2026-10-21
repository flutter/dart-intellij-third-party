// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.editor;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;

import java.awt.datatransfer.StringSelection;

/**
 * Pasting multi-line text into a single-line doc comment must keep the {@code ///} prefix.
 */
public class DartDocCommentPasteTest extends DartCodeInsightFixtureTestCase {

    private void doTest(String before, String pasted, String after) {
        myFixture.configureByText("test.dart", before);
        CopyPasteManager.getInstance().setContents(new StringSelection(pasted));
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
        myFixture.checkResult(after);
    }

    public void testMultiLinePasteInDocComment() {
        doTest("/// <caret>\nclass A {}", "a\nb\nc", "/// a\n/// b\n/// c<caret>\nclass A {}");
    }

    public void testIndentedDocComment() {
        doTest("class A {\n  /// <caret>\n  void f() {}\n}", "a\nb",
                "class A {\n  /// a\n  /// b<caret>\n  void f() {}\n}");
    }

    public void testPastedLineStartingWithLineComment() {
        doTest("/// <caret>\nclass A {}", "a\n// b", "/// a\n/// // b<caret>\nclass A {}");
    }

    public void testSingleLinePasteUnchanged() {
        doTest("/// <caret>\nclass A {}", "abc", "/// abc<caret>\nclass A {}");
    }

    public void testRegularLineCommentStillUsesDoubleSlash() {
        doTest("// <caret>\nclass A {}", "a\nb", "// a\n// b<caret>\nclass A {}");
    }

    public void testFourSlashCommentIsNotDocComment() {
        // '////' is a regular comment; the platform's existing behavior must stay unchanged.
        doTest("//// <caret>\nclass A {}", "a\nb", "//// a\n// b<caret>\nclass A {}");
    }

    public void testPasteOutsideCommentUnchanged() {
        doTest("class A {}\n<caret>", "a\nb", "class A {}\na\nb<caret>");
    }
}