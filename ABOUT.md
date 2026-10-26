<!--* freshness: { reviewed: '2026-10-25' } *-->
# dart-intellij-third-party Blueprint

## System Overview
The `dart-intellij-third-party` repository provides the official Dart plugin for IntelliJ IDEA and Android Studio, together with its build, CI (GitHub Actions and Kokoro), release pipelines, Dart SDK pin automation, issue triage automation, and AI agent configuration.

## Subdirectories & System Boundaries
- `kokoro`: [./kokoro/ABOUT.md](./kokoro/ABOUT.md) - Continuous integration and release pipeline configurations for Kokoro environments.
- `third_party`: [./third_party/ABOUT.md](./third_party/ABOUT.md) - Core Dart IntelliJ plugin sources, generated lexer/parser code, third-party libraries, and Gradle build infrastructure.
- `tool`: [./tool/ABOUT.md](./tool/ABOUT.md) - Maintenance scripts and verification tooling for AI agent skills, AI ignore configuration, and Dart SDK provisioning.
- `.agents`: AI agent workspace customizations (`AGENTS.md`, `rules/`, `skills/`, `design-docs/`).
- `.github`: GitHub configuration: workflows (`presubmit.yaml`, `integration_tests.yaml`, `update_dart.yaml` weekly SDK pin bump, `no-response.yaml` issue triage), `dependabot.yml`, `CODEOWNERS`, issue/PR templates.
- `.gemini`: Gemini Code Assist configuration (`config.yaml`, `styleguide.md`).
- `dart-sdk/` (gitignored, not checked in): pinned Dart SDK provisioned by `tool/provision_dart.sh`; used as `DART_HOME` in CI.

## Root Files
- `gradlew` / `gradlew.bat`: Convenience wrappers that forward all arguments to `third_party/gradlew`; path arguments must be relative to `third_party/`.
- `README.md`: Developer setup, test, and verifier instructions plus the AI agent skill list.
- `CONTRIBUTING.md`: Contribution guide.
- `.aiexclude` / `.aiconfig`: AI tooling ignore and configuration files.

## Global Invariants
- AI agent skills defined under `.agents/skills/` must remain synchronized with documentation links in `README.md` (enforced by `tool/check_agent_skills.sh`).
- `.aiexclude` entries must match `.gemini/config.yaml` `ignore_patterns` in both directions, and every `.agents/rules/*.md` file must declare a `trigger:` in its YAML frontmatter (enforced by `tool/check_ai_config.sh`).
- Kokoro CI configurations rely on the bash scripts (`build.sh`, `deploy.sh`, `setup.sh`) in `third_party/tool/kokoro/`, which invoke `./gradlew`.

## System Verification
- Test: `./tool/check_agent_skills.sh` (run from the repository root)
- Test: `./tool/check_ai_config.sh`
- Test: `./tool/validate_skills.sh`
