<!--* freshness: { reviewed: '2026-10-30' } *-->
# codeInsight

## Overview
Application-level persisted settings for Dart code insight.

## Interface
- `DartCodeInsightSettings` - `PersistentStateComponent` stored in `other.xml`; field `INSERT_DEFAULT_ARG_VALUES` (default true).

## Invariants
- Application service obtained via `DartCodeInsightSettings.getInstance()`.

## Side Effects
- Persists settings to `other.xml`.

## Verification
- No dedicated tests; verify via Settings > Editor > General > Smart Keys for Dart.
