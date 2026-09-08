<!--* freshness: { reviewed: '2026-08-04' } *-->
# protocol

## Overview
Data classes representing Dart Analysis Server protocol DTOs (e.g. AnalysisError, CompletionSuggestion, HoverInformation, Location, Outline).

## Interface
- org.dartlang.analysis.server.protocol.AnalysisError
- org.dartlang.analysis.server.protocol.CompletionSuggestion
- org.dartlang.analysis.server.protocol.HoverInformation
- org.dartlang.analysis.server.protocol.Location
- org.dartlang.analysis.server.protocol.Outline
- org.dartlang.analysis.server.protocol.* (105 protocol model classes)

## Invariants
- Parses and wraps raw JsonObject/JsonArray instances from DAS JSON protocol.

## Side Effects
- Read-only DTO data accessors.
