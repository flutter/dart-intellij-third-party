<!--* freshness: { reviewed: '2026-10-25' } *-->
# ide

## Overview
Root of the Dart plugin's IDE integration. Direct sources provide brace matching, commenting, Go to Symbol/Class contributors, implementation text selection, reader mode, and write protection for excluded Dart files. Feature areas live in sub-packages.

## Subdirectories & Boundaries
- `actions`: [./actions/ABOUT.md](./actions/ABOUT.md) - Pub actions, Reformat with Dart Style, Sort Members, Create Dart File, Go to Super, inheritors searcher, editor notifications.
- `annotator`: [./annotator/ABOUT.md](./annotator/ABOUT.md) - applies Analysis Server errors and highlighting as editor annotations.
- `codeInsight`: [./codeInsight/ABOUT.md](./codeInsight/ABOUT.md) - persisted Dart code-insight settings.
- `completion`: [./completion/ABOUT.md](./completion/ABOUT.md) - server-backed code completion, statement completion, goto declaration.
- `copyright`: [./copyright/ABOUT.md](./copyright/ABOUT.md) - copyright header updates for Dart files.
- `devtools`: [./devtools/ABOUT.md](./devtools/ABOUT.md) - launches and tracks the `dart devtools` server.
- `documentation`: [./documentation/ABOUT.md](./documentation/ABOUT.md) - completion-popup docs and api.dart.dev URLs.
- `editor`: [./editor/ABOUT.md](./editor/ABOUT.md) - typing, enter, quote, indent, selection, and copy/paste handlers.
- `errorTreeView`: [./errorTreeView/ABOUT.md](./errorTreeView/ABOUT.md) - Dart Analysis tool window and server control actions.
- `findUsages`: [./findUsages/ABOUT.md](./findUsages/ABOUT.md) - server-backed Find Usages and usage grouping.
- `formatter`: [./formatter/ABOUT.md](./formatter/ABOUT.md) - IntelliJ formatting model and code style settings.
- `generation`: [./generation/ABOUT.md](./generation/ABOUT.md) - Generate actions (constructors, accessors, equals/hashCode, toString, override/implement).
- `hierarchy`: [./hierarchy/ABOUT.md](./hierarchy/ABOUT.md) - type, method, and call hierarchies.
- `imports`: [./imports/ABOUT.md](./imports/ABOUT.md) - Optimize Imports via the Analysis Server.
- `index`: [./index/ABOUT.md](./index/ABOUT.md) - file-based indexes (components, classes, symbols, libraries, imports/exports, parts).
- `info`: [./info/ABOUT.md](./info/ABOUT.md) - parameter info popup (`DartParameterInfoHandler`).
- `inspections`: [./inspections/ABOUT.md](./inspections/ABOUT.md) - `DartPathPackageReferenceInspection`.
- `marker`: [./marker/ABOUT.md](./marker/ABOUT.md) - method separators and server-backed override/implementation gutter markers.
- `moveCode`: [./moveCode/ABOUT.md](./moveCode/ABOUT.md) - move statement up/down.
- `refactoring`: [./refactoring/ABOUT.md](./refactoring/ABOUT.md) - refactorings.
- `runner`: [./runner/ABOUT.md](./runner/ABOUT.md) - run configurations and debugger.
- `spelling`: [./spelling/ABOUT.md](./spelling/ABOUT.md) - spellchecking support.
- `structure`: [./structure/ABOUT.md](./structure/ABOUT.md) - structure view.
- `surroundWith`: [./surroundWith/ABOUT.md](./surroundWith/ABOUT.md) - Surround With templates.
- `template`: [./template/ABOUT.md](./template/ABOUT.md) - live and postfix templates.
- `testIntegration`: [./testIntegration/ABOUT.md](./testIntegration/ABOUT.md) - test navigation and creation.
- `toolingDaemon`: [./toolingDaemon/ABOUT.md](./toolingDaemon/ABOUT.md) - Dart Tooling Daemon (DTD) WebSocket integration.

## Interface
- `DartBraceMatcher` - paired brace matching.
- `DartCommenter` - line/block/doc comment definitions.
- `DartSymbolContributor` / `DartClassContributor` - Go to Symbol / Go to Class contributors.
- `DartImplementationTextSelectioner` - text range for implementation popups.
- `DartNamedElementNode` - navigation tree node for named elements.
- `DartReaderModeMatcher` (Kotlin) - reader-mode eligibility.
- `DartWritingAccessProvider` - `NonProjectFileWritingAccessExtension`.

## Invariants
- All classes are JetBrains platform extensions registered in `plugin.xml` for the Dart language.

## Side Effects
- Read-only PSI symbol lookup and editor behaviour hooks.
- `DartWritingAccessProvider` reports a Dart file as not writable only when `ProjectFileIndex.isExcluded(file)` is true; it has no SDK or pub-cache logic.

## Verification
- Feature tests live under `third_party/src/test/java/com/jetbrains/lang/dart/ide/` (editor, findUsages, moveCode, refactoring, runner, testIntegration, toolingDaemon).
