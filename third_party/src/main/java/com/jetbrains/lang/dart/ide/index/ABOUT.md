<!--* freshness: { reviewed: '2026-10-25' } *-->

# index

## Overview

These are IntelliJ file-based indexes over Dart PSI. They power resolution,
navigation, and library lookup without the analysis server.

## Interface

- `DartImportAndExportIndex`: a single-entry index that stores the
  `DartImportOrExportInfo` list for each file.
- `DartPartUriIndex` / `DartPartOfIndex`: the `part` URIs for each file and a
  flag for whether the file is a `part of` file.
- `DartClassIndex`, `DartSymbolIndex`, `DartLibraryIndex`: scalar
  name-to-files indexes for classes, top-level symbols, and library names.
- `DartComponentIndex`: maps a name to `DartComponentInfo`. That info holds
  the component type and library name and is serialized by the
  `DartComponentInfo*Externalizer` classes.
- `DartFileIndexData`: shared per-file data that the indexers compute once.
- `DartShowHideInfo`: describes the `show`/`hide` combinators on an import
  or export.
- `DartIndexUtil`: computes `DartFileIndexData` from PSI.

## Invariants

- All indexes are registered as `fileBasedIndex` in `META-INF/plugin.xml`.
- Change an index version whenever its key or value format changes.

## Side Effects

- Persistent IDE index storage only.

## Verification

- Covered indirectly by the resolve, completion, and navigation tests in
  `third_party/src/test`.
