<!--* freshness: { reviewed: '2026-10-25' } *-->
# third_party

## Overview
Subsystem partition containing auto-generated lexer/parser sources, vendored third-party libraries (analysisServer, platform-lsp), build/CI tooling, and primary Dart IntelliJ plugin source code.

## Subdirectories & Boundaries
- `gen`: [./gen/ABOUT.md](./gen/ABOUT.md) - Auto-generated parser, lexer, PSI, and icon classes (GrammarKit, JFlex, icon class generator).
- `src`: [./src/ABOUT.md](./src/ABOUT.md) - Primary Dart IntelliJ plugin implementation source code, resources, and tests.
- `thirdPartySrc`: [./thirdPartySrc/ABOUT.md](./thirdPartySrc/ABOUT.md) - Vendored third-party dependencies (`analysisServer`, `platform-lsp`).
- `tool`: [./tool/ABOUT.md](./tool/ABOUT.md) - Plugin verifier baseline check/update scripts and per-IDE `baseline/` files, plus Kokoro CI shell scripts.
- `gradle`: Gradle version catalog (`libs.versions.toml`) and wrapper configuration (`wrapper/`).

## Interface
- `build.gradle.kts`: Gradle Kotlin DSL build configuration for compiling, testing, and packaging the Dart IntelliJ plugin; declares repositories and computes the plugin version from `CHANGELOG.md` (with a `-dev.<date>-<commit>` suffix when `-Pdev` is set).
- `settings.gradle.kts`: Gradle project settings; only sets `rootProject.name = "Dart"`.
- `gradlew` / `gradlew.bat`: Gradle wrapper scripts for Unix and Windows environments.
- `gradle.properties`: Plugin name/id, `pluginSinceBuild`, `ideaVersion`, `javaVersion`, and Gradle/JVM flags (configuration cache, build cache, JVM args). It does not hold the plugin version.
- `CHANGELOG.md`: Release notes; the latest entry is the source of the plugin version.
- `plugin-content.yaml`: Distribution packaging manifest specifying bundled plugin artifacts.
- `Dart-community.iml` / `intellij.dart.iml`: IntelliJ module definition files.
- `LICENSE.TXT`: Apache 2.0 license for the plugin sources.

## Invariants
- Root directory for Gradle build execution; must be built using Gradle wrapper (`./gradlew`), either here or via the repository-root forwarding wrapper.
- Depends on IntelliJ Platform Gradle Plugin.

## Side Effects
- Compiles plugin classes and generates distribution ZIPs under `build/distributions/`.
- Downloads and caches Gradle dependencies in `.gradle/`.

## Verification
- Build / Analysis: `./gradlew buildPlugin`
- Test: `./gradlew test`
