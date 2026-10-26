<!--* freshness: { reviewed: '2026-10-25' } *-->
# folding

## Overview
Code folding for Dart files: the folding builder, persisted Dart-specific folding settings, and the settings UI under Editor | General | Code Folding.

## Interface
- com.jetbrains.lang.dart.folding.DartFoldingBuilder: `CustomFoldingBuilder` (so it supports `// region` / `// endregion` custom-region comments) that also implements `DumbAware`.
- com.jetbrains.lang.dart.folding.DartCodeFoldingSettings: application-level `PersistentStateComponent` stored in `editor.xml`. It holds "collapse parts" (default on) and "collapse generic parameters" (default off).
- com.jetbrains.lang.dart.folding.DartCodeFoldingOptionsProvider: `CodeFoldingOptionsProvider` (`BeanConfigurable`) that exposes `DartCodeFoldingSettings` in the settings UI.

## Invariants
- Fold regions are computed for the following:
  - the file header comment
  - consecutive import/export statements and part statements
  - comments and runs of single-line (doc) comments
  - class, enum, mixin, and extension bodies
  - function bodies and function-expression bodies
  - type arguments and multi-line strings
  - set/map and list literals
  - constructor invocations (including `new`-less ones, detected by a capitalised name)
  - assert statements, if statements, and loops (for, while, do-while)
  - formal parameter lists and non-empty argument lists
- Custom folding roots are the file, class bodies, and function bodies.
- What collapses by default follows the platform `CodeFoldingSettings`: file header, imports, doc comments, and methods. Parts and type arguments follow `DartCodeFoldingSettings`. Everything else starts expanded.
- The import fold region can be removed while collapsed, because imports change often through automatic edits.

## Side Effects
- Read-only PSI traversal producing `FoldingDescriptor` ranges.
- `DartCodeFoldingSettings` persists to `editor.xml`.

## Verification
- Covered by the Dart folding tests in the plugin's test sources (`DartFoldingTest`).
