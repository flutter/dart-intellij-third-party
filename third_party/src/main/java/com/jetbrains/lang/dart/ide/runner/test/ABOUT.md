<!--* freshness: { reviewed: '2026-10-25' } *-->

# test

## Overview

Run configurations for `package:test`, with results shown in the IntelliJ
SM test runner.

## Interface

- `DartTestRunConfiguration` / `...Type` / `...Producer`: create test
  configurations for a file, directory, group, or single test.
- `DartTestRunnerParameters`: extends `DartCommandLineRunnerParameters` with
  the test scope and name.
- `DartTestRunningState`: extends `DartCommandLineRunningState` and runs
  the test runner in JSON reporter mode.
- `DartTestEventsConverter`: converts JSON reporter events into SM test
  events.
- `DartTestRerunner` / `DartTestRerunnerAction`: rerun failed tests.
- `ui/DartTestConfigurationEditorForm`: the settings editor.

## Invariants

- The converter assumes the JSON reporter protocol.

## Side Effects

- Spawns Dart test processes.

## Verification

- No dedicated unit test found in `third_party/src/test`.
