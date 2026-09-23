<!--* freshness: { reviewed: '2026-08-31' } *-->
# dart-intellij-third-party Blueprint

## System Overview
The `dart-intellij-third-party` repository provides the official Dart plugin for IntelliJ IDEA and Android Studio, and automated build, integration testing, release pipelines, synchronization scripts, and issue triage automation.

## Subdirectories & System Boundaries
- `kokoro`: [./kokoro/ABOUT.md](./kokoro/ABOUT.md) - Continuous integration and release pipeline configurations for Kokoro environments.
- `third_party`: [./third_party/ABOUT.md](./third_party/ABOUT.md) - Core Dart IntelliJ plugin sources, generated lexer/parser code, third-party libraries, and Gradle build infrastructure.
- `tool`: [./tool/ABOUT.md](./tool/ABOUT.md) - Maintenance scripts and verification tooling for AI agent skills, Dart SDK provisioning, and documentation.

## Global Invariants
- AI agent skills defined under `.agents/skills/` must remain synchronized with documentation links in `README.md`.
- Kokoro CI configurations rely on Gradle build scripts located in `third_party/tool/kokoro/`.


## System Verification
- Test: `./tool/check_agent_skills.sh`
