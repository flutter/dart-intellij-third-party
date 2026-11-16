# Reviewer assignment

`.github/workflows/assign-reviewers.yaml` runs every 20 minutes. It requests **one** reviewer per open PR, and trims the double requests that `CODEOWNERS` makes today.

- **Who reviews what** (lanes, substitutes, paths, bot authors) is in **`.github/assign-reviewers.yml`**. Edit that file to change assignments; the code doesn't need to change.
- The logic is in `assign-reviewers.js`, and the tests are in `assign-reviewers.test.js`.

## Lanes (defaults in `.github/assign-reviewers.yml`)

| Lane | Matches | Reviewers |
|---|---|---|
| `lsp` | `lsp` label; "lsp" in the title; LSP paths; authors in `lspAuthors` | helin24, jwren |
| `build` | `dependabot` and `flutteractionsbot` PRs; PRs that only touch build files (Gradle, `gradle/`, `kokoro/`, `tool/` except baselines, `.github/`) | pq (helin24 substitutes) |
| `default` | Everything else, including other bot or agent authors | pq, helin24 |

Within a lane, the pick rotates on PR number (`pool[number % pool.length]`), after removing the author and anyone away. If the lane is empty, the pick falls back to the lane's substitutes, then to {pq, helin24, jwren}.

## When it does nothing
The workflow leaves a PR alone when any of these is true:
- it's a draft;
- it has a `blocked` or `waiting for customer response` label;
- a team was requested;
- a maintainer has already reviewed it;
- a person removed a review request;
- its reviewers were picked by hand (anything other than the `CODEOWNERS` pair).

## Changing the config
Edit `.github/assign-reviewers.yml` in a PR; it's in the build lane, so pq reviews it. If the file is malformed, the run fails with a message naming the bad key. The workflow converts it to JSON with `yq`, which is preinstalled on GitHub's Ubuntu runners. The test suite also checks the real file when `yq` is installed locally.

## Settings (repo variables)
| Variable | Meaning |
|---|---|
| `ASSIGN_DRY_RUN` | `false` = live. Anything else, or unset, = dry run: decisions are logged in the job summary only |
| `ASSIGN_SINCE` | ISO timestamp. Only PRs created at or after it are touched. Unset = do nothing |
| `REVIEWERS_AWAY` | Comma-separated logins to skip while someone is out, e.g. `pq`. Set it in **both** repos |

## Testing on one PR
Actions → *Assign reviewers* → *Run workflow*. Set `only_pr` to a PR number, and tick `write` to apply the change even in dry run.

## Kill switch and rollback
1. Set `ASSIGN_DRY_RUN` to anything other than `false` (takes effect immediately), or run `gh workflow disable assign-reviewers.yaml`.
2. While `CODEOWNERS` still has `* @helin24 @pq`, that's all you need: behavior goes back to today's.
3. After the `CODEOWNERS` line is removed, also revert that PR.

## Run the tests
The presubmit job `assign-reviewers-tests` runs these on every PR. To run them locally:
```
node --test .github/scripts/*.test.js
```

## Failures
A run fails (red X) when a PR has no available reviewer, an API call fails, or more than 5 PRs would change in one run. GitHub emails whoever last edited the `cron` line.
