<!--* freshness: { reviewed: '2026-10-25' } *-->
# structureView

## Overview
Structure view for LSP-backed files, built from `textDocument/documentSymbol`. Copied from upstream `platform/lsp-impl/structureView`.

## Interface
- `LspStructureViewFactory` - `PsiStructureViewFactory` building the tree from document symbols (patched by `patch.py`; its upstream `lang.psiStructureViewFactory` XML registration is also adjusted there).
- `LspStructureViewProjectActivity` - Startup activity that refreshes the structure view when servers start/stop or open files (registered as a `postStartupActivity` by `patch.py`).

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- Publishes `StructureViewWrapperImpl.STRUCTURE_CHANGED` on the EDT via `invokeLater`.

## Verification
- No tests in this directory.
