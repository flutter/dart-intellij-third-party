<!--* freshness: { reviewed: '2026-10-25' } *-->
# util

## Overview
Utility functions for LSP/Document position conversion, text edit application, navigation to LSP locations, and parsing locations from LSP command arguments.

## Interface
- `Lsp4jUtil.kt`: `getLsp4jPosition`, `getLsp4jRange`, `getOffsetInDocument`, `getRangeInDocument` (IntelliJ `Document` offsets <-> lsp4j `Position`/`Range`); `applyTextEdits`/`applyTextEdit` (apply lsp4j `TextEdit`s to a `Document`); `getDocumentFilterPattern` (extract a `DocumentFilter` pattern); `Diagnostic.messageIfStringOrEmpty` (string message accessor used by diagnostics code, added via `patch.py`).
- `LspNavigationUtils.kt`: `navigateOrShowPopup`, `navigateToLocation`, `showLspNavigationPopup` for opening LSP `Location`s.
- `Lsp4jService.kt`: Abstract application service with `extractLocationsFromJson(arguments)`, which parses `Location` lists from LSP command arguments (e.g. "showReferences"); implemented by `impl/util/Lsp4jServiceImpl`.

## Invariants
- Position conversions work on IntelliJ `Document` offsets only; this package has no `VirtualFile`/URI converters.

## Side Effects
- `applyTextEdits`/`applyTextEdit` modify `Document` contents; navigation helpers open editors or show popups. Other functions are pure conversions.
