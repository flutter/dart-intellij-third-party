<!--* freshness: { reviewed: '2026-10-25' } *-->
# analyzer

## Overview
Integration with the Dart Analysis Server (DAS): process lifecycle, analysis roots, the
client-side cache of server results, and error reporting.

## Interface
- `DartAnalysisServerService`: project-level service that owns the DAS connection,
  requests, and listeners.
- `DartAnalysisServerImpl` (internal): `RemoteAnalysisServerImpl` subclass that handles
  server-initiated requests such as opening URLs and showing messages.
- `DartServerData`: cache of diagnostics, highlights, navigation, outline, and other
  per-file server results.
- `DartServerRootsHandler`: computes and sends analysis roots.
- `DartAnalysisServerErrorHandler`: logs DAS errors to the IDE event log with on-demand details.
- `DartAnalysisServerMessages`: sends and receives analysis-busy messages on the project message bus.
- `DartClosingLabelManager`: application-level service that toggles the closing-labels
  declarative inlay provider.
- `DartFileInfo.kt`: `getDartFileInfo(...)`, `DartLocalFileInfo`, `DartNotLocalFileInfo`.
- `DartLspUtil.kt`: LSP position-to-offset and text-edit helpers.

## Invariants
- `DartAnalysisServerService` is project-level (`project.getService(...)`); only
  `DartClosingLabelManager` is application-level.
- `DASSCacheInvalidator` is a private `CachesInvalidator` extension, not public API.

## Side Effects
- Starts the external analysis server process and registers document listeners.
- When the server starts, also starts the LSP bridge
  (`DartBridgeLspServerManager.startBridgeServer()`; see [../lsp/ABOUT.md](../lsp/ABOUT.md)).
- `DartClosingLabelManager` changes the declarative inlay provider setting and schedules
  inlay recomputation in open editors.
- `DASSCacheInvalidator` (File | Invalidate Caches) stops servers in open projects and deletes
  `~/.dartServer` (`%LOCALAPPDATA%\.dartServer` on Windows).

## Verification
- Tests live under `third_party/src/test/java/com/jetbrains/dart/analysisServer`.
