<!--* freshness: { reviewed: '2026-10-25' } *-->
# dtd

## Overview
`DTDProcess` launches a Dart Tooling Daemon (DTD) process and talks to it over the shared
[WebSocket wrapper](../websocket/ABOUT.md). Today its only consumer is
[`analytics/Analytics.kt`](../analytics/ABOUT.md), which uses a short-lived process for
unified-analytics consent and telemetry checks. The main DTD integration for DevTools and
editor services is `ide/toolingDaemon/DartToolingDaemonService.kt`
([../ide/toolingDaemon/DartToolingDaemonService.kt](../ide/toolingDaemon/DartToolingDaemonService.kt)).

## Interface
- `DTDProcess`: `start(sdk)`, `sendRequest(method, params, includeSecret, consumer)`,
  `cancelRequest(id)`, `terminate()`, the `webSocketReady` flag, and the `listener` property.
  `sendRequest` returns the request ID; it throws `WebSocketException` if the socket is not ready.
- `DTDProcessListener`: callbacks `onProcessStarted(uri)`, `onWebSocketOpen()`,
  `onWebSocketMessage(text)`, and `onWebSocketClose()`.

## Invariants
- Reads `tooling_daemon_details.uri` and `trusted_client_secret` from the process's stdout.
- `sendRequest` can add the trusted-client secret to the params (`includeSecret`).
- Responses are matched to callers by request ID. `streamNotify` messages are dispatched to
  `DartToolingDaemonListener`s.
- Registered service handlers run on a pooled thread. No public API populates `servicesMap`
  yet, and `addToolingDaemonListener` has no callers (TODO in source).
- WebSocket rules in
  [websocket-design.md](../../../../../../../../../.agents/design-docs/websocket-design.md) apply.

## Side Effects
- Launches a background DTD process and opens a WebSocket connection to it.

## Verification
- `com.jetbrains.dart.dartToolingDaemon.DTDProcessTest` under
  `third_party/src/test/java/com/jetbrains/dart/dartToolingDaemon`.
