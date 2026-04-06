#!/bin/bash

source ./third_party/tool/kokoro/setup.sh
setup

echo "kokoro build start"

cd third_party

VERSION=$(./gradlew -q printVersion -Pdev --no-configuration-cache | tail -n 1)

./gradlew buildPlugin -Pdev

echo "kokoro build finished"

echo "kokoro deploy start"

DART_KEYSTORE_ID=74840
DART_KEYSTORE_NAME=jetbrains-plugin-upload-auth-token

KOKORO_TOKEN_FILE="${KOKORO_KEYSTORE_DIR}/${DART_KEYSTORE_ID}_${DART_KEYSTORE_NAME}"
if [ ! -f "$KOKORO_TOKEN_FILE" ]; then
  echo "Error: Keystore token file not found at $KOKORO_TOKEN_FILE"
  exit 1
fi
TOKEN=$(cat "$KOKORO_TOKEN_FILE")

ZIP_FILE="build/distributions/Dart.zip"
if [ ! -f "$ZIP_FILE" ]; then
  echo "Error: Zip file not found at $ZIP_FILE"
  exit 1
fi

echo "Uploading $ZIP_FILE to JetBrains Marketplace..."
curl -i \
  --header "Authorization: Bearer $TOKEN" \
  -F pluginId=6351 \
  -F file=@"$ZIP_FILE" \
  -F channel=dev \
  https://plugins.jetbrains.com/plugin/uploadPlugin

echo "kokoro deploy finished"
