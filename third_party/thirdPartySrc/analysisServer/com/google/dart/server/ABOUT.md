<!--* freshness: { reviewed: '2026-10-25' } *-->
# server

## Overview
Public client API for the Dart Analysis Server: listener interfaces, per-request consumer callbacks, the server socket abstraction, and error/notification enums. The code came from the Dart SDK but is now maintained locally; direct edits here are expected (see [../../../../../README.md](../../../../../README.md)).

## Interface
- `AnalysisServerListener` / `AnalysisServerListenerAdapter` - Notification callbacks and a no-op adapter base class.
- `AnalysisServerStatusListener` - Server liveness callback.
- `AnalysisServerSocket` - Abstraction over starting/stopping the server process and its request/response streams.
- `RequestListener` / `ResponseListener` - Hooks observing raw outgoing requests and incoming responses.
- `Consumer`, `BasicConsumer`, `JsonConsumer` - Base callback types; 47 `*Consumer*.java` files in total, one per typed request (e.g. `GetHoverConsumer`, `GetSuggestionsConsumer2`, `GetSuggestionDetailsConsumer2`).
- `DartLspTextDocumentContentConsumer`, `DartLspWorkspaceApplyEditRequestConsumer`, `ShowMessageRequestConsumer` - Consumers for LSP-over-legacy-protocol requests and server-to-client requests.
- `AnalysisServerErrorCode`, `ExtendedRequestErrorCode`, `NotificationKind`, `DebugService` - Error code, notification, and debug-service enums/constants.
- [./generated/ABOUT.md](./generated/ABOUT.md) - `AnalysisServer` request interface (hand-maintained).
- [./internal/ABOUT.md](./internal/ABOUT.md) - Broadcast listener and remote stdio transport.
- [./utilities/ABOUT.md](./utilities/ABOUT.md) - General, instrumentation, and logging helpers.

## Invariants
- Defines consumer interfaces for typed request responses.
- Locally maintained fork of upstream Dart SDK client code.

## Side Effects
- Asynchronous response callback handling.
