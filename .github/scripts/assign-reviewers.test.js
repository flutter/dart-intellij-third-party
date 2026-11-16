// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Run with: node --test .github/scripts/*.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const run = require('./assign-reviewers.js');
const {classify, pickReviewer, decide, loadConfig} = run;

// Mirrors .github/assign-reviewers.yml, so the tests don't depend on a YAML parser.
// A separate test (below) checks the real file when `yq` is installed.
const RAW_CONFIG = {
  lanes: {lsp: ['helin24', 'jwren'], build: ['pq'], default: ['pq', 'helin24']},
  substitutes: {build: ['helin24']},
  fallback: ['pq', 'helin24', 'jwren'],
  codeowners: ['pq', 'helin24'],
  lspAuthors: [],
  buildBotAuthors: ['dependabot[bot]', 'flutteractionsbot'],
  lspPaths: ['/lsp/', 'platform-lsp', '/dartlsp/', '/DartLsp[^/]*$'],
  buildPaths: [
    '(^|/)(build\\.gradle(\\.kts)?|settings\\.gradle(\\.kts)?|gradle\\.properties|gradlew(\\.bat)?|libs\\.versions\\.toml)$',
    '^(gradle|kokoro|\\.github|third_party/gradle)/',
    '^(third_party/)?tool/(?!baseline/)',
  ],
  skipLabels: ['blocked', 'waiting for customer response'],
  maxWritesPerRun: 5,
};
const config = loadConfig(RAW_CONFIG);

// A minimal pull request, as returned by the GitHub REST API. Override fields per test.
const pr = (overrides = {}) => ({
  number: 100,
  title: 'Some change',
  draft: false,
  state: 'open',
  created_at: '2026-11-20T00:00:00Z',
  user: {login: 'someone', type: 'User'},
  labels: [],
  requested_reviewers: [],
  requested_teams: [],
  ...overrides,
});

// users('pq', 'helin24') -> [{login: 'pq'}, {login: 'helin24'}]
const users = (...logins) => logins.map((login) => ({login}));

test('classify: LSP signals', () => {
  assert.equal(classify(pr({labels: [{name: 'lsp'}]}), [], config), 'lsp');
  assert.equal(classify(pr({title: 'feat(lsp): migrate Outline'}), [], config), 'lsp');
  assert.equal(classify(pr({title: '[LSP] Remove legacy calls'}), [], config), 'lsp');
  // lspAuthors is empty today, so a contractor's non-LSP PR stays in default...
  assert.equal(classify(pr({user: {login: 'ranbeuer'}}), ['third_party/src/Foo.java'], config), 'default');
  // ...but listing an author still routes their PRs to lsp.
  const withAuthor = loadConfig({...RAW_CONFIG, lspAuthors: ['ranbeuer']});
  assert.equal(classify(pr({user: {login: 'ranbeuer'}}), ['third_party/src/Foo.java'], withAuthor), 'lsp');
  assert.equal(
    classify(pr(), ['third_party/src/main/java/com/jetbrains/lang/dart/ide/refactoring/DartLspRenameHandler.kt'], config),
    'lsp',
  );
  assert.equal(classify(pr(), ['third_party/src/main/java/com/jetbrains/lang/dart/lsp/Foo.java'], config), 'lsp');
});

test('classify: build lane', () => {
  assert.equal(classify(pr({user: {login: 'dependabot[bot]'}, title: 'Bump lsp4j'}), ['third_party/build.gradle.kts'], config), 'build');
  assert.equal(classify(pr({user: {login: 'flutteractionsbot'}}), ['README.md'], config), 'build');
  assert.equal(classify(pr(), ['.github/workflows/presubmit.yaml', 'gradle.properties'], config), 'build');
  assert.equal(classify(pr(), ['.github/scripts/assign-reviewers.js'], config), 'build');
  assert.equal(classify(pr(), ['third_party/tool/kokoro/build.sh', 'third_party/tool/check_verifier_baselines.sh'], config), 'build');
  assert.equal(classify(pr(), ['tool/check_ai_config.sh'], config), 'build');
});

test('classify: default', () => {
  assert.equal(classify(pr(), ['tool/baseline/261/verifier-baseline.txt'], config), 'default');
  assert.equal(classify(pr(), ['third_party/tool/baseline/253/verifier-baseline.txt'], config), 'default');
  assert.equal(classify(pr(), ['build.gradle.kts', 'third_party/src/Foo.java'], config), 'default');
  assert.equal(classify(pr({user: {login: 'reidbaker-agent'}}), ['skills_lint.yaml'], config), 'default');
  assert.equal(classify(pr({title: 'Fix lspci detection'}), [], config), 'default'); // word boundary
});

test('pickReviewer: rotates on PR number', () => {
  assert.equal(pickReviewer('default', 10, 'someone', [], config), 'pq');
  assert.equal(pickReviewer('default', 11, 'someone', [], config), 'helin24');
  assert.equal(pickReviewer('lsp', 10, 'someone', [], config), 'helin24');
  assert.equal(pickReviewer('lsp', 11, 'someone', [], config), 'jwren');
});

test('pickReviewer: excludes author and away, then substitutes, then fallback', () => {
  assert.equal(pickReviewer('default', 10, 'pq', [], config), 'helin24');
  assert.equal(pickReviewer('lsp', 10, 'helin24', [], config), 'jwren');
  assert.equal(pickReviewer('default', 10, 'someone', ['pq'], config), 'helin24');
  assert.equal(pickReviewer('build', 10, 'pq', [], config), 'helin24');
  assert.equal(pickReviewer('build', 10, 'someone', ['pq'], config), 'helin24');
  assert.equal(pickReviewer('default', 10, 'someone', ['pq', 'helin24'], config), 'jwren');
  assert.equal(pickReviewer('default', 10, 'jwren', ['pq', 'helin24'], config), null);
});

const base = {files: [], reviews: [], timeline: [], away: [], config};

test('decide: requests one reviewer when none is requested', () => {
  const d = decide({...base, pr: pr({number: 11})});
  assert.equal(d.action, 'request');
  assert.deepEqual(d.add, ['helin24']);
});

test('decide: trims CODEOWNERS double request', () => {
  const d = decide({...base, pr: pr({number: 10, requested_reviewers: users('helin24', 'pq')})});
  assert.equal(d.action, 'trim');
  assert.deepEqual(d.add, []);
  assert.deepEqual(d.remove, ['helin24']);
});

test('decide: LSP PR swaps pq out for helin or jwren', () => {
  const d = decide({...base, pr: pr({number: 11, title: '[lsp] thing', requested_reviewers: users('pq', 'helin24')})});
  assert.equal(d.lane, 'lsp');
  assert.deepEqual(d.add, ['jwren']);
  assert.deepEqual(d.remove.sort(), ['helin24', 'pq']);
});

test('decide: maintainer-authored PR (CODEOWNERS requested only the other maintainer)', () => {
  // helin authors an LSP PR: CODEOWNERS requested pq; the LSP lane minus helin is jwren.
  const d = decide({...base, pr: pr({user: {login: 'helin24'}, title: 'lsp: x', requested_reviewers: users('pq')})});
  assert.deepEqual(d.add, ['jwren']);
  assert.deepEqual(d.remove, ['pq']);
  // pq authors a default PR: CODEOWNERS requested helin, which is already right.
  const n = decide({...base, pr: pr({user: {login: 'pq'}, requested_reviewers: users('helin24')})});
  assert.equal(n.action, 'none');
});

test('decide: leaves manual picks, reviews, removals, drafts, teams and skip labels alone', () => {
  assert.equal(decide({...base, pr: pr({requested_reviewers: users('jwren')})}).action, 'skip');
  assert.equal(
    decide({...base, pr: pr({requested_reviewers: users('pq', 'helin24')}), reviews: [{user: {login: 'helin24'}, state: 'COMMENTED'}]}).action,
    'skip',
  );
  assert.equal(
    decide({...base, pr: pr(), timeline: [{event: 'review_request_removed', actor: {login: 'pq', type: 'User'}}]}).action,
    'skip',
  );
  assert.equal(decide({...base, pr: pr({draft: true})}).action, 'skip');
  assert.equal(decide({...base, pr: pr({requested_teams: [{slug: 'x'}]})}).action, 'skip');
  assert.equal(decide({...base, pr: pr({labels: [{name: 'waiting for customer response'}]})}).action, 'skip');
});

test('decide: drive-by reviews and the bot\'s own removals do not block', () => {
  const d = decide({
    ...base,
    pr: pr(),
    reviews: [{user: {login: 'contributor'}, state: 'COMMENTED'}, {user: {login: 'helin24'}, state: 'PENDING'}],
    timeline: [{event: 'review_request_removed', actor: {login: 'github-actions[bot]', type: 'Bot'}}],
  });
  assert.equal(d.action, 'request');
});

// --- run(): fail-closed and write behavior, with a fake GitHub client ---

// `freshChanges` simulates edits made between the first read and the re-check
// right before writing: {prNumber: {field: value}}.
function fakeGitHub(prs, freshChanges = {}) {
  const calls = {request: [], remove: [], get: 0};
  const byNumber = Object.fromEntries(prs.map((p) => [p.number, p]));
  const rest = {
    pulls: {
      list: 'list',
      listFiles: 'files',
      listReviews: 'reviews',
      get: async ({pull_number}) => {
        calls.get++;
        return {data: {...byNumber[pull_number], ...freshChanges[pull_number]}};
      },
      requestReviewers: async (a) => calls.request.push(a),
      removeRequestedReviewers: async (a) => calls.remove.push(a),
    },
    issues: {listEventsForTimeline: 'timeline'},
  };
  const paginate = async (fn) => (fn === 'list' ? prs : []);
  return {github: {rest, paginate}, calls};
}

function fakeCore() {
  const summary = {addHeading: () => summary, addRaw: () => summary, addTable: () => summary, write: async () => {}};
  return {summary, info: () => {}, notice: () => {}, warning: () => {}, failed: null, setFailed(m) { this.failed = m; }};
}

const context = {repo: {owner: 'flutter', repo: 'dart-intellij-third-party'}};

test('run: unset ASSIGN_DRY_RUN means dry run (no writes)', async () => {
  const {github, calls} = fakeGitHub([pr({number: 11})]);
  await run({github, context, core: fakeCore(), config, env: {ASSIGN_SINCE: '2026-11-01T00:00:00Z'}});
  assert.equal(calls.request.length, 0);
  assert.equal(calls.remove.length, 0);
});

test('run: no ASSIGN_SINCE means nothing happens', async () => {
  const {github, calls} = fakeGitHub([pr({number: 11})]);
  const out = await run({github, context, core: fakeCore(), config, env: {ASSIGN_DRY_RUN: 'false'}});
  assert.equal(out.rows.length, 0);
  assert.equal(calls.request.length, 0);
});

test('run: live mode adds first, then removes; ignores PRs before ASSIGN_SINCE', async () => {
  const {github, calls} = fakeGitHub([
    pr({number: 11, title: '[lsp] x', requested_reviewers: users('pq', 'helin24')}),
    pr({number: 12, created_at: '2026-10-01T00:00:00Z'}),
  ]);
  await run({github, context, core: fakeCore(), config, env: {ASSIGN_DRY_RUN: 'false', ASSIGN_SINCE: '2026-11-01T00:00:00Z'}});
  assert.deepEqual(calls.request.map((c) => [c.pull_number, c.reviewers]), [[11, ['jwren']]]);
  assert.deepEqual(calls.remove.map((c) => c.pull_number), [11]);
});

test('run: only_pr with write works in dry run (canary)', async () => {
  const {github, calls} = fakeGitHub([pr({number: 11})]);
  await run({github, context, core: fakeCore(), config, env: {ONLY_PR: '11', FORCE_WRITE: 'true'}});
  assert.equal(calls.request.length, 1);
});

test('run: only_pr that is not a number fails and writes nothing', async () => {
  const {github, calls} = fakeGitHub([pr({number: 11})]);
  let message = '';
  try {
    await run({github, context, core: fakeCore(), config, env: {ONLY_PR: 'abc', FORCE_WRITE: 'true'}});
  } catch (e) {
    message = e.message;
  }
  assert.match(message, /only_pr must be a PR number/);
  assert.equal(calls.request.length, 0);
  assert.equal(calls.get, 0);
});

test('run: does not write if a team was requested since the first read', async () => {
  const {github, calls} = fakeGitHub([pr({number: 11})], {11: {requested_teams: [{slug: 'reviewers'}]}});
  const out = await run({github, context, core: fakeCore(), config, env: {ASSIGN_DRY_RUN: 'false', ASSIGN_SINCE: '2026-11-01T00:00:00Z'}});
  assert.equal(calls.request.length, 0);
  assert.equal(out.rows[0].applied, false);
});

test('run: write cap stops after maxWritesPerRun', async () => {
  const many = Array.from({length: 8}, (_, i) => pr({number: 20 + i}));
  const {github, calls} = fakeGitHub(many);
  const core = fakeCore();
  await run({github, context, core, config, env: {ASSIGN_DRY_RUN: 'false', ASSIGN_SINCE: '2026-11-01T00:00:00Z'}});
  assert.equal(calls.request.length, config.maxWritesPerRun);
  assert.match(core.failed, /write cap/);
});

// --- config file ---

test('loadConfig rejects a malformed config with a clear message', () => {
  let message = '';
  try {
    loadConfig({lanes: {lsp: ['helin24']}, maxWritesPerRun: 0});
  } catch (e) {
    message = e.message;
  }
  assert.match(message, /lanes\.build must be a list of strings/);
  assert.match(message, /maxWritesPerRun/);
});

test('the checked-in .github/assign-reviewers.yml loads and matches the test config', (t) => {
  let json;
  try {
    const {execFileSync} = require('node:child_process');
    const path = require('node:path');
    const file = path.join(__dirname, '..', 'assign-reviewers.yml');
    json = execFileSync('yq', ['-o=json', '.', file], {encoding: 'utf8'});
  } catch (e) {
    t.skip('yq is not installed');
    return;
  }
  const fromFile = loadConfig(JSON.parse(json));
  assert.deepEqual(fromFile.lanes, config.lanes);
  assert.deepEqual(fromFile.substitutes, config.substitutes);
  assert.equal(fromFile.lspPathPattern.source, config.lspPathPattern.source);
  assert.equal(fromFile.buildPathPattern.source, config.buildPathPattern.source);
});
