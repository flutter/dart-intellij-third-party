<!--* freshness: { reviewed: '2026-10-25' } *-->

# server

## Overview

Configurations that run or attach to a Dart VM: command-line apps, remote
debugging, and `webdev` web apps.

## Interface

- `DartCommandLineRunConfiguration` / `...Type` / `...RunnerParameters` /
  `...RunningState`: run a Dart file with VM options, arguments, and a
  working directory.
- `DartCommandLineRuntimeConfigurationProducer`: creates a configuration
  from a `main()` context.
- `DartRemoteDebugConfiguration` / `...Type` / `DartRemoteDebugParameters`:
  attach to an already running VM.
- `DartConsoleFolding`: folds the long command lines that `dart`/`pub run
  test` processes echo into a short summary.
- `OpenDartObservatoryUrlAction`: opens the VM Service/Observatory URL.
- `ui/`: Swing editor forms (`.form` + Java) for the command-line,
  remote-debug, and webdev configurations.
- `webdev/`: `DartWebdevConfiguration` / `...Type` / `...Producer` /
  `DartWebdevParameters` / `DartWebdevRunningState` launch `webdev daemon`.
  `DartDaemonParserUtil` parses daemon JSON, and `DartWebdevConsoleView`
  shows its output.

## Subdirectories & Boundaries

- [./vmService/ABOUT.md](./vmService/ABOUT.md): the XDebugger implementation
  on top of the Dart VM Service protocol.

## Invariants

- Parameter classes are `Cloneable` and are persisted through run-config
  XML serialization.

## Side Effects

- Spawns `dart` / `webdev` processes.

## Verification

- `com.jetbrains.lang.dart.ide.runner.server.webdev.DartWebdevConfigurationTest`
