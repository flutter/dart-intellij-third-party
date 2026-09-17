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

/// Approves pull requests and applies the autosubmit label.
///
/// This does not merge anything. Applying the label only queues the PR for
/// the `auto-submit` bot, which merges it once every requirement is met and
/// strips the label again if any of them fail.
///
/// Only run this after the user has explicitly confirmed each PR.
///
/// Usage:
///   dart run approve_and_label.dart --pr owner/repo#123 [--pr ...]
///       [--label autosubmit] [--body "text"] [--dry-run]
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

/// A pull request to approve, identified by repository and number.
typedef PrRef = ({String repo, int number});

/// The outcome of running a subprocess.
typedef CommandResult = ({bool ok, String output});

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
PrRef parseRef(String ref) {
  final match = _prRefPattern.firstMatch(ref.trim());
  if (match == null) {
    stderr.writeln(
      "Invalid PR reference '$ref'. Expected 'owner/repo#number'.",
    );
    exit(exitBadUsage);
  }
  return (repo: match.group(1)!, number: int.parse(match.group(2)!));
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
/// Fails open, returning false when the lookup itself fails: a redundant
/// approval is harmless, whereas skipping a needed approval is not.
bool alreadyApprovedBy(PrRef pr, String login) {
  final reviews = runCommand('gh', [
    'api',
    // Approvals can be pushed past the first page of 30 by review churn.
    '--paginate',
    'repos/${pr.repo}/pulls/${pr.number}/reviews',
    '--jq',
    '.[] | select(.state == "APPROVED") | .user.login',
  ]);
  if (!reviews.ok) return false;

  return const LineSplitter().convert(reviews.output).contains(login);
}

/// Ensures [pr] carries an approving review from the current user.
///
/// Returns true if the PR was already approved or is now approved.
bool ensureApproved(PrRef pr, String ref, String body, String? login) {
  if (login != null && alreadyApprovedBy(pr, login)) {
    stdout.writeln('$ref: already approved by you, skipping approval.');
    return true;
  }

  final approve = runCommand('gh', [
    'pr',
    'review',
    '${pr.number}',
    '--repo',
    pr.repo,
    '--approve',
    if (body.isNotEmpty) ...['--body', body],
  ]);

  if (!approve.ok) {
    stderr.writeln('$ref: FAILED to approve: ${approve.output}');
    return false;
  }

  stdout.writeln('$ref: approved.');
  return true;
}

/// Adds [label] to [pr], returning whether the edit succeeded.
bool applyLabel(PrRef pr, String ref, String label) {
  final result = runCommand('gh', [
    'pr',
    'edit',
    '${pr.number}',
    '--repo',
    pr.repo,
    '--add-label',
    label,
  ]);

  if (!result.ok) {
    stderr.writeln("$ref: FAILED to add label '$label': ${result.output}");
    return false;
  }

  stdout.writeln("$ref: added label '$label'.");
  return true;
}

/// Approves [pr] and adds [label]. Returns true when the PR is fully queued.
///
/// Labeling is skipped when approval fails. Labeling an unapproved PR would
/// leave it in the exact state the auto-submit bot rejects, causing it to
/// strip the label and comment.
bool process(
  PrRef pr, {
  required String label,
  required String body,
  required bool dryRun,
  required String? login,
}) {
  final ref = '${pr.repo}#${pr.number}';

  if (dryRun) {
    stdout.writeln("[dry-run] Would approve $ref and add label '$label'.");
    return true;
  }

  if (!ensureApproved(pr, ref, body, login)) {
    stderr.writeln('$ref: skipping label; an unapproved PR will not merge.');
    return false;
  }

  return applyLabel(pr, ref, label);
}

const usage = '''
Approves PRs and applies the autosubmit label, queueing them for the
auto-submit bot. This does not merge anything itself.

Usage: dart run approve_and_label.dart --pr owner/repo#123 [options]

Options:
  --pr <owner/repo#n>  PR to approve and label. Repeat for multiple PRs.
  --label <name>       Label to apply after approval. (default: autosubmit)
  --body <text>        Optional review comment body.
  --dry-run            Print the intended actions without calling GitHub.
  -h, --help           Show this help text.

Values may be given as "--flag value" or "--flag=value".

Exit codes:
  0  Every PR was approved and labeled.
  1  The GitHub CLI is unavailable, or at least one PR failed.
  2  Invalid arguments.
''';

/// Parsed command line options.
typedef Options = ({List<String> prs, String label, String body, bool dryRun});

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
  required String label,
  required String body,
  required bool dryRun,
}) {
  if (prs.isEmpty) {
    stderr.writeln('At least one --pr is required.\n');
    stderr.write(usage);
    exit(exitBadUsage);
  }

  if (label.trim().isEmpty) {
    stderr.writeln('--label must not be empty.');
    exit(exitBadUsage);
  }

  return (prs: prs, label: label, body: body, dryRun: dryRun);
}

/// Minimal argument parser; avoids a package:args dependency so the script
/// runs directly with `dart run` and no `pub get`.
///
/// Returns null when help was requested and the caller should exit quietly.
Options? parseArgs(List<String> args) {
  final prs = <String>[];
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

  return validateOptions(prs: prs, label: label, body: body, dryRun: dryRun);
}

void main(List<String> args) {
  final options = parseArgs(args);
  if (options == null) return;

  requireGitHubCli();

  // Parse every ref up front so a malformed one cannot leave a half-applied
  // batch behind.
  final refs = [for (final ref in options.prs) parseRef(ref)];

  final login = options.dryRun ? null : fetchAuthenticatedLogin();

  var failures = 0;
  for (final ref in refs) {
    final ok = process(
      ref,
      label: options.label,
      body: options.body,
      dryRun: options.dryRun,
      login: login,
    );
    if (!ok) failures++;
  }

  stdout.writeln('\nProcessed ${refs.length} PR(s); $failures failure(s).');
  exit(failures > 0 ? exitFailure : 0);
}
