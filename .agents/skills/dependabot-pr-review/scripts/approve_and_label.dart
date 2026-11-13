// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/// Approves pull requests and applies the autosubmit label, or re-runs their
/// Kokoro builds.
///
/// This does not merge anything. Applying the label only queues the PR for
/// the `auto-submit` bot, which merges it once every requirement is met and
/// strips the label again if any of them fail.
///
/// PRs passed with `--rerun-kokoro` get the [kokoroRunLabel], plus a
/// [recreateComment] in [recreateRepos], whose Kokoro job ignores a label
/// added after the PR was opened. Elsewhere, an out-of-date branch is updated
/// first and a stuck label is re-added (see [rerunKokoro]). They are neither
/// approved nor labeled for auto-submit unless `--queue` is passed.
///
/// Only run this after the user has explicitly confirmed each PR.
///
/// Usage:
///   dart run approve_and_label.dart [--pr owner/repo#123 ...]
///       [--rerun-kokoro owner/repo#456 ...] [--queue] [--label autosubmit]
///       [--body "text"] [--dry-run]
///
/// Exits 0 when every PR succeeded, 1 if any failed, and 2 for invalid
/// arguments.
library;

import 'dart:convert';
import 'dart:io';

final _prRefPattern = RegExp(r'^([\w.\-]+/[\w.\-]+)#(\d+)$');

/// Exit code used when one or more PRs could not be processed.
const exitFailure = 1;

/// Exit code used for invalid command line arguments.
const exitBadUsage = 2;

/// The label that asks Kokoro to build a PR.
const kokoroRunLabel = 'kokoro:run';

/// Repositories whose Kokoro job only reads [kokoroRunLabel] when a commit is
/// pushed, not when the label is added.
///
/// For these, a re-run also asks Dependabot to recreate the PR, which pushes a
/// new commit for Kokoro to build. In the other repositories, adding the label
/// starts a run straight away, and Kokoro removes it again. Recreating there
/// would push a commit after the label was used up, which Kokoro would then
/// skip.
///
/// Entries must be lowercase, matching [parseRef].
const recreateRepos = {'flutter/dart-intellij-third-party'};

/// Comment that asks Dependabot to rebuild its branch as a new commit.
const recreateComment = '@dependabot recreate';

/// The login `gh` reports for PRs opened by Dependabot.
const dependabotLogin = 'app/dependabot';

/// Whether actions that change GitHub are only printed, not performed.
///
/// Set once from the command line in [main]. Read-only lookups still run, so a
/// dry run reports what a real run would do for each PR's current state.
late final bool dryRun;

/// A pull request to act on, identified by repository and number.
typedef PrRef = ({String repo, int number});

/// The outcome of running a subprocess.
typedef CommandResult = ({bool ok, String output});

/// The parts of a PR's current state that a Kokoro re-run depends on.
typedef PrInfo = ({
  String state,
  String author,
  Set<String> labels,
  String headRefOid,
  String baseRefName,
});

/// Commit author logins that count as Dependabot's own commits.
///
/// Must match `dependabotCommitLogins` in `list_dependabot_prs.dart`.
const dependabotCommitLogins = {'dependabot[bot]', 'dependabot'};

/// Comment that asks Dependabot to rebase its branch onto the base branch.
///
/// Dependabot refuses it once anyone else has committed to the branch.
const rebaseComment = '@dependabot rebase';

/// How long to wait for a branch update to produce a new head commit.
const headMoveTimeout = Duration(minutes: 10);

/// How long to wait after a push for Kokoro to start on its own, before
/// adding [kokoroRunLabel] to start it explicitly.
///
/// In `flutter/flutter-intellij`, a push made while the label is present
/// starts a build, so a leftover label may already have done the job.
const pushTriggerGrace = Duration(seconds: 60);

/// How long a [kokoroRunLabel] may sit without a Kokoro status before it is
/// treated as stuck. Must match `kokoroLabelStuckAfter` in the listing script.
const kokoroLabelStuckAfter = Duration(minutes: 15);

/// Runs [executable] with [args], capturing stdout and stderr.
///
/// A missing executable is reported as a failed result rather than thrown, so
/// callers never surface a raw stack trace.
CommandResult runCommand(String executable, List<String> args) {
  final ProcessResult result;
  try {
    result = Process.runSync(executable, args);
  } on ProcessException catch (e) {
    return (ok: false, output: 'Failed to run $executable: ${e.message}');
  }

  final stdoutText = (result.stdout as String).trim();
  final stderrText = (result.stderr as String).trim();
  if (result.exitCode == 0) {
    return (ok: true, output: stdoutText);
  }
  return (ok: false, output: stderrText.isNotEmpty ? stderrText : stdoutText);
}

/// Exits with a friendly message when the GitHub CLI is unavailable.
///
/// Probes `gh` directly rather than shelling out to `which`, which does not
/// exist on Windows and would itself throw when missing.
void requireGitHubCli() {
  try {
    Process.runSync('gh', ['--version']);
  } on ProcessException {
    stderr.writeln("Error: GitHub CLI ('gh') is not installed or not in PATH.");
    exit(exitFailure);
  }
}

/// Parses an `owner/repo#number` reference, exiting on malformed input.
///
/// The repository is lowercased, because GitHub names are case-insensitive
/// and the result is compared against [recreateRepos] and other refs.
PrRef parseRef(String ref) {
  final match = _prRefPattern.firstMatch(ref.trim());
  if (match == null) {
    stderr.writeln(
      "Invalid PR reference '$ref'. Expected 'owner/repo#number'.",
    );
    exit(exitBadUsage);
  }
  return (
    repo: match.group(1)!.toLowerCase(),
    number: int.parse(match.group(2)!),
  );
}

/// The `owner/repo#number` form of [pr], used in log lines.
String describe(PrRef pr) => '${pr.repo}#${pr.number}';

/// Whether re-running Kokoro on [pr] needs a recreated commit.
bool needsRecreate(PrRef pr) => recreateRepos.contains(pr.repo);

/// Runs a `gh` command that changes [pr], or only prints it under [dryRun].
///
/// [action] describes the change for log lines, for example
/// `add label 'autosubmit'`. Returns whether the change succeeded.
bool mutate(PrRef pr, String action, List<String> ghArgs) {
  final ref = describe(pr);
  if (dryRun) {
    stdout.writeln('[dry-run] $ref: would $action.');
    return true;
  }

  final result = runCommand('gh', ghArgs);
  if (!result.ok) {
    stderr.writeln('$ref: FAILED to $action: ${result.output}');
    return false;
  }

  stdout.writeln('$ref: did $action.');
  return true;
}

/// The authenticated GitHub login, or null when it cannot be determined.
///
/// Resolved once per run: the value cannot change mid-run, and querying it
/// per PR would spawn a subprocess and a network round trip each time.
String? fetchAuthenticatedLogin() {
  final result = runCommand('gh', ['api', 'user', '--jq', '.login']);
  if (!result.ok || result.output.isEmpty) {
    stderr.writeln(
      'Warning: could not determine the authenticated user '
      '(${result.output}). Existing approvals will not be detected.',
    );
    return null;
  }
  return result.output;
}

/// Whether [login] has already approved [pr].
///
/// Only each reviewer's current state counts. The full review history would
/// report a stale `APPROVED` even after it was dismissed or superseded by a
/// later `CHANGES_REQUESTED`, which would skip an approval the PR still needs.
///
/// Fails open, returning false when the lookup itself fails: a redundant
/// approval is harmless, whereas skipping a needed approval is not.
bool alreadyApprovedBy(PrRef pr, String login) {
  final reviews = runCommand('gh', [
    'pr',
    'view',
    '${pr.number}',
    '--repo',
    pr.repo,
    '--json',
    'latestReviews',
    '--jq',
    '.latestReviews[] | select(.state == "APPROVED") | .author.login',
  ]);
  if (!reviews.ok) return false;

  return const LineSplitter().convert(reviews.output).contains(login);
}

/// The author and labels of [pr], or null with a warning when the lookup
/// fails.
PrInfo? fetchPrInfo(PrRef pr) {
  final result = runCommand('gh', [
    'pr',
    'view',
    '${pr.number}',
    '--repo',
    pr.repo,
    '--json',
    'state,author,labels,headRefOid,baseRefName',
  ]);
  if (result.ok) {
    try {
      if (jsonDecode(result.output) case {
        'state': final String state,
        'author': {'login': final String author},
        'labels': final List<dynamic> labels,
        'headRefOid': final String headRefOid,
        'baseRefName': final String baseRefName,
      }) {
        return (
          state: state,
          author: author,
          labels: {
            for (final label in labels)
              if (label case {'name': final String name}) name,
          },
          headRefOid: headRefOid,
          baseRefName: baseRefName,
        );
      }
    } on FormatException {
      // Fall through to the warning below.
    }
  }

  stderr.writeln(
    'Warning: ${describe(pr)}: could not read the author and labels '
    '(${result.output}).',
  );
  return null;
}

/// Ensures [pr] carries an approving review from the current user.
///
/// Returns true if the PR was already approved or is now approved.
bool ensureApproved(PrRef pr, String body, String? login) {
  if (login != null && alreadyApprovedBy(pr, login)) {
    stdout.writeln('${describe(pr)}: already approved by you, skipping.');
    return true;
  }

  return mutate(pr, 'approve', [
    'pr',
    'review',
    '${pr.number}',
    '--repo',
    pr.repo,
    '--approve',
    if (body.isNotEmpty) ...['--body', body],
  ]);
}

/// Adds [label] to [pr], returning whether the edit succeeded.
bool applyLabel(PrRef pr, String label) => mutate(pr, "add label '$label'", [
  'pr',
  'edit',
  '${pr.number}',
  '--repo',
  pr.repo,
  '--add-label',
  label,
]);

/// Posts [recreateComment] on [pr], returning whether it succeeded.
bool requestRecreate(PrRef pr) => mutate(pr, "comment '$recreateComment'", [
  'pr',
  'comment',
  '${pr.number}',
  '--repo',
  pr.repo,
  '--body',
  recreateComment,
]);

/// Removes [label] from [pr], returning whether the edit succeeded.
bool removeLabel(PrRef pr, String label) => mutate(
  pr,
  "remove label '$label'",
  ['pr', 'edit', '${pr.number}', '--repo', pr.repo, '--remove-label', label],
);

/// How many commits [info]'s head is behind its base, or null on failure.
int? behindBy(PrRef pr, PrInfo info) {
  final result = runCommand('gh', [
    'api',
    'repos/${pr.repo}/compare/${info.baseRefName}...${info.headRefOid}',
    '--jq',
    '.behind_by',
  ]);
  return result.ok ? int.tryParse(result.output) : null;
}

/// Whether anyone other than Dependabot committed to [pr]'s branch.
///
/// Fails closed, returning true when the lookup fails: then a manual update
/// is used, which works either way, instead of a rebase comment that
/// Dependabot might refuse.
bool hasNonBotCommits(PrRef pr) {
  final result = runCommand('gh', [
    'api',
    '--paginate',
    'repos/${pr.repo}/pulls/${pr.number}/commits',
    '--jq',
    '.[] | (.author.login // .commit.author.name)',
  ]);
  if (!result.ok) return true;
  return const LineSplitter()
      .convert(result.output)
      .any((login) => !dependabotCommitLogins.contains(login));
}

/// The newest Kokoro status `(state, createdAt)` on [sha], or null.
({String state, DateTime createdAt})? newestKokoroStatus(PrRef pr, String sha) {
  final result = runCommand('gh', [
    'api',
    'repos/${pr.repo}/commits/$sha/statuses',
    '--jq',
    '[.[] | select(.context | ascii_downcase | startswith("kokoro"))][0]'
        ' | if . == null then "" else "\\(.state) \\(.created_at)" end',
  ]);
  if (!result.ok || result.output.isEmpty) return null;
  final parts = result.output.split(' ');
  final createdAt = parts.length == 2 ? DateTime.tryParse(parts[1]) : null;
  return createdAt == null ? null : (state: parts[0], createdAt: createdAt);
}

/// When [kokoroRunLabel] was last added to [pr], or null.
DateTime? kokoroLabelAddedAt(PrRef pr) {
  final result = runCommand('gh', [
    'api',
    '--paginate',
    'repos/${pr.repo}/issues/${pr.number}/timeline',
    '--jq',
    '.[] | select(.event == "labeled" and .label.name == "$kokoroRunLabel")'
        ' | .created_at',
  ]);
  final stamps = const LineSplitter().convert(result.ok ? result.output : '');
  return stamps.isEmpty ? null : DateTime.tryParse(stamps.last);
}

/// Brings [pr]'s branch up to date with its base and returns the new head
/// SHA, or null on failure or timeout.
///
/// Uses [rebaseComment] when only Dependabot has committed, which keeps
/// Dependabot managing the PR. Otherwise Dependabot would refuse to rebase,
/// so it merges the base in with `gh pr update-branch` instead. That works at
/// once and keeps existing approvals, but Dependabot stops managing the PR.
/// (`@dependabot recreate` is the other way out, but it can take a long time
/// to act, or not act at all.)
String? updateBranch(PrRef pr, PrInfo info) {
  final ref = describe(pr);
  final manual = hasNonBotCommits(pr);
  final ok = manual
      ? mutate(pr, 'update the branch (merge ${info.baseRefName})', [
          'pr',
          'update-branch',
          '${pr.number}',
          '--repo',
          pr.repo,
        ])
      : mutate(pr, "comment '$rebaseComment'", [
          'pr',
          'comment',
          '${pr.number}',
          '--repo',
          pr.repo,
          '--body',
          rebaseComment,
        ]);
  if (!ok) return null;
  if (dryRun) return '<new head>';

  stdout.writeln(
    '$ref: waiting up to ${headMoveTimeout.inMinutes} minutes for the new '
    'head...',
  );
  final deadline = DateTime.now().add(headMoveTimeout);
  while (DateTime.now().isBefore(deadline)) {
    final head = fetchPrInfo(pr)?.headRefOid;
    if (head != null && head != info.headRefOid) {
      stdout.writeln('$ref: branch updated, new head ${head.substring(0, 8)}.');
      return head;
    }
    sleep(const Duration(seconds: 15));
  }
  stderr.writeln(
    '$ref: FAILED: the head did not move within '
    '${headMoveTimeout.inMinutes} minutes. Run --rerun-kokoro again once '
    'Dependabot has rebased the branch.',
  );
  return null;
}

/// Starts Kokoro on [headSha] unless a build is already running there.
///
/// A [kokoroRunLabel] that is already present is removed first, since
/// re-adding a present label fires no event.
bool startKokoro(PrRef pr, String headSha, {required bool labelPresent}) {
  final ref = describe(pr);
  if (newestKokoroStatus(pr, headSha)?.state == 'pending') {
    stdout.writeln('$ref: Kokoro is already running on the head.');
    return true;
  }
  if (labelPresent && !removeLabel(pr, kokoroRunLabel)) return false;
  return applyLabel(pr, kokoroRunLabel);
}

/// Approves [pr] and adds [label]. Returns true when the PR is fully queued.
///
/// Labeling is skipped when approval fails. Labeling an unapproved PR would
/// leave it in the exact state the auto-submit bot rejects, causing it to
/// strip the label and comment.
bool approveAndLabel(
  PrRef pr, {
  required String label,
  required String body,
  required String? login,
}) {
  if (!ensureApproved(pr, body, login)) {
    stderr.writeln(
      '${describe(pr)}: skipping label; an unapproved PR will not merge.',
    );
    return false;
  }

  return applyLabel(pr, label);
}

/// Asks Kokoro to build [pr] again.
///
/// In [recreateRepos], adds [kokoroRunLabel] unless it is already present,
/// then asks Dependabot to recreate the PR. The label must be in place before
/// Dependabot pushes, because that push is when those repositories' Kokoro
/// job reads it. Only Dependabot acts on the comment, so any other author is
/// refused there before anything changes.
///
/// Elsewhere (in `flutter/flutter-intellij`), Kokoro starts when the label is
/// added, and also on a push made while the label is present:
/// - If the branch is behind its base, it is updated first (see
///   [updateBranch]). Re-running Kokoro on an out-of-date branch would only
///   rebuild code that predates any fix on the base branch. If the push
///   itself does not start Kokoro within [pushTriggerGrace], the label is
///   added.
/// - Otherwise, a label that is present and fresh means a run is queued.
///   One older than [kokoroLabelStuckAfter] with no Kokoro status since is
///   stuck: it was added while a build was running, which Kokoro ignores. It
///   is removed and added again.
/// - Otherwise the label is added.
///
/// Does not approve the PR or add the auto-submit label; see [queueAfterRerun]
/// for that.
bool rerunKokoro(PrRef pr) {
  final ref = describe(pr);
  final info = fetchPrInfo(pr);
  final recreate = needsRecreate(pr);

  if (info != null && info.state != 'OPEN') {
    stderr.writeln('$ref: FAILED: the PR is ${info.state.toLowerCase()}.');
    return false;
  }

  if (recreate) {
    if (info == null) {
      stderr.writeln(
        "$ref: FAILED: could not read the PR to confirm it's from Dependabot.",
      );
      return false;
    }
    if (info.author != dependabotLogin) {
      stderr.writeln(
        '$ref: FAILED: re-running Kokoro here needs a Dependabot PR '
        '(author: ${info.author}).',
      );
      return false;
    }
    if (info.labels.contains(kokoroRunLabel)) {
      stdout.writeln("$ref: '$kokoroRunLabel' is already applied.");
    } else if (!applyLabel(pr, kokoroRunLabel)) {
      return false;
    }
    if (requestRecreate(pr)) return true;
    stderr.writeln(
      "$ref: '$kokoroRunLabel' is on the PR, so re-running with "
      '--rerun-kokoro will only post the comment.',
    );
    return false;
  }

  if (info == null) {
    // Can't check freshness or the branch; adding the label is harmless.
    return applyLabel(pr, kokoroRunLabel);
  }
  final labelPresent = info.labels.contains(kokoroRunLabel);

  if ((behindBy(pr, info) ?? 0) > 0) {
    stdout.writeln(
      '$ref: the branch is behind ${info.baseRefName}; updating it first.',
    );
    final head = updateBranch(pr, info);
    if (head == null) return false;
    if (dryRun) {
      stdout.writeln(
        "[dry-run] $ref: would add '$kokoroRunLabel' if the push does not "
        'start Kokoro.',
      );
      return true;
    }
    stdout.writeln(
      '$ref: waiting ${pushTriggerGrace.inSeconds}s to see whether the push '
      'starts Kokoro by itself...',
    );
    sleep(pushTriggerGrace);
    final stillPresent =
        fetchPrInfo(pr)?.labels.contains(kokoroRunLabel) ?? labelPresent;
    return startKokoro(pr, head, labelPresent: stillPresent);
  }

  if (labelPresent) {
    final addedAt = kokoroLabelAddedAt(pr);
    final statusAt = newestKokoroStatus(pr, info.headRefOid)?.createdAt;
    final stuck =
        addedAt != null &&
        (statusAt == null || statusAt.isBefore(addedAt)) &&
        DateTime.now().toUtc().difference(addedAt) > kokoroLabelStuckAfter;
    if (!stuck) {
      stdout.writeln("$ref: '$kokoroRunLabel' is already applied.");
      stdout.writeln('$ref: a Kokoro run is already queued.');
      return true;
    }
    stdout.writeln("$ref: '$kokoroRunLabel' looks stuck; re-adding it.");
  }
  return startKokoro(pr, info.headRefOid, labelPresent: labelPresent);
}

/// After a successful re-run, approves [pr] if needed and adds [label], so
/// the auto-submit bot merges it once the new build passes.
///
/// Only safe once the failed Kokoro status is no longer on the head, which
/// is true after a branch update or recreate, but not after a plain label
/// re-run on the same commit: then the bot would strip [label] straight
/// away. Whether the bot also strips it while a build is still pending is
/// unverified, so the caller warns that the label may bounce.
bool queueAfterRerun(
  PrRef pr, {
  required String label,
  required String body,
  required String? login,
}) => approveAndLabel(pr, label: label, body: body, login: login);

final usage =
    '''
Approves PRs and applies the autosubmit label, queueing them for the
auto-submit bot, and re-runs Kokoro builds. This does not merge anything
itself.

Usage: dart run approve_and_label.dart [--pr owner/repo#123]
           [--rerun-kokoro owner/repo#456] [options]

Options:
  --pr <owner/repo#n>            PR to approve and label. Repeat for multiple
                                 PRs.
  --rerun-kokoro <owner/repo#n>  PR whose Kokoro builds should run again.
                                 Adds '$kokoroRunLabel', and in
                                 ${recreateRepos.join(', ')}
                                 also comments '$recreateComment' (Dependabot
                                 PRs only). Elsewhere, updates an out-of-date
                                 branch first and re-adds a stuck label. The
                                 PR is not approved unless --queue is given.
                                 Repeat for multiple PRs.
  --queue                        With --rerun-kokoro: also approve (if needed)
                                 and add the label, so the PR merges once the
                                 new build passes. It may bounce if the bot
                                 rejects a pending build (unverified).
  --label <name>                 Label to apply after approval.
                                 (default: autosubmit)
  --body <text>                  Optional review comment body.
  --dry-run                      Print the changes a real run would make
                                 without making them. Still reads PR state.
  -h, --help                     Show this help text.

At least one --pr or --rerun-kokoro is required, and a PR may not be passed
to both.

Values may be given as "--flag value" or "--flag=value".

Exit codes:
  0  Every PR was processed successfully.
  1  The GitHub CLI is unavailable, or at least one PR failed.
  2  Invalid arguments.
''';

/// Parsed command line options.
typedef Options = ({
  List<String> prs,
  List<String> reruns,
  String label,
  String body,
  bool dryRun,
  bool queue,
});

/// Splits `--flag=value` into its parts. The value is null for a bare flag.
(String, String?) splitFlag(String arg) => switch (arg.indexOf('=')) {
  final index when index > 0 => (
    arg.substring(0, index),
    arg.substring(index + 1),
  ),
  _ => (arg, null),
};

/// Validates parsed values, exiting with usage on any problem.
Options validateOptions({
  required List<String> prs,
  required List<String> reruns,
  required String label,
  required String body,
  required bool dryRun,
  required bool queue,
}) {
  if (prs.isEmpty && reruns.isEmpty) {
    stderr.writeln('At least one --pr or --rerun-kokoro is required.\n');
    stderr.write(usage);
    exit(exitBadUsage);
  }

  if (label.trim().isEmpty) {
    stderr.writeln('--label must not be empty.');
    exit(exitBadUsage);
  }

  if (queue && reruns.isEmpty) {
    stderr.writeln('--queue only applies to --rerun-kokoro PRs.');
    exit(exitBadUsage);
  }

  return (
    prs: prs,
    reruns: reruns,
    label: label,
    body: body,
    dryRun: dryRun,
    queue: queue,
  );
}

/// Minimal argument parser; avoids a package:args dependency so the script
/// runs directly with `dart run` and no `pub get`.
///
/// Returns null when help was requested and the caller should exit quietly.
Options? parseArgs(List<String> args) {
  final prs = <String>[];
  final reruns = <String>[];
  var label = 'autosubmit';
  var body = '';
  var dryRun = false;
  var queue = false;

  for (var i = 0; i < args.length; i++) {
    final (flag, inlineValue) = splitFlag(args[i]);

    String nextValue() {
      if (inlineValue != null) return inlineValue;
      if (i + 1 >= args.length) {
        stderr.writeln('Missing value for $flag');
        exit(exitBadUsage);
      }
      return args[++i];
    }

    switch (flag) {
      case '-h' || '--help':
        stdout.write(usage);
        return null;
      case '--dry-run':
        dryRun = true;
      case '--queue':
        queue = true;
      case '--pr':
        prs.add(nextValue());
      case '--rerun-kokoro':
        reruns.add(nextValue());
      case '--label':
        label = nextValue();
      case '--body':
        body = nextValue();
      case final unknown:
        stderr.writeln('Unknown argument: $unknown\n');
        stderr.write(usage);
        exit(exitBadUsage);
    }
  }

  return validateOptions(
    prs: prs,
    reruns: reruns,
    label: label,
    body: body,
    dryRun: dryRun,
    queue: queue,
  );
}

/// Exits when a PR was passed to both `--pr` and `--rerun-kokoro`.
///
/// A PR still showing a failed Kokoro build cannot be queued for auto-submit:
/// the bot would strip the label at once. Refuse the combination rather than
/// guess which action was meant.
void refuseOverlap(Set<PrRef> approvals, Set<PrRef> reruns) {
  final both = approvals.intersection(reruns);
  if (both.isEmpty) return;

  stderr.writeln(
    'These PRs were passed to both --pr and --rerun-kokoro: '
    '${both.map(describe).join(', ')}. Re-run Kokoro first, then approve '
    'once the build passes, or use --rerun-kokoro with --queue.',
  );
  exit(exitBadUsage);
}

void main(List<String> args) {
  final options = parseArgs(args);
  if (options == null) return;
  dryRun = options.dryRun;

  // Parse every ref up front so a malformed one cannot leave a half-applied
  // batch behind.
  final approvals = {for (final ref in options.prs) parseRef(ref)};
  final reruns = {for (final ref in options.reruns) parseRef(ref)};
  refuseOverlap(approvals, reruns);

  requireGitHubCli();
  final login = approvals.isEmpty && !options.queue
      ? null
      : fetchAuthenticatedLogin();

  var failures = 0;
  for (final pr in approvals) {
    final ok = approveAndLabel(
      pr,
      label: options.label,
      body: options.body,
      login: login,
    );
    if (!ok) failures++;
  }
  final rerun = <PrRef>[];
  final queued = <PrRef>[];
  for (final pr in reruns) {
    if (!rerunKokoro(pr)) {
      failures++;
      continue;
    }
    if (options.queue &&
        queueAfterRerun(
          pr,
          label: options.label,
          body: options.body,
          login: login,
        )) {
      queued.add(pr);
    } else {
      rerun.add(pr);
    }
  }
  if (queued.isNotEmpty) {
    stdout.writeln(
      "\nRe-run and queued with '${options.label}': "
      '${queued.map(describe).join(', ')}. If the new build fails, the '
      'auto-submit bot will remove the label again.',
    );
  }

  final total = approvals.length + reruns.length;
  stdout.writeln('\nProcessed $total PR(s); $failures failure(s).');
  remindToApprove(rerun, label: options.label);
  exit(failures > 0 ? exitFailure : 0);
}

/// Reminds the user that [rerun] PRs still need approving and [label]ing.
///
/// A re-run deliberately leaves both for later, so without this the PR sits
/// open after its new build passes.
void remindToApprove(List<PrRef> rerun, {required String label}) {
  if (rerun.isEmpty) return;
  stdout.writeln(
    '\nNOT queued for auto-submit yet: ${rerun.map(describe).join(', ')}.\n'
    'Once the new Kokoro build passes, run this again with --pr to approve '
    "and add '$label' (or re-run the dependabot-pr-review skill).",
  );
}
