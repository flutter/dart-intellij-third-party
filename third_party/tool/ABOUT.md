<!--* freshness: { reviewed: '2026-10-25' } *-->
# tool

## Overview
Maintenance tools and scripts for checking and updating IntelliJ plugin verifier baselines, plus Kokoro CI scripts.

## Subdirectories & Boundaries
- `kokoro`: [./kokoro/ABOUT.md](./kokoro/ABOUT.md) - Kokoro CI automation scripts for environment setup, building, and deployment.
- `baseline`: Per-IDE-branch verifier baselines (`253/`, `261/`, `262/`, `263/`, each holding `verifier-baseline.txt`). Each line after the `#` header comments is `<section name><TAB><issue text>`, so severity (report section) is preserved.

## Interface
- `check_verifier_baselines.sh [check|update]`: Holds all comparison logic. Reads `build/reports/pluginVerifier` (it does not run Gradle). `check` (default) exits non-zero on any issue not in the baseline and emits GitHub annotations and a job summary under GitHub Actions; `update` rewrites the baselines from the reports on disk. Informational findings (deprecated/experimental/internal/override-only API usages) from vendored `com.intellij.platform.dartlsp` sources are filtered out as noise.
- `update_baselines.sh`: Deletes `build/reports/pluginVerifier`, runs `./gradlew verifyPlugin` (exit status ignored), then runs `check_verifier_baselines.sh update`.
- `update_baselines.bat`: Windows equivalent of `update_baselines.sh`; requires `bash` (Git for Windows) on PATH to run `check_verifier_baselines.sh update`.

## Invariants
- Run the update scripts from the repository root. `update_baselines.bat` aborts unless `third_party` exists in the current directory; `update_baselines.sh` and `check_verifier_baselines.sh` resolve their own paths and work from any directory.
- Presubmit runs `./third_party/tool/check_verifier_baselines.sh check` after the verifier tasks.
- `check_verifier_baselines.sh` fails if no verifier reports were produced.

## Side Effects
- `update_baselines.*`: Deletes `build/reports/pluginVerifier`, executes Gradle plugin verification, and overwrites `baseline/*/verifier-baseline.txt` files.
- `check_verifier_baselines.sh check`: Writes GitHub annotations and the job summary when running in GitHub Actions.

## Verification
- Build / Analysis: `bash -n update_baselines.sh check_verifier_baselines.sh`
- Test: `./third_party/tool/check_verifier_baselines.sh check` (after `./gradlew verifyPlugin`)
