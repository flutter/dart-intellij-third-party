<!--* freshness: { reviewed: '2026-10-25' } *-->
# framework

## Overview
Minimal OSGi Version utility class (Version) for parsing semantic version strings. A single class vendored from the OSGi Alliance framework API (Copyright OSGi Alliance 2004, 2012), licensed under Apache License 2.0 per its file header — not the EPL that covers the rest of `analysisServer/`.

## Interface
- org.osgi.framework.Version

## Invariants
- Parses version numbers (major, minor, micro, qualifier).
- Vendored upstream code; avoid local edits.

## Side Effects
- Pure version comparison logic.
