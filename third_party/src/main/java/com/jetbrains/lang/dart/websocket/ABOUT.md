<!--* freshness: { reviewed: '2026-10-25' } *-->
# websocket

## Overview
The canonical, plugin-owned WebSocket wrapper around the JDK `java.net.http.WebSocket`. It is
the only WebSocket transport DTD and VM Service code may use. The architecture, invariants, and
change rules are in
[websocket-design.md](../../../../../../../../../.agents/design-docs/websocket-design.md).
Read that document before changing these files.

## Interface
- `WebSocket`: one instance per URI and connection attempt. `connect()`, `send(text)`, and
  `close()` block on JDK futures with 10-second bounds. The `eventHandler` property receives callbacks.
- `WebSocketEventHandler`: `onOpen`, `onMessage`, `onClose`, `onPing`, `onPong`.
- `WebSocketMessage`: one complete text message (`text`).
- `WebSocketException`: normalized failure type that keeps the original cause.

## Invariants
- Transport only: JSON-RPC, request IDs, secrets, readiness, and logging belong to consumers.
- Depends only on the JDK and Kotlin stdlib (no IntelliJ APIs, Gson, or coroutines).
- Requests unlimited demand on open, reassembles text fragments, and never delivers partial text.
- Sends are serialized with a lock. Ping/Pong replies are handled automatically by the JDK.
- A failed or closed connection is terminal; JDK `onError` is reported as `onClose()`.
- Callbacks run on JDK transport threads. Do not call `connect`, `send`, or `close` on the EDT.
- Dart is canonical for these four files; Flutter mirrors them under
  `third_party/vmServiceDrivers/org/dartlang/vm/service/internal/websocket/`.

## Side Effects
- Opens network connections through a shared `HttpClient`.

## Verification
- `dartToolingDaemon/DTDProcessTest`, `dartToolingDaemon/DartToolingDaemonServiceTest`, and
  `vmService/VmServiceWebsocketTest` under `third_party/src/test/java/com/jetbrains/dart/`.
  (§7 of the design doc still names `com.jetbrains.dart.vmService.VmServiceTest`, which does not exist.)
