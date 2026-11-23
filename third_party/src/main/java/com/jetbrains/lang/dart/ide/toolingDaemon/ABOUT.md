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
  `startServiceIfNeeded()`, `startService()`/`stopService()`, `sendRequest(method, params,
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
- `startServiceIfNeeded()` is the production entry point: it starts at most one
  DTD per session (until `stopService()`), and only when the
  `dart.launch.dtd.and.devtools` registry key is set and a module has Dart
  support enabled and an SDK of at least 3.4. It's called on project open
  (`DartStartupActivity`, for projects with Dart support), when a `pub get`
  finishes (`DartPubActionBase`; a project just created with the wizard can open
  before its SDK is visible), and when Dart support is enabled or the Dart SDK
  library changes after open (`DartWorkspaceModelChangeListener`). It returns at
  once; in the background, it waits up to about 3.7 s for the analytics
  configuration first, because the DTD keeps the `DASH__SUPPRESS_ANALYTICS` it
  starts with. In unit-test mode it does nothing unless a test sets
  `startForTests`.
- All socket I/O goes through `com.jetbrains.lang.dart.websocket.WebSocket`.
  Never use the JDK `java.net.http.WebSocket` directly (see
  `.agents/design-docs/websocket-design.md`).
- This package owns JSON-RPC, request IDs, the trusted-client secret, and
  readiness (`webSocketReady`). The wrapper only handles transport.
- Requests queue until `onOpen`. The queue is drained in FIFO order. While the
  socket isn't ready, about `MAX_PENDING_REQUESTS` (100) are queued; later
  requests are dropped (service registrations never are), so a DTD that never
  connects or has died can't grow the queue without bound. The first drop and
  every 100th after it are logged at debug.
- `onPing`/`onPong` are no-ops. The JDK sends the reply Pong automatically.
- The trusted secret is added only when `includeSecret == true`.

## Side Effects

- Spawns and kills a `dart tooling-daemon` process.
- On start, triggers `DartDevToolsService.startService(uri)` and
  `DartLspService.connectToDtd(project, uri)`.
- Opens one WebSocket per service start.
- Navigates editors on the EDT for `navigateToCode`.
- Registers editor and message-bus listeners for the project lifetime.

## Verification

- `./gradlew test --tests "com.jetbrains.dart.dartToolingDaemon.DartToolingDaemonServiceTest"`
- For WebSocket-related changes, also run `com.jetbrains.dart.dartToolingDaemon.DTDProcessTest` and `com.jetbrains.dart.vmService.VmServiceWebsocketTest`.
