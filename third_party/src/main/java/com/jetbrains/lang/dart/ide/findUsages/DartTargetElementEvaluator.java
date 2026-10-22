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
 *
 * NOTE: This is a transitional fix during the LSP migration. When Find Usages is invoked,
 * IntelliJ's SearchTargetVariantsDataRule queries both modern SEARCH_TARGETS (LSP) and
 * falls back to TargetElementUtil if USAGE_TARGETS_KEY is null. Since Dart files maintain
 * a PSI reference tree, TargetElementUtil resolves the reference through PSI, leading to
 * competing targets. This evaluator (or its legacy fallback) can be removed or simplified
 * once the plugin is fully migrated to LSP and legacy DAS / PSI reference resolution is
 * deprecated, or if the platform avoids the TargetElementUtil fallback when LSP targets exist.
 */
public class DartTargetElementEvaluator extends TargetElementEvaluatorEx2 {
  @Override
  public @Nullable Collection<PsiElement> getTargetCandidates(@NotNull PsiReference reference) {
    final PsiElement element = reference.getElement();
    final Project project = element.getProject();
    if (DartAnalysisServerService.isLspReferencesEnabled(project)) {
      // Suppress PSI candidates when LSP handles references to prevent IntelliJ from showing
      // an ambiguous "Find Usages Of" chooser popup.
      return Collections.emptyList();
    }
    // Fall back to standard PSI / DAS reference resolution when LSP is disabled.
    return null;
  }
}
