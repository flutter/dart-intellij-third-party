#!/usr/bin/env bash
#
# Run the IntelliJ plugin verifier and refresh the committed baselines from the
# resulting reports. Run this from the repository root.
#
# The comparison logic lives in check_verifier_baselines.sh so that the "check"
# and "update" paths can never drift apart.

set -uo pipefail

BOLD=$'\033[1m'
NC=$'\033[0m'

THIRD_PARTY="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo -e "${BOLD}Running plugin verification...${NC}"
rm -rf "$THIRD_PARTY/build/reports/pluginVerifier"
(cd "$THIRD_PARTY" && ./gradlew verifyPlugin)

# `verifyPlugin` exits non-zero when it finds problems, which is exactly the
# case we want to re-baseline, so its status is intentionally ignored here.
# check_verifier_baselines.sh fails if no reports were produced at all.

exec "$THIRD_PARTY/tool/check_verifier_baselines.sh" update
