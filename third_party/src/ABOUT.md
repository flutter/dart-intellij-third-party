<!--* freshness: { reviewed: '2026-10-25' } *-->
# src

## Overview
Source code directory containing the Dart plugin production code and resources (`main/`) and its tests (`test/`).

## Subdirectories & Boundaries
- `main`: [./main/ABOUT.md](./main/ABOUT.md) - Production Java sources and plugin resources.
- `test`: Test tree (no ABOUT.md). `test/java/com/jetbrains/lang/dart/` holds the unit tests; `test/java/com/jetbrains/dart/` holds the `analysisServer`, `analytics`, `dartToolingDaemon`, and `vmService` tests; `test/testData/` holds fixture files.

## Invariants
- Source container.
- The `com.jetbrains.dart.analysisServer` tests require `DART_HOME` to point at a Dart SDK.
