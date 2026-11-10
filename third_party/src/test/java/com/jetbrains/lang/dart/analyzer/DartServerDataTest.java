/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.analyzer;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class DartServerDataTest extends DartCodeInsightFixtureTestCase {
  public void testComputedErrorsRestartsDaemonWithReason() {
    final PsiFile psiFile = myFixture.addFileToProject("sample.dart", "void main() {}\n");
    final DartFileInfo fileInfo = DartFileInfoKt.getDartFileInfo(getProject(), psiFile.getVirtualFile().getUrl());
    assertNotNull(fileInfo.findFile());
    final DartServerData serverData = new DartServerData(DartAnalysisServerService.getInstance(getProject()));

    final DaemonCodeAnalyzer delegate = DaemonCodeAnalyzer.getInstance(getProject());
    final AtomicInteger zeroArgRestarts = new AtomicInteger();
    final List<Object> restartReasons = new CopyOnWriteArrayList<>();
    final Disposable spyDisposable = Disposer.newDisposable();
    try {
      ServiceContainerUtil.replaceService(getProject(), DaemonCodeAnalyzer.class, new DaemonCodeAnalyzer() {
        @Override
        public void restart() {
          zeroArgRestarts.incrementAndGet();
        }

        @Override
        public void restart(@NotNull Object reason) {
          restartReasons.add(reason);
        }

        @Override
        public void settingsChanged() {
          delegate.settingsChanged();
        }

        @Override
        public void setUpdateByTimerEnabled(boolean value) {
          delegate.setUpdateByTimerEnabled(value);
        }

        @Override
        public void disableUpdateByTimer(@NotNull Disposable parentDisposable) {
          delegate.disableUpdateByTimer(parentDisposable);
        }

        @Override
        public boolean isHighlightingAvailable(@NotNull PsiFile file) {
          return delegate.isHighlightingAvailable(file);
        }

        @Override
        public void setImportHintsEnabled(@NotNull PsiFile file, boolean value) {
          delegate.setImportHintsEnabled(file, value);
        }

        @Override
        @SuppressWarnings("removal")
        public void resetImportHintsEnabledForProject() {
        }

        @Override
        public void setHighlightingEnabled(@NotNull PsiFile file, boolean value) {
          delegate.setHighlightingEnabled(file, value);
        }

        @Override
        public boolean isImportHintsEnabled(@NotNull PsiFile file) {
          return delegate.isImportHintsEnabled(file);
        }

        @Override
        public boolean isAutohintsAvailable(@NotNull PsiFile file) {
          return delegate.isAutohintsAvailable(file);
        }

        @Override
        public void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file) {
          delegate.autoImportReferenceAtCursor(editor, file);
        }
      }, spyDisposable);

      assertTrue(serverData.computedErrors(fileInfo, Collections.emptyList(), false));
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      assertEquals(0, zeroArgRestarts.get());
      assertFalse(restartReasons.contains("Dart analysis server force file annotation"));

      assertTrue(serverData.computedErrors(fileInfo, Collections.emptyList(), true));
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      assertEquals(0, zeroArgRestarts.get());
      assertTrue("Expected restart reason in: " + restartReasons,
                 restartReasons.contains("Dart analysis server force file annotation"));
    }
    finally {
      Disposer.dispose(spyDisposable);
    }
  }
}
