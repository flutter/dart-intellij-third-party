<!--* freshness: { reviewed: '2026-10-25' } *-->
# lsp

## Overview
In-process LSP bridge between the IntelliJ Platform LSP client and the Dart Analysis Server
(DAS) running in legacy mode. No separate `dart language-server` process is launched.
The platform connects over a loopback socket to `DartBridgeLspServer`, which translates
requests into DAS `lsp.handle` requests (or into legacy DAS requests for LSP-only methods)
on the existing [analyzer](../analyzer/ABOUT.md) connection.

## Interface
- `DartBridgeLspServer`: the bridge `DartLanguageServer`. Its KDoc describes the request flow.
- `DartBridgeLspServerManager`: project service that opens the loopback `ServerSocket` and runs the bridge.
- `DartLspServerDescriptor`: `ProjectWideLspServerDescriptor` ("Dart (Bridge)") that returns
  `LspCommunicationChannel.Socket(port, startProcess = false)`. Also defines `DartLspCompletionSupport`.
- `DartLspServerSupportProvider`: provider registered in plugin.xml.
- `DartLanguageServer`: custom protocol extensions (`dart/diagnosticServer`), plus
  `DiagnosticServerResult` and the `DartLspService` object.
- `DartLspDiagnosticConverter`: converts LSP diagnostics into legacy `AnalysisError`s for the
  Dart Analysis tool window and Project View.
- `DartLspClosingLabelsService` (with `DartPublishClosingLabelsParams`, `DartLspClosingLabel`):
  project service that holds closing labels published over LSP.
- `DartLspConfigurationSync` (with internal `DartLspConfigurationPushState`): keeps the server's
  `dart` configuration in sync with the Dart inlay-hint settings.
- `DartLspInlayHintsConfiguration`: maps Dart inlay-hint checkboxes to the `dart` configuration section.
- `DartLspInlayHintSupport`: `LspInlayHintSupport` that filters server hints by kind.
- `LspMethod`: enum of supported LSP methods and their experimental flags.

## Invariants
- Standard document sync notifications (`didOpen`, `didChange`, `didClose`) are ignored. File
  content stays synced through DAS `analysis.updateContent`.
- Some methods are not registered in legacy DAS and are translated manually (for example
  definition through `analysis.getNavigation`).
- `DartLspServerSupportProvider` ignores automatic startup (`fileOpened`) and hides the LSP
  status-bar widget. The bridge lifecycle is driven by `DartAnalysisServerService`.

## Side Effects
- Opens a loopback `ServerSocket` on a dynamic port and serves JSON-RPC in-process.
- Sends `workspace/didChangeConfiguration` when the Dart inlay-hint settings drift from what
  the server last read. Drift is detected before each inlay-hint request cycle
  (`DartLspInlayHintSupport.shouldAskServerForInlayHints`), since the settings publish no change event.
