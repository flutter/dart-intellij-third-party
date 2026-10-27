<!--* freshness: { reviewed: '2026-10-25' } *-->
# assists

## Overview
Quick assist intentions powered by the Analysis Server (`edit_getAssists`).

## Interface
- `DartQuickAssistIntention`: base intention. Assists are exposed as fixed numbered
  subclasses `DartQuickAssistIntention0` through `DartQuickAssistIntention14` (at most 15).
- `DartQuickAssistSet`: application service that fetches and caches assists.
- `DartQuickAssistIntentionListener`: listener fired through
  `DartAnalysisServerService.fireBeforeQuickAssistIntentionInvoked`.
- `AssistUtils`: applies `SourceChange` edits to documents.
- `DartSourceEditException`: thrown when a source edit cannot be applied.

## Invariants
- All legacy assists are unavailable when `DartConfigurable.isLspCodeActionsEnabled(project)`
  is true; LSP code actions are used instead.
- `DartQuickAssistSet` reuses the last result while the file path, selection, and PSI
  modification count are unchanged.
- Intention preview edits a non-physical copy (`DartQuickFix.doInvokeForPreview`).

## Side Effects
- Modifies document text when an assist is applied.
- Reports analytics: `AnalyticsData.forAssist` when an assist is applied, and a
  `dart.legacy_assist` duration event per `edit_getAssists` request.
