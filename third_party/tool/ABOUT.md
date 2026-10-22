<!--* freshness: { reviewed: '2026-08-31' } *-->
# tool

## Overview
Maintenance tools and scripts for updating IntelliJ plugin verifier baseline reports.

## Subdirectories & Boundaries
- `kokoro`: [./kokoro/ABOUT.md](./kokoro/ABOUT.md) - Kokoro CI automation scripts for environment setup, building, and deployment.

## Interface
- `update_baselines.sh`: Shell script to update IntelliJ plugin verifier baseline reports from `./gradlew verifyPlugin` output.
- `update_baselines.bat`: Windows batch script to update IntelliJ plugin verifier baseline reports.

## Invariants
- Must be run from `third_party/`; expects `./gradlew verifyPlugin` report outputs.

## Side Effects
- Executes Gradle plugin verification and overwrites `baseline/*/verifier-baseline.txt` files.

## Verification
- Build / Analysis: `bash -n update_baselines.sh`
