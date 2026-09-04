/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.findUsages;

import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Evaluates target elements for Dart references.
 *
 * When LSP references are enabled, this evaluator suppresses PSI target candidates
 * so that IntelliJ's find usages action does not produce duplicate/ambiguous search targets
 * (e.g. an LSP SearchTarget alongside a legacy PsiTargetVariant).
 */
public class DartTargetElementEvaluator extends TargetElementEvaluatorEx2 {
  @Override
  public @Nullable Collection<PsiElement> getTargetCandidates(@NotNull PsiReference reference) {
    final PsiElement element = reference.getElement();
    final Project project = element.getProject();
    if (DartAnalysisServerService.isLspReferencesEnabled(project)) {
      return Collections.emptyList();
    }
    return null;
  }
}
