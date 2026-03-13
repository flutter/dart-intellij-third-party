// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import org.dartlang.analysis.server.protocol.RefactoringProblem
import org.dartlang.analysis.server.protocol.RenameFeedback
import org.dartlang.analysis.server.protocol.SourceChange

data class LspRenameRefactoringResult(
    val initialProblems: List<RefactoringProblem> = emptyList(),
    val optionsProblems: List<RefactoringProblem> = emptyList(),
    val finalProblems: List<RefactoringProblem> = emptyList(),
    val feedback: RenameFeedback? = null,
    val change: SourceChange? = null,
    val potentialEdits: List<String> = emptyList(),
)
