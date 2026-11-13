<!--* freshness: { reviewed: '2026-10-25' } *-->

# surroundWith

## Overview

"Surround With" (Ctrl+Alt+T) for Dart expressions and statements. It works
from PSI.

## Interface

- `DartExpressionSurroundDescriptor` / `DartStatementsSurroundDescriptor`:
  registered as `lang.surroundDescriptor`. They find the selected
  expression or statements and list the surrounders that apply.
- `expression/`: `DartWithParenthesisExpressionSurrounder`,
  `DartWithNotParenthesisExpressionSurrounder` (`!(expr)`), and
  `DartWithBracketsExpressionSurrounder`, all built on
  `DartWithExpressionSurrounder`.
- `statement/`: `if`, `if/else`, `while`, `do/while`, `for`, `try/catch`, and
  `try/catch/finally` surrounders, built on `DartStatementSurrounder` and the
  `Dart*StatementSurrounderBase` helpers.

## Invariants

- A surrounder replaces only the selected range and returns the caret range
  for the inserted template.

## Side Effects

- Edits the document inside the platform write action.

## Verification

- `com.jetbrains.lang.dart.ide.DartSurroundWithTest`
