// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.jetbrains.lang.dart.psi.DartArrayAccessExpression;
import com.jetbrains.lang.dart.psi.DartListLiteralExpression;
import com.jetbrains.lang.dart.psi.DartNewExpression;
import com.jetbrains.lang.dart.psi.DartParenthesizedExpression;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import com.jetbrains.lang.dart.psi.DartSetOrMapLiteralExpression;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import com.jetbrains.lang.dart.psi.DartUriElement;
import org.jetbrains.annotations.NotNull;

/**
 * Adjusts {@link CompletionInitializationContext#setReplacementOffset(int)} for Dart PSI nodes
 * whose {@link PsiReference} range extends through closing delimiters such as {@code )}, {@code ]},
 * {@code }}, or closing quotes.
 * <p>
 * This runs before both legacy DAS completion and LSP completion so that accepting a completion
 * item with {@code Tab} ({@code Lookup.REPLACE_SELECT_CHAR}) does not delete enclosing delimiters.
 */
public final class DartPsiCompletionContributor extends CompletionContributor implements DumbAware {
  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    final PsiElement psiElement = context.getFile().findElementAt(context.getStartOffset());
    final PsiElement parent = psiElement != null ? psiElement.getParent() : null;
    if (parent instanceof DartStringLiteralExpression) {
      final PsiElement parentParent = parent.getParent();
      if (parentParent instanceof DartUriElement) {
        final Pair<String, TextRange> uriAndRange = ((DartUriElement)parentParent).getUriStringAndItsRange();
        context.setReplacementOffset(parentParent.getTextRange().getStartOffset() + uriAndRange.second.getEndOffset());
      }
      else {
        // If replacement context is not set explicitly then com.intellij.codeInsight.completion.CompletionProgressIndicator#duringCompletion
        // implementation looks for the reference at caret and on Tab replaces the whole reference.
        // Without the following hack Tab replaces the whole string literal including closing quote.
        context.setReplacementOffset(context.getReplacementOffset());
      }
    }
    else {
      PsiReference reference = context.getFile().findReferenceAt(context.getStartOffset());
      if (reference instanceof PsiMultiReference && ((PsiMultiReference)reference).getReferences().length > 0) {
        reference.getRangeInElement(); // to ensure that references are sorted by range
        reference = ((PsiMultiReference)reference).getReferences()[0];
      }
      if (reference instanceof DartNewExpression) {
        // historically DartNewExpression is a reference; it can appear here only in situation like new Foo(o.<caret>);
        // without the following hack closing paren is replaced on Tab. We won't get here if at least one symbol after dot typed.
        if (((DartNewExpression)reference).getLastChild() instanceof PsiErrorElement) {
          // f(.new) or f(.named) - we want default replacement logic
        }
        else {
          context.setReplacementOffset(context.getStartOffset());
        }
      }
      else if (reference instanceof DartParenthesizedExpression ||
               reference instanceof DartListLiteralExpression ||
               reference instanceof DartSetOrMapLiteralExpression ||
               reference instanceof DartArrayAccessExpression ||
               reference instanceof DartStringLiteralExpression) {
        context.setReplacementOffset(context.getStartOffset());
      }
      if (reference instanceof DartReferenceExpression) {
        final PsiElement firstChild = ((DartReferenceExpression)reference).getFirstChild();
        final PsiElement lastChild = ((DartReferenceExpression)reference).getLastChild();
        if (firstChild != lastChild &&
            lastChild instanceof PsiErrorElement &&
            context.getStartOffset() <= firstChild.getTextRange().getEndOffset()) {
          context.setReplacementOffset(firstChild.getTextRange().getEndOffset());
        }
      }
    }
  }
}
