<!--* freshness: { reviewed: '2026-10-25' } *-->
# platformListeners

## Overview
IntelliJ platform listeners that forward IDE events to running LSP servers.

## Interface
- `LspDocumentListener` - `DocumentListener` driving `didChange` notifications.
- `LspFileDocumentManagerListener` - `FileDocumentManagerListener` reacting to document saves.
- `LspFileEditorManagerListener` - `FileEditorManagerListener` reacting to editors opening/closing files.
- `LspFileListener` - `AsyncFileListener` forwarding VFS changes (e.g. to `../fileEvents/`).

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.
- Registered via `platform-lsp/resources/dart-lsp-impl.xml`.

## Side Effects
- Triggers LSP notifications in response to IDE events.

## Verification
- No tests in this directory.
