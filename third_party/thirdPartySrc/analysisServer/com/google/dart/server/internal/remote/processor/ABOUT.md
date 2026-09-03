<!--* freshness: { reviewed: '2026-08-04' } *-->
# processor

## Overview
Notification and response processors for parsing incoming Analysis Server JSON responses.

## Interface
- com.google.dart.server.internal.remote.processor.*Processor (55 notification/result processors e.g. AnalysisErrorsProcessor, AssistsProcessor, HoverProcessor, NavigationProcessor)

## Invariants
- Parses JSON response objects and dispatches typed data to registered AnalysisServerListener callbacks.

## Side Effects
- Invokes listener callback methods on IDE threads.
