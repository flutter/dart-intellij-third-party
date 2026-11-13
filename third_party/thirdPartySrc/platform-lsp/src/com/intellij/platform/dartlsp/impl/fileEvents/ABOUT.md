<!--* freshness: { reviewed: '2026-10-25' } *-->
# fileEvents

## Overview
Support for `workspace/didChangeWatchedFiles`: reports file-system events matching the server's dynamically registered watchers.

## Interface
- `LspWatchedFiles` - Per-server sender that filters changes against `didChangeWatchedFiles` registration options from `LspDynamicCapabilities`.
- `FileChangeInfo` - Description of a single file change to report.

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- Sends watched-file notifications to servers.

## Verification
- No tests in this directory.
