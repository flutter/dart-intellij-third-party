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

echo "Fetching updates for Dart plugin ($PLUGIN_ID) in channel '$CHANNEL'..."
UPDATES_JSON=$(curl -s "https://plugins.jetbrains.com/api/plugins/$PLUGIN_ID/updates?channel=$CHANNEL")

# Verify jq is available
if ! command -v jq &> /dev/null; then
  echo "Error: jq is required but not installed."
  exit 1
fi

UPDATE_COUNT=$(echo "$UPDATES_JSON" | jq '. | length')
if [ "$UPDATE_COUNT" -eq 0 ]; then
  echo "No updates found in '$CHANNEL' channel."
  exit 0
fi

echo "Found $UPDATE_COUNT updates in channel '$CHANNEL'."

# Get list of objects with id, version, cdate
UPDATES=$(echo "$UPDATES_JSON" | jq -c '.[] | {id: .id, version: .version, date: (.cdate | tonumber | (./1000) | strftime("%Y-%m-%d"))}')

while read -r UPDATE; do
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
done <<< "$UPDATES"

echo "Done."
