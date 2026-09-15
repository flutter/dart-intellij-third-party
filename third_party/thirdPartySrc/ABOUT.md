<!--* freshness: { reviewed: '2026-08-31' } *-->
# thirdPartySrc

## Overview
Container directory for third-party source modules integrated into the Dart IntelliJ plugin codebase.

## Subdirectories & Boundaries
- `analysisServer`: [./analysisServer/ABOUT.md](./analysisServer/ABOUT.md) - Dart Analysis Server client library, remote processors, and protocol model classes.
- `platform-lsp`: [./platform-lsp/ABOUT.md](./platform-lsp/ABOUT.md) - Vendored JetBrains IntelliJ Platform LSP API and implementation.

## Invariants
- Third-party root partition for vendored library dependencies.
- Contains external upstream source dependencies adapted for the plugin.

## Side Effects
- Provides protocol models and LSP runtime services to the Dart plugin.
