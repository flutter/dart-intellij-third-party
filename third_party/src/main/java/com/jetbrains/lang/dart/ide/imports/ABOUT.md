<!--* freshness: { reviewed: '2026-10-25' } *-->
# imports

## Overview
Optimize Imports for Dart, delegated to the Analysis Server's organize-directives edit.

## Interface
- `DartImportOptimizer` - Legacy `ImportOptimizer` for `DartFile` when LSP code actions are disabled; applies `DartAnalysisServerService.edit_organizeDirectives` via `AssistUtils.applyFileEdit`. When `DartConfigurable.isLspCodeActionsEnabled` is true, it yields to JetBrains' `LspImportOptimizer`.

## Invariants
- Supports only `DartFile` PSI when LSP code actions are disabled; flushes file contents to the server (`updateFilesContent`) before requesting the edit.
- Commits the document after applying the edit so `formatter/DartPostFormatProcessor` runs afterwards.

## Side Effects
- Rewrites import/export directives in the document; shows an "organized directives" notification when changed.

## Verification
- `./gradlew :test --tests "com.jetbrains.lang.dart.ide.imports.DartLspImportOptimizerTest"`
