// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/**
 * Assigns one reviewer to each open pull request.
 *
 * Runs from .github/workflows/assign-reviewers.yaml. Lanes and other settings
 * live in .github/assign-reviewers.yml. See README.md in this directory for
 * the settings and kill switch.
 *
 * Overview:
 *   1. Pick a lane for the PR (lsp, build, or default).
 *   2. Pick a reviewer from that lane by rotating on the PR number.
 *   3. Compare with who is requested today:
 *        - nobody requested        -> request the reviewer
 *        - the CODEOWNERS pair     -> trim to the one reviewer
 *        - anything else           -> leave it alone (a person chose)
 */
'use strict';

// ---------------------------------------------------------------------------
// Configuration (lives in .github/assign-reviewers.yml)
// ---------------------------------------------------------------------------

const REQUIRED_LANES = ['lsp', 'build', 'default'];

/**
 * Turns the parsed .github/assign-reviewers.yml into the config object the
 * rest of this file uses: path lists become regular expressions, and anything
 * missing or malformed throws a clear error.
 */
function loadConfig(raw) {
  raw = raw || {};
  const problems = [];
  const listOfStrings = (value, name) => {
    const ok = Array.isArray(value) && value.every((item) => typeof item === 'string');
    if (!ok) problems.push(`${name} must be a list of strings`);
    return ok ? value : [];
  };

  const lanes = {};
  for (const lane of REQUIRED_LANES) {
    lanes[lane] = listOfStrings(raw.lanes && raw.lanes[lane], `lanes.${lane}`);
  }
  const substitutes = {};
  for (const [lane, logins] of Object.entries(raw.substitutes || {})) {
    substitutes[lane] = listOfStrings(logins, `substitutes.${lane}`);
  }
  const config = {
    lanes,
    substitutes,
    fallback: listOfStrings(raw.fallback, 'fallback'),
    codeowners: listOfStrings(raw.codeowners, 'codeowners'),
    lspAuthors: listOfStrings(raw.lspAuthors, 'lspAuthors'),
    buildBotAuthors: listOfStrings(raw.buildBotAuthors, 'buildBotAuthors'),
    lspPathPattern: new RegExp(listOfStrings(raw.lspPaths, 'lspPaths').join('|') || '(?!)', 'i'),
    buildPathPattern: new RegExp(listOfStrings(raw.buildPaths, 'buildPaths').join('|') || '(?!)'),
    skipLabels: listOfStrings(raw.skipLabels, 'skipLabels').map((label) => label.toLowerCase()),
    maxWritesPerRun: raw.maxWritesPerRun,
  };
  if (!Number.isInteger(config.maxWritesPerRun) || config.maxWritesPerRun < 1) {
    problems.push('maxWritesPerRun must be a positive whole number');
  }
  if (problems.length > 0) {
    throw new Error(`Invalid .github/assign-reviewers.yml: ${problems.join('; ')}`);
  }
  return config;
}

/** Reads the JSON that the workflow converts from the YAML config. */
function loadConfigFile(path) {
  if (!path) throw new Error('ASSIGN_CONFIG_JSON is not set');
  const fs = require('fs');
  return loadConfig(JSON.parse(fs.readFileSync(path, 'utf8')));
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

/** GitHub logins are case-insensitive, so compare them in lower case. */
function normalize(login) {
  return (login || '').toLowerCase();
}

function loginsOf(users) {
  return (users || []).map((user) => normalize(user.login));
}

function hasLabel(pr, names) {
  return (pr.labels || []).some((label) => names.includes(normalize(label.name)));
}

function sameMembers(a, b) {
  return a.length === b.length && a.every((x) => b.includes(x));
}

/** Everyone who can be picked by this workflow. */
function allReviewers(config) {
  const everyone = [
    ...Object.values(config.lanes).flat(),
    ...Object.values(config.substitutes).flat(),
    ...config.fallback,
  ];
  return [...new Set(everyone.map(normalize))];
}

// ---------------------------------------------------------------------------
// Step 1: choose a lane
// ---------------------------------------------------------------------------

/**
 * Returns the lane for a PR: 'lsp', 'build', or 'default'.
 *
 * @param {object} pr A pull request from the GitHub REST API.
 * @param {string[]} files Paths of the files the PR changes.
 */
function classify(pr, files, config) {
  const author = normalize(pr.user && pr.user.login);

  if (config.buildBotAuthors.map(normalize).includes(author)) return 'build';

  const isLsp =
    hasLabel(pr, ['lsp']) ||
    /\blsp\b/i.test(pr.title || '') ||
    config.lspAuthors.map(normalize).includes(author) ||
    files.some((file) => config.lspPathPattern.test(file));
  if (isLsp) return 'lsp';

  const onlyBuildFiles = files.length > 0 && files.every((file) => config.buildPathPattern.test(file));
  if (onlyBuildFiles) return 'build';

  return 'default';
}

// ---------------------------------------------------------------------------
// Step 2: choose a reviewer
// ---------------------------------------------------------------------------

/**
 * Picks a reviewer for a lane by rotating on the PR number.
 *
 * Tries the lane first, then its substitutes, then the fallback list. The PR
 * author and anyone away are never picked. Returns null if nobody is available.
 */
function pickReviewer(lane, prNumber, author, away, config) {
  const isAvailable = (login) => normalize(login) !== normalize(author) && !away.includes(normalize(login));
  const candidateLists = [config.lanes[lane] || [], config.substitutes[lane] || [], config.fallback];

  for (const list of candidateLists) {
    const available = list.filter(isAvailable).map(normalize);
    if (available.length > 0) {
      return available[prNumber % available.length];
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Step 3: decide what to do
// ---------------------------------------------------------------------------

/**
 * Builds a decision record.
 *
 * action is one of:
 *   'skip'    leave the PR alone
 *   'none'    already has the right reviewer
 *   'request' add a reviewer
 *   'trim'    reduce the CODEOWNERS pair to one reviewer
 *   'error'   nobody is available
 */
function decision(action, reason, {lane = null, add = [], remove = []} = {}) {
  return {action, reason, lane, add, remove};
}

/** Why a PR should be left alone, or null if it shouldn't. */
function reasonToSkip(pr, reviews, timeline, config) {
  if (pr.draft) return 'draft';
  if (hasLabel(pr, config.skipLabels)) return 'skip label';
  if ((pr.requested_teams || []).length > 0) return 'team review requested';

  const author = normalize(pr.user && pr.user.login);
  const reviewers = allReviewers(config);
  const maintainerReviewed = reviews.some((review) => {
    const reviewer = normalize(review.user && review.user.login);
    const counts = review.state !== 'PENDING' && review.state !== 'DISMISSED';
    return counts && reviewer !== author && reviewers.includes(reviewer);
  });
  if (maintainerReviewed) return 'already reviewed by a maintainer';

  // If a person removed a review request, respect their choice. (Removals by
  // this workflow show up with a Bot actor and don't count.)
  const personRemovedRequest = timeline.some(
    (event) => event.event === 'review_request_removed' && event.actor && event.actor.type !== 'Bot',
  );
  if (personRemovedRequest) return 'a person removed a review request';

  return null;
}

/**
 * Decides what to do with one PR. Makes no API calls.
 *
 * @param {object} args
 * @param {object} args.pr The pull request.
 * @param {string[]} args.files Paths of the changed files.
 * @param {object[]} args.reviews Reviews on the PR.
 * @param {object[]} args.timeline Timeline events for the PR.
 * @param {string[]} args.away Lower-case logins of people who are away.
 */
function decide({pr, files, reviews, timeline, away, config}) {
  const skipReason = reasonToSkip(pr, reviews, timeline, config);
  if (skipReason) return decision('skip', skipReason);

  const author = normalize(pr.user && pr.user.login);
  const lane = classify(pr, files, config);
  const reviewer = pickReviewer(lane, pr.number, author, away, config);
  if (!reviewer) return decision('error', 'no available reviewer', {lane});

  const requested = loginsOf(pr.requested_reviewers);

  // Nobody requested yet (this is the normal case once CODEOWNERS is gone).
  if (requested.length === 0) {
    return decision('request', 'no reviewer yet', {lane, add: [reviewer]});
  }

  // Exactly the CODEOWNERS requests: trim them down to the one reviewer.
  const codeownersRequests = config.codeowners.map(normalize).filter((login) => login !== author);
  if (sameMembers(requested, codeownersRequests)) {
    const add = requested.includes(reviewer) ? [] : [reviewer];
    const remove = requested.filter((login) => login !== reviewer);
    if (add.length === 0 && remove.length === 0) {
      return decision('none', `already one reviewer (${reviewer})`, {lane});
    }
    return decision('trim', 'trim CODEOWNERS requests', {lane, add, remove});
  }

  // Someone picked reviewers by hand.
  return decision('skip', 'reviewer already chosen', {lane});
}

// ---------------------------------------------------------------------------
// Running against GitHub
// ---------------------------------------------------------------------------

/** Reads REVIEWERS_AWAY ("pq, helin24") and warns about unknown logins. */
function parseAway(value, core, config) {
  const away = (value || '').split(/[\s,]+/).map(normalize).filter(Boolean);
  const known = allReviewers(config);
  for (const login of away) {
    if (!known.includes(login)) {
      core.warning(`REVIEWERS_AWAY lists "${login}", who isn't in any lane.`);
    }
  }
  return away;
}

/** Reads the workflow settings from environment variables. */
function readSettings(env, core, config) {
  let onlyPr = null;
  if (env.ONLY_PR) {
    // Fail loudly rather than quietly falling back to a full run.
    if (!/^\d+$/.test(env.ONLY_PR.trim())) {
      throw new Error(`only_pr must be a PR number, got "${env.ONLY_PR}"`);
    }
    onlyPr = Number(env.ONLY_PR);
  }
  // Fail closed: only an explicit 'false' turns off dry run.
  const live = env.ASSIGN_DRY_RUN === 'false';
  const canaryWrite = onlyPr !== null && env.FORCE_WRITE === 'true';
  const since = env.ASSIGN_SINCE ? new Date(env.ASSIGN_SINCE) : null;
  return {
    onlyPr,
    write: live || canaryWrite,
    since: since && !isNaN(since.getTime()) ? since : null,
    away: parseAway(env.REVIEWERS_AWAY, core, config),
  };
}

/** The open PRs to consider in this run. */
async function loadPullRequests(github, repoRef, settings) {
  if (settings.onlyPr !== null) {
    const {data} = await github.rest.pulls.get({...repoRef, pull_number: settings.onlyPr});
    return [data];
  }
  const open = await github.paginate(github.rest.pulls.list, {...repoRef, state: 'open', per_page: 100});
  return open.filter((pr) => !pr.draft && new Date(pr.created_at) >= settings.since);
}

/** Files, reviews, and timeline for one PR. */
async function loadDetails(github, repoRef, number) {
  const [files, reviews, timeline] = await Promise.all([
    github.paginate(github.rest.pulls.listFiles, {...repoRef, pull_number: number, per_page: 100}),
    github.paginate(github.rest.pulls.listReviews, {...repoRef, pull_number: number, per_page: 100}),
    github.paginate(github.rest.issues.listEventsForTimeline, {...repoRef, issue_number: number, per_page: 100}),
  ]);
  return {files: files.map((file) => file.filename), reviews, timeline};
}

/**
 * Applies a decision. Re-reads the PR first and does nothing if its reviewers
 * (or team requests) changed in the meantime. Adds before removing, so the PR always has a reviewer.
 * Returns true if anything was changed.
 */
async function apply(github, repoRef, pr, d) {
  const {data: fresh} = await github.rest.pulls.get({...repoRef, pull_number: pr.number});
  const unchanged =
    fresh.state === 'open' &&
    !fresh.draft &&
    (fresh.requested_teams || []).length === 0 &&
    sameMembers(loginsOf(pr.requested_reviewers), loginsOf(fresh.requested_reviewers));
  if (!unchanged) return false;

  if (d.add.length > 0) {
    await github.rest.pulls.requestReviewers({...repoRef, pull_number: pr.number, reviewers: d.add});
  }
  if (d.remove.length > 0) {
    await github.rest.pulls.removeRequestedReviewers({...repoRef, pull_number: pr.number, reviewers: d.remove});
  }
  return true;
}

/** Writes a table of this run's decisions to the job summary and the log. */
async function report(core, rows, settings) {
  const header = ['PR', 'Lane', 'Action', 'Add', 'Remove', 'Applied', 'Reason'].map((data) => ({data, header: true}));
  const lines = rows.map(({pr, d, applied}) => [
    `#${pr.number}`,
    d.lane || '',
    d.action,
    d.add.join(', '),
    d.remove.join(', '),
    applied ? 'yes' : 'no',
    d.reason,
  ]);

  await core.summary
    .addHeading(`Assign reviewers (${settings.write ? 'live' : 'dry run'})`, 3)
    .addRaw(`Away: ${settings.away.join(', ') || 'none'}`, true)
    .addTable([header, ...lines])
    .write();

  for (const {pr, d, applied} of rows) {
    core.info(`#${pr.number}: ${d.action} (${d.reason}) lane=${d.lane} add=${d.add} remove=${d.remove} applied=${applied}`);
  }
}

/** Entry point, called from the workflow via actions/github-script. */
async function run({github, context, core, env = process.env, config = loadConfigFile(env.ASSIGN_CONFIG_JSON)}) {
  const repoRef = context.repo; // {owner, repo}
  const settings = readSettings(env, core, config);

  if (settings.onlyPr === null && settings.since === null) {
    core.notice('ASSIGN_SINCE is not set; nothing to do.');
    return {rows: [], errors: []};
  }

  const rows = [];
  const errors = [];
  let writes = 0;

  for (const pr of await loadPullRequests(github, repoRef, settings)) {
    try {
      const details = await loadDetails(github, repoRef, pr.number);
      const d = decide({pr, ...details, away: settings.away, config});
      let applied = false;

      const wantsChange = d.action === 'request' || d.action === 'trim';
      if (settings.write && wantsChange) {
        if (writes >= config.maxWritesPerRun) {
          errors.push(`#${pr.number}: write cap (${config.maxWritesPerRun}) reached; stopping`);
          rows.push({pr, d, applied});
          break;
        }
        applied = await apply(github, repoRef, pr, d);
        if (applied) writes++;
        else d.reason += ' (skipped: reviewers changed since read)';
      }

      if (d.action === 'error') errors.push(`#${pr.number}: ${d.reason}`);
      rows.push({pr, d, applied});
    } catch (e) {
      // Keep going: one bad PR shouldn't block the rest.
      errors.push(`#${pr.number}: ${e.message}`);
    }
  }

  await report(core, rows, settings);
  if (errors.length > 0) core.setFailed(errors.join('\n'));
  return {rows, errors};
}

module.exports = run;
module.exports.loadConfig = loadConfig;
module.exports.classify = classify;
module.exports.pickReviewer = pickReviewer;
module.exports.decide = decide;
module.exports.parseAway = parseAway;
