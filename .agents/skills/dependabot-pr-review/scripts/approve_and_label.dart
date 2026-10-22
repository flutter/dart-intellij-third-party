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
/// PRs passed with `--rerun-kokoro` are neither approved nor labeled for
/// auto-submit. They get the [kokoroRunLabel], plus a [recreateComment] in
/// [recreateRepos], whose Kokoro job ignores a label added after the PR was
/// opened. Approve them on a later pass, once the new build passes.
///
/// Only run this after the user has explicitly confirmed each PR.
///
/// Usage:
///   dart run approve_and_label.dart [--pr owner/repo#123 ...]
///       [--rerun-kokoro owner/repo#456 ...] [--label autosubmit]
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
typedef PrInfo = ({String author, Set<String> labels});

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
    'author,labels',
  ]);
  if (result.ok) {
    try {
      if (jsonDecode(result.output) case {
        'author': {'login': final String author},
        'labels': final List<dynamic> labels,
      }) {
        return (
          author: author,
          labels: {
            for (final label in labels)
              if (label case {'name': final String name}) name,
          },
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
/// Adds [kokoroRunLabel] unless it is already present, then, for repositories
/// in [recreateRepos], asks Dependabot to recreate the PR. The label must be in
/// place before Dependabot pushes, because that push is when those
/// repositories' Kokoro job reads it. Only Dependabot acts on the comment, so
/// any other author is refused there before anything changes.
///
/// Elsewhere, a label that is still present means a run is already queued:
/// Kokoro removes it as soon as it starts. Re-adding it would fire no new
/// event, so that case is reported and treated as success. When the PR can't
/// be read, the label is added anyway, since re-adding it is harmless.
///
/// Does not approve the PR or add the auto-submit label: while the failed
/// Kokoro status is still attached, the auto-submit bot would strip that label
/// straight away.
bool rerunKokoro(PrRef pr) {
  final ref = describe(pr);
  final info = fetchPrInfo(pr);
  final recreate = needsRecreate(pr);

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
  }

  if (info?.labels.contains(kokoroRunLabel) ?? false) {
    stdout.writeln("$ref: '$kokoroRunLabel' is already applied.");
    if (!recreate) {
      stdout.writeln('$ref: a Kokoro run is already queued.');
      return true;
    }
  } else if (!applyLabel(pr, kokoroRunLabel)) {
    return false;
  }

  if (!recreate || requestRecreate(pr)) return true;
  stderr.writeln(
    "$ref: '$kokoroRunLabel' is on the PR, so re-running with "
    '--rerun-kokoro will only post the comment.',
  );
  return false;
}

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
                                 PRs only). The PR is not approved. Repeat for
                                 multiple PRs.
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

  return (prs: prs, reruns: reruns, label: label, body: body, dryRun: dryRun);
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
    'once the build passes.',
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
  final login = approvals.isEmpty ? null : fetchAuthenticatedLogin();

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
  for (final pr in reruns) {
    if (rerunKokoro(pr)) {
      rerun.add(pr);
    } else {
      failures++;
    }
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
