// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

final retainedObjects = <Object>[];

class GenericBox<T extends num> {
  GenericBox(this.value);

  final T value;
}

T identity<T>(T value) => value;

void main() {
  retainedObjects.add(GenericBox<int>(identity<int>(42)));
  print(retainedObjects.single);
}
