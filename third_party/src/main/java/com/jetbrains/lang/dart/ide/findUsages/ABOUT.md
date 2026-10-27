<!--* freshness: { reviewed: '2026-10-25' } *-->
# findUsages

## Overview
Find Usages for Dart backed by the Analysis Server, plus usage grouping rules, usage types, and target-element evaluation.

## Interface
- `DartServerFindUsagesHandlerFactory` / `DartServerFindUsagesHandler` - `FindUsagesHandler` that runs `search_findElementReferences` and maps results to usages.
- `DartFindUsagesProvider` - `FindUsagesProvider` (word scanning, element type names).
- `DartTargetElementEvaluator` - `TargetElementEvaluatorEx2` resolving the element under the caret.
- `DartUsageTypeProvider` - classifies usages (read/write/etc.).
- `DartClassMemberGroupRuleProvider` + `DartClassMemberGroupingRule`, `DartUnitMemberGroupRuleProvider` + `DartUnitMemberUsageGroupingRule` - group usages by class / top-level member; `DartComponentUsageGroup` is the group node.
- `ReadActionConsumer` - `Consumer` wrapper that runs callbacks inside a read action.

## Invariants
- Reference search depends on a running Analysis Server.

## Side Effects
- Sends search requests to the Analysis Server; read-only otherwise.

## Verification
- `third_party/src/test/java/com/jetbrains/lang/dart/ide/findUsages/DartTargetElementEvaluatorTest.kt`.
