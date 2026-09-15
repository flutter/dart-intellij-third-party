<!--* freshness: { reviewed: '2026-08-04' } *-->
# actions

## Overview
IDE action handlers (CreateDartFileAction, DartStyleAction, DartPubGetAction, DartSortMembersAction) for Dart development tasks.

## Interface
- com.jetbrains.lang.dart.ide.actions.CreateDartFileAction
- com.jetbrains.lang.dart.ide.actions.DartStyleAction
- com.jetbrains.lang.dart.ide.actions.DartPubGetAction
- com.jetbrains.lang.dart.ide.actions.DartSortMembersAction

## Invariants
- Actions extend AnAction or AbstractDartFileProcessingAction and execute in WriteAction or background task.

## Side Effects
- Invokes dart format, pub get, creates files, or formats document ranges.
