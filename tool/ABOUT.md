<!--* freshness: { reviewed: '2026-10-25' } *-->
# Tool

## Overview
Contains repository maintenance and automated validation tooling for AI agent skills, AI ignore configuration, documentation consistency, and development SDK provisioning.

## Interface
- `check_agent_skills.sh`: Shell script that verifies bidirectional consistency between AI agent skills defined in `.agents/skills/` and documented links in `README.md`.
- `check_ai_config.sh`: Shell script that checks `.aiexclude` against `.gemini/config.yaml` `ignore_patterns` in both directions, flags stale `.aiexclude` entries (path missing and not gitignored), and requires a `trigger:` in the YAML frontmatter of every `.agents/rules/*.md`.
- `validate_skills.sh`: Runs `dart pub get` and `dart run skills_lint` (extra arguments are forwarded) to lint the agent skills.
- `provision_dart.sh`: Shell script that downloads and provisions the pinned Dart SDK (`DART_VERSION`, currently `3.13.0`) for the host OS and architecture into `../dart-sdk` relative to the current directory.
- `pubspec.yaml` / `pubspec.lock`: Dart package `dart_intellij_tool` with the `skills_lint` dev dependency.
- `skills_lint.yaml`: `skills_lint` rules (relative/absolute path checks, trailing whitespace) applied to `../.agents/skills`.

## Invariants
- `check_agent_skills.sh` resolves `README.md` and `.agents/skills` relative to the current directory and never changes directory, so it must be run from the repository root.
- `check_agent_skills.sh` exits `1` if `README.md` is missing or the skill list and directories disagree; if `.agents/skills` is missing it prints a message and exits `0`.
- `check_ai_config.sh` and `validate_skills.sh` change into the correct directory themselves and can be run from any directory.
- `provision_dart.sh` checks for existence of `../dart-sdk` and skips download if already present; it aborts on an unsupported OS, and any non-arm64 architecture falls back to `x64`. Run it from `tool/` or `third_party/` (as CI does) so the SDK lands in the repository root's `dart-sdk/`.
- The `DART_VERSION` pin is checked and bumped weekly by `.github/workflows/update_dart.yaml`.

## Side Effects
- `check_agent_skills.sh`: Reads `README.md` and scans `.agents/skills/` directory structure; outputs to stdout/stderr.
- `check_ai_config.sh`: Reads `.aiexclude`, `.gemini/config.yaml`, and `.agents/rules/*.md`; calls `git check-ignore`.
- `validate_skills.sh`: Resolves pub dependencies into `tool/.dart_tool/`.
- `provision_dart.sh`: Downloads Dart SDK zip from Google storage into the current directory, unpacks it into `../dart-sdk`, and deletes the zip.

## Verification
- Build / Analysis: `bash -n check_agent_skills.sh check_ai_config.sh provision_dart.sh validate_skills.sh`
- Test (from repository root): `./tool/check_agent_skills.sh`, `./tool/check_ai_config.sh`, `./tool/validate_skills.sh`
