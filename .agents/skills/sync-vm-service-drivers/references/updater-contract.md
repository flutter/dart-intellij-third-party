# Incremental VM Service Driver Updater Contract

The audit bundle is immutable evidence for choosing and implementing one protocol revision. Validate
`report.json` against its bundled schema, verify `manifest.json`, confirm the SDK target identity,
and compare plugin precondition hashes before relying on it. Regenerate stale evidence.

## Select one candidate span

The recorded baseline is a historical generation anchor and may be older than the plugin. Determine
the plugin's current version from `VmService.java`. In `change_candidates`, find the candidate that
first transitions into that version; for the baseline version, use the baseline itself. The first
following candidate whose `protocol_after` differs identifies the sole target version. Select every
candidate after the current-version anchor through the final consecutive candidate whose
`protocol_after` still equals that target version. Stop before the first candidate that transitions
beyond the target version. If the target is the latest recorded version, include all remaining
candidates that continue to declare it.

Include same-version candidates on both sides of the transition into the target. For example, a
documentation or type change made while the specification still says 4.4 belongs to the 4.4-to-4.5
update if it follows the 4.4 anchor, and a compatibility fix that still declares 4.5 also belongs to
that update. This produces the stabilized final state of 4.5 without including the transition to
4.6.

If the current version has no matching entry, the versions are non-monotonic, or there is no later
transition, do not guess. Report that the plugin is up to date or that history is insufficient.

## Use generated output safely

Reconstruct or generate the Java trees at the current-version anchor and final target-version
candidate and compare that incremental delta with the working tree. The report's net
baseline-to-latest readiness is not a substitute for this incremental comparison when its
historical baseline is older than the plugin.

Treat generated additions and changes as the expected API shape, then review them for Java type
safety, nullability, repository conventions, and existing customizations. Preserve plugin-owned
files and intentional deviations. If a selected change overlaps a local customization, stop for a
human decision instead of replacing it.

The candidate span remains fixed through the test-first pauses. Because test and usage sources are
manifest inputs, regenerate and validate a fresh bundle after adding the accepted tests and before
production implementation. Confirm that the new report selects the same span; if the SDK or driver
inputs changed enough to alter it, tell the user and stop rather than silently changing scope.
