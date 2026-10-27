<!--* freshness: { reviewed: '2026-10-25' } *-->
# imports

## Overview
Optimize Imports for Dart, delegated to the Analysis Server's organize-directives edit.

## Interface
- `DartImportOptimizer` - `ImportOptimizer` for `DartFile`; applies `DartAnalysisServerService.edit_organizeDirectives` via `AssistUtils.applyFileEdit`.

## Invariants
- Supports only `DartFile` PSI; flushes file contents to the server (`updateFilesContent`) before requesting the edit.
- Commits the document after applying the edit so `formatter/DartPostFormatProcessor` runs afterwards.

## Side Effects
- Rewrites import/export directives in the document; shows an "organized directives" notification when changed.

## Verification
- No dedicated tests in this repo; verify via Code > Optimize Imports on a Dart file.
