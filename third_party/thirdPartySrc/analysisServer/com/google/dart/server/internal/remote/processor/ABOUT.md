<!--* freshness: { reviewed: '2026-10-25' } *-->
# processor

## Overview
Notification and response processors for parsing incoming Analysis Server JSON responses.

## Interface
- `JsonProcessor` - Abstract base with JSON-to-model conversion helpers.
- `NotificationProcessor` - Abstract base for notification handlers that call `AnalysisServerListener` methods (e.g. `NotificationAnalysisErrorsProcessor`, `NotificationAnalysisNavigationProcessor`, `NotificationServerStatusProcessor`).
- `ResultProcessor` - Base for request-response handlers that deliver results to per-request `Consumer` callbacks (e.g. `AssistsProcessor`, `FixesProcessor`, `HoverProcessor`, `GetNavigationProcessor`).
- `NotificationLspProcessor` - Handles LSP notifications tunnelled over the legacy protocol.
- `DartLspTextDocumentContentProcessor` - Result processor for the LSP-over-legacy text document content request.

## Invariants
- `NotificationProcessor` subclasses dispatch typed data to registered `AnalysisServerListener` callbacks.
- `ResultProcessor` subclasses dispatch typed data to the `Consumer` supplied with the originating request.

## Side Effects
- Invokes listener/consumer callbacks synchronously on the thread that reads server responses; no marshalling to IDE/EDT threads.
