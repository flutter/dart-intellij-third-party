<!--* freshness: { reviewed: '2026-10-26' } *-->

# refactoring

## Overview

Rename, extract, inline, and move-file refactorings. By default they are driven
by the legacy analysis server (DAS) refactoring protocol. When LSP code actions
are enabled, Extract Method, Extract Local Variable, and Inline instead run as
LSP `textDocument/codeAction` refactorings (no options dialog). When
experimental LSP features are enabled, file renames and moves use LSP
`workspace/willRenameFiles`.

## Interface

- `ServerRefactoring`: the base wrapper around a DAS refactoring. It tracks
  initial, options, and final `RefactoringStatus` values and the resulting
  `SourceChange`, and it collects analytics.
- `ServerRenameRefactoring`, `ServerExtractLocalVariableRefactoring`,
  `ServerExtractMethodRefactoring`, `ServerInlineLocalRefactoring`,
  `ServerInlineMethodRefactoring`: the concrete DAS refactorings.
- `DartServerRenameHandler` + `DartRenameDialog`: the rename UI (registered
  as `renameHandler`).
- `DartInlineHandler`: the `inlineActionHandler`. Delegates to
  `DartLspRefactoringHelper.performInline` when LSP code actions are enabled.
- `ServerRefactoringDialog`: the shared preview and status dialog.
- `DartRefactoringSupportProvider`: connects the extract handlers to the
  platform.
- `DartLspRenamePsiElementProcessor`: renames Dart files and directories
  through LSP (`order="first"`). It also hosts the shared `getFileUri`,
  `applyWorkspaceEdit`, and `awaitFutureCheckingCanceled` helpers.
- `DartLspRefactoringHelper`: object that turns the caret or selection into a
  `textDocument/codeAction` request (`refactor.extract` / `refactor.inline`),
  picks the matching enabled action by title, and applies it. Shows an error
  hint when the refactoring isn't available at that location.
- `extract/DartServerExtractMethodHandler`,
  `introduce/DartServerExtractLocalVariableHandler`: the action handlers. Both
  delegate to `DartLspRefactoringHelper` when LSP code actions are enabled.
- `moveFile/DartServerMoveDartFileHandler`: the `moveFileHandler`. It uses
  LSP when experimental features are on and `MoveFileRefactoring` (DAS)
  otherwise.
- `status/`: `RefactoringStatus`, `RefactoringStatusEntry`,
  `RefactoringStatusContext`, and `RefactoringStatusSeverity`.

## Invariants

- The LSP rename/move path runs only when
  `DartConfigurable.isExperimentalLspFeaturesEnabled` is true and the file is
  in the analysis roots.
- The LSP extract/inline path runs only when
  `DartConfigurable.isLspCodeActionsEnabled(project)` is true and a Dart SDK
  is configured.
- `DartLspRenamePsiElementProcessor.findReferences` returns nothing so the
  platform does not apply the same edits a second time.
- LSP waits are capped at 10 s and honor `ProgressManager.checkCanceled()`.

## Side Effects

- Applies workspace edits and saves documents inside a
  `WriteCommandAction`.
- Sends `analysis_*` / `edit_getRefactoring` requests to DAS, or
  `willRenameFiles` / `textDocument/codeAction` to LSP.

## Verification

- `com.jetbrains.lang.dart.ide.refactoring.DartLspRenamePsiElementProcessorTest`
- `com.jetbrains.lang.dart.ide.refactoring.DartLspRefactoringHelperTest`
- `DartServerRenameTest`, `DartExtract*RefactoringTest`,
  `DartInline*RefactoringTest`, `DartRefactoringAnalyticsTest` (in
  `com.jetbrains.dart.analysisServer`)
