<!--* freshness: { reviewed: '2026-10-25' } *-->

# testIntegration

## Overview

Navigation between a Dart source file and its test.

## Interface

- `DartTestFinder`: registered as `testFinder`. It maps a file to its test
  and back using Dart naming conventions (`foo.dart` ↔ `foo_test.dart`).

## Invariants

- Uses file names and paths only. It never consults the analysis server.

## Side Effects

- None.

## Verification

- `com.jetbrains.lang.dart.ide.testIntegration.DartTestFinderTest`
