<!--* freshness: { reviewed: '2026-08-31' } *-->
# third_party

## Overview
Subsystem partition containing auto-generated lexer/parser sources, vendored third-party libraries (analysisServer, platform-lsp), build/CI tooling, and primary Dart IntelliJ plugin source code.

## Subdirectories & Boundaries
- `gen`: [./gen/ABOUT.md](./gen/ABOUT.md) - Auto-generated parser, lexer, and PSI classes from GrammarKit.
- `src`: [./src/ABOUT.md](./src/ABOUT.md) - Primary Dart IntelliJ plugin implementation source code.
- `thirdPartySrc`: [./thirdPartySrc/ABOUT.md](./thirdPartySrc/ABOUT.md) - Vendored third-party dependencies (`analysisServer`, `platform-lsp`).
- `tool`: [./tool/ABOUT.md](./tool/ABOUT.md) - Plugin verifier baseline update scripts and Kokoro build configs.

## Interface
- `build.gradle.kts`: Gradle Kotlin DSL build configuration for compiling, testing, and packaging the Dart IntelliJ plugin.
- `settings.gradle.kts`: Gradle project settings defining the root project name and plugin management repositories.
- `gradlew` / `gradlew.bat`: Gradle wrapper scripts for Unix and Windows environments.
- `gradle.properties`: Gradle JVM arguments, IntelliJ platform version, and plugin version configurations.
- `plugin-content.yaml`: Distribution packaging manifest specifying bundled plugin artifacts.

## Invariants
- Root directory for Gradle build execution; must be built using Gradle wrapper (`./gradlew`).
- Depends on IntelliJ Platform Gradle Plugin.

## Side Effects
- Compiles plugin classes and generates distribution ZIPs under `build/distributions/`.
- Downloads and caches Gradle dependencies in `.gradle/`.

## Verification
- Build / Analysis: `./gradlew buildPlugin`
- Test: `./gradlew test`
