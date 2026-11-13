<!--* freshness: { reviewed: '2026-10-25' } *-->
# logging

## Overview
Logging of communication between the IDE and external language services (LSP servers).

## Interface
- `LanguageServiceLoggerService` - Application service managing loggers per log file name (`connect`/`disconnect`).
- `LanguageServiceLogger` - Writes one server's traffic log.

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- Writes log files under `<IDE log dir>/language-services/`; the number of files per log name is bounded by `MAX_LOG_FILE_COUNT` (5), with older files deleted.

## Verification
- No tests in this directory.
