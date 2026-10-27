<!--* freshness: { reviewed: '2026-10-25' } *-->
# completion

## Overview
Code completion for Dart backed by the Analysis Server, plus statement completion (Smart Enter), completion char filtering, and goto-declaration.

## Interface
- `DartServerCompletionContributor` - `CompletionContributor` using `completion_getSuggestions2` (or legacy `completion_getSuggestions`) and suggestion-details requests for auto-import.
- `DartServerStatementCompletionProcessor` - `SmartEnterProcessor` applying `edit_getStatementCompletion`.
- `DartLookupObject` - lookup element payload that resolves back to PSI for documentation.
- `DartCharFilter` - `CharFilter` controlling which typed chars commit or continue a lookup.
- `DartGotoDeclarationHandler` - `GotoDeclarationHandlerBase` for Dart.
- `DartCompletionExtension` - extension point `Dart.completionExtension` for customising lookups (used by e.g. Flutter).
- `DartCompletionTimerExtension` - extension point `Dart.completionTimerExtension` for completion latency reporting.

## Invariants
- Completion requires a running Analysis Server; results are mapped to IntelliJ `LookupElement`s.

## Side Effects
- Sends completion and statement-completion requests to the Analysis Server.
- Accepting a suggestion can insert text and add imports to the document.

## Verification
- No unit tests in this repo; verify by invoking completion and Smart Enter in a Dart file.
