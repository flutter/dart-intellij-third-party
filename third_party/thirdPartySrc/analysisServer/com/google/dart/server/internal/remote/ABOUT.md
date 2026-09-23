<!--* freshness: { reviewed: '2026-08-04' } *-->
# remote

## Overview
Remote process management and stdio I/O socket communication (RemoteAnalysisServerImpl, StdioServerSocket) for Dart Analysis Server.

## Interface
- com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
- com.google.dart.server.internal.remote.StdioServerSocket
- com.google.dart.server.internal.remote.ByteRequestSink
- com.google.dart.server.internal.remote.ByteResponseStream
- ./processor/ABOUT.md
- ./utilities/ABOUT.md

## Invariants
- Manages the spawned dart analyze --sdk process stdio streams.

## Side Effects
- Spawns external Dart analysis server process; reads/writes JSON over stdin/stdout.
