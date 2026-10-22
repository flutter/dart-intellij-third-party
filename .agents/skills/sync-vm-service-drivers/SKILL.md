---
name: sync-vm-service-drivers
description: Advance the Dart IntelliJ plugin's VM Service Java drivers by exactly one protocol version through an LLM-authored, user-reviewed, test-first workflow. Use to explain the next revision, add red tests, decide whether SDK-backed integration coverage is worthwhile, implement the driver changes, and prepare manual verification steps.
---

# Sync VM Service Drivers

Advance exactly one protocol version per invocation. The version declared by
`third_party/src/main/java/com/jetbrains/lang/dart/ide/runner/server/vmService/vmServiceDrivers/service/VmService.java`
is the current version; the first later revision in the Dart SDK's
`runtime/vm/service/service.md` history is the target. Do not jump directly to the latest revision.

This is an interactive red-green workflow. The review pauses below are mandatory: do not replace a
pause with an assumption that the user approves the tests or the next phase.

This skill is instruction-only. Do not use, restore, create, or depend on a code generator, audit
script, report bundle, schema, or manifest. The LLM must inspect the repository and SDK history
directly and author every test and production change.

## Establish the next-version change

Prefer the sibling `../sdk` checkout and use immutable SDK commits as evidence. Inspect its branch,
cleanliness, current commit, and relationship to remote `main` with read-only Git operations. Never
update a dirty, diverged, or non-`main` checkout. Ask before fast-forwarding a clean, stale `main`.
If no usable checkout exists, offer a filtered SDK clone or inspect the same files and commit history
through the official Dart SDK repository with network permission.

Read [the protocol-history selection contract](references/updater-contract.md), then inspect
`service.md` and its Git history directly. Determine the current plugin version, locate its SDK
history anchor, identify the first later protocol version, and include the target version's
consecutive same-version stabilization commits. Stop before the following protocol transition. If
the history is incomplete, non-monotonic, or does not contain the current version, stop and explain
what evidence is missing.

Keep an evidence record in the conversation containing:

- the plugin repository commit and initial worktree status;
- the SDK source, branch, commit, cleanliness, and freshness evidence;
- the current and target protocol versions;
- the ordered SDK commit IDs selected for this one-version span; and
- the affected RPCs, types, fields, and revision notes from those commits.

For the selected span, read the complete affected RPC and type definitions—not only revision notes
or diff hunks. Inspect corresponding driver elements, consumers, request methods, response dispatch,
tests, and relevant plugin Git history. Infer the required Java files and API shape from those
sources. Before editing any file, tell the user:

- what the next revision adds, changes, removes, or deprecates;
- which same-version changes are included and why;
- the likely Java files and relevant plugin usage sites; and
- compatibility concerns such as nullability, numeric range, unions, dispatch, or removed values.

The existing drivers are the style and compatibility baseline. Review timestamp, duration, ID,
offset, and count ranges instead of translating protocol numbers mechanically; PR #654 required
`long`/`Long` to prevent overflow. Preserve the historical copyright year in existing files and use
the current calendar year for files first added by the upgrade. Do not bulk-rewrite the driver tree.

## Write the unit test first

Add focused Kotlin unit tests for the next-version contract while leaving all production driver
files and protocol constants unchanged. Reuse existing VM Service driver test fixtures and test the
smallest observable behavior that proves the change, for example:

- JSON decoding for a new response, field, or enum value;
- RPC method name, optional parameters, numeric widths, and consumer passed to `request`;
- response-type dispatch to the correct consumer; or
- compatibility behavior for a changed or deprecated protocol value.

In this project, needing a configured Dart SDK is the boundary between unit and integration tests.
Any test that does not need the Dart SDK belongs in this unit-test phase, even if it exercises
WebSocket code, request/response routing, or asynchronous callbacks. Include all such SDK-free
coverage before the unit-test review pause. Keep SDK-free driver tests in production-aligned
`vmServiceDrivers` packages and SDK-backed live-process tests separate from them.

Use boundary-revealing values where relevant, such as values greater than `Integer.MAX_VALUE` for
microseconds represented as 64-bit values. Do not write tests that merely match comments or
formatting. A missing API may make the red state a compilation failure; that is acceptable when
unavoidable, but explain it precisely.

### Mandatory pause: unit test review

After writing only the unit tests, stop. Give the user:

- the files and behaviors covered;
- the exact focused Gradle command to run;
- the expected failure and why it proves the production behavior is still missing; and
- a request to review and run the tests, then return the result.

Do not write production code, bump the protocol version, or silently weaken a test before the user
returns. If the test unexpectedly passes, investigate whether it actually proves the new behavior.

## Recommend for or against an integration test

After the user has reviewed the unit tests, make a specific recommendation and give the reasoning.
Recommend integration coverage only when the behavior requires the Dart SDK, such as launching a
real Dart process or VM, checking SDK-version compatibility, or producing behavior that only a real
Dart program can provide. WebSocket transport, request/response routing, and asynchronous callbacks
do not make a test an integration test by themselves; when they can be exercised without the SDK,
cover them during the unit-test phase. Recommend against SDK-backed coverage when the change is
passive data modeling—such as an enum value, JSON accessor, documentation, or deprecation—and focused
unit tests plus manual verification cover the meaningful risk better.

State the proposed integration scenario, the failure it could catch, and its setup/flakiness cost.
Ask the user to accept or decline the recommendation; do not infer their choice.

If the user accepts integration coverage, write only that test while production code and the
protocol version remain unchanged. The test must exercise the required SDK boundary; otherwise
reclassify it as a unit test. Prefer the narrowest stable end-to-end boundary and avoid timing or
external-network dependence.

### Mandatory pause: integration test review

After adding the integration test, stop again. Describe it, provide the exact command, state the
expected red result, and give the user time to inspect and run it. Do not implement production code
until the user returns and approves continuing. If the user declines integration coverage, record
that decision and proceed only when they authorize implementation.

## Revalidate and implement

Before production edits, repeat the read-only checks recorded at the start. Confirm that the SDK
target commit and ordered selected commit IDs are unchanged, the driver entrypoint still declares
the same current version, and the plugin worktree contains only the expected test changes plus
preserved pre-existing changes. If any protocol evidence changed, explain it and stop instead of
silently changing scope.

Implement only the selected next-version span. Write the necessary driver elements, consumers, RPC
overloads, parameter serialization, response routing, and documentation directly. Match neighboring
driver patterns while preserving deliberate deviations and unrelated local changes. When the
specification is ambiguous or conflicts with an existing compatibility choice, explain the evidence
and stop for a user decision. Finally update `versionMajor`/`versionMinor` to the target version.

Run the focused unit tests, any approved integration test, Java compilation, and existing VM Service
regression suites. Never change a test merely to make it green. If a test remains red, tell the user
the failing command and symptom, explain whether the cause is production logic, an incorrect test
assumption, protocol ambiguity, or the environment, and fix in-scope production problems. This
phase is complete only when the applicable tests are green; otherwise report the unresolved reason.

## Prepare manual verification

Finish with concrete manual test methods tailored to the change. Prefer a reproducible Sandbox IDE
debug session: run the sandbox under the debugger, start a compatible Dart debug session, place
breakpoints at the new driver API and request/response dispatch points, invoke or trigger the new VM
Service behavior, and inspect both raw JSON and the parsed Java object. State the expected method,
parameters, response type, fields, and callback.

Suggest a minimal Dart program or Evaluate Expression call that reaches the behavior, plus useful
evidence to capture (SDK version, values, debugger state, and screenshots). The manual-testing notes
and screenshots in [PR #654](https://github.com/flutter/dart-intellij-third-party/pull/654) are the
model for RPC additions. Do not claim that manual verification passed unless it was actually run.

## Boundaries

- Keep the upgrade to one protocol version and do not bundle later revisions.
- Resume from the existing phase after a pause; inspect the worktree and preserve user edits.
- Do not create or invoke VM Service audit or generation helper scripts, report bundles, schemas,
  or manifests.
- Read-only shell and repository tools are allowed for direct inspection; they must not write code.
- Do not update the SDK checkout, commit, push, or open a pull request without explicit permission.
- Do not overwrite deliberate driver customizations or unrelated worktree changes.
