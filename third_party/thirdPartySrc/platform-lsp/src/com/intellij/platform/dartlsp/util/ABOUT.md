<!--* freshness: { reviewed: '2026-08-31' } *-->
# util

## Overview
Utility functions for LSP URI resolution, positioning conversion, and lsp4j data conversion.

## Interface
- `Lsp4jUtil.kt`: Extension functions and converters between IntelliJ Document/VirtualFile offsets and LSP Position/Range.
- `LspNavigationUtils.kt`: Navigation helpers for opening LSP locations and finding editor elements.
- `Lsp4jService.kt`: Coroutine and threading helpers for asynchronous LSP operations.

## Invariants
- Converts between IntelliJ VirtualFile/Document offsets and LSP Range/Position/DocumentUri.

## Side Effects
- Read-only data mapping and document offset calculations.
