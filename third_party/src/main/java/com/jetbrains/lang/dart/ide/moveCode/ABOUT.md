<!--* freshness: { reviewed: '2026-10-25' } *-->

# moveCode

## Overview

"Move Statement Up/Down" support for Dart. Both movers work from PSI.

## Interface

- `DartStatementMover`: moves executable statements within a function. A
  statement cannot leave its enclosing component.
- `DartComponentMover`: moves top-level functions, classes, and class
  members past a neighboring component.

## Invariants

- Registered as `statementUpDownMover`. The statement mover is ordered
  before the component mover, and the component mover before `xml`.

## Side Effects

- Edits the document through the platform move action.

## Verification

- `DartStatementMoverTest`, `DartComponentMoverTest`, `DartCodeMoverTest`
  (in `com.jetbrains.lang.dart.ide.moveCode`)
