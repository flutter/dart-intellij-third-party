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

/// Lists open Dependabot pull requests and summarizes their CI status.
///
/// Emits a human readable markdown table on stdout and, optionally, a JSON
/// payload for downstream processing.
///
/// Usage:
///   dart run list_dependabot_prs.dart [--repo both] [--label autosubmit]
///       [--limit 50] [--output-file <path>]
///
/// Exits 0 on success, 1 if any repository query failed, and 2 for invalid
/// arguments. A failed query is never reported as "no PRs found".
library;

import 'dart:convert';
import 'dart:io';

const defaultRepos = <String>[
  'flutter/dart-intellij-third-party',
  'flutter/flutter-intellij',
];

/// The `--json` fields requested from `gh pr list`.
const prFields =
    'number,title,url,author,createdAt,updatedAt,headRefName,isDraft,'
    'mergeable,reviewDecision,labels,statusCheckRollup';

const failingConclusions = <String>{
  'FAILURE',
  'TIMED_OUT',
  'CANCELLED',
  'ACTION_REQUIRED',
  'STARTUP_FAILURE',
  'STALE',
};

const passingConclusions = <String>{'SUCCESS', 'NEUTRAL', 'SKIPPED'};

/// Stand-in name for a PR whose title cannot be parsed or is missing.
///
/// Never render an empty dependency name: the name identifies the PR in the
/// approval prompt, and a blank entry would ask the user to approve nothing.
const unknownDependency = '(unknown dependency)';

/// Exit code used when one or more repository queries fail.
const exitQueryFailed = 1;

/// Exit code used for invalid command line arguments.
const exitBadUsage = 2;

/// Overall CI state of a pull request.
enum BuildStatus {
  passing('PASS'),
  failing('FAIL'),
  pending('PENDING'),
  noChecks('NO CHECKS');

  const BuildStatus(this.label);

  /// Short label used in the summary table.
  final String label;
}

/// Which bucket a single CI check falls into.
enum _CheckBucket { passing, failing, pending }

/// A single CI check attached to a pull request's head commit.
final class Check {
  Check({required this.name, required this.state, required this.url});

  final String name;
  final String state;
  final String url;

  Map<String, Object?> toJson() => {'name': name, 'state': state, 'url': url};
}

/// Aggregated CI results for a pull request.
final class ChecksSummary {
  ChecksSummary({
    required this.passing,
    required this.failing,
    required this.pending,
  });

  final List<Check> passing;
  final List<Check> failing;
  final List<Check> pending;

  BuildStatus get status {
    if (failing.isNotEmpty) return BuildStatus.failing;
    if (pending.isNotEmpty) return BuildStatus.pending;
    if (passing.isNotEmpty) return BuildStatus.passing;
    return BuildStatus.noChecks;
  }

  Map<String, Object?> toJson() => {
    'status': status.name,
    'passingCount': passing.length,
    'failingCount': failing.length,
    'pendingCount': pending.length,
    'failingChecks': failing.map((c) => c.toJson()).toList(),
    'pendingChecks': pending.map((c) => c.toJson()).toList(),
  };
}

/// The dependency a Dependabot PR updates, parsed from the PR title.
final class DependencyBump {
  /// Normalizes [name], substituting [unknownDependency] when it is blank.
  DependencyBump({required String name, this.fromVersion, this.toVersion})
    : name = name.trim().isEmpty ? unknownDependency : name.trim();

  /// Matches titles like:
  ///   Bump org.jetbrains.kotlin.jvm from 2.4.10 to 2.4.20 in /third_party
  ///   Bump actions/checkout from 4 to 5
  ///   [Security] Update `foo` requirement from ^1.0.0 to ^2.0.0
  static final _versioned = RegExp(
    r'^(?:\[Security\]\s*)?'
    r'(?:chore\(deps[^)]*\):\s*)?'
    r'(?:bump|update)\s+'
    r'(?<name>\S+)\s+'
    r'(?:requirement\s+)?'
    r'from\s+(?<from>\S+)\s+to\s+(?<to>\S+?)'
    r'(?:\s+in\s+\S+)?$',
    caseSensitive: false,
  );

  /// Matches grouped updates like:
  ///   Bump the dev-dependencies group with 3 updates
  ///   Bump the actions group in /.github with 2 updates
  static final _group = RegExp(
    r'^(?:chore\(deps[^)]*\):\s*)?'
    r'(?:bump|update)\s+(?:the\s+)?(?<name>\S+)\s+group\b.*$',
    caseSensitive: false,
  );

  /// Trailing `in /some/path` qualifier, stripped from fallback names.
  static final _inPathSuffix = RegExp(r'\s+in\s+\S+$');

  /// Extracts the bumped dependency from a Dependabot PR [title].
  ///
  /// Falls back to a lightly cleaned up title when the format is unrecognized,
  /// so an unusual title still shows something meaningful.
  factory DependencyBump.parse(String title) {
    final trimmed = title.trim();

    if (_versioned.firstMatch(trimmed) case final match?) {
      return DependencyBump(
        name: _unquote(match.namedGroup('name')!),
        fromVersion: match.namedGroup('from'),
        toVersion: match.namedGroup('to'),
      );
    }

    if (_group.firstMatch(trimmed) case final match?) {
      return DependencyBump(
        name: '${_unquote(match.namedGroup('name')!)} group',
      );
    }

    return DependencyBump(name: trimmed.replaceFirst(_inPathSuffix, ''));
  }

  /// Strips backticks and quotes that some titles wrap around the name.
  static String _unquote(String value) =>
      value.replaceAll(RegExp('^[`\'"]+|[`\'"]+\$'), '');

  final String name;
  final String? fromVersion;
  final String? toVersion;

  /// A compact `from -> to` description, or `'-'` when either version is
  /// unknown (for example, for grouped updates).
  String get versionChange => switch ((fromVersion, toVersion)) {
    (final from?, final to?) => '$from -> $to',
    _ => '-',
  };

  Map<String, Object?> toJson() => {
    'name': name,
    'fromVersion': fromVersion,
    'toVersion': toVersion,
  };
}

/// An open Dependabot pull request and its review state.
final class PullRequest {
  PullRequest({
    required this.repo,
    required this.number,
    required this.title,
    required this.url,
    required this.author,
    required this.createdAt,
    required this.updatedAt,
    required this.headRefName,
    required this.isDraft,
    required this.mergeable,
    required this.reviewDecision,
    required this.labels,
    required this.hasLabel,
    required this.checks,
  }) : dependency = DependencyBump.parse(title);

  /// Builds a [PullRequest] from one `gh pr list --json` entry.
  ///
  /// Every field is defensively defaulted: `gh` omits nulls, and the schema
  /// can drift between CLI versions.
  factory PullRequest.fromJson(
    Map<String, Object?> json, {
    required String repo,
    required String label,
  }) {
    final labels = [
      for (final entry in (json['labels'] as List<Object?>? ?? const []))
        if (entry is Map<String, Object?> && entry['name'] != null)
          entry['name'].toString(),
    ];

    return PullRequest(
      repo: repo,
      number: (json['number'] as num?)?.toInt() ?? 0,
      title: (json['title'] ?? '').toString(),
      url: (json['url'] ?? '').toString(),
      author: ((json['author'] as Map<String, Object?>?)?['login'] ?? 'unknown')
          .toString(),
      createdAt: (json['createdAt'] ?? '').toString(),
      updatedAt: (json['updatedAt'] ?? '').toString(),
      headRefName: (json['headRefName'] ?? '').toString(),
      isDraft: json['isDraft'] == true,
      mergeable: (json['mergeable'] ?? 'UNKNOWN').toString(),
      reviewDecision: switch (json['reviewDecision']) {
        final String decision when decision.isNotEmpty => decision,
        _ => 'REVIEW_REQUIRED',
      },
      labels: labels,
      hasLabel: labels.contains(label),
      checks: summarizeChecks(json['statusCheckRollup'] as List<Object?>?),
    );
  }

  final String repo;
  final int number;
  final String title;
  final String url;
  final String author;
  final String createdAt;
  final String updatedAt;
  final String headRefName;
  final bool isDraft;
  final String mergeable;
  final String reviewDecision;
  final List<String> labels;

  /// Whether the PR already carries the label being queried for.
  final bool hasLabel;

  final ChecksSummary checks;

  /// The dependency being bumped, parsed from [title].
  final DependencyBump dependency;

  /// The `owner/repo#number` reference accepted by `approve_and_label.dart`.
  String get ref => '$repo#$number';

  Map<String, Object?> toJson() => {
    'repo': repo,
    'number': number,
    'title': title,
    'dependency': dependency.toJson(),
    'url': url,
    'author': author,
    'createdAt': createdAt,
    'updatedAt': updatedAt,
    'headRefName': headRefName,
    'isDraft': isDraft,
    'mergeable': mergeable,
    'reviewDecision': reviewDecision,
    'labels': labels,
    'hasLabel': hasLabel,
    'checks': checks.toJson(),
  };
}

/// Runs [executable] with [args], returning stdout, or null on failure.
///
/// Returns null rather than throwing when the executable is missing, so
/// callers can report a friendly message instead of a stack trace.
String? runCommand(String executable, List<String> args) {
  final ProcessResult result;
  try {
    result = Process.runSync(executable, args);
  } on ProcessException catch (e) {
    stderr.writeln('Failed to run $executable: ${e.message}');
    return null;
  }

  if (result.exitCode != 0) {
    stderr.writeln(
      'Error running $executable ${args.join(' ')}: ${result.stderr}',
    );
    return null;
  }
  return (result.stdout as String).trim();
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
    exit(exitQueryFailed);
  }
}

/// Classifies a GitHub Actions `CheckRun` rollup entry.
({_CheckBucket bucket, String state}) _classifyCheckRun(
  Map<String, Object?> entry,
) {
  final status = (entry['status'] ?? '').toString().toUpperCase();
  final conclusion = (entry['conclusion'] ?? '').toString().toUpperCase();

  final bucket = switch (conclusion) {
    _ when status != 'COMPLETED' || conclusion.isEmpty => _CheckBucket.pending,
    _ when failingConclusions.contains(conclusion) => _CheckBucket.failing,
    _ when passingConclusions.contains(conclusion) => _CheckBucket.passing,
    _ => _CheckBucket.pending,
  };

  return (bucket: bucket, state: conclusion.isEmpty ? status : conclusion);
}

/// Classifies a legacy `StatusContext` rollup entry.
({_CheckBucket bucket, String state}) _classifyStatusContext(
  Map<String, Object?> entry,
) {
  final state = (entry['state'] ?? '').toString().toUpperCase();

  return (
    bucket: switch (state) {
      'FAILURE' || 'ERROR' => _CheckBucket.failing,
      'SUCCESS' => _CheckBucket.passing,
      _ => _CheckBucket.pending,
    },
    state: state,
  );
}

/// Reduces a `statusCheckRollup` array to a [ChecksSummary].
///
/// Handles both `CheckRun` entries (GitHub Actions) and `StatusContext`
/// entries (legacy commit statuses).
ChecksSummary summarizeChecks(List<Object?>? rollup) {
  final buckets = {
    _CheckBucket.passing: <Check>[],
    _CheckBucket.failing: <Check>[],
    _CheckBucket.pending: <Check>[],
  };

  for (final entry in rollup ?? const []) {
    if (entry is! Map<String, Object?>) continue;

    final classified = entry['__typename'] == 'CheckRun'
        ? _classifyCheckRun(entry)
        : _classifyStatusContext(entry);

    buckets[classified.bucket]!.add(
      Check(
        name: (entry['name'] ?? entry['context'] ?? 'unknown').toString(),
        state: classified.state,
        url: (entry['detailsUrl'] ?? entry['targetUrl'] ?? '').toString(),
      ),
    );
  }

  return ChecksSummary(
    passing: buckets[_CheckBucket.passing]!,
    failing: buckets[_CheckBucket.failing]!,
    pending: buckets[_CheckBucket.pending]!,
  );
}

/// Decodes a `gh pr list` payload, or null if it is not a JSON array.
///
/// A successful HTTP call can still return an object (for example
/// `{"message": "API rate limit exceeded"}`), which must not be cast blindly.
List<Object?>? decodePrList(String raw, String repo) {
  final Object? decoded;
  try {
    decoded = jsonDecode(raw);
  } on FormatException catch (e) {
    stderr.writeln('Failed to parse PR list for $repo: $e');
    return null;
  }

  if (decoded is! List<Object?>) {
    stderr.writeln(
      'Failed to parse PR list for $repo: expected a JSON array, got '
      '${decoded.runtimeType}.',
    );
    return null;
  }
  return decoded;
}

/// Fetches open Dependabot PRs for a single repository.
///
/// Returns null when the query itself fails, which callers must distinguish
/// from an empty list: "the query broke" and "this repo is clean" demand
/// opposite responses from the operator.
List<PullRequest>? fetchPullRequests(String repo, int limit, String label) {
  stderr.writeln('--- Fetching open Dependabot PRs for $repo ---');
  final raw = runCommand('gh', [
    'pr',
    'list',
    '--repo',
    repo,
    '--author',
    'app/dependabot',
    '--state',
    'open',
    '--limit',
    '$limit',
    '--json',
    prFields,
  ]);

  if (raw == null) return null;
  if (raw.isEmpty) return const [];

  final decoded = decodePrList(raw, repo);
  if (decoded == null) return null;

  final prs = [
    for (final entry in decoded)
      if (entry is Map<String, Object?>)
        PullRequest.fromJson(entry, repo: repo, label: label),
  ];

  prs.sort((a, b) => a.number.compareTo(b.number));
  return prs;
}

/// Escapes a value for safe interpolation into a markdown table cell.
String escapeCell(String value) => value.replaceAll('|', r'\|');

/// Prints the markdown summary table for [prs].
void printTable(List<PullRequest> prs) {
  stdout.writeln(
    '| Repo | PR | Dependency | Version | Build | Checks (pass/fail/pending) | '
    'Review | Mergeable | Has label |',
  );
  stdout.writeln('| --- | --- | --- | --- | --- | --- | --- | --- | --- |');

  for (final pr in prs) {
    final checks = pr.checks;
    final counts =
        '${checks.passing.length}/${checks.failing.length}/'
        '${checks.pending.length}';
    stdout.writeln(
      '| ${escapeCell(pr.repo.split('/').last)} '
      '| #${pr.number} '
      '| ${escapeCell(pr.dependency.name)} '
      '| ${escapeCell(pr.dependency.versionChange)} '
      '| ${checks.status.label} '
      '| $counts '
      '| ${escapeCell(pr.reviewDecision)} '
      '| ${escapeCell(pr.mergeable)} '
      '| ${pr.hasLabel ? 'yes' : 'no'} |',
    );
  }
}

/// Prints the per-PR notes that follow the table.
void printFootnotes(List<PullRequest> prs, String label) {
  stdout.writeln();

  for (final pr in prs.where((pr) => pr.checks.failing.isNotEmpty)) {
    final names = pr.checks.failing.map((c) => c.name).join(', ');
    stdout.writeln('- ${pr.ref} (${pr.dependency.name}) failing: $names');
  }

  final alreadyLabeled = prs.where((pr) => pr.hasLabel).toList();
  if (alreadyLabeled.isNotEmpty) {
    final refs = alreadyLabeled.map((pr) => pr.ref).join(', ');
    stdout.writeln("- Already labeled '$label': $refs");
  }

  final drafts = prs.where((pr) => pr.isDraft).toList();
  if (drafts.isNotEmpty) {
    stdout.writeln('- Draft PRs: ${drafts.map((pr) => pr.ref).join(', ')}');
  }
}

/// Writes the full result payload to [path] as indented JSON.
void writeJson(String path, List<PullRequest> prs, String label) {
  final file = File(path);
  file.parent.createSync(recursive: true);
  const encoder = JsonEncoder.withIndent('  ');
  file.writeAsStringSync(
    encoder.convert({
      'label': label,
      'prs': [for (final pr in prs) pr.toJson()],
    }),
  );
  stderr.writeln('\nSaved ${prs.length} PRs to ${file.absolute.path}');
}

/// Reports results to stdout/stderr, keeping failures visually distinct.
void reportResults(
  List<PullRequest> prs,
  List<String> failedRepos,
  Options options,
) {
  if (prs.isEmpty && failedRepos.isNotEmpty) {
    stderr.writeln('Could not list any PRs: every repository query failed.');
  } else if (prs.isEmpty) {
    stdout.writeln('No open Dependabot PRs found.');
  } else {
    printTable(prs);
    printFootnotes(prs, options.label);
  }

  if (failedRepos.isNotEmpty) {
    stderr.writeln(
      '\nWARNING: failed to query ${failedRepos.join(', ')}. '
      'These results are INCOMPLETE; do not treat them as "nothing to do".',
    );
  }

  if (options.outputFile case final path?) {
    writeJson(path, prs, options.label);
  }
}

const usage = '''
Lists open Dependabot PRs and their build status.

Usage: dart run list_dependabot_prs.dart [options]

Options:
  --repo <owner/repo>   Repository to query, or "both" for the Dart and
                        Flutter IntelliJ plugin repos. (default: both)
  --label <name>        Label checked for on each PR. (default: autosubmit)
  --limit <n>           Maximum PRs to fetch per repo. (default: 50)
  --output-file <path>  Optional path to save the results as JSON.
  -h, --help            Show this help text.

Values may be given as "--flag value" or "--flag=value".

Exit codes:
  0  Success.
  1  The GitHub CLI is unavailable, or a repository query failed.
  2  Invalid arguments.
''';

/// Parsed command line options.
typedef Options = ({String repo, String label, int limit, String? outputFile});

/// Splits `--flag=value` into its parts. The value is null for a bare flag.
(String, String?) splitFlag(String arg) => switch (arg.indexOf('=')) {
  final index when index > 0 => (
    arg.substring(0, index),
    arg.substring(index + 1),
  ),
  _ => (arg, null),
};

/// Validates raw flag [values], exiting on any problem.
Options validateOptions(Map<String, String> values) {
  final repo = values['--repo'] ?? 'both';
  if (repo != 'both' && !RegExp(r'^[\w.\-]+/[\w.\-]+$').hasMatch(repo)) {
    stderr.writeln("Invalid --repo '$repo'. Expected 'owner/repo' or 'both'.");
    exit(exitBadUsage);
  }

  final limit = int.tryParse(values['--limit'] ?? '50');
  if (limit == null || limit <= 0) {
    stderr.writeln('--limit must be a positive integer.');
    exit(exitBadUsage);
  }

  return (
    repo: repo,
    label: values['--label'] ?? 'autosubmit',
    limit: limit,
    outputFile: values['--output-file'],
  );
}

/// Minimal argument parser; avoids a package:args dependency so the script
/// runs directly with `dart run` and no `pub get`.
///
/// Returns null when help was requested and the caller should exit quietly.
Options? parseArgs(List<String> args) {
  const known = {'--repo', '--label', '--limit', '--output-file'};
  final values = <String, String>{};

  for (var i = 0; i < args.length; i++) {
    final arg = args[i];

    if (arg == '-h' || arg == '--help') {
      stdout.write(usage);
      return null;
    }

    final (flag, inlineValue) = splitFlag(arg);

    if (!known.contains(flag)) {
      stderr.writeln('Unknown argument: $arg\n');
      stderr.write(usage);
      exit(exitBadUsage);
    }

    if (inlineValue != null) {
      values[flag] = inlineValue;
      continue;
    }
    if (i + 1 >= args.length) {
      stderr.writeln('Missing value for $flag');
      exit(exitBadUsage);
    }
    values[flag] = args[++i];
  }

  return validateOptions(values);
}

void main(List<String> args) {
  final options = parseArgs(args);
  if (options == null) return;

  requireGitHubCli();

  final repos = options.repo == 'both' ? defaultRepos : [options.repo];
  final prs = <PullRequest>[];
  final failedRepos = <String>[];

  for (final repo in repos) {
    final fetched = fetchPullRequests(repo, options.limit, options.label);
    if (fetched == null) {
      failedRepos.add(repo);
    } else {
      prs.addAll(fetched);
    }
  }

  reportResults(prs, failedRepos, options);

  exit(failedRepos.isEmpty ? 0 : exitQueryFailed);
}
