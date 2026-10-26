<!--* freshness: { reviewed: '2026-10-25' } *-->
# logging

## Overview
Plugin-wide logging setup (`PluginLogger.kt`). It routes the `com.jetbrains.lang.dart` `java.util.logging` hierarchy to a rotating `dash.log` file in the IDE log directory. The Flutter plugin writes to the same file.

## Interface
- com.jetbrains.lang.dart.logging.PluginLogger: Kotlin `object`.
  - `createLogger(Class<*>)`: returns an IntelliJ `com.intellij.openapi.diagnostic.Logger` for the class name.

## Invariants
- On first use, attaches a `java.util.logging.FileHandler` to the `com.jetbrains.lang.dart` JUL logger. The handler writes `<PathManager.getLogPath()>/dash.log`, appends, and rotates at 10 MB across 5 files, using the private `PluginLogFormatter`.
- If the Dart logger or the `io.flutter` logger already has a `FileHandler`, that handler is reused instead of creating a new one, so both plugins share one `dash.log`. Setup is synchronized on the JUL `LogManager`.
- Handler-creation failures (`IOException`, `SecurityException`) are reported through the IntelliJ logger; no fallback exists yet.

## Side Effects
- Creates, appends to, and rotates `dash.log*` files in the IDE log directory.
- Emits diagnostic log entries through IntelliJ's logger.

## Verification
- No dedicated tests. Check manually by inspecting `dash.log` in the IDE log directory.
