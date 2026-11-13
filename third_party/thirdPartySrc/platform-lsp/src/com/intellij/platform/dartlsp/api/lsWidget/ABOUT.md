<!--* freshness: { reviewed: '2026-10-25' } *-->
# lsWidget

## Overview
API for the Language Services status bar widget/popup that lists running LSP servers.

## Interface
- `LspServerWidgetItem` - Open `LanguageServiceWidgetItem` for one `LspServer` (icon, tooltip with version, error state when `ShutdownUnexpectedly`, popup section and actions).
- `LspWidgetInternalService` - Abstract application service backing the widget; implemented by `impl/lsWidget/LspWidgetInternalServiceImpl`.

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- None directly; UI behaviour lives in `../../impl/lsWidget/`.

## Verification
- No tests in this directory; re-run `patch.py` and build the plugin after syncing.
