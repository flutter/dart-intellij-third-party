<!--* freshness: { reviewed: '2026-08-04' } *-->
# dtd

## Overview
Dart Tooling Daemon (DTD) process management (DTDProcess.kt).

## Interface
- com.jetbrains.lang.dart.dtd.DTDProcess

## Invariants
- Manages lifecycle and connection to DTD process for Dart development tools.

## Side Effects
- Launches background DTD process and communicates via WebSocket.
