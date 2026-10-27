<!--* freshness: { reviewed: '2026-10-25' } *-->

# structure

## Overview

The Structure tool window for Dart files. Its contents come from the
analysis server's outline data.

## Interface

- `DartStructureViewFactory`: registered as `lang.psiStructureViewFactory`.
- `DartStructureViewModel`: the tree model. It rebuilds when `DartServerData`
  outline updates arrive.
- `DartStructureViewElement`: wraps one server outline node.

## Invariants

- Without an outline from the analysis server, the view is empty.

## Side Effects

- Subscribes to analysis-server outline notifications while the view is open.

## Verification

- No dedicated test found.
