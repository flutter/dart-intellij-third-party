<!--* freshness: { reviewed: '2026-10-25' } *-->

# runner

## Overview

Run and debug support for Dart: run configurations, the debug program
runner, breakpoints, and console hyperlinking. The VM Service debugger, the
command-line, remote, and webdev configurations, and the `package:test`
integration live in subpackages.

## Interface

- `DartRunner`: the debug-only `GenericProgramRunner` for command-line,
  test, remote-debug, and webdev configurations. For local configurations it
  creates a DAS execution context, then starts a `DartVmServiceDebugProcess`.
- `DartLineBreakpointType`, `DartExceptionBreakpointType` +
  `DartExceptionBreakpointProperties`: the XDebugger breakpoint types.
- `DartConsoleFilterProvider`, `DartConsoleFilter`,
  `DartRelativePathsConsoleFilter`, `DartPositionInfo`: turn stack-trace
  locations in the console into links.
- `DartExecutionHelper`: shows and clears analysis-issue notifications at
  launch time and computes the scope of files that can affect execution.
- `actions/DartPopFrameAction`: the debugger "Drop Frame" action.
- `base/`: `DartRunConfiguration` (interface), `DartRunConfigurationBase`
  (shared base for command-line and test configurations), and
  `DartDebuggerEditorsProvider`.
- `util/`: `DartTestLocationProvider` (an `SMTestLocator` for test tree
  navigation) and `TestUtil`.

## Subdirectories & Boundaries

- [./server/ABOUT.md](./server/ABOUT.md): command-line, remote-debug, and
  webdev configurations, plus the VM Service debugger.
- [./test/ABOUT.md](./test/ABOUT.md): `package:test` run configurations and
  the SM test-runner integration.

## Invariants

- `DartRunner.canRun` accepts only the Debug executor. Plain "Run" uses each
  configuration's own state.
- Configuration types and producers are registered in `META-INF/plugin.xml`.

## Side Effects

- Launches Dart processes and opens VM Service WebSocket connections
  (through the subpackages).

## Verification

- `com.jetbrains.lang.dart.ide.runner.DartConsoleFilterTest`
- See the subpackage ABOUT files for debugger and test-runner coverage.
