<!--* freshness: { reviewed: '2026-10-25' } *-->
# copyright

## Overview
Copyright plugin integration for Dart files.

## Interface
- `UpdateDartCopyrightsProvider` - `UpdateCopyrightsProvider` that returns a private `UpdateJavaScriptFileCopyright` subclass accepting only `DartFile`s (JS-style comment headers).

## Invariants
- Only applies to `DartFile` PSI.

## Side Effects
- Inserts or updates copyright header comments in Dart files when the Copyright action runs.

## Verification
- No dedicated tests; verify with Update Copyright on a Dart file.
