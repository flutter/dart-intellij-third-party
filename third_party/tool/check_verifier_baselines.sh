#!/usr/bin/env bash
#
# Compare IntelliJ plugin-verifier reports against per-IDE-branch baselines.
#
# Usage:
#   check_verifier_baselines.sh [check|update]
#
#   check  (default) Exit non-zero if any NEW issue appears relative to the
#                    committed baselines. Emits GitHub annotations and a job
#                    summary when running under GitHub Actions.
#   update           Rewrite the baselines from the reports currently on disk.
#
# This script does NOT run Gradle; it only reads the reports produced by
# `./gradlew verifyPlugin`. Use tool/update_baselines.sh to do both.
#
# Baseline format (one issue per line, after `#` header comments):
#
#   <section name><TAB><issue text>
#
# Carrying the section keeps severity information that a bare list of issue
# strings would lose, so a potential NoSuchMethodError is never silently
# treated like a cosmetic deprecation.

set -uo pipefail

MODE="${1:-check}"
case "$MODE" in
  check | update) ;;
  *)
    echo "usage: $(basename "$0") [check|update]" >&2
    exit 2
    ;;
esac

THIRD_PARTY="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORTS="$THIRD_PARTY/build/reports/pluginVerifier"
BASELINES="$THIRD_PARTY/tool/baseline"

# Report sections that are informational rather than structural. This list
# exists for exactly one purpose: to scope the vendored-source filter below.
# It is deliberately NOT a severity policy -- Gradle's `failureLevel` in
# build.gradle.kts decides what fails the build. This script's contract is
# simpler: anything new relative to the baseline is reported and fails.
#
# "Dynamic Plugin Status" is absent on purpose, so that a non-dynamic plugin
# report is never mistaken for informational noise.
INFO_SECTIONS='Deprecated API usages|Experimental API usages|Internal API usages|Override-only API usages'
INFO_RE="^($INFO_SECTIONS)"$'\t'

# Vendored platform-lsp sources are copied from upstream and churn constantly,
# so their informational findings are dropped as noise. The filter deliberately
# does NOT apply outside the informational sections: a compatibility problem in
# vendored code is still a real NoSuchMethodError risk at runtime.
VENDORED='com\.intellij\.platform\.dartlsp'

RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
YELLOW=$'\033[1;33m'
BOLD=$'\033[1m'
NC=$'\033[0m'

SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"

# Flatten a report.md into "SECTION<TAB>issue" lines.
#
# report.md looks like:
#   ## Compatibility problems (5)
#   ### Invocation of unresolved method ...
#   * Method com.example.Foo.bar(...) contains an *invokevirtual* ...
#
# The `### ` group headings are redundant with the `* ` detail lines, so only
# the details are kept.
normalize() {
  awk '
    /^## / {
      section = substr($0, 4)
      sub(/[ \t]*\([0-9]+\)[ \t]*$/, "", section)
      next
    }
    /^\* / { if (section != "") print section "\t" substr($0, 3) }
  ' "$1" | tr -d '\r' | LC_ALL=C sort -u
}

# Select the issues worth baselining: everything outside the informational
# sections (kept in full, vendored or not), plus the non-vendored informational
# usages.
relevant() {
  local report="$1"
  {
    normalize "$report" | grep -Ev "$INFO_RE" || true
    normalize "$report" | grep -E "$INFO_RE" | grep -v "$VENDORED" || true
  } | LC_ALL=C sort -u
}

if [ ! -d "$REPORTS" ]; then
  echo -e "${RED}${BOLD}Error: no verifier reports found at $REPORTS${NC}" >&2
  echo "Run './gradlew verifyPlugin' in third_party/ first." >&2
  exit 1
fi

shopt -s nullglob
reports=("$REPORTS"/*/report.md)
if [ ${#reports[@]} -eq 0 ]; then
  echo -e "${RED}${BOLD}Error: no report.md files under $REPORTS${NC}" >&2
  exit 1
fi

if [ "$MODE" = check ]; then
  {
    echo "## Plugin Verifier"
    echo
    echo "| IDE build | New issues | Verdict |"
    echo "|---|---:|---|"
  } >> "$SUMMARY"
fi

status=0
details=""

current="$(mktemp)"
trap 'rm -f "$current"' EXIT INT TERM

for report in "${reports[@]}"; do
  ide="$(basename "$(dirname "$report")")" # e.g. IU-263.4732.28
  branch="${ide#*-}"                       # 263.4732.28
  branch="${branch%%.*}"                   # 263
  baseline="$BASELINES/$branch/verifier-baseline.txt"

  relevant "$report" > "$current"

  # Guard against a silently unparseable report. Every real report contains
  # hundreds of `* ` bullets, so recognising none of them means the format
  # changed underneath us -- which would otherwise turn this gate into a no-op
  # that reports success.
  bullets="$(grep -c '^\* ' "$report" || true)"
  parsed="$(wc -l < "$current" | tr -d ' ')"
  if [ "$bullets" -gt 0 ] && [ "$parsed" -eq 0 ]; then
    status=1
    echo -e "${RED}${BOLD}Error: could not parse $report${NC}"
    echo "::error title=Unparseable verifier report for $ide::$report contains $bullets issue bullet(s) but none were recognised, so nothing was verified. The report format has probably changed; update third_party/tool/check_verifier_baselines.sh."
    echo "| \`$ide\` | ? | :x: unparseable report |" >> "$SUMMARY"
    continue
  fi

  if [ "$MODE" = update ]; then
    mkdir -p "$(dirname "$baseline")"
    {
      echo "# Plugin verifier baseline for IDE branch $branch."
      echo "# Generated from $ide by third_party/tool/update_baselines.sh -- do not edit by hand."
      cat "$current"
    } > "$baseline"
    echo -e "${GREEN}Updated $baseline ($(wc -l < "$current" | tr -d ' ') issues from $ide)${NC}"
    continue
  fi

  echo -e "${BOLD}Checking $ide against baseline/$branch...${NC}"

  if [ ! -f "$baseline" ]; then
    status=1
    echo -e "${RED}${BOLD}Error: no baseline for IDE branch $branch (verified $ide).${NC}"
    echo "::error title=No verifier baseline for IDE branch $branch::$ide was verified but third_party/tool/baseline/$branch/verifier-baseline.txt does not exist, so its issues were never checked. Run ./third_party/tool/update_baselines.sh and commit the result."
    echo "| \`$ide\` | ? | :x: no baseline |" >> "$SUMMARY"
    continue
  fi

  recorded_from="$(sed -n 's/^# Generated from \([^ ]*\) .*/\1/p' "$baseline" | head -n 1)"

  # `tr -d '\r'` matters: baseline files are covered by `* text=auto` in
  # .gitattributes, so they check out CRLF on Windows. Without this, every
  # baselined line would fail to match and be reported as new.
  new="$(LC_ALL=C comm -13 \
    <(grep -v '^#' "$baseline" | tr -d '\r' | LC_ALL=C sort -u) \
    "$current")"

  n_new=$([ -z "$new" ] && echo 0 || printf '%s\n' "$new" | wc -l | tr -d ' ')

  drift_note=""
  if [ -n "$recorded_from" ] && [ "$recorded_from" != "$ide" ]; then
    drift_note="baseline was recorded against \`$recorded_from\`, now verifying \`$ide\`"
  fi

  if [ "$n_new" -eq 0 ]; then
    echo -e "${GREEN}No new issues for $ide.${NC}"
    echo "| \`$ide\` | 0 | :white_check_mark: ok |" >> "$SUMMARY"
    continue
  fi

  status=1
  echo -e "${RED}${BOLD}$n_new new verifier issue(s) for $ide:${NC}"
  printf '%s\n' "$new" | sed 's/^/  /'

  # Collapse to a single annotation so it renders at the top of the run page.
  # `%` must be escaped to `%25` first, before `%0A` newlines are introduced,
  # or GitHub mis-parses the workflow command.
  annotation="$(printf '%s\n' "$new" | head -n 20 \
    | sed 's/%/%25/g; s/\r/%0D/g; s/\t/: /' \
    | sed -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')"
  echo "::error title=$n_new new verifier issue(s) in $ide::$annotation"

  details+=$'\n'"<details open><summary><b>$ide — $n_new new issue(s)</b></summary>"$'\n'
  [ -n "$drift_note" ] && details+=$'\n'"_${drift_note}_"$'\n'
  details+=$'\n```\n'"$(printf '%s\n' "$new" | sed 's/\t/\n    /')"$'\n```\n'
  details+="</details>"$'\n'

  echo "| \`$ide\` | $n_new | :x: new issues |" >> "$SUMMARY"
done

if [ "$MODE" = update ]; then
  echo -e "${BOLD}Done updating baselines.${NC}"
  exit 0
fi

[ -n "$details" ] && printf '%s\n' "$details" >> "$SUMMARY"

if [ "$status" -ne 0 ]; then
  {
    echo
    echo "> [!WARNING]"
    echo "> If these changes are expected (for example a new IDE EAP build), refresh"
    echo "> the baselines by running \`./third_party/tool/update_baselines.sh\` from the"
    echo "> repository root and committing the result."
  } >> "$SUMMARY"

  echo
  echo -e "${RED}${BOLD}Build failed: new verification issues were detected.${NC}"
  echo -e "${YELLOW}To update the baselines with these new issues, run:${NC}"
  echo -e "${YELLOW}  ./third_party/tool/update_baselines.sh${NC}"
  echo -e "${YELLOW}from the repository root and commit the changes.${NC}"
  exit 1
fi

echo -e "${GREEN}${BOLD}Verification passed: no new issues.${NC}"
