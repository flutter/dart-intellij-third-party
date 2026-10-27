<!--* freshness: { reviewed: '2026-10-25' } *-->
# documentSync

## Overview
Keeps the language server's view of open documents in sync with the IDE.

## Interface
- `LspDocumentSyncManager` - Per-server sender of `textDocument/didOpen`, `didChange`, `didClose` (and save) notifications.
- `LspOpenedFilesService` - Project service tracking which files are open for LSP purposes.
- `LspDidChangeUtil` - Builds `didChange` content-change events (full or incremental).

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- Sends document notifications to running servers.

## Verification
- No tests in this directory.
