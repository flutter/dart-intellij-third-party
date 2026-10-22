---
trigger: always_on
description: "Keep agent context small: orient via ABOUT.md, scope searches, read large files by range, and run targeted builds and tests."
---

# Agent Efficiency Rule

Everything an agent reads (file contents, command output, search results) stays in the conversation history and is re-sent on every later turn. Keep context small and relevant, but don't trade a single read for many tiny ones: extra turns re-send the history too.

## Orient before exploring

- Start from the hierarchical `ABOUT.md` manifests (see the [Load Repository Context](../skills/load-repo-context/SKILL.md) skill) instead of broad directory listings or repository-wide scans.
- Paths in `.aiexclude` (build outputs, IDE caches, the provisioned `dart-sdk/`, the IntelliJ Platform cache) are never relevant; don't list, search, or read them.

## Searching

- Prefer symbol-aware tools (an IDE or language-server integration) for finding definitions and usages, when one is available.
- Otherwise, scope text searches to the relevant source root (e.g. `third_party/src/main`, `third_party/thirdPartySrc`) and cap the number of results.
- `third_party/src/test/testData` holds more than 1,000 test fixtures. Read the specific fixtures for the test you're working on; don't search or list the whole tree unless you need to.

## Reading large files

Read targeted line ranges (after locating the relevant symbol) for these large files instead of loading them whole:

| File | Lines | Notes |
| :--- | ---: | :--- |
| `third_party/gen/com/jetbrains/lang/dart/DartParser.java` | ~10,000 | Generated from `Dart.bnf`. Read and edit the grammar instead. |
| `third_party/gen/com/jetbrains/lang/dart/lexer/_DartLexer.java` | ~1,800 | Generated from `Dart.flex`. Read and edit the lexer spec instead. |
| `third_party/src/main/java/com/jetbrains/lang/dart/analyzer/DartAnalysisServerService.java` | ~3,000 | |
| `third_party/src/main/java/com/jetbrains/lang/dart/ide/runner/server/vmService/vmServiceDrivers/service/VmService.java` | ~1,500 | Generated VM Service driver. |

## Builds and tests

This is a Java/Kotlin IntelliJ plugin built with Gradle. Most Dart-specific tooling (for example, analysis through the Dart MCP server) only applies to the Dart scripts under `tool/` and `.agents/skills/*/scripts/`.

- Run the narrowest Gradle task that answers your question, e.g. `./gradlew :test --tests "com.jetbrains.lang.dart.parser.DartParsingTest"` instead of the full suite or `buildPlugin`.
- Use `-q` or `--console=plain`, and keep only the tail of the output (e.g. `| tail -n 50`). Don't stream full Gradle logs into the conversation.
- For test failures, read the specific result file under `third_party/build/test-results/` rather than re-running with verbose logging.

## Delegation

If your agent supports subagents, delegate open-ended research (surveying a subsystem, reading upstream docs, root-causing across many files) and ask for a concise summary back, so the exploration doesn't accumulate in the main conversation.
