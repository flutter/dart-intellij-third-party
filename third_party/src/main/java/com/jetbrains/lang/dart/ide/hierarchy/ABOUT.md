<!--* freshness: { reviewed: '2026-10-25' } *-->
# hierarchy

## Overview
Type, method, and call hierarchy views for Dart, backed by the Analysis Server. Sub-packages `type/`, `method/`, and `call/` are small and are covered here rather than in their own ABOUT.md files.

## Interface
- `DartHierarchyUtil` - shared helpers: element at caret, `getTypeHierarchyItems` (`search_getTypeHierarchy`), `findDartClass`, `isExecutable`, node comparator.
- `type/DartTypeHierarchyProvider`, `DartTypeHierarchyBrowser`, `DartTypeHierarchyNodeDescriptor` - type hierarchy UI.
- `type/DartServerTypeHierarchyTreeStructure`, `DartServerSupertypesHierarchyTreeStructure`, `DartServerSubtypesHierarchyTreeStructure` - tree structures built from server `TypeHierarchyItem`s; `DartServerTypeHierarchyTreeStructure.filterSuperClasses` is also used by `generation/`.
- `method/DartMethodHierarchyProvider`, `DartMethodHierarchyBrowser`, `DartMethodHierarchyTreeStructure`, `DartMethodHierarchyNodeDescriptor` - method override hierarchy from the server type hierarchy.
- `call/DartCallHierarchyProvider`, `DartCallHierarchyBrowser`, `DartCallHierarchyNodeDescriptor` - call hierarchy UI.
- `call/DartCallHierarchyTreeStructure` (abstract), `DartCallerTreeStructure`, `DartCalleeTreeStructure` - callers via `findUsages/DartServerFindUsagesHandler`; callees via `analysis_getNavigation`.

## Invariants
- All hierarchies need a running Analysis Server; results map back to PSI `DartClass`/`DartComponent` elements.

## Side Effects
- Sends type-hierarchy, reference-search, and navigation requests to the Analysis Server; read-only otherwise.

## Verification
- No dedicated tests in this repo; verify via Navigate > Type / Method / Call Hierarchy in a Dart file.
