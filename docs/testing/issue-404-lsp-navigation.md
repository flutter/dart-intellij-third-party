# Test direct Implementation and Super navigation (issue 404)

Use this procedure to check the experimental LSP routes in the sandbox IDE and collect evidence that direct navigation does not fall back to legacy hierarchy requests. This is a procedure, not a record of completed builds or GUI checks.

## Quick path

1. Preserve any existing test/verifier reports before cleaning or running filtered tests; close the running sandbox IDE.
2. In a terminal, replace the example paths and run:

   ```shell
   cd /path/to/dart-intellij-third-party/third_party
   export JAVA_HOME=/path/to/jdk-21
   export DART_HOME=/path/to/dart-sdk
   export PATH="$JAVA_HOME/bin:$DART_HOME/bin:$PATH"
   "$DART_HOME/bin/dart" --version
   ./gradlew clean prepareSandbox --no-build-cache
   ./gradlew runIde
   ```

3. In the sandbox, open a disposable Dart project. Under **Settings > Languages & Frameworks > Dart**, configure that SDK and select **Turn on experimental LSP features**.
4. Create the two sample files below, enable instrumentation, and invoke each direct action once after analysis settles. Compare its UI destination with its matching protocol response.
5. Repeat with the checkbox OFF in a separate trace, then restore logging and settings.

The configured IDE is IntelliJ IDEA 2026.1.3, the plugin's `sinceBuild` is 253, and the Java target is 21. Use `runIde`, not `runTarget` with its older default. Record the actual SDK version; neither these instructions nor one successful SDK run establish a minimum supported Dart version. Gradle tests consume `DART_HOME`; do not rely on `DART_SDK` alone.

**Rebuilt is not fresh configuration.** `clean prepareSandbox --no-build-cache` rebuilds artifacts and prepares the existing sandbox. The current sandbox is under `third_party/.intellijPlatform/sandbox/Dart/IU-2026.1.3`, outside `build`; the command does not establish empty configuration, system state, or caches. Preserve settings: do not delete or rename sandbox directories. Truly isolated-configuration testing remains a separate pending check; no custom fresh-sandbox Gradle property is configured.

## Minimal two-file fixture

Create these files together in the Dart project's `lib` directory. Keep the caret on the indicated declaration name when testing.

`base.dart`:

```dart
abstract class Base {
  void work();
}

class Middle extends Base {
  @override
  void work() {}
}

class NoOverride extends Middle {}

void topLevel() {}
```

`leaf.dart`:

```dart
import 'base.dart';

class Leaf extends NoOverride {
  @override
  void work() {}
}
```

Use **Navigate > Implementation(s)** and **Navigate > Super Method** (or locate those actions through **Help > Find Action**). Avoid gutter navigation and Method Hierarchy during a direct-action capture.

| Checkbox / direct action | Expected feature request | Expected UI / response |
| --- | --- | --- |
| ON: Implementation on `Base.work` | Inner `textDocument/implementation` | Actual overrides (`Middle.work`, `Leaf.work`) appear as destinations; compare with returned locations. |
| ON: Super on class `Leaf` | Inner `dart/textDocument/super` | One native destination: `NoOverride`. |
| ON: Super on `Leaf.work` | Inner `dart/textDocument/super` | One native effective destination: `Middle.work`, inherited through `NoOverride`. |
| ON: Super on `topLevel` | Inner `dart/textDocument/super`, if applicable | A null result means no movement, not a legacy retry. |
| ON: empty/null/error/unsupported response | Same native route, if a request can be issued | No legacy fallback; no fabricated destination. Record the actual response and UI. |
| OFF: Implementation or Super | Preserved legacy path, normally `search.getTypeHierarchy` | Existing legacy navigation; no native feature request attributable to this action. |

Super uses the active editor/caret and accepts one nullable SDK location without hierarchy reconstruction or Object filtering. Implementation uses a PSI search anchor, which can be a resolved declaration when invoked on a reference; do not assume its request always uses the raw caret position. The SDK decides native Super semantics: record its answer and whether the UI agrees, including any deviation from the simple fixture expectations.

## Capture the actual plugin transport

The plugin's analyzer runs as `dart language-server --protocol=analyzer`. Native requests travel inside outer `lsp.handle` requests, with the inner request in `lspMessage` and response in `lspResponse`. A separately launched `--protocol=lsp` server is **not** evidence about plugin routing.

1. Open **Help > Find Action > Registry** and locate `dart.server.additional.arguments`.
2. Record its previous value. Append the following argument, preserving all existing arguments and replacing the example with an absolute, space-free, new unused log path in a writable directory:

   ```text
   --instrumentation-log-file=/absolute/no-spaces/new-unused-navigation-on.log
   ```

   The plugin splits additional arguments on literal spaces; quoting a path containing spaces does not solve this.
3. Restart Dart Analysis Server from the Analysis panel or Find Action (action ID `Dart.Restart.Analysis.Server`). Wait for analysis to settle.
4. Record checkbox state, file URI, declaration/caret or search anchor, and time. Invoke just one direct navigation action per observation window. Record the resulting UI destination or lack of movement.
5. Find the outgoing outer request and inner method/parameters, then the matching response using both levels of request IDs. Check URI, positions, errors/null, and UI agreement. Repeat for the other action.
6. For OFF comparisons, use a different unused log path and restart the server again, retaining startup context. Do not mix ON/OFF observations without labeling them.

This command only locates candidate records; inspect surrounding content and matching responses in the log viewer/editor:

```shell
# Any working directory; replace this with the actual capture path.
grep -nE 'lsp\.handle|textDocument/implementation|dart/textDocument/super|search\.getTypeHierarchy' /absolute/no-spaces/new-unused-navigation-on.log
```

**Pass criterion:** with the checkbox ON, the direct action uses its native feature request and has no action-attributable `search.getTypeHierarchy`, including after an error or null response. This does **not** require zero DAS traffic. Gutters, Method Hierarchy, analysis, and overlays remain legacy. Extra definition or document-change traffic can be legitimate. A grep hit count, total absence of unrelated startup traffic, or a screenshot alone is not sufficient proof; correlate the action, parameters, IDs, response, and UI.

### Optional session communications log

Where supported by the installed SDK, `--session-log=/absolute/no-spaces/navigation-session.json` captures JSON Lines with `time`, `kind`, `sender`, `receiver`, and `message`. Alternatively, use analyzer settings **View analyzer diagnostics > Session communications log > Start capturing**, perform the actions, then **Stop > Download**. Availability depends on SDK version; use instrumentation above if these options are absent. Session logs can include essential startup context rather than an empty action-only window. Do not describe raw instrumentation logs as JSON Lines.

## Manual SDK and lifecycle checklist

For each row, record SDK/IDE versions, checkbox state, action, response/destination, and pass/fail/pending. Repeat relevant navigation with the checkbox OFF as a legacy control.

- [ ] Class, member, constructor, inherited-through-intermediate, mixin, interface, and `Object` cases: compare the native SDK response with the UI, not a client-reconstructed hierarchy.
- [ ] Cross-file destinations and SDK/library-root files, both already open at startup and opened after startup.
- [ ] Unsaved edits and concurrent edits: verify navigation uses current content; add Unicode and CRLF examples rather than assuming coverage.
- [ ] IDE startup, analysis-server restart, and checkbox ON → OFF → ON: subsequent actions use the selected route without hanging.
- [ ] Close the editor or project while navigation is pending: no stale navigation or legacy retry. If the response is too fast to observe this, mark pending.
- [ ] Cancel a pending action when cancellation is actually observable: no late UI movement or fallback. Do not label an unobserved race as passed.
- [ ] Dynamic plugin reload only if the IDE permits it; otherwise record restart-required/pending and test restart separately.
- [ ] Record isolated-configuration testing separately from artifact rebuilding; do not claim a fresh profile based on `clean`.

Socket fixtures protect client routing and guards, not native SDK semantics. `DartLspSuperNavigationTest` has 17 methods covering a positive cross-file case, null/internal-error/MethodNotFound, malformed targets, cancellation, a genuinely delivered late reply, disposed editor, separately owned disposed project, and a legacy-observer sensitivity control. Every session verifies that the actual service hierarchy facade reaches an injected semantic spy with the expected URI, offset, and `superOnly` arguments before asserting no fallback; a quiet, disconnected legacy backend is not sufficient evidence. It observes the task-finished UI boundary and uses an ordered client configuration barrier after the late response. It does not replace the manual semantic matrix above; dedicated non-Dart/nonrunning-server, Unicode/CRLF/concurrent-edit, and native mixin/interface coverage is not established by those tests.

## Automated checks and report preservation

Run from `third_party` with the quick-path environment. First close the sandbox and preserve prior reports. Run clean preparation before collecting new evidence, not after it:

```shell
./gradlew clean prepareSandbox --no-build-cache
./gradlew test --rerun-tasks
```

Before the filtered run below, preserve the full-suite XML and HTML reports in a separate evidence location; filtered runs can overwrite them. Report paths relative to `third_party` are `build/test-results/test` and `build/reports/tests/test`.

```shell
./gradlew test --tests 'com.jetbrains.lang.dart.lsp.DartLspSuperNavigationTest' --tests 'com.jetbrains.lang.dart.lsp.DartLspNavigationTest' --tests 'com.jetbrains.lang.dart.lsp.DartBridgeLspServerTest' --tests 'com.jetbrains.dart.analysisServer.DartGotoImplementationTest' --rerun-tasks
./gradlew verifyPlugin
./tool/check_verifier_baselines.sh check
```

Run and report both verifier commands, even if the first fails. Verifier IDEs come from a dynamic recommended matrix: record each resolved IDE build and retain `build/reports/pluginVerifier/*/report.md`. Report raw blocking verifier failures separately from the baseline comparison; neither a baseline match nor copied-source status automatically exempts compatibility failures. Do not automatically update baselines.

## Cleanup and evidence handoff

- Restore the previous Registry value (remove only the logging argument you added), restore the original experimental checkbox state, and restart Dart Analysis Server to stop logging.
- Logs can contain private source, paths, and project details. Keep them out of commits and review/anonymize before any public sharing. SDK log-normalization tooling requires an SDK source checkout; it is not a script in this plugin repository.
- Record commit/worktree identity, local modifications, exact commands and outcomes, IDE/SDK/JDK versions, checkbox states, report locations, and per-action trace IDs/URI/parameters/response/UI destination.
- List failed, skipped, unavailable, and pending checks explicitly, including GUI, dynamic reload, cancellation races, and isolated configuration. Automated results are not manual results.

## References

- [Dart SDK instrumentation tutorial](https://github.com/dart-lang/sdk/blob/bb3e268ce85d703e7ff47876dbe1ec63876e3ff8/pkg/analysis_server/doc/tutorial/instrumentation.md)
- [Dart SDK session-log tutorial](https://github.com/dart-lang/sdk/blob/bb3e268ce85d703e7ff47876dbe1ec63876e3ff8/pkg/analysis_server/doc/tutorial/session_log.md)
- [Navigation service](../../third_party/src/main/java/com/jetbrains/lang/dart/lsp/DartLspNavigationService.kt), [bridge](../../third_party/src/main/java/com/jetbrains/lang/dart/lsp/DartBridgeLspServer.kt), and [direct Super tests](../../third_party/src/test/java/com/jetbrains/lang/dart/lsp/DartLspSuperNavigationTest.kt)
- [Build configuration](../../third_party/build.gradle.kts) and [contributor testing instructions](../../CONTRIBUTING.md#running-plugin-tests)

The SDK tutorials are published by `dart-lang/sdk` and pinned to commit `bb3e268ce85d703e7ff47876dbe1ec63876e3ff8`; they document that revision, not a minimum supported installed SDK.
