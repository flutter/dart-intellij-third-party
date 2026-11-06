<!--* freshness: { reviewed: '2026-10-25' } *-->
# test

## Overview
Platform `TestSourcesFilter` for Dart (`DartTestSourcesFilter`). It marks files under a package's `test/` folder as test sources platform-wide, without requiring a test source root to be configured. This affects search scopes, inspections, navigation, run actions, and the Project View, not only the Project View.

## Interface
- com.jetbrains.lang.dart.test.DartTestSourcesFilter: extends `com.intellij.openapi.roots.TestSourcesFilter`.

## Invariants
- Returns `true` only for local-file-system files under a project base directory, when the file itself or one of its ancestors (still inside that base directory) is a `test` folder next to a `pubspec.yaml`. A directory merely named `test` does not qualify.
- Depends on `projectView.DartIconProvider.isFolderNearPubspecYaml` for the "next to a `pubspec.yaml`" check.

## Side Effects
- Read-only VFS checks used to classify files as test sources.

## Verification
- No dedicated tests in this package. Check manually: files under `<package>/test/` should appear in the "Test" scopes.
