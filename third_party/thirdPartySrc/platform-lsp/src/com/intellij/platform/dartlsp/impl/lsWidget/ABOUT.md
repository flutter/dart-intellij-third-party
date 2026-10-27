<!--* freshness: { reviewed: '2026-10-25' } *-->
# lsWidget

## Overview
Implementation of the Language Services status bar widget entries for LSP servers.

## Interface
- `LspWidgetItemsProvider` - `LanguageServiceWidgetItemsProvider` contributing one item per running LSP server.
- `LspWidgetInternalServiceImpl` - Implements `api/lsWidget/LspWidgetInternalService`, including an action to open a server's error output.

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- Updates status bar widget UI.

## Verification
- No tests in this directory.
