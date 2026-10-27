<!--* freshness: { reviewed: '2026-10-25' } *-->
# remote

## Overview
Remote process management and stdio I/O socket communication (RemoteAnalysisServerImpl, StdioServerSocket) for Dart Analysis Server.

## Interface
- `RemoteAnalysisServerImpl` - Abstract `AnalysisServer` implementation that serializes requests, matches responses, and dispatches notifications.
- `StdioServerSocket` - `AnalysisServerSocket` that launches the server process and wires its stdin/stdout/stderr.
- `RequestSink` / `ByteRequestSink` - Destination for JSON requests; byte-stream implementation writing to process stdin.
- `BlockingRequestSink` - Queues all but "get version" requests until it is converted to a real sink.
- `ErrorRequestSink` - Sink that answers every request with an error response.
- `ResponseSink` - Destination for responses (used by `BlockingRequestSink`/`ErrorRequestSink`).
- `ResponseStream` / `ByteResponseStream` - Source of JSON responses; byte-stream implementation reading process stdout.
- `LineReaderStream` / `ByteLineReaderStream` - Line-oriented reader used for the process stderr stream.
- `ServerErrorReaderThread` - Daemon thread echoing server stderr and turning "Unhandled exception:" output into `server.error` notifications.
- `ServerVersionMismatchException` - Thrown by `RemoteAnalysisServerImpl.start()` when the server version is incompatible.
- `DebugPrintStream` - Sink for debug tracing of traffic.
- `FileReadMode` - Enum of how file contents are read (`AS_IS`, `NORMALIZE_EOL_ALWAYS`).
- [./processor/ABOUT.md](./processor/ABOUT.md)
- [./utilities/ABOUT.md](./utilities/ABOUT.md)

## Invariants
- `StdioServerSocket` launches `[runtimePath, vmArguments..., analysisServerPath, --client-id=..., --client-version=..., serverArguments...]` via `ProcessBuilder` and owns that process's stdio streams.
- `stop()` waits up to 5 seconds for a graceful exit before destroying the process.

## Side Effects
- Spawns external Dart analysis server process; reads/writes JSON over stdin/stdout.
- `StdioServerSocket.start()` calls `Analytics.updateEnvironment(processBuilder.environment())`, altering the child process environment.
