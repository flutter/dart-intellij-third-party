<!--* freshness: { reviewed: '2026-08-04' } *-->
# analyzer

## Overview
Services for managing Dart Analysis Server integration (DartAnalysisServerService, DartServerRootsHandler).

## Interface
- com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
- com.jetbrains.lang.dart.analyzer.DartServerRootsHandler
- com.jetbrains.lang.dart.analyzer.DartClosingLabelManager
- com.jetbrains.lang.dart.analyzer.DASSCacheInvalidator

## Invariants
- Application-level service managing analysis roots, server lifecycle, and file diagnostics cache.

## Side Effects
- Starts external analysis server process, registers document listeners, updates editor highlighting.
