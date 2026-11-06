<!--* freshness: { reviewed: '2026-10-25' } *-->

# vmService

## Overview

The Dart debugger, built on IntelliJ XDebugger and the Dart VM Service
protocol. This package connects to the VM, tracks isolates, maps
breakpoints, and builds stack frames and values.

## Interface

- `DartVmServiceDebugProcess`: the `XDebugProcess`. `scheduleConnect(url)`
  retries `VmService.connect` on a pooled thread until the timeout expires.
- `VmServiceWrapper`: a `Disposable` facade over `VmService` for isolate
  setup, breakpoints, stepping, evaluation, and frames.
- `DartVmServiceListener`: handles VM stream events (pause, resume, isolate
  start and exit, and output).
- `DartVmServiceBreakpointHandler`, `DartExceptionBreakpointHandler`:
  XDebugger breakpoint handlers.
- `IsolatesInfo`: the registry of live isolates.
- `VmServiceConsumers`: adapter base classes for driver consumers.
- `frame/`: `DartVmServiceSuspendContext`, `DartVmServiceExecutionStack`,
  `DartVmServiceStackFrame`, `DartVmServiceValue`, `DartStaticFieldsGroup`,
  `DartAsyncMarkerFrame`, and `DartVmServiceEvaluator` /
  `...EvaluatorInFrame`.
- `vmServiceDrivers/service/`: the plugin-owned Java VM Service client
  (`VmService`, `VmServiceBase`, `consumer/`, `element/`, `internal/`
  request sinks, and `logging/`). The upstream Dart SDK no longer
  maintains it.

## Invariants

- The driver's socket is `com.jetbrains.lang.dart.websocket.WebSocket`
  (`VmServiceBase.connect`). Never use the JDK WebSocket directly (see
  [../../../../websocket/ABOUT.md](../../../../websocket/ABOUT.md) and
  `.agents/design-docs/websocket-design.md`).
- `VmServiceBase.connect` accepts only `ws`/`wss` URIs. It waits up to 5 s
  for `getVersion` before returning.
- The Dart copy of the WebSocket wrapper is canonical. Flutter mirrors only
  the four wrapper files, not this driver.
- Advance the driver one protocol version at a time (skill
  `sync-vm-service-drivers`).

## Side Effects

- Opens a WebSocket to the VM and prints errors to the debug console.

## Verification

- `VmServiceDriverRequestTest`, `VmServiceDriverResponseTest`,
  `VmServiceDriverElementTest` (in
  `com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service`)
- `com.jetbrains.dart.vmService.VmServiceWebsocketTest`,
  `VmServicePerfettoCpuSamplesTest` (real SDK integration)
