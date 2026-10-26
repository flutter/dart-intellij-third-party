<!--* freshness: { reviewed: '2026-10-25' } *-->
# features

## Overview
IDE feature implementations backed by LSP requests. Each subdirectory wires one platform extension (contributor, provider, handler) to the corresponding LSP method, consulting the server's `LspCustomization` (see `../../api/customization/`).

## Subdirectories & Boundaries
- `codeLens/` - Code Vision provider for `textDocument/codeLens` (`LspCodeVisionProvider` patched by `patch.py`).
- `completion/` - Code completion.
- `documentSymbol/` - Breadcrumbs and structure-view support from `textDocument/documentSymbol`.
- `documentation/` - Documentation target provider backed by hover (`HoverResultCache`).
- `folding/` - Folding ranges.
- `formatter/` - Formatting service, import optimizer, and on-type formatting.
- `hierarchy/` - Call (`hierarchy/call/`) and type (`hierarchy/type/`) hierarchy browsers.
- `highlighting/` - Diagnostics and semantic highlighting (`LspDiagnosticAndLazyQuickFixes` patched by `patch.py`).
- `highlightingCommon/` - Shared highlighting caches.
- `inlayHint/` - Inlay hints provider.
- `inlayHintColor/` - Color inlay hints from `textDocument/documentColor`.
- `intention/`, `quickFix/` - Code actions surfaced as intentions and quick fixes.
- `navigation/` - Go to (type) definition and implicit references (`LspImplicitReferenceProvider` patched by `patch.py`).
- `parameterInfo/` - Signature help.
- `rename/` - Rename handler.
- `selectWord/` - Extend selection via selection ranges.
- `usages/` - Find usages.
- `workspaceSymbol/` - Go to Class/Symbol contributors via `workspace/symbol`.

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- Sends LSP requests and applies results to editors/documents.
