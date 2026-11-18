<!--* freshness: { reviewed: '2026-11-17' } *-->
# hierarchy

## Overview
Method hierarchy view for Dart, backed by the Analysis Server (type and call hierarchies are handled by `LspTypeHierarchyProvider` and `LspCallHierarchyProvider`). Sub-package `method/` is small and is covered here rather than in its own ABOUT.md file.

## Interface
- `DartHierarchyUtil` - shared helpers: element at caret, `getTypeHierarchyItems` (`search_getTypeHierarchy`), `filterSuperClasses` (used by `generation/`), `findDartClass`, `findDartComponent`, `isExecutable`.
- `method/DartMethodHierarchyProvider`, `DartMethodHierarchyBrowser`, `DartMethodHierarchyTreeStructure`, `DartMethodHierarchyNodeDescriptor` - method override hierarchy from the server type hierarchy.

## Invariants
- Method hierarchy needs a running Analysis Server; results map back to PSI `DartClass`/`DartComponent` elements.

## Side Effects
- Sends type-hierarchy requests to the Analysis Server; read-only otherwise.

## Verification
- `DartHierarchyUtilTest` covers `DartHierarchyUtil.filterSuperClasses`; verify UI flows via Navigate > Type / Method / Call Hierarchy in a Dart file.
