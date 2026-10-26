<!--* freshness: { reviewed: '2026-10-25' } *-->
# Kokoro macOS External

## Overview
Configures and executes Kokoro Continuous Integration (CI) and release pipelines for macOS external environments.

## Interface
- `continuous.cfg`: Continuous integration build configuration triggering continuous build script.
- `presubmit.cfg`: Presubmit build configuration for change validation.
- `release.cfg`: Release pipeline configuration managing keystore credentials and zip distribution artifacts.
- `kokoro_build.sh`: Entry point script for presubmit and continuous automated builds; runs `third_party/tool/kokoro/build.sh`.
- `kokoro_release.sh`: Entry point script for scheduled release and deployment builds; runs `third_party/tool/kokoro/deploy.sh`.

## Invariants
- `kokoro_build.sh` and `kokoro_release.sh` depend on `KOKORO_ARTIFACTS_DIR` environment variable to locate the checked out repository workspace (`${KOKORO_ARTIFACTS_DIR}/github/dart-intellij-third-party`).
- `release.cfg` requires keystore config `74840` (`jetbrains-plugin-upload-auth-token`) for plugin deployment.
- Scripts enable `set -e` after printing the start banner (`date`/`echo`), so every subsequent build step fails fast.
- The scripts are not marked executable; Kokoro invokes them by path via `build_file`. Run them with `bash <script>` if invoking manually.

## Side Effects
- Invokes build and deploy scripts in `third_party/tool/kokoro/`.
- Generates zip distribution artifacts under `third_party/build/distributions/`.

## Verification
- Build / Analysis: `bash -n kokoro_build.sh kokoro_release.sh`
