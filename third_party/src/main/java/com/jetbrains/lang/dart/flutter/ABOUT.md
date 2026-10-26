<!--* freshness: { reviewed: '2026-10-25' } *-->
# flutter

## Overview
Static Flutter detection helpers shared across the Dart plugin (`FlutterUtil`). The class finds the Flutter SDK root from a Dart SDK path, decides whether a module is a Flutter module, checks whether a `pubspec.yaml` declares Flutter, and reports whether the `io.flutter` plugin is installed.

## Interface
- com.jetbrains.lang.dart.flutter.FlutterUtil
  - `getFlutterRoot(dartSdkPath)`: strips the `/bin/cache/dart-sdk` suffix, or returns `null` if the path does not end with it.
  - `isFlutterModule(Module)`: true for the legacy `FLUTTER_MODULE_TYPE` module type, or when Dart support is enabled for the module and the project Dart SDK sits inside a Flutter SDK.
  - `isPubspecDeclaringFlutter(VirtualFile)`: parses the pubspec with SnakeYAML and checks for `dependencies: flutter:`.
  - `isFlutterPluginInstalled()`: whether plugin `io.flutter` is installed (computed once, at class init).

## Invariants
- `isPubspecDeclaringFlutter` intentionally mirrors `PubRoot.declaresFlutter` in the Flutter plugin so both plugins agree. Keep them in sync.
- YAML parse and I/O errors are swallowed and reported as "not Flutter".

## Side Effects
- Reads pubspec file contents (cached through `contentsToByteArray(true)`), module types, and Dart SDK settings. No writes.

## Verification
- No dedicated tests in this package; covered indirectly by callers' tests.
