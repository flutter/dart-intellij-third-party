<!--* freshness: { reviewed: '2026-10-25' } *-->
# customization

## Overview
Per-feature customization API for LSP servers. An `LspServerDescriptor` exposes an `LspCustomization`, which returns one customizer per LSP feature; most customizers are sealed classes with an open `Lsp*Support` subclass (enabled, overridable) and an `Lsp*Disabled` object.

## Interface
- `LspCustomization` - Aggregates all feature customizers for a server.
- `DeprecatedLspCustomization` - Internal adapter mapping legacy descriptor properties onto `LspCustomization`.
- `Lsp*Customizer` - One per feature: call/type hierarchy, code actions, code lens, commands, completion, diagnostics, document color/highlights/link/symbol, find references, folding range, formatting, on-type formatting, go to (type) definition, hover, inlay hints, optimize imports, rename, selection range, semantic tokens, signature help, symbol kind, workspace symbol.
- `LspIntentionAction` - `IntentionAction` wrapping an LSP `CodeAction`.
- `LspWorkspaceSymbolNavigationItem` - Navigation item for workspace-symbol results.

## Invariants
- Upstream-synced; changes must be codified in `patch.py` (e.g. `LspDiagnosticsCustomizer` is patched there).

## Side Effects
- None directly; customizers are consulted by `../../impl/features/`.

## Verification
- No tests in this directory; re-run `patch.py` and build the plugin after syncing.
