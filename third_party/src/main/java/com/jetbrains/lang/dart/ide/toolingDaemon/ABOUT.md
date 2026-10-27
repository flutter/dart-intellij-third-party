<!--* freshness: { reviewed: '2026-10-25' } *-->

# toolingDaemon

## Overview

Project-level integration with the Dart Tooling Daemon (DTD). This package
launches `dart tooling-daemon --machine` and parses its startup JSON. It then
connects over WebSocket and speaks DTD JSON-RPC: requests, responses, service
registration, and stream notifications. It also publishes IDE editor state
to DTD.

Related: [../../dtd/ABOUT.md](../../dtd/ABOUT.md) (standalone `DTDProcess`),
[../../websocket/ABOUT.md](../../websocket/ABOUT.md) (the shared transport
wrapper this package must use).

## Interface

- `DartToolingDaemonService`: `@Service(PROJECT)`. Provides
  `startService()`/`stopService()`, `sendRequest(method, params,
  includeSecret, consumer)`, `registerServiceMethod(...)` and
  `addToolingDaemonListener(...)` (both used by the Flutter plugin),
  `ensureRootsUpToDate()` (`FileSystem.setIDEWorkspaceRoots`), and
  `uri`/`webSocketReady`.
- `DartToolingDaemonConsumer`: callback for a JSON-RPC response.
- `DartToolingDaemonListener`: receives `streamNotify` events by stream ID.
- `DartToolingDaemonRequestHandler` / `DartToolingDaemonResponse`: handler
  and result/error pair for services that this IDE registers with DTD.
- `DtdEditorService` (internal): registers `Editor.getActiveLocation` and
  `Editor.navigateToCode`.
- `DartActiveLocationChangeHandler` (internal): debounces (300 ms) editor,
  caret, selection, and file events, then posts `Editor`/`activeLocationChanged`
  events.
- `DartToolingDaemonUtils.kt`: `getActiveLocation()` builds the
  text-document and selections payload. Requires a read lock.
- `DartToolingDaemonCommandLine.kt`: `createDtdCommandLine()` adds
  `--ping-interval=15` when the SDK is 3.11.0 or later. `isDtdCommandLine()`
  filters the echoed command line from the output.
- `DartCopyDtdUriAction`: copies the DTD URI to the clipboard. It is
  enabled only when the socket is ready.

## Invariants

- The minimum SDK is 3.4. Older SDKs never start DTD.
- All socket I/O goes through `com.jetbrains.lang.dart.websocket.WebSocket`.
  Never use the JDK `java.net.http.WebSocket` directly (see
  `.agents/design-docs/websocket-design.md`).
- This package owns JSON-RPC, request IDs, the trusted-client secret, and
  readiness (`webSocketReady`). The wrapper only handles transport.
- Requests queue until `onOpen`. The queue is drained in FIFO order.
- `onPing`/`onPong` are no-ops. The JDK sends the reply Pong automatically.
- The trusted secret is added only when `includeSecret == true`.

## Side Effects

- Spawns and kills a `dart tooling-daemon` process.
- On start, triggers `DartDevToolsService.startService(uri)` and
  `DartAnalysisServerService.connectToDtd(uri)`.
- Opens one WebSocket per service start.
- Navigates editors on the EDT for `navigateToCode`.
- Registers editor and message-bus listeners for the project lifetime.

## Verification

- `./gradlew test --tests "com.jetbrains.dart.dartToolingDaemon.DartToolingDaemonServiceTest"`
- For WebSocket-related changes, also run `com.jetbrains.dart.dartToolingDaemon.DTDProcessTest` and `com.jetbrains.dart.vmService.VmServiceWebsocketTest`.
