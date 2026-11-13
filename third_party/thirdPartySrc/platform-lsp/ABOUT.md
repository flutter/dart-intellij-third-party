<!--* freshness: { reviewed: '2026-10-25' } *-->
# platform-lsp

## Overview
Third-party platform LSP source module patched for Dart plugin LSP support. Copied from JetBrains `intellij-community` (`platform/lsp`, `platform/lsp-impl`, `platform/lsp-impl/structureView`, and their `resources/`) by [`.agents/skills/patch-copied-lsp-sources/scripts/patch.py`](../../../.agents/skills/patch-copied-lsp-sources/scripts/patch.py), which renames the package `com.intellij.platform.lsp` to `com.intellij.platform.dartlsp` and applies local patches.

## Interface
- [./src/ABOUT.md](./src/ABOUT.md) - Kotlin/Java sources (`com.intellij.platform.dartlsp`).
- `resources/dart-lsp-impl.xml` - Plugin XML registrations (extensions, services, listeners) for the copied LSP module, produced and patched by `patch.py`.
- `resources/intentionDescriptions/DartLspIntention` - Intention description resources (renamed from upstream `LspIntention`).

## Invariants
- Third-party module partition.
- Do not edit files here directly; codify every change in `patch.py` so it survives the next sync (see [../README.md](../README.md)).
- Sources carry JetBrains' Apache 2.0 license headers.
