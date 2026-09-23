<!--* freshness: { reviewed: '2026-08-31' } *-->
# kokoro

## Overview
Kokoro CI automation scripts for environment setup, building plugin artifacts, and deployment.

## Interface
- `build.sh`: Builds plugin distribution zip via `./gradlew buildPlugin`.
- `deploy.sh`: Publishes plugin distribution to JetBrains Marketplace via `./gradlew publishPlugin`.
- `setup.sh`: Sets up JDK 21 environment and runs `./gradlew clean`.

## Invariants
- Executes in Kokoro CI environment with Gradle wrapper (`./gradlew`).

## Side Effects
- Downloads dependencies, invokes `./gradlew buildPlugin` and `./gradlew publishPlugin`, mutates build output directory.

## Verification
- Build / Analysis: `bash -n build.sh deploy.sh setup.sh`
