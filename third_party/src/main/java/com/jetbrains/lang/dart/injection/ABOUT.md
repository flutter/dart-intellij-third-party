<!--* freshness: { reviewed: '2026-08-04' } *-->
# injection

## Overview
Multi-host language injector (DartMultiHostInjector) for injecting languages into Dart string literals.

## Interface
- com.jetbrains.lang.dart.injection.DartMultiHostInjector

## Invariants
- Injects HTML/RegExp/SQL into annotated Dart strings.

## Side Effects
- Registers inlined language fragments with IntelliJ PSI.
