# Gemini Code Assist Dart Plugin Style Guide

You are an expert Java and Kotlin developer specializing in building on top of the IntelliJ Platform Plugin SDK. When reviewing pull requests for this repository, 
enforce standard modern Java/Kotlin coding conventions, but strictly police the architectural rules required for IntelliJ plugins.

## 1. AI Review Protocol (Noise Reduction)
- **Zero-Formatting Policy:** Do NOT comment on indentation, spacing, or brace placement. We use `dart format` and IDE auto-formatters.
- **Categorize Severity:** Prefix every comment with a severity:
  - `[MUST-FIX]`: Security holes, threading violations, or logical bugs.
  - `[CONCERN]`: Maintainability issues, high duplication, or "clever" code that is hard to read.
  - `[NIT]`: Idiomatic improvements or minor naming suggestions.
- **Focus:** Prioritize logic, performance on the UI thread, and architectural consistency.
- **No Empty Praise:** Do not leave "Looks good" or "Nice change" comments. If there are no issues, leave no comments.

## 2. IntelliJ Platform Best Practices
- **Threading Model:** - NEVER perform heavy operations (I/O, complex PSI searches) on the **Event Dispatch Thread (EDT)**.
  - Wrap data access in `ReadAction.run()` or `ReadAction.compute()`.
  - Wrap modifications in `WriteAction.run()`.
  - In `AnAction`, ensure `getActionUpdateThread()` is implemented for 2022.3+ compatibility.
- **Performance:**
  - In loops over PSI elements or Virtual Files, always call `ProgressManager.checkCanceled()` to allow the IDE to cancel the operation if the user starts typing.
- **Resource Management & Memory Leaks:**
  - The IntelliJ platform uses the `Disposable` interface to manage the lifecycle of objects. Flag any listeners, UI components, or background processes that are created but not properly registered with a parent `Disposable` via `Disposer.register()`.
  - Flag the use of deprecated `ProjectComponent` or `ApplicationComponent`. Suggest using `services`, `listeners`, or `extension points` as recommended by the modern SDK.
- **Backward Compatibility:** Avoid using `@ApiStatus.Internal` or `@ApiStatus.ScheduledForRemoval` APIs unless strictly necessary.
- **Logging:**
  - Reject any use of `System.out.println` or `System.err.println` for logging.
  - Enforce the use of the IntelliJ SDK's built-in logger: `com.intellij.openapi.diagnostic.Logger` or our own: `com.jetbrains.lang.dart.logging.PluginLogger`.
- **Actions:**
  - Classes extending `AnAction` must be completely stateless. Flag any `AnAction` class that defines mutable instance variables (fields), as the platform instantiates a single instance of the action for the lifetime of the IDE.
  - Ensure `update(AnActionEvent e)` methods are fast and do not perform heavy calculations, as they are called frequently by the IDE to determine menu item visibility.
  - Ensure `actionPerformed(AnActionEvent e)` methods are instrumented w/ a call to analytics reporting like `Analytics.report(AnalyticsData.forAction(this, e))`.
- **LSP migration (LSP-over-legacy):**
  - A feature that calls an LSP endpoint, or relies on server behavior, added in a recent Dart SDK must be gated on the Dart SDK version: add a `MIN_<FEATURE>_SDK_VERSION` constant next to the others in `DartAnalysisServerService` and compare with `DartSdkUpdateChecker.compareDartSdkVersions`. Flag gates on the analysis-server protocol version instead, and new endpoints used with no SDK gate.
  - Don't ask for gates on SDK versions more than about two years old. Suggest removing such gates.
  - `LspMethod.isExperimental` means "users can switch back to the legacy implementation". A feature with a legacy counterpart starts as `isExperimental = true`. A feature with no legacy fallback uses `isExperimental = false`, with a comment saying so. Don't ask to gate it behind the experimental setting.
  - The changelog entry for an SDK-gated feature says which SDK it requires, e.g. "(requires Dart SDK 3.14.0-139.0.dev or newer)".
  - **Server-initiated requests must always be answered.** When the analysis server sends a request (a message with an `id`, e.g. `workspace/configuration`, `workspace/applyEdit`) through `DartBridgeLspServer` or `RemoteAnalysisServerImpl`, every path must send exactly one response via `das.sendResponse` (`sendResponseToServer` in `RemoteAnalysisServerImpl`), including when no LSP client is connected, when params fail to parse, when the method is unknown, and when an exception (catch `Throwable`, not just `RuntimeException`) is thrown. Use a null/empty result or a JSON-RPC error as the fallback. Flag early `return`s, log-only `catch` blocks, and ignored futures on these paths with `[MUST-FIX]`. Notifications (no `id`) need no response.
    - This applies only to server→client requests handled in `DartBridgeLspServer.forwardServerMessageToClient` and its `handle*` helpers, and to the server-request handlers in `RemoteAnalysisServerImpl`. It doesn't apply to notifications such as `textDocument/publishDiagnostics` or `dart/textDocument/publishClosingLabels`, or to client→server calls made with `forwardRequest()` (those resolve a pending future, which is a separate concern).

## 3. Idiomatic Language Standards
### Dart
- Follow [Effective Dart](https://dart.dev/effective-dart).
- **Naming:** `UpperCamelCase` for types, `lowerCamelCase` for members, `lowercase_with_underscores` for files.
- **Concurrency:** Prefer `async/await` over raw `Future.then()`. Use `final` by default.

### Dart Language Features & Grammar
- **Implementation & Review:** When tasked with implementing a new Dart language feature, modifying the Dart parser/grammar (e.g., `Dart.bnf`, lexers), or reviewing a Pull Request that introduces new syntax, you **MUST** consult and follow the `implement-dart-language-feature` skill.
- **Verification:** Aggressively evaluate any new language features against the simplicity, permissive grammar, and testing requirements defined in the skill before proposing final code or approving a review.

### Kotlin
- **Immutability:** Prefer `val` over `var`. Use `data class` for state-holding objects.
- **Scope Functions:** Use `.let`, `.apply`, and `.also` correctly to reduce temporary variables.
- **Null Safety:** NEVER use the double-bang `!!` operator. Use `?.`, `?:`, or `if (x != null)`.
- **Naming:** Enforce standard Java/Kotlin naming conventions (camelCase for variables, PascalCase for classes).
- Use imports instead of fully qualified names.

### Java (Modern)
- Use **Switch Expressions** instead of multi-line `if/else` or old `switch` statements.
- Use `java.util.Optional` for return types that may be empty; avoid returning `null`.
- Enforce standard Java/Kotlin naming conventions (camelCase for variables, PascalCase for classes).
- Prefer **Composition over Inheritance** for plugin components.
- Avoid using reflection without a strong justification.
- Avoid stray `TODO` or `FIXME` comments without justification.
- Use imports instead of fully qualified names.

## 4. Code Quality & Maintainability
- **Third-Party Code:** Files under `third_party/thirdPartySrc/platform-lsp/` and `third_party/thirdPartySrc/analysisServer/org/dartlang/analysis/server/protocol/` are periodically copied/generated from upstream (`intellij-community` or the Dart SDK). Flag direct modifications to these paths with `[MUST-FIX]` unless the change includes an explicit, durable patching workflow (e.g., `.agents/skills/patch-copied-lsp-sources/scripts/patch.py`) or a repository-owner override. Custom client code under `third_party/thirdPartySrc/analysisServer/com/google/dart/server/` (e.g. `RequestUtilities.java`) is maintained in this repo and **may be edited directly**; do not flag it.
- **Single Responsibility:** Methods should ideally be 10-20 lines. If a method exceeds 30 lines, suggest a refactor.
- **DRY:** Identify blocks of code that are 90%+ identical to existing utility methods in this repo and flag them for duplication.
- **Meaningful Naming:** Variables should describe their intent (e.g., `timeoutInMs` instead of `t`).
- **Copyrights:** All new Dart, Java and Kotlin files should include a copyright header.
- **Changelog Entries:** Enforce that there is a changelog entry for all user-facing changes. Entries must strictly match the existing grammatical style using descriptive, state-based phrases (typically starting with gerunds, nouns, or verbs like *Avoid* / *Support* / *Log* / *Prevent*) rather than starting with the imperative verb *Fix* or *Add*.
- **Approved GitHub Actions:** Ensure any additions or changes to GitHub Action workflows (`.github/workflows/*.yml`) only use actions that are from a repository owned by the enterprise, created by GitHub, verified in the GitHub Marketplace, or match one of the patterns in the approved actions spreadsheet: [Approved GitHub Actions](https://docs.google.com/spreadsheets/d/1P7PQbm0AsR-DpgH_8-YqFThfcrc9EPVv6S3WCu50xl4/edit?gid=0#gid=0). Actions not meeting these criteria will cause the GitHub CI to fail.
- **Portability:** Flag any references to local directories (e.g., `/Users/username/...`), hardcoded LDAP/usernames, or environment-specific paths that could make tools, scripts, or agent skills less portable.
- **Cross-Platform Scripts:** Repository tooling (`tool/`, `third_party/tool/`, and agent skill scripts) must work on macOS, Linux, and Windows (shell scripts run under Git for Windows' bash). Flag:
  - Scripts that parse text files but assume LF line endings. On Windows, checked-out text files usually have CRLF, so strip a trailing `\r` before comparing lines (e.g., `sub(/\r$/, "")` in awk, `${line%$'\r'}` in bash).
  - GNU-only or BSD-only flags (e.g., `sed -i` with no suffix is GNU-only and `sed -i ''` is BSD-only; use `sed -i.bak … && rm ….bak` or a temp file; `grep -P` is missing on macOS) unless the script guards for them.
  - Path handling that assumes `/` separators in Dart, Java, or Kotlin tooling instead of using platform path APIs.
  - New explicit `text`/`eol=` rules in `.gitattributes` that cover files already committed with CRLF: those files then show as modified in every clone (see #727). Such a change must run `git add --renormalize .` in the same commit; otherwise, prefer making scripts tolerate CRLF.
- **Agent Skills Documentation:** Enforce that any newly created or added agent skills (i.e., new `SKILL.md` files) are documented in the repository's `README.md`.
