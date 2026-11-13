<!--* freshness: { reviewed: '2026-10-25' } *-->
# instrumentation

## Overview
Instrumentation logging and health metric tracking utilities (Instrumentation, HealthUtils), plus a bundled generic Base64 codec.

## Interface
- com.google.dart.server.utilities.instrumentation.Instrumentation - Static entry point holding the active `InstrumentationLogger`.
- com.google.dart.server.utilities.instrumentation.InstrumentationBuilder - Interface for building and logging a single instrumentation record.
- com.google.dart.server.utilities.instrumentation.InstrumentationLogger - Interface for creating `InstrumentationBuilder`s.
- com.google.dart.server.utilities.instrumentation.InstrumentationLevel - Recording levels: `EVERYTHING`, `METRICS`, `OFF`.
- com.google.dart.server.utilities.instrumentation.HealthUtils
- com.google.dart.server.utilities.instrumentation.Base64 - Generic Base64 encoder/decoder (not instrumentation-specific).

## Invariants
- Collects performance and error metrics for DAS communication.

## Side Effects
- Outputs diagnostic performance metrics and health logs.
