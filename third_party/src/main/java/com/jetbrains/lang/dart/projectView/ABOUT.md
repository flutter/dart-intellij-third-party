<!--* freshness: { reviewed: '2026-10-25' } *-->
# projectView

## Overview
Project View customizations for Dart projects: the tree structure for the Dart SDK and "Dart Packages" libraries and legacy `packages/` symlinks, folder and file icons, file nesting rules, and error decoration.

## Interface
- com.jetbrains.lang.dart.projectView.DartTreeStructureProvider: package-private, `TreeStructureProvider` + `DumbAware`. Under External Libraries, it makes the Dart SDK node open Dart settings and the "Dart Packages" node non-navigable. It renders SDK and package `lib` roots specially and replaces legacy `packages/<name>` folders with nodes that point to the live package.
- com.jetbrains.lang.dart.projectView.DartIconProvider: `IconProvider` with special icons for non-local (remote-URI) Dart files and for the `build`, `web`, `test`, and `tool` folders and `packages/*` folders next to a `pubspec.yaml`.
  - `isFolderNearPubspecYaml(folder, name)`: public static helper. It returns true when a local directory with the given name sits next to a `pubspec.yaml`. It is also used outside this package, e.g. by `test/DartTestSourcesFilter`.
- com.jetbrains.lang.dart.projectView.DartNestingRulesProvider: `ProjectViewNestingRulesProvider` that nests `.dart.js`, `.dart.js.map`, `.dart.js.deps`, `.dart.js.tar.gz`, and `.module` files under their `.dart` file.
- com.jetbrains.lang.dart.projectView.DartNodeDecorator: package-private `ProjectViewNodeDecorator`. It paints file and directory nodes with `ERRORS_ATTRIBUTES` when `DartAnalysisServerService.isFileWithErrors` is true.

## Invariants
- Changes presentation only. Project structure and folder exclusions are not modified here.

## Side Effects
- Changes the Project View tree's nodes, icons, nesting, and decorations. No file writes.

## Verification
- No dedicated tests in this package. Check manually in the Project View of a Dart project.
