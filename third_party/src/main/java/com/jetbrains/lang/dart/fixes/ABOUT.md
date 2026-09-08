<!--* freshness: { reviewed: '2026-08-04' } *-->
# fixes

## Overview
Quick fix intentions (DartQuickFix, DartQuickFixSet) for resolving Dart analysis errors.

## Interface
- com.jetbrains.lang.dart.fixes.DartQuickFix
- com.jetbrains.lang.dart.fixes.DartQuickFixSet

## Invariants
- Applies SourceEdit fixes received from Analysis Server diagnostics.

## Side Effects
- Mutates document source code in response to quick fix invocation.
