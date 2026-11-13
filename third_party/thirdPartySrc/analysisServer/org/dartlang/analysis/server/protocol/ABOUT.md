<!--* freshness: { reviewed: '2026-10-25' } *-->
# protocol

## Overview
Data classes representing Dart Analysis Server protocol DTOs (e.g. AnalysisError, CompletionSuggestion, HoverInformation, Location, Outline), plus hand-written Kotlin types for LSP-over-legacy-protocol messages.

## Interface
- org.dartlang.analysis.server.protocol.AnalysisError
- org.dartlang.analysis.server.protocol.CompletionSuggestion
- org.dartlang.analysis.server.protocol.HoverInformation
- org.dartlang.analysis.server.protocol.Location
- org.dartlang.analysis.server.protocol.Outline
- org.dartlang.analysis.server.protocol.* (104 generated Java protocol model classes)
- `DartLspProtocol.kt` - Hand-written Kotlin DartLsp* types (`DartLspApplyWorkspaceEditParams`, `DartLspApplyWorkspaceEditResult`, `DartLspWorkspaceEdit`, `DartLspTextEdit`, `DartLspDocumentChange`, `DartLspTextDocumentEdit`, `DartLspVersionedTextDocumentIdentifier`, `DartLspCreateFile`/`DartLspRenameFile`/`DartLspDeleteFile`, `DartLspRange`, `DartLspPosition`), used by `AnalysisServer.lsp_workspaceApplyEdit`.

## Invariants
- Parses and wraps raw JsonObject/JsonArray instances from DAS JSON protocol.
- The `.java` files carry the Dart SDK "automatically generated" header and are generated from the SDK protocol spec; do not hand-edit them. `DartLspProtocol.kt` is not generated.

## Side Effects
- Read-only DTO data accessors.
