<!--* freshness: { reviewed: '2026-10-25' } *-->
# projectWizard

## Overview
New Project / New Module wizard support for Dart. The main path runs `dart create --template <id>` from the selected Dart SDK. It then finds the generated `pubspec.yaml` and entrypoint, opens them, runs `pub get`, and creates run configurations. A built-in command-line app template is the fallback when `dart create --list-templates` returns nothing.

## Interface
- com.jetbrains.lang.dart.projectWizard.DartModuleBuilder: `ModuleBuilder` for the Java-style New Module wizard. It also holds the shared helpers `setupProject`, SDK setup, and the "open files + pub get" scheduler. `isPubGetScheduledForNewlyCreatedProject(Project)` is public.
- com.jetbrains.lang.dart.projectWizard.DartProjectGenerator: `WebProjectTemplate<DartProjectWizardData>` (ID `Dart`) for the WebStorm-style generator. It delegates to `DartModuleBuilder.setupProject`.
- com.jetbrains.lang.dart.projectWizard.DartGeneratorPeer (+ `.form`): `ProjectGeneratorPeer` UI for choosing the SDK path and template. It loads the templates asynchronously.
- com.jetbrains.lang.dart.projectWizard.DartModuleWizardStep: `ModuleWizardStep` that wraps `DartGeneratorPeer` for `DartModuleBuilder`.
- com.jetbrains.lang.dart.projectWizard.DartProjectWizardData: value holder for the SDK path and selected `DartProjectTemplate`.
- com.jetbrains.lang.dart.projectWizard.DartProjectTemplate: abstract template base.
  - `generateProject(sdkRoot, module, baseDir)`
  - `loadTemplatesAsync`: caches templates per SDK path and falls back to `CmdLineAppTemplate`.
  - Run-configuration helpers for command-line, webdev, and test runs.
- com.jetbrains.lang.dart.projectWizard.DartCreate (+ nested `DartCreateTemplate`): runs `dart create`. `getAvailableTemplates` parses `--list-templates` JSON. `generateInto` runs `--force --no-pub --template <id> <name>` with a 30 s timeout.
- com.jetbrains.lang.dart.projectWizard.DartCreateProjectTemplate: package-private `DartProjectTemplate` backed by a `dart create` template. It resolves `__projectName__` in the entrypoint and creates test, command-line (`bin/`), or webdev (`web/*.html`) run configurations as appropriate.
- com.jetbrains.lang.dart.projectWizard.CmdLineAppTemplate: package-private fallback that writes a minimal `pubspec.yaml` and `bin/<module>.dart` directly.

## Invariants
- `DartProjectTemplate.loadTemplatesAsync` must be called on a pooled thread without a read action. The consumer is invoked on the EDT.
- Project generation runs on a pooled thread. File opening, `pub get`, and run-configuration creation are deferred until the IDE is non-modal and the module is not disposed.
- `dart create` is invoked with `--no-pub`; `pub get` is run afterwards through `DartPubGetAction`.

## Side Effects
- Spawns `dart create` processes (with analytics environment variables set by `Analytics.updateEnvironment`).
- Creates project files and directories on disk, configures the Dart SDK library, updates known SDK paths, adds run configurations, opens editors, and runs `pub get`.

## Verification
- No dedicated tests in this package. Check manually through File | New | Project | Dart.
