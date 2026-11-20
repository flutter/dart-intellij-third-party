<!--* freshness: { reviewed: '2026-11-18' } *-->
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
- Unit tests in `src/test/java/com/jetbrains/lang/dart/ide/annotator/DartAnnotatorTest.kt` cover legacy highlight suppression when LSP semantic tokens highlighting is active; also verify errors and quick fixes by opening a Dart file in the IDE.
