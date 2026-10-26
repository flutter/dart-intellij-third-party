<!--* freshness: { reviewed: '2026-10-25' } *-->
# kokoro

## Overview
Kokoro CI automation scripts for environment setup, building plugin artifacts, and deployment to the JetBrains Marketplace dev channel.

## Interface
- `build.sh`: Sources `setup.sh`, runs `setup`, then builds the plugin distribution zip via `run_gradle_with_retry buildPlugin` in `third_party/`.
- `deploy.sh`: Sources `setup.sh`, runs `setup`, computes the dev version with `printVersion -Pdev`, builds with `buildPlugin -Pdev --info`, then uploads `build/distributions/Dart.zip` with `curl` to `https://plugins.jetbrains.com/plugin/uploadPlugin` (`pluginId=6351`, `channel=dev`). The upload token comes from `JB_MARKETPLACE_TOKEN` or, if unset, the Kokoro keystore file `${KOKORO_KEYSTORE_DIR}/74840_jetbrains-plugin-upload-auth-token`.
- `setup.sh`: Defines `setup()` (enables `set -e`/`pipefail`, prints `java --version`, exports IPv6-preferring `JAVA_OPTS`, runs `./gradlew --version`) and `run_gradle_with_retry [--max-retries N] [--delay-secs N] <gradle args>` (defaults: 2 retries, 15 s delay; no retry when interrupted, exit code >= 128), used for every Gradle call.

## Invariants
- Executes in Kokoro CI environment with Gradle wrapper (`./gradlew`).
- `build.sh` and `deploy.sh` must start in the repository root: they `source ./third_party/tool/kokoro/setup.sh` and then `cd third_party`.
- `deploy.sh` exits `1` if neither a token source nor `build/distributions/Dart.zip` is available.

## Side Effects
- Downloads dependencies, invokes `./gradlew buildPlugin`, mutates the build output directory.
- `deploy.sh` publishes the built zip to the JetBrains Marketplace `dev` channel over HTTPS.

## Verification
- Build / Analysis: `bash -n build.sh deploy.sh setup.sh`
- Manual (from repository root): `JB_MARKETPLACE_TOKEN=<token> ./third_party/tool/kokoro/deploy.sh`
