<!--* freshness: { reviewed: '2026-10-25' } *-->
# thirdPartySrc

## Overview
Container directory for third-party source modules integrated into the Dart IntelliJ plugin codebase. It mixes upstream-synced copies with code that originated upstream but is now maintained locally. See [./README.md](./README.md) for the edit policy and per-subtree provenance.

## Subdirectories & Boundaries
- `analysisServer`: [./analysisServer/ABOUT.md](./analysisServer/ABOUT.md) - Dart Analysis Server Java client library, remote processors, and protocol model classes (originally from the Dart SDK, EPL v1.0; partly forked locally).
- `platform-lsp`: [./platform-lsp/ABOUT.md](./platform-lsp/ABOUT.md) - JetBrains IntelliJ Platform LSP API and implementation, copied from `intellij-community` and renamed to `com.intellij.platform.dartlsp`.

## Invariants
- `platform-lsp/` is upstream-synced: never edit it directly; codify every change in `.agents/skills/patch-copied-lsp-sources/scripts/patch.py` so it survives the next sync.
- `analysisServer/org/dartlang/analysis/server/protocol/*.java` are generated from the Dart SDK spec; `analysisServer/com/google/dart/server/` (including the hand-maintained `generated/AnalysisServer.java`) is a local fork where direct edits are expected.

## Side Effects
- Provides protocol models and LSP runtime services to the Dart plugin.
