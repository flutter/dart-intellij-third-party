<!--* freshness: { reviewed: '2026-08-04' } *-->
# icons

## Overview
Provides auto-generated Swing Icon constants for Dart file types, test runners, tool windows, and warning markers.

## Interface
- icons.DartIcons (Dart_13, Dart_16, Dart_file, Dart_remote, Dart_test, DartWeb, PubServeToolWindow, Warning_point)

## Invariants
- Icon constants are static final and loaded via IntelliJ IconManager.
- Icons are auto-generated from SVG assets and should not be edited manually.

## Side Effects
- Loads rasterized/SVG icon resources into JVM memory upon class loading.
