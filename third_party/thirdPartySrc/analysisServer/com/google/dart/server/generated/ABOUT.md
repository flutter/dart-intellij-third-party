<!--* freshness: { reviewed: '2026-09-25' } *-->
# generated

## Overview
AnalysisServer client interface (AnalysisServer) for Dart Analysis Server API. Originally generated from the Dart SDK spec, now maintained by hand despite its "generated" header and package name.

## Interface
- com.google.dart.server.generated.AnalysisServer

## Invariants
- Declares the DAS request methods (e.g. analysis_setAnalysisRoots, completion_getSuggestions2) plus hand-written LSP-over-legacy methods (lsp_connectToDtd, lsp_workspaceApplyEdit, lsp_workspaceConfiguration).
- Must not be regenerated from the Dart SDK; edit it by hand together with RemoteAnalysisServerImpl (see the comment at the top of AnalysisServer.java).

## Side Effects
- Submits JSON-RPC requests to the running Dart Analysis Server instance.
