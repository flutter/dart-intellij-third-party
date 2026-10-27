<!--* freshness: { reviewed: '2026-10-25' } *-->
# util

## Overview
Static helpers shared across the plugin: PSI construction and traversal, legacy PSI resolve,
URI resolution, and pub/package/build file handling.

## Interface
- PSI and resolve helpers:
  - `DartResolveUtil`: PSI-based resolution (class hierarchy, components, imports).
  - `DartClassResolveResult`, `DartGenericSpecialization`: resolved class plus type-argument specialization.
  - `DartPsiImplUtil`: helpers called from generated PSI (for example string literal handling).
  - `DartElementGenerator`: creates PSI elements from text.
  - `UsefulPsiTreeUtil`, `DartControlFlowUtil`: PSI tree and control-flow helpers.
  - `DartRefactoringUtil`: helpers for PSI-based refactorings.
  - `DartPresentableUtil`: presentable text for components, parameters, and types.
- URI and package helpers:
  - `DartUrlResolver` (abstract) and `DartUrlResolverImpl`: map `dart:`, `package:`, and
    `file:` URIs to files and back.
  - `PubspecYamlUtil`: finds and parses `pubspec.yaml` (cached).
  - `PackageConfigFileUtil`: finds and reads `.dart_tool/package_config.json`.
  - `DartBuildFileUtil`, `DartBazelFileUtil`: Bazel `BUILD`/`WORKSPACE` project support.
  - `PathUtil`: path helpers.
- Misc:
  - `ProcessAdapter`: no-op `ProcessListener` base class.
  - `PrintingLogger`: `Logger` that prints to a `PrintStream` (`SYSTEM_OUT`), used for local debugging.

## Invariants
- Classes are stateless static utilities. The exceptions are caches: parsed `pubspec.yaml` and
  `package_config.json` data are cached on the file and keyed by modification stamp, and some
  PSI resolve results use `CachedValuesManager` or `DartClassResolveCache`.
- Helpers that touch PSI or VFS follow the usual IntelliJ read-action rules.

## Side Effects
- None, apart from logging and caching.
