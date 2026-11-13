<!--* freshness: { reviewed: '2026-10-25' } *-->
# dartlsp

## Overview
Package root for IntelliJ LSP platform integration module. This is a renamed copy of upstream `com.intellij.platform.lsp` (from `intellij-community`), produced by `.agents/skills/patch-copied-lsp-sources/scripts/patch.py`.

## Interface
- [./api/ABOUT.md](./api/ABOUT.md) - Public LSP API (server support providers, descriptors, customizers).
- [./impl/ABOUT.md](./impl/ABOUT.md) - Server lifecycle, connectors, and editor feature implementations.
- [./util/ABOUT.md](./util/ABOUT.md) - LSP4J/document conversion and navigation helpers.

## Invariants
- LSP integration package.
- Edits must be codified in `patch.py`; direct edits are lost on the next sync.
