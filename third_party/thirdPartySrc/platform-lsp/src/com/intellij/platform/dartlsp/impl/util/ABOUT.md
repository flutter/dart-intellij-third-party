<!--* freshness: { reviewed: '2026-10-25' } *-->
# util

## Overview
Internal implementation helpers for the `dartlsp` API utilities.

## Interface
- `Lsp4jServiceImpl` - Implements `../../util/Lsp4jService` (`extractLocationsFromJson`).
- `Lsp4jConverters.kt` - `Location.toLocationLink()`.
- `LspWorkspaceEditApplier` - Applies an lsp4j `WorkspaceEdit` by wrapping it in a `CodeAction` and invoking `LspIntentionAction` on the EDT.

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- `LspWorkspaceEditApplier.applyWorkspaceEdit()` modifies documents/files.

## Verification
- No tests in this directory.
