<!--* freshness: { reviewed: '2026-11-23' } *-->

# refactoring

## Overview

Rename, extract, inline, and move-file refactorings. By default symbol
refactorings are driven by the legacy analysis server (DAS) refactoring
protocol. When LSP code actions are enabled, Extract Method, Extract Local
Variable, and Inline instead run as LSP `textDocument/codeAction` refactorings
(no options dialog). File and directory renames and moves use the platform LSP
`LspWillRenameFilesHandler` (`workspace/willRenameFiles`, backported from
`IJPL-249183`) for all users whenever the Dart LSP server is running.

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
- `DartLspRenamePsiElementProcessor`: `renamePsiElementProcessor` (`order="first"`)
  that suppresses `DartFileReference` PSI rename usages when renaming
  Dart files and directories.
- `DartLspRefactoringHelper`: object that turns the caret or selection into a
  `textDocument/codeAction` request (`refactor.extract` / `refactor.inline`),
  picks the matching enabled action by title, and applies it. Shows an error
  hint when the refactoring isn't available at that location.
- `extract/DartServerExtractMethodHandler`,
  `introduce/DartServerExtractLocalVariableHandler`: the action handlers. Both
  delegate to `DartLspRefactoringHelper` when LSP code actions are enabled.
- `status/`: `RefactoringStatus`, `RefactoringStatusEntry`,
  `RefactoringStatusContext`, and `RefactoringStatusSeverity`.

## Invariants

- The LSP file/directory rename and move path (`LspWillRenameFilesHandler`) runs
  for all users (not gated on experimental LSP features); legacy DAS
  `MoveFileRefactoring` / `DartServerMoveDartFileHandler` have been removed.
- The LSP extract/inline path runs only when
  `DartConfigurable.isLspCodeActionsEnabled(project)` is true and a Dart SDK
  is configured.
- `DartLspRenamePsiElementProcessor.findReferences` returns nothing so
  `DartFileReference` does not duplicate or corrupt edits applied by
  `LspWillRenameFilesHandler`.

## Side Effects

- Applies workspace edits inside the refactoring write action or a
  `WriteCommandAction`.
- Sends `analysis_*` / `edit_getRefactoring` requests to DAS, or
  `textDocument/codeAction` to LSP (`workspace/willRenameFiles` is sent by
  `LspWillRenameFilesHandler` in `platform-lsp`).

## Verification

- `com.jetbrains.lang.dart.ide.refactoring.DartLspWillRenameFilesTest`
- `com.jetbrains.lang.dart.ide.refactoring.DartLspRenamePsiElementProcessorTest`
- `com.jetbrains.lang.dart.ide.refactoring.DartLspRefactoringHelperTest`
- `DartServerRenameTest`, `DartExtract*RefactoringTest`,
  `DartInline*RefactoringTest`, `DartRefactoringAnalyticsTest` (in
  `com.jetbrains.dart.analysisServer`)
