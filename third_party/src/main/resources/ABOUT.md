<!--* freshness: { reviewed: '2026-10-25' } *-->
# resources

## Overview
Non-code resources packaged into the Dart plugin JAR: the plugin descriptor, localized message bundles, icons, and descriptions/templates for IDE features.

## Interface
- `META-INF/plugin.xml`: Plugin descriptor (`<id>Dart</id>`) registering all extensions, actions, and services; declares `messages.DartBundle` as the resource bundle.
- `META-INF/dart-yaml.xml` / `META-INF/dart-copyright.xml`: Optional descriptors loaded only when the YAML (`org.jetbrains.plugins.yaml`) or Copyright (`com.intellij.copyright`) plugins are present.
- `META-INF/pluginIcon.svg`: Marketplace/plugin manager icon.
- `messages/`: Resource bundles `DartBundle.properties`, `DartLspBundle.properties`, and `DartDeprecatedMessageBundle.properties`.
- `icons/`: Classic UI SVG icons (with `_dark` variants) plus `icons/expui/` New UI icons; source for the generated `DartIcons` class in `../../../gen/icons/`.
- `DartIconMappings.json`: Maps New UI (`expui`) icon names to their classic icon paths.
- `inspectionDescriptions/`: HTML descriptions for inspections (`DartOutdatedDependencies`, `DartPathPackageReference`) and `metaInformation.json`.
- `intentionDescriptions/`: Description and before/after samples for `DartQuickAssistIntention`.
- `inlayProviders/`: `preview.dart` samples for the `dart.closing.labels`, `dart.parameter.names`, and `dart.types` inlay hint providers.
- `postfixTemplates/`: Description and before/after samples for each Dart postfix template.
- `liveTemplates/dart_miscellaneous.xml`: Dart live templates.
- `fileTemplates/internal/Dart File.dart.ft`: Template for new Dart files.
- `spelling/dart.dic`: Spellchecker dictionary of Dart terms.

## Invariants
- Class names, bundle keys, and file names referenced from `plugin.xml` and Java code must match the files here; IntelliJ resolves description folders by inspection/intention/template class name.
- Icon SVGs must stay in sync with `DartIconMappings.json` and the generated `DartIcons` class.

## Side Effects
- None; static resources loaded by the IntelliJ Platform at runtime.

## Verification
- Build / Analysis: `./gradlew verifyPluginStructure` and `./gradlew buildPlugin` (from `third_party/`)
