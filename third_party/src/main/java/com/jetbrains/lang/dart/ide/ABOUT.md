<!--* freshness: { reviewed: '2026-08-31' } *-->
# ide

## Overview
Core IDE extensions for brace matching, code commenting, symbol/class navigation contributors, reader mode, and file writing access control.

## Subdirectories & Boundaries
- `actions`: [./actions/ABOUT.md](./actions/ABOUT.md) - Dart editor and IDE actions (Pub, fix, organize imports, inspections).

## Interface
- com.jetbrains.lang.dart.ide.DartBraceMatcher
- com.jetbrains.lang.dart.ide.DartCommenter
- com.jetbrains.lang.dart.ide.DartSymbolContributor
- com.jetbrains.lang.dart.ide.DartClassContributor
- com.jetbrains.lang.dart.ide.DartImplementationTextSelectioner
- com.jetbrains.lang.dart.ide.DartNamedElementNode
- com.jetbrains.lang.dart.ide.DartReaderModeMatcher
- com.jetbrains.lang.dart.ide.DartWritingAccessProvider

## Invariants
- Provides JetBrains platform language extensions for Dart.

## Side Effects
- Read-only PSI symbol indexing and editor behavior hooks.
- Controls writing access permissions for Dart SDK and pub cache files.
