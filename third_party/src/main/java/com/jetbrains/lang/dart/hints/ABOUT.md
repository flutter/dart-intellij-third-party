<!--* freshness: { reviewed: '2026-08-31' } *-->
# hints

## Overview
Declarative inlay closing labels provider (`DartClosingLabelsInlayHintsProvider.kt`) for rendering closing labels at the end of Dart code blocks.

## Interface
- com.jetbrains.lang.dart.hints.DartClosingLabelsInlayHintsProvider

## Invariants
- Implements IntelliJ declarative `InlayHintsProvider` with provider ID `dart.closing.labels`.
- Resolves closing label positions and text using `DartAnalysisServerService.getClosingLabels()`.

## Side Effects
- Renders closing label text presentations at end-of-line positions in the active editor.
