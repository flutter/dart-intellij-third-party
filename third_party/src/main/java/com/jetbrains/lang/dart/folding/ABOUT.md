<!--* freshness: { reviewed: '2026-08-04' } *-->
# folding

## Overview
Code folding builder (DartFoldingBuilder) and folding settings for Dart files.

## Interface
- com.jetbrains.lang.dart.folding.DartFoldingBuilder
- com.jetbrains.lang.dart.folding.DartCodeFoldingSettings

## Invariants
- Computes code folding regions for imports, class bodies, and function bodies.

## Side Effects
- Read-only AST traversal producing FoldingDescriptor ranges.
