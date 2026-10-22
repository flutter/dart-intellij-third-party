# VM Service Protocol-History Selection Contract

Select exactly one protocol revision using direct, read-only inspection. Do not create an audit
script, generated report, schema, manifest, or code-generation step.

## Establish immutable inputs

Record the plugin commit, initial worktree status, and version declared by
`third_party/src/main/java/com/jetbrains/lang/dart/ide/runner/server/vmService/vmServiceDrivers/service/VmService.java`.
For the SDK source, record the checkout or official repository used, its exact target commit, branch,
cleanliness, and the evidence used to judge whether it is current. Do not substitute a moving branch
name for a commit ID in later reasoning.

The authoritative specification path is `runtime/vm/service/service.md`. Read its protocol header,
Public RPCs, Public Types, and Revision History directly at the relevant commits.

## Find the current-version anchor

Inspect the SDK commits that changed `service.md` in chronological order. Locate the transition where
the specification header first became the version currently declared by the plugin. A Git pickaxe
search for the exact protocol-header text can locate candidate transitions, but inspect the commit
and surrounding file history rather than trusting the search result alone.

Also inspect the plugin commit or pull request that introduced its current version. If it identifies
the final SDK specification commit incorporated by that upgrade, use that immutable commit as the
current-version anchor. Otherwise use the first SDK transition into the current version as a
conservative anchor, include later same-version commits as candidates, and compare their behavior
with the existing Java so already implemented changes are not duplicated.

If the current version cannot be anchored in complete SDK history, stop and tell the user what is
missing. Do not infer an anchor from dates alone.

## Select the next stabilized revision

Starting after the anchor, inspect each commit that changed `service.md`:

1. Include same-version commits that follow the anchor; they may contain compatibility or
   documentation changes not represented by a version bump.
2. The first commit whose protocol header differs from the current version establishes the sole
   target version.
3. Include that transition and every consecutive later `service.md` commit that still declares the
   target version.
4. Stop before the first commit that declares a later protocol version.

If there is no later transition, report that no next protocol version is available. If versions are
non-monotonic or relevant history is shallow or missing, stop instead of guessing.

Record the selected commits in order with their full IDs, titles, protocol before/after values, and
revision notes. This ordered list is the scope boundary for the rest of the workflow.

## Derive semantic and Java changes

For each selected commit, inspect both the `service.md` diff and the full post-commit definitions.
List added, changed, removed, and deprecated RPCs, types, fields, enum values, and semantics. The
final target snapshot is authoritative when intermediate commits revise one another.

Search the plugin directly for every affected symbol and inspect neighboring implementations, tests,
and relevant Git history. Build an explicit mapping from protocol changes to Java elements,
consumers, request serialization, response dispatch, and tests. Derive inheritance, nullability,
numeric widths, defaults, overloads, union handling, and callback behavior from the specification
and established repository conventions.

Do not mechanically translate a diff or assume a protocol type maps to one Java file. Preserve
intentional compatibility behavior and historical copyright years in existing files; new files use
the current calendar year. If the protocol is ambiguous or conflicts with existing behavior, stop
for a human decision.

## Revalidate after review pauses

Before production implementation, repeat the initial read-only checks and reconstruct the selected
commit list from the same immutable SDK target. Confirm that the target commit, ordered span, current
plugin version, and relevant pre-existing worktree changes are unchanged. Expected newly added tests
may differ; unrelated changes must be preserved. If the evidence or scope changed, report it and
stop rather than silently selecting a different revision.
