<!--* freshness: { reviewed: '2026-10-25' } *-->
# annotator

## Overview
Turns Analysis Server diagnostics and semantic highlighting into editor annotations for Dart files.

## Interface
- `DartAnnotator` - `Annotator` that applies `DartAnalysisServerService.getErrors()` and `getHighlight()` results for the file.
- `DartProblemGroup` - `SuppressableProblemGroup` providing suppress/ignore actions for analysis errors.

## Invariants
- Annotations come from Analysis Server results, not from PSI analysis.

## Side Effects
- Adds editor annotations and highlighting; reads cached server results only.

## Verification
- No dedicated unit tests; verify by opening a Dart file with errors and checking highlights and quick fixes.
