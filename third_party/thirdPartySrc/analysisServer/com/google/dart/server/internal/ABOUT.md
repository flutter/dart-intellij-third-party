<!--* freshness: { reviewed: '2026-10-25' } *-->
# internal

## Overview
Internal multiplexer and broadcasting listener wrapper (BroadcastAnalysisServerListener).

## Interface
- com.google.dart.server.internal.BroadcastAnalysisServerListener
- [./remote/ABOUT.md](./remote/ABOUT.md)

## Invariants
- Fan-out notification broadcast to multiple AnalysisServerListener instances.

## Side Effects
- Forwards notifications to subscribers.
