<!--* freshness: { reviewed: '2026-10-25' } *-->
# analysisServer

## Overview
Third-party Java client library for communicating with the Dart Analysis Server. Originally taken from the Dart SDK (`pkg/analysis_server/tool/spec/generated/java`, see [./readme.txt](./readme.txt)) under the Eclipse Public License v1.0 ([./LICENSE](./LICENSE)).

## Interface
- [./com/ABOUT.md](./com/ABOUT.md) - `com.google.dart.server` client API, remote process transport, and processors (locally maintained fork).
- [./org/ABOUT.md](./org/ABOUT.md) - Generated protocol model classes (`org.dartlang.analysis.server.protocol`) plus a vendored OSGi `Version` class (Apache License 2.0, not EPL).

## Invariants
- Third-party library partition; see [../README.md](../README.md) for the edit policy.
- `org/dartlang/analysis/server/protocol/*.java` are generated from the Dart SDK spec, but `org/dartlang/analysis/server/protocol/DartLspProtocol.kt` is hand-written Kotlin.
- `com/google/dart/server/` originated upstream but is now maintained locally; direct edits are expected (for example, `generated/AnalysisServer.java` is maintained by hand).
