<!--* freshness: { reviewed: '2026-10-25' } *-->
# codeInsight

## Overview
Application-level persisted settings for Dart code insight.

## Interface
- `DartCodeInsightSettings` - `PersistentStateComponent` stored in `other.xml`; fields `INSERT_DEFAULT_ARG_VALUES` (default true) and `ADD_IMPORTS_ON_PASTE` (YES/NO/ASK, default ASK).

## Invariants
- Application service obtained via `DartCodeInsightSettings.getInstance()`.

## Side Effects
- Persists settings to `other.xml`.

## Verification
- No dedicated tests; verify via Settings > Editor > General > Smart Keys / Auto Import for Dart.
