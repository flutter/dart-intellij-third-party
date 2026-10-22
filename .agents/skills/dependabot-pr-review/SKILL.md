---
name: dependabot-pr-review
description: Reviews open automated dependency pull requests in the Dart (flutter/dart-intellij-third-party) and Flutter (flutter/flutter-intellij) IntelliJ plugin repositories. Covers Dependabot version bumps and bot-authored Flutter SDK rolls. Lists each PR with its build status, asks the user which ones to approve, then approves them and applies the "autosubmit" label. When Kokoro is the only failing build, offers to re-run it with the "kokoro:run" label.
---

# Dependabot PR Review Skill

This skill triages open automated dependency PRs across the two IntelliJ
plugin repositories. It reports build health for each PR, gets explicit user
confirmation for each one, then approves the confirmed PRs and applies the
`autosubmit` label.

Two kinds of PR are in scope, because they share one lifecycle: opened by
automation, labeled for auto-submit, and stalled when that label is stripped.

| Source | Author | Example title |
| --- | --- | --- |
| `dependabot` | `app/dependabot` | `Bump org.jetbrains.kotlin.jvm from 2.4.10 to 2.4.20` |
| `sdk-roll` | `flutteractionsbot` | `ci: bump pinned Flutter SDK to version 3.47.5` |

SDK rolls appear only in `flutter/flutter-intellij`; the Dart repo has no SDK
roller, so an empty result there is normal, not a failure.

> [!IMPORTANT]
> Never approve or label a PR that the user has not confirmed. The user may
> confirm PRs individually or accept the bulk option covering every PR with a
> passing build, but there is no shortcut that approves failing or pending PRs
> in bulk.

## How the autosubmit Label Works

This skill never merges anything. It approves and labels; the `auto-submit` bot
does the merging. Understanding the label's lifecycle explains what this skill
is really fixing:

1. The opening automation applies `autosubmit` itself at PR creation. For
   Dependabot this is configured under `labels:` in each repo's
   `.github/dependabot.yml`; SDK rolls are labeled by the workflow that files
   them.
2. The `auto-submit` bot tries to merge, and **removes the label** if any
   requirement is unmet, leaving a comment explaining why.
3. The label does not come back on its own. The PR stalls until a human
   resolves the blocker and re-applies it.

So an open automated PR **without** the label has already been rejected once,
and the comment says why. The two common cases:

| Build | Bot's reason | What this skill does |
| --- | --- | --- |
| `PASS` | "At least 1 approving review is required" | Approving and re-labeling unblocks the merge. This is the main win. |
| `FAIL` | A named check failed | Re-labeling is futile; the bot strips it again within minutes. Fix the check first. |

> [!WARNING]
> Re-applying `autosubmit` to a PR with failing checks does not merge it. The
> bot removes the label again and posts another comment. Only approve a failing
> PR when the user has explicitly accepted that the label will likely bounce.

## How the kokoro:run Label Works

Some presubmit builds run on Kokoro rather than GitHub Actions. They report
legacy commit statuses named after the platform, such as `kokoro-mac`, and
link to an internal dashboard.

1. Dependabot applies `kokoro:run` alongside `autosubmit` when it opens a PR
   (see `labels:` in `.github/dependabot.yml`).
2. Kokoro posts a `pending` status, and the `kokoro-team` account **removes
   the label** within seconds. The build result replaces the pending status.
3. Kokoro only builds Dependabot's commits while the label is present.

Re-running a failed Kokoro build, to pick up an upstream fix or get past an
intermittent failure, works differently in each repository:

| Repository | Kokoro reads the label when... | To re-run |
| --- | --- | --- |
| `flutter/flutter-intellij` | it is added | Add `kokoro:run`. |
| `flutter/dart-intellij-third-party` | a commit is pushed | Add `kokoro:run`, then comment `@dependabot recreate`. |

The difference comes from each repository's Kokoro job configuration, which
lives outside these repositories. In the Dart repository, a label added to an
existing PR sits there unused. A recreate makes Dependabot push a new commit,
and Kokoro reads the label at that point. Recreating takes about a minute,
re-runs every check, and discards any manual edits to the branch, which is
fine for a bot PR. It only works on Dependabot PRs, not SDK rolls.

In `flutter-intellij`, a `kokoro:run` label that is still present means a run
is queued. In the Dart repository it means nothing: it may be left over from an
earlier attempt.

A re-run doesn't approve anything, and the PR can't be approved in the same
pass: the auto-submit bot strips `autosubmit` from a PR with a failed check.
Approve it on a later pass, once the new build passes. Stale approvals aren't
dismissed in either repository, so a PR approved earlier stays approved after a
recreate.

---

## Workflow

### Step 1: Choose the Repositories

Default to **both** repositories:

- `flutter/dart-intellij-third-party` (the Dart plugin)
- `flutter/flutter-intellij` (the Flutter plugin)

Only narrow the scope if the user explicitly asks for a single repository, in
which case pass `--repo <owner/repo>` in the next step.

### Step 2: List the PRs and Their Build Status

Run the listing script from the `dart-intellij-third-party` workspace root:

```bash
dart run .agents/skills/dependabot-pr-review/scripts/list_dependabot_prs.dart \
  --output-file "<appDataDir>/brain/<conversation-id>/scratch/dependabot_prs.json"
```

The script is dependency-free, so it runs directly with `dart run` without a
`pub get` step. It prints a markdown table and writes the full JSON payload to
the scratch file.

By default it queries both `app/dependabot` and `flutteractionsbot`. Pass
`--authors <a,b>` to narrow or extend that list, for example
`--authors app/dependabot` to skip SDK rolls. Because `gh pr list` accepts only
one `--author`, the script runs one query per repository **per author**, so the
progress lines and any failure warning name both.

> [!IMPORTANT]
> Check the exit code. `0` means every query succeeded. `1` means at least one
> query failed (expired `gh` auth, network problem, rate limit), and the script
> prints a `WARNING` naming the failed repo and author. In that case the
> listing is **incomplete**: report the failure to the user and stop. Never
> present partial results as "nothing to do". `2` means the arguments were
> invalid.
>
> `1` covers both a total failure, where every query failed, and a partial one,
> where some queries failed and the rest found nothing. The wording differs but
> the response does not: in both cases the listing is incomplete. A `WARNING`
> is only ever printed alongside exit `1`.

Per-PR build status is one of:

| Table | JSON | Meaning |
| --- | --- | --- |
| `PASS` | `passing` | All checks completed successfully (neutral/skipped count as passing). |
| `FAIL` | `failing` | At least one check failed, errored, timed out, or was cancelled. |
| `PENDING` | `pending` | Checks are still running and none have failed. |
| `NO CHECKS` | `noChecks` | No checks are attached to the head commit. |

### Step 3: Present the Results

Show the user the table of PRs. For each PR include the repository, PR number
and link, the dependency being bumped and its version change, the source, the
build status, the current review decision, the mergeable state, and whether the
`autosubmit` label is already present.

Call out anything that needs judgement before presenting the approval question:

- PRs with `FAIL` checks, including the names of the failing checks.
- PRs where Kokoro is the only failing build. The script's footnote marks
  these `only Kokoro failed`. They can be re-run with `kokoro:run` rather than
  needing a fix. If the same bump passed in the other repository, say so: it
  points to an intermittent failure. Other checks on these PRs may still be
  pending, so mention any that are.
- PRs that are still `PENDING`.
- PRs that are drafts, are not `MERGEABLE`, or already carry the `autosubmit`
  label (these usually need no further action).

Identify each PR by its **dependency name** (for example
`org.jetbrains.kotlin.jvm`, or `Flutter SDK` for a roll) rather than the full
PR title. The script parses this out of the title, along with the version
change, and exposes it in the `Dependency` and `Version` table columns and
under `dependency` in the JSON payload. Unrecognized title formats fall back to
a cleaned up title.

The `Source` column (`source` in JSON) distinguishes a `dependabot` library
bump from an `sdk-roll`. Always mention the source for a roll: it repoints the
toolchain every CI job builds against, which is a wider change than a single
library bump even when the build is green. A version with no origin, such as
`-> 3.47.5`, is normal for a roll, whose title names only the target.

### Step 4: Ask the User About Each PR

Use the `ask_question` tool with a **single multi-select question**. It has one
option per PR, so the user can decide individually, plus a bulk option covering
every PR whose build passes. Format each option as the user's own response,
naming the dependency, for example:

- `Approve and label ALL 2 passing PRs — cli_util, org.jetbrains.kotlin.jvm`
- `Approve and label dart-intellij-third-party#665 — org.jetbrains.kotlin.jvm 2.4.10 -> 2.4.20 (build PASSING)`
- `Approve and label flutter-intellij#9131 — Flutter SDK -> 3.47.5 (sdk-roll, build PASSING)`
- `Re-run Kokoro on dart-intellij-third-party#725 — gradle-wrapper 9.7.1 -> 9.8.0 (only kokoro-mac failing; approve on a later pass)`
- `Approve and label flutter-intellij#8123 — actions/checkout 4 -> 5 (build FAILING: verify-plugin, label will likely bounce)`

Guidelines for the question:

- Put the bulk **approve all passing** option first, then the individual
  passing PRs, then `PENDING`, then Kokoro re-runs, then failing ones.
- The bulk option covers only PRs with a `PASS` build. Never offer a bulk
  option that sweeps in failing or pending PRs; those always require an
  individual selection.
- Name the dependencies the bulk option covers and state the count, so the
  user knows exactly what they are agreeing to.
- Omit the bulk option when fewer than two PRs are passing, since it would
  duplicate a single individual option.
- Tag SDK rolls inline as `sdk-roll`, in their individual option and in the
  bulk option's dependency list, for example
  `ALL 3 passing PRs — cli_util, kotlin.jvm, Flutter SDK (sdk-roll)`. A green
  roll is eligible for bulk approval like any other passing PR, but the user
  should never approve one without noticing it is a toolchain change.
- Annotate non-passing PRs inline so risk is visible at the point of decision,
  and note that the bot will strip the label from a failing PR.
- For each PR where `checks.onlyKokoroFailing` is true, offer a **Re-run
  Kokoro** option naming the failing Kokoro checks, in place of its approve
  option. Never include re-runs in a bulk option. In `flutter-intellij`, skip
  the re-run when `hasKokoroRunLabel` is true and say one is already queued. In
  the Dart repository, offer it regardless, but only for Dependabot PRs.
- Exclude PRs that already have the `autosubmit` label, and mention in your
  message that they were skipped because they are already queued.
- If the user asks to be prompted one at a time instead, ask a separate
  yes/no question per PR.

Build the final set of PRs from the union of the selected options, dropping
duplicates. For example, selecting the bulk option *and* an individual failing
PR approves all passing PRs plus that one failing PR. Any PR not covered by a
selected option is left untouched.

### Step 5: Approve, Label, and Re-run

For the selected PRs only, run:

```bash
dart run .agents/skills/dependabot-pr-review/scripts/approve_and_label.dart \
  --pr flutter/dart-intellij-third-party#665 \
  --pr flutter/flutter-intellij#8123 \
  --rerun-kokoro flutter/dart-intellij-third-party#725
```

The script approves each `--pr` PR with `gh pr review --approve` and then
applies the `autosubmit` label with `gh pr edit --add-label`. It skips the
approval step if the current user has already approved the PR, and it
continues past individual failures so one bad PR does not block the rest. Use
`--dry-run` to preview the changes without making them. A dry run still reads
each PR, so its preview matches what a real run would do.

Each `--rerun-kokoro` PR gets `kokoro:run`, plus a `@dependabot recreate`
comment in the Dart repository, as described in
[How the kokoro:run Label Works](#how-the-kokororun-label-works). It isn't
approved. The script refuses a PR passed to both flags, and in the Dart
repository it refuses any PR not opened by Dependabot. If the comment fails
after the label was added, running the same command again posts only the
comment.

Merging is the `auto-submit` bot's job and happens asynchronously, so a
successful run here means "queued", not "merged".

### Step 6: Report Back

Summarize what happened: which PRs were approved and labeled, which had Kokoro
re-run, which were skipped by the user, and any that failed (for example,
because the user cannot approve a PR they authored, or lacks write access to
apply labels). Include links so the user can follow up.

Be precise that labeled PRs are queued rather than merged, and that the bot
merges them only once it is satisfied. For any failing PR that was labeled
anyway, tell the user to expect the bot to remove the label again.

For a re-run, say that the build has been requested, not that it passed. The
script ends by listing re-run PRs as `NOT queued for auto-submit yet`; relay
that plainly. The PR stays open until someone approves and labels it, so tell
the user to run this skill again once the new build finishes, when it will
show up as a passing PR.

---

## Notes

- Requires the GitHub CLI (`gh`) to be installed and authenticated with write
  access to both repositories, plus a Dart SDK on the path.
- If `dart` or `gh` is not found, prefix the command with an explicit `PATH`,
  for example
  `PATH="/opt/homebrew/bin:$HOME/src/repos/flutter/bin:$PATH"`. A sandboxed
  shell often has neither on its default `PATH`.
- Both scripts are standalone Dart files that import only `dart:` libraries, so
  no `pubspec.yaml` or `dart pub get` is needed.
- PRs are identified by author: `app/dependabot` for version bumps and
  `flutteractionsbot` for SDK rolls. Override with `--authors`.
- `approve_and_label.dart` is source-agnostic: it takes `owner/repo#number` and
  treats every PR the same, so no flag is needed to approve a roll.
- The label name is configurable via `--label` on both scripts, but
  `autosubmit` is the correct default for these repositories. The Kokoro
  re-run label, `kokoro:run`, is fixed in both scripts; it exists in both
  repositories.
- The repositories that need `@dependabot recreate` are listed in
  `recreateRepos` in `approve_and_label.dart`. Remove the Dart repository from
  it if its Kokoro job starts honoring labels added later.
- A check counts as Kokoro when its name starts with `kokoro`, ignoring case.
  The JSON payload exposes `checks.onlyKokoroFailing` and `hasKokoroRunLabel`
  for each PR.
- Both scripts support `--help`.

## Bundled Resources

- **`scripts/list_dependabot_prs.dart`**: Lists open automated dependency PRs
  (Dependabot bumps and SDK rolls) with a normalized CI status summary; writes
  JSON and prints a markdown table. Flags PRs where only Kokoro failed.
- **`scripts/approve_and_label.dart`**: Approves the confirmed PRs and applies
  the `autosubmit` label, queueing them for the `auto-submit` bot. With
  `--rerun-kokoro`, re-runs Kokoro instead, without approving.
