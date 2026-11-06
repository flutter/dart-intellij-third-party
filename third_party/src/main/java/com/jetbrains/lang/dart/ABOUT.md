<!--* freshness: { reviewed: '2026-10-25' } *-->
# dart

## Overview
Root package of the Dart language plugin. It holds the core language definition
(language, file type, parser definition, token sets, the Grammar-Kit grammar), the
project-open startup activity, and the VFS/WorkspaceModel listeners that keep the
"Dart Packages" library current. Feature areas live in the subpackages listed below.

## Interface
- `DartLanguage`: the `Language` singleton for Dart.
- `DartFileType`: the `.dart` `LanguageFileType`.
- `DartParserDefinition`: wires the lexer, the generated parser, and `DartFile`.
- `Dart.bnf`: Grammar-Kit grammar. The generated parser and PSI live in
  [../../../../../../../gen/com/jetbrains/lang/dart/ABOUT.md](../../../../../../../gen/com/jetbrains/lang/dart/ABOUT.md).
- `DartGeneratedParserUtilBase`: hand-written `GeneratedParserUtilBase` helpers that the
  generated parser imports statically.
- `DartTokenTypesSets`: token sets plus the lazy-parseable doc-comment and block element types.
- `DartElementType`, `DartComponentType`: PSI element type base and the component kind enum.
- `DartBundle`, `DartDeprecatedMessageBundle`: message bundles (`messages.DartBundle`,
  `messages.DartDeprecatedMessageBundle`).
- `DartPluginCapabilities`: queryable list of Dart plugin capabilities.
- `DartSearchableOptionContributor`: contributes Dart settings to Settings search.
- `DartStartupActivity`: `ProjectActivity` run on project open (see Side Effects).
- `DartFileListener`: `AsyncFileListener` that schedules "Dart Packages" library updates.
- `DartWorkspaceModelChangeListener`: `WorkspaceModelChangeListener` that shares
  "Dart Packages" library upkeep with `DartFileListener`.

## Subdirectories & Boundaries
- [./analytics/ABOUT.md](./analytics/ABOUT.md): Dart unified analytics reporting.
- [./analyzer/ABOUT.md](./analyzer/ABOUT.md): Dart Analysis Server integration.
- [./assists/ABOUT.md](./assists/ABOUT.md): Analysis Server quick assists.
- [./contextInfo/ABOUT.md](./contextInfo/ABOUT.md): declaration range handler.
- [./dtd/ABOUT.md](./dtd/ABOUT.md): short-lived DTD process used by analytics.
- [./fixes/ABOUT.md](./fixes/ABOUT.md): Analysis Server quick fixes.
- [./flutter/ABOUT.md](./flutter/ABOUT.md): Flutter detection helpers.
- [./folding/ABOUT.md](./folding/ABOUT.md): code folding.
- [./highlight/ABOUT.md](./highlight/ABOUT.md): syntax highlighting.
- [./hints/ABOUT.md](./hints/ABOUT.md): declarative inlay hint providers.
- [./ide/ABOUT.md](./ide/ABOUT.md): editor, run, refactoring, and tooling features.
- [./injection/ABOUT.md](./injection/ABOUT.md): language injection.
- [./lexer/ABOUT.md](./lexer/ABOUT.md): lexers and JFlex grammars.
- [./logging/ABOUT.md](./logging/ABOUT.md): plugin file logging.
- [./lsp/ABOUT.md](./lsp/ABOUT.md): in-process LSP bridge to the Analysis Server.
- [./projectView/ABOUT.md](./projectView/ABOUT.md): Project View decorations.
- [./projectWizard/ABOUT.md](./projectWizard/ABOUT.md): new-project wizard.
- [./psi/ABOUT.md](./psi/ABOUT.md): hand-written PSI.
- [./pubServer/ABOUT.md](./pubServer/ABOUT.md): pub serve integration.
- [./resolve/ABOUT.md](./resolve/ABOUT.md): PSI resolve.
- [./sdk/ABOUT.md](./sdk/ABOUT.md): Dart SDK model, libraries, and settings page.
- [./test/ABOUT.md](./test/ABOUT.md): test framework support.
- [./ui/ABOUT.md](./ui/ABOUT.md): shared Swing components.
- [./util/ABOUT.md](./util/ABOUT.md): PSI, resolve, and pub/package utilities.
- [./websocket/ABOUT.md](./websocket/ABOUT.md): canonical JDK WebSocket wrapper.
- [./xml/ABOUT.md](./xml/ABOUT.md): XML typed-handler support in Dart files.

## Invariants
- Main entry point for Dart language support in IntelliJ; extensions are registered in
  `third_party/src/main/resources/META-INF/plugin.xml`.
- The parser is generated from `Dart.bnf`; do not hand-edit generated sources.
- "Dart Packages" library state is configured by `DartStartupActivity` and then kept
  current by `DartFileListener` and `DartWorkspaceModelChangeListener`.

## Side Effects
- `DartStartupActivity` launches three jobs on the `DartAnalysisServerService` scope:
  - Excludes `.dart_tool`, `.pub`, and `build` folders through one WorkspaceModel update,
    then schedules a "Dart Packages" roots update.
  - Starts the Analysis Server (`serverReadyForRequest`) when a Dart SDK is configured and
    enabled, unless a new project is waiting for its first `pub get`. When the
    `dart.launch.dtd.and.devtools` registry key is set, it also starts `DartToolingDaemonService`.
  - Reports settings analytics (`Analytics.report(SettingsData)`) unless analytics are suppressed.
- `DartFileListener` and `DartWorkspaceModelChangeListener` update the "Dart Packages" library.

## Verification
- Tests live under `third_party/src/test/java/com/jetbrains/lang/dart` and
  `third_party/src/test/java/com/jetbrains/dart/analysisServer`.
