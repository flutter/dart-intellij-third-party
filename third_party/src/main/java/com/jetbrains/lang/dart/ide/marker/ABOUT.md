<!--* freshness: { reviewed: '2026-10-25' } *-->

# marker

## Overview

Gutter line markers for Dart editors.

## Interface

- `DartMethodLineMarkerProvider`: draws method separator lines from PSI.
- `DartServerImplementationsMarkerProvider`: draws "is implemented/overridden"
  markers from `DartServerData` implemented-class and implemented-member
  data supplied by the analysis server.
- `DartServerOverrideMarkerProvider`: draws "overrides/implements" markers
  from `DartServerData` override data supplied by the analysis server.

## Invariants

- The server-backed providers show nothing until the analysis server has
  sent data for the file.

## Side Effects

- None.

## Verification

- `DartServerImplementationsMarkerProviderTest`,
  `DartServerOverrideMarkerProviderTest` (in `com.jetbrains.dart.analysisServer`)
