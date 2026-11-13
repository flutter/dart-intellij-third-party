<!--* freshness: { reviewed: '2026-10-25' } *-->
# resolve

## Overview
Reference resolution for Dart PSI. `DartResolver` resolves `DartReference`s from Analysis Server navigation regions, cached through the platform `ResolveCache`. `DartPsiScopeProcessor` and its subclasses do PSI-based declaration lookups that respect `show`/`hide` combinators.

## Interface
- com.jetbrains.lang.dart.resolve.DartResolver: implements `ResolveCache.AbstractResolver<DartReference, List<? extends PsiElement>>`. PSI impls call it as `ResolveCache.resolveWithCaching(ref, DartResolver.INSTANCE, …)`.
  - Static helpers: `findRegion`, `processRegionsInRange`, `getTargetElements`, `getElementForNavigationTarget`.
- com.jetbrains.lang.dart.resolve.DartPsiScopeProcessor: abstract `PsiScopeProcessor`. It filters `DartComponentName`s through a stack of imported-file `show`/`hide` filters, hides private (`_`) names from imports, and delegates to `doExecute`.
- com.jetbrains.lang.dart.resolve.DartResolveProcessor: finds a named, non-member component, telling getters from setters by l-value context.
- com.jetbrains.lang.dart.resolve.ComponentNameScopeProcessor: collects all component names into a set.
- com.jetbrains.lang.dart.resolve.ClassNameScopeProcessor: collects class names. It has no callers in this repo; it is kept for the Flutter plugin.

## Invariants
- `DartResolver.resolve` finds the navigation region that matches the reference's text range in `DartAnalysisServerService.getNavigation(file)`. It retries with adjusted ranges for `part of`, `super.foo`, and `new`/shorthand constructor expressions. With no matching region, it returns `null`.
- Results are memoized by `ResolveCache` and invalidated on PSI change.

## Side Effects
- Reads Analysis Server navigation data and PSI. No writes.

## Verification
- Covered by `DartServerResolverTest` (`src/test/java/com/jetbrains/dart/analysisServer/`).
