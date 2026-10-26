<!--* freshness: { reviewed: '2026-10-25' } *-->
# fixes

## Overview
Quick fixes for Dart analysis errors, backed by Analysis Server `SourceChange` fixes.

## Interface
- `DartQuickFix`: the `IntentionAction` that applies one server fix. Its
  `doInvokeForPreview` is also reused by assists.
- `DartQuickFixSet`: not an intention. A per-error holder of up to `MAX_QUICK_FIXES = 5`
  `DartQuickFix` instances that are filled lazily.
- `DartQuickFixListener`: listener fired through `DartAnalysisServerService.fireBeforeQuickFixInvoked`.

## Invariants
- `DartQuickFixSet` fetches fixes through `askForFixesAndWaitABitIfReceivedQuickly`.
- Fixes whose ID starts with `dart.fix.ignore` go to `DartProblemGroup` as ignore fixes
  instead of quick-fix slots.
- On SDKs older than 2.14, non-error problems show the plugin's own suppress actions.
- Intention preview edits a non-physical copy (`doInvokeForPreview`).

## Side Effects
- Mutates document source when a fix is applied. When a file edit has `fileStamp == -1`,
  `DartQuickFix` first creates the missing directories and file.
- Reports analytics: `forFix` when a fix is applied, and a `dart.legacy_fix` duration event
  when fixes are fetched.
