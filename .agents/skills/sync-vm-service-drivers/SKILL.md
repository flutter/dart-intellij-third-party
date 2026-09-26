---
name: sync-vm-service-drivers
description: Advance the Dart IntelliJ plugin's generated VM Service Java drivers by exactly one protocol version using a user-reviewed, test-first workflow. Use to explain the next protocol revision, add red tests, decide whether SDK-backed integration coverage is worthwhile, implement the generated driver changes, and prepare manual verification steps.
---

# Sync VM Service Drivers

Advance exactly one protocol version per invocation. The version declared by
`vmServiceDrivers/service/VmService.java` is the current version; the next revision in the Dart SDK's
`runtime/vm/service/service.md` is the target. Do not jump directly to the latest revision.

This is an interactive red-green workflow. The review pauses below are mandatory: do not replace a
pause with an assumption that the user approves the tests or the next phase.

## Establish the next-version change

Prefer the sibling `../sdk` checkout and use immutable SDK commits as evidence. Inspect its branch,
cleanliness, and relationship to remote `main` with read-only Git commands. Never update a dirty,
diverged, or non-`main` checkout. Ask before fast-forwarding a clean, stale `main`. If no usable SDK
checkout exists, offer a filtered clone or, with network permission, use the audit script's GitHub
mode.

Run the deterministic audit from the plugin repository root and validate its bundle:

```bash
python3 .agents/skills/sync-vm-service-drivers/scripts/audit.py
python3 .agents/skills/sync-vm-service-drivers/scripts/validate_report.py \
  third_party/build/reports/vm-service-drivers/<source-id>
```

Use `report.json` and the candidate artifacts as evidence. Read
[the incremental updater contract](references/updater-contract.md) before selecting or applying a
candidate span. Use the first later protocol transition to identify the next version, then select
the commits after the commit that first entered the current plugin version through the final
consecutive candidate that still declares the target version. Stop before the following protocol
transition. This includes same-version changes both before and after the target transition so the
upgrade uses that version's stabilized state. If the report cannot establish that ordered history,
stop and explain what source evidence is missing.

Before editing any file, tell the user:

- the current and next protocol versions;
- what the next revision adds, changes, removes, or deprecates;
- any intervening same-version changes included in the upgrade;
- the affected RPCs, types, fields, generated files, and relevant plugin usage sites; and
- compatibility concerns such as nullability, numeric range, unions, dispatch, or removed values.

Generated Java is evidence, not an instruction to overwrite the driver tree. Check the protocol's
semantics and existing Java conventions. In particular, review timestamp, duration, ID, offset, and
count ranges instead of accepting a generated `int`/`Integer` mechanically; PR #654 required
`long`/`Long` to prevent overflow.

## Write the unit test first

Add focused Kotlin unit tests for the next-version contract while leaving all production driver
files and protocol constants unchanged. Reuse existing VM Service driver test fixtures and test the
smallest observable behavior that proves the change, for example:

- JSON decoding for a new response, field, or enum value;
- RPC method name, optional parameters, numeric widths, and consumer passed to `request`;
- response-type dispatch to the correct consumer; or
- compatibility behavior for a changed or deprecated protocol value.

In this project, the dependency on a configured Dart SDK is the boundary between unit and
integration tests. Any test that does not need the Dart SDK belongs in this unit-test phase, even if
it exercises WebSocket code, request/response routing, or asynchronous callbacks. Include all such
SDK-free coverage before the unit-test review pause. Keep SDK-free driver tests in
production-aligned `vmServiceDrivers` packages and keep SDK-backed live-process tests separate from
them.

Use boundary-revealing values where relevant, such as values greater than `Integer.MAX_VALUE` for
microseconds represented as 64-bit values. Do not write tests that merely match generated comments
or formatting. A missing generated API may make the red state a compilation failure; that is
acceptable when unavoidable, but explain it precisely.

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

## Implement and reach green

After all accepted tests are in place, regenerate the audit and validate the fresh bundle before
production edits; test sources are hashed audit inputs, so the pre-test bundle is intentionally
stale. Confirm that the selected candidate span did not change. Apply only that next-version span.
Preserve plugin-owned files, deliberate deviations from generated output, and unrelated local
changes; stop for a user decision when the next-version change overlaps them. Add or update the
necessary driver elements, consumers, RPC overloads, serialization, response routing, and
documentation, then update `versionMajor`/`versionMinor` to the next protocol version.

Run the focused unit tests, any approved integration test, Java compilation, and the existing VM
Service regression test suites. Never change a test merely to make it green. If a test remains red,
tell the user the failing command and symptom, explain whether the cause is production logic, an
incorrect test assumption, generated-code limitations, or the environment, and fix in-scope
production problems. This phase is complete only when the applicable tests are green; otherwise
report the unresolved reason plainly.

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
- Do not update the SDK checkout, commit, push, or open a pull request without explicit permission.
- Do not overwrite generated customizations or unrelated worktree changes.
