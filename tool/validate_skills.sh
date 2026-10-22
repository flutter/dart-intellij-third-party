#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail on errors.
set -e

SCRIPT_DIR="${BASH_SOURCE[0]%/*}"
if [[ "$SCRIPT_DIR" != "${BASH_SOURCE[0]}" ]]; then
  cd "$SCRIPT_DIR"
fi

echo "Resolving dependencies for skills_lint..."
dart pub get

echo "Running skills_lint..."
dart run skills_lint "$@"
