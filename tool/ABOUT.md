<!--* freshness: { reviewed: '2026-08-31' } *-->
# Tool

## Overview
Contains repository maintenance and automated validation tooling for AI agent skills, documentation consistency, and development SDK provisioning.

## Interface
- `validate_agent_configs.sh`: Shell script that verifies bidirectional consistency between AI agent skills defined in `.agents/skills/` and documented links in `README.md`, and that `.geminiignore` and `.claudeignore` are identical.
- `provision_dart.sh`: Shell script that downloads and provisions the pinned Dart SDK archive for the host OS and architecture into `dart-sdk/`.

## Invariants
- `validate_agent_configs.sh` expects `README.md` to exist in the repository root and `.agents/skills` directory to be present.
- `validate_agent_configs.sh` exits with code `0` on success and `1` on documentation mismatch or missing files.
- `provision_dart.sh` checks for existence of `../dart-sdk` and skips download if already present; aborts on unsupported OS/architecture.

## Side Effects
- `validate_agent_configs.sh`: Reads `README.md`, `.geminiignore`, and `.claudeignore`, and scans `.agents/skills/` directory structure; outputs to stdout/stderr.
- `provision_dart.sh`: Downloads Dart SDK zip from Google storage and unpacks it into `dart-sdk/`.

## Verification
- Build / Analysis: `bash -n validate_agent_configs.sh provision_dart.sh`
- Test: `./validate_agent_configs.sh`
