// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

void main() {
  final stopwatch = Stopwatch()..start();
  var value = 1;
  while (stopwatch.elapsedMilliseconds < 2000) {
    value = ((value * 31) ^ stopwatch.elapsedMicroseconds) & 0x7fffffff;
  }
  print('profile workload: $value');
}
