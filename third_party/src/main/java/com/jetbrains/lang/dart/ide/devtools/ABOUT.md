<!--* freshness: { reviewed: '2026-10-25' } *-->
# devtools

## Overview
Project service that launches the Dart DevTools server and records its host and port.

## Interface
- `DartDevToolsService` (Kotlin, project `@Service`) - `startService(dtdUri)` runs `dart devtools --machine [--dtd-uri=...]`; `devToolsHostAndPort` is read by `runner/server` (`OpenDartObservatoryUrlAction`, `DartCommandLineRunningState`).

## Invariants
- Started from `toolingDaemon/DartToolingDaemonService` with the DTD URI.
- Requires Dart SDK >= 2.15; a second `startService` call while running is ignored.
- `devToolsHostAndPort` is set from the `server.started` JSON event and cleared on process termination.

## Side Effects
- Spawns a `dart devtools` process (environment updated via `Analytics.updateEnvironment`); destroys it on dispose.

## Verification
- No dedicated tests; verify by enabling `dart.launch.dtd.and.devtools` and running a Dart app, then opening DevTools.
