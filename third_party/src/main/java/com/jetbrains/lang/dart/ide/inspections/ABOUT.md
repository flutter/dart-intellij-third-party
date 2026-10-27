<!--* freshness: { reviewed: '2026-10-25' } *-->

# inspections

## Overview

IDE inspections implemented on the plugin side. Most Dart diagnostics come
from the analysis server instead.

## Interface

- `DartPathPackageReferenceInspection`: a YAML inspection for `pubspec.yaml`
  that flags invalid `path:` package references. It is registered in
  `META-INF/dart-yaml.xml`, so it loads only when the YAML plugin is
  present.

## Invariants

- Applies only to pubspec YAML files.

## Side Effects

- None (it only reports problems).

## Verification

- No dedicated test found.
