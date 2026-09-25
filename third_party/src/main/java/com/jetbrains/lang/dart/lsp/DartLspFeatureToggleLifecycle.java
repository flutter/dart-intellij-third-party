// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.lsp;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/** Coordinates persisted experimental-LSP changes with the required bridge restart. */
public final class DartLspFeatureToggleLifecycle {
  private DartLspFeatureToggleLifecycle() {
  }

  public static boolean applyIfChanged(boolean previousValue,
                                       boolean nextValue,
                                       @NotNull Consumer<Boolean> persistValue,
                                       @NotNull Runnable restartBridge) {
    if (previousValue == nextValue) return false;

    persistValue.accept(nextValue);
    restartBridge.run();
    return true;
  }
}
