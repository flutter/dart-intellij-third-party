<!--* freshness: { reviewed: '2026-10-25' } *-->

# refactoring

## Overview

Rename, extract, inline, and move-file refactorings. Most are driven by the
legacy analysis server (DAS) refactoring protocol. When experimental LSP
features are enabled, file renames and moves instead use LSP
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
- `DartInlineHandler`: the `inlineActionHandler`.
- `ServerRefactoringDialog`: the shared preview and status dialog.
- `DartRefactoringSupportProvider`: connects the extract handlers to the
  platform.
- `DartLspRenamePsiElementProcessor`: renames Dart files and directories
  through LSP (`order="first"`). It also hosts the shared `getFileUri`,
  `applyWorkspaceEdit`, and `awaitFutureCheckingCanceled` helpers.
- `extract/DartServerExtractMethodHandler`,
  `introduce/DartServerExtractLocalVariableHandler`: the action handlers.
- `moveFile/DartServerMoveDartFileHandler`: the `moveFileHandler`. It uses
  LSP when experimental features are on and `MoveFileRefactoring` (DAS)
  otherwise.
- `status/`: `RefactoringStatus`, `RefactoringStatusEntry`,
  `RefactoringStatusContext`, and `RefactoringStatusSeverity`.

## Invariants

- The LSP path runs only when `DartConfigurable.isExperimentalLspFeaturesEnabled`
  is true and the file is in the analysis roots.
- `DartLspRenamePsiElementProcessor.findReferences` returns nothing so the
  platform does not apply the same edits a second time.
- LSP waits are capped at 10 s and honor `ProgressManager.checkCanceled()`.

## Side Effects

- Applies workspace edits and saves documents inside a
  `WriteCommandAction`.
- Sends `analysis_*` / `edit_getRefactoring` requests to DAS, or
  `willRenameFiles` to LSP.

## Verification

- `com.jetbrains.lang.dart.ide.refactoring.DartLspRenamePsiElementProcessorTest`
- `DartServerRenameTest`, `DartExtract*RefactoringTest`,
  `DartInline*RefactoringTest`, `DartRefactoringAnalyticsTest` (in
  `com.jetbrains.dart.analysisServer`)
