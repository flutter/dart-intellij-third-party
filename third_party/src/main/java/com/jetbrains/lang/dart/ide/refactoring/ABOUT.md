<!--* freshness: { reviewed: '2026-11-05' } *-->

# refactoring

## Overview

Rename, extract, inline, and move-file refactorings. By default they are driven
by the legacy analysis server (DAS) refactoring protocol. When LSP code actions
are enabled, Extract Method, Extract Local Variable, and Inline instead run as
LSP `textDocument/codeAction` refactorings (no options dialog). When
experimental LSP features are enabled, file and directory renames and moves use
the platform LSP `LspWillRenameFilesHandler` (`workspace/willRenameFiles`,
backported from `IJPL-249183`).

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
  that suppresses legacy `DartFileReference` PSI rename usages when renaming
  Dart files and directories with experimental LSP features enabled.
- `DartLspRefactoringHelper`: object that turns the caret or selection into a
  `textDocument/codeAction` request (`refactor.extract` / `refactor.inline`),
  picks the matching enabled action by title, and applies it. Shows an error
  hint when the refactoring isn't available at that location.
- `extract/DartServerExtractMethodHandler`,
  `introduce/DartServerExtractLocalVariableHandler`: the action handlers. Both
  delegate to `DartLspRefactoringHelper` when LSP code actions are enabled.
- `moveFile/DartServerMoveDartFileHandler`: the legacy DAS `moveFileHandler`
  (`MoveFileRefactoring`). Yields (`canProcessElement` returns `false`) when
  experimental LSP features are enabled so `LspWillRenameFilesHandler` handles
  file and directory moves.
- `status/`: `RefactoringStatus`, `RefactoringStatusEntry`,
  `RefactoringStatusContext`, and `RefactoringStatusSeverity`.

## Invariants

- The LSP file/directory rename and move path (`LspWillRenameFilesHandler`) runs
  when `DartConfigurable.isExperimentalLspFeaturesEnabled` is true (which
  enables `workspace.fileOperations.willRename` on `DartLspServerDescriptor`).
- The LSP extract/inline path runs only when
  `DartConfigurable.isLspCodeActionsEnabled(project)` is true and a Dart SDK
  is configured.
- `DartLspRenamePsiElementProcessor.findReferences` returns nothing so legacy
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
