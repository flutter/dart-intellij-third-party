<!--* freshness: { reviewed: '2026-10-28' } *-->
# hierarchy

## Overview
Method and call hierarchy views for Dart, backed by the Analysis Server (type hierarchy is handled by `LspTypeHierarchyProvider`). Sub-packages `method/` and `call/` are small and are covered here rather than in their own ABOUT.md files.

## Interface
- `DartHierarchyUtil` - shared helpers: element at caret, `getTypeHierarchyItems` (`search_getTypeHierarchy`), `filterSuperClasses` (used by `generation/`), `findDartClass`, `isExecutable`, node comparator.
- `method/DartMethodHierarchyProvider`, `DartMethodHierarchyBrowser`, `DartMethodHierarchyTreeStructure`, `DartMethodHierarchyNodeDescriptor` - method override hierarchy from the server type hierarchy.
- `call/DartCallHierarchyProvider`, `DartCallHierarchyBrowser`, `DartCallHierarchyNodeDescriptor` - call hierarchy UI.
- `call/DartCallHierarchyTreeStructure` (abstract), `DartCallerTreeStructure`, `DartCalleeTreeStructure` - callers via `findUsages/DartServerFindUsagesHandler`; callees via `analysis_getNavigation`.

## Invariants
- All hierarchies need a running Analysis Server; results map back to PSI `DartClass`/`DartComponent` elements.

## Side Effects
- Sends type-hierarchy, reference-search, and navigation requests to the Analysis Server; read-only otherwise.

## Verification
- `DartHierarchyUtilTest` covers `DartHierarchyUtil.filterSuperClasses`; verify UI flows via Navigate > Type / Method / Call Hierarchy in a Dart file.
