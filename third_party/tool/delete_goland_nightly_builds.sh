#!/bin/bash

# To run this script locally from the repository root:
# JB_MARKETPLACE_TOKEN="your_real_token" ./third_party/tool/delete_goland_nightly_builds.sh [--dry-run]

set -e

DRY_RUN=false
if [ "$1" == "--dry-run" ]; then
  DRY_RUN=true
  echo "Running in dry-run mode. No updates will actually be deleted."
fi

DART_KEYSTORE_ID=74840
DART_KEYSTORE_NAME=jetbrains-plugin-upload-auth-token

if [ -n "$JB_MARKETPLACE_TOKEN" ]; then
  TOKEN="$JB_MARKETPLACE_TOKEN"
  echo "Using token from JB_MARKETPLACE_TOKEN environment variable."
else
  KOKORO_TOKEN_FILE="${KOKORO_KEYSTORE_DIR}/${DART_KEYSTORE_ID}_${DART_KEYSTORE_NAME}"
  if [ ! -f "$KOKORO_TOKEN_FILE" ]; then
    echo "Error: Keystore token file not found at $KOKORO_TOKEN_FILE"
    echo "Please set JB_MARKETPLACE_TOKEN or ensure KOKORO_KEYSTORE_DIR is set correctly."
    exit 1
  fi
  TOKEN=$(cat "$KOKORO_TOKEN_FILE")
fi

PLUGIN_ID=6351
CHANNEL="goland-nightly"

# Verify jq is available
if ! command -v jq &> /dev/null; then
  echo "Error: jq is required but not installed."
  exit 1
fi

echo "Scanning all pages to collect updates in channel '$CHANNEL'..."

PAGE=1
ALL_UPDATES=""
TOTAL_COUNT=0

while true; do
  UPDATES_JSON=$(curl -s "https://plugins.jetbrains.com/api/plugins/$PLUGIN_ID/updates?channel=$CHANNEL&page=$PAGE")
  UPDATE_COUNT=$(echo "$UPDATES_JSON" | jq '. | length')
  
  if [ "$UPDATE_COUNT" -eq 0 ]; then
    break
  fi
  
  TOTAL_COUNT=$((TOTAL_COUNT + UPDATE_COUNT))
  echo "Page $PAGE: found $UPDATE_COUNT updates (Total collected: $TOTAL_COUNT)."
  
  # Append to our list of JSON objects
  PAGE_UPDATES=$(echo "$UPDATES_JSON" | jq -c '.[] | {id: .id, version: .version, date: (.cdate | tonumber | (./1000) | strftime("%Y-%m-%d"))}')
  if [ -z "$ALL_UPDATES" ]; then
    ALL_UPDATES="$PAGE_UPDATES"
  else
    ALL_UPDATES="${ALL_UPDATES}
${PAGE_UPDATES}"
  fi
  
  PAGE=$((PAGE + 1))
done

if [ "$TOTAL_COUNT" -eq 0 ]; then
  echo "No updates found in '$CHANNEL' channel."
  exit 0
fi

echo "Finished scanning. Preparing to process $TOTAL_COUNT updates."

while read -r UPDATE; do
  if [ -z "$UPDATE" ]; then
    continue
  fi

  ID=$(echo "$UPDATE" | jq -r '.id')
  VERSION=$(echo "$UPDATE" | jq -r '.version')
  DATE=$(echo "$UPDATE" | jq -r '.date')
  
  if [ "$DRY_RUN" = true ]; then
    echo "Would delete update ID $ID (Version: $VERSION, Date: $DATE)"
  else
    echo "Deleting update ID $ID (Version: $VERSION, Date: $DATE)..."
    STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
      --header "Authorization: Bearer $TOKEN" \
      "https://plugins.jetbrains.com/api/updates/$ID")
    
    if [ "$STATUS_CODE" -ge 200 ] && [ "$STATUS_CODE" -lt 300 ]; then
      echo "Successfully deleted update ID $ID."
    else
      echo "Failed to delete update ID $ID. HTTP status code: $STATUS_CODE"
    fi
  fi
done <<< "$ALL_UPDATES"

echo "Done. Processed $TOTAL_COUNT updates."
