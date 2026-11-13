<!--* freshness: { reviewed: '2026-10-25' } *-->
# sdk

## Overview
Dart SDK model and configuration: the "Dart SDK" and "Dart Packages" project libraries,
SDK discovery and validation, SDK update checks, and the Dart settings page.

## Interface
- `DartSdk`: SDK home path and version, resolved from the project-level "Dart SDK" library
  (`DART_SDK_LIB_NAME`). `getDartSdk(project)` is the usual entry point.
- `DartSdkLibUtil`: creates and updates the "Dart SDK" library and enables or disables it per module
  (`isDartSdkEnabled`, `disableDartSdk`, ...).
- `DartSdkUtil`: SDK home validation (`isDartSdkHome`), SDK path combo setup, and remembered SDK paths.
- `DartSdkLibraryPresentationProvider`: library presentation for the SDK; also `findDartCoreRoot`.
- `DartPackagesLibraryType`, `DartPackagesLibraryProperties`: the "Dart Packages" library kind
  (`DART_PACKAGES_LIBRARY_NAME`) maintained by `DartFileListener`.
- `DartSdkUpdateChecker`: checks for new SDK releases. Its `compareDartSdkVersions` is also used
  across the plugin to gate features on the SDK version.
- `DartSdkUpdateOption`: update-check channel (`DoNotCheck`, `Stable`, `StableAndDev`).
- `DartConfigurable` (+ `.form`): the Dart settings page. Also exposes project flags such as
  `isExperimentalLspFeaturesEnabled`, `isLspCodeActionsEnabled`, and the webdev port.

## Invariants
- `DartSdkLibraryPresentationProvider` and `DartPackagesLibraryType` are registered in
  `META-INF/plugin.xml` (`library.presentationProvider`, `library.type`).
- SDK-version feature gates use `DartSdkUpdateChecker.compareDartSdkVersions`.

## Side Effects
- Persists settings in `PropertiesComponent`: known SDK paths, last update check, update channel,
  experimental LSP flag, and webdev port.
- `DartSdkUpdateChecker` reads the latest `VERSION` file from `storage.googleapis.com/dart-archive`
  and can show an update notification. `DartAnalysisServerService` triggers it through
  `mayBeCheckForSdkUpdate`.
- `DartSdkLibUtil` and `DartConfigurable` modify project libraries and module dependencies.
- When the experimental LSP setting changes, applying `DartConfigurable` stops the LSP bridge,
  updates client capabilities, and starts the bridge again (`DartBridgeLspServerManager`).
