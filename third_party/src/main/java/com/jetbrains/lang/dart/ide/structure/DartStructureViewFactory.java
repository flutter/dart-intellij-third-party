// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.structure;

import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.platform.dartlsp.impl.structureView.LspStructureViewFactory;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DartStructureViewFactory implements PsiStructureViewFactory {
  @Override
  public @Nullable StructureViewBuilder getStructureViewBuilder(final @NotNull PsiFile psiFile) {
    if (DartConfigurable.isExperimentalLspFeaturesEnabled(psiFile.getProject())) {
      StructureViewBuilder lspBuilder = new LspStructureViewFactory().getStructureViewBuilder(psiFile);
      if (!(lspBuilder instanceof TreeBasedStructureViewBuilder treeBasedLspBuilder)) {
        return lspBuilder;
      }
      return new TreeBasedStructureViewBuilder() {
        @Override
        public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
          return treeBasedLspBuilder.createStructureViewModel(editor);
        }

        @Override
        public @NotNull StructureView createStructureView(FileEditor fileEditor, @NotNull Project project, StructureViewModel model) {
          StructureView view = super.createStructureView(fileEditor, project, model);
          configureAutoScrollFromSource(view, project);
          return view;
        }

        @Override
        public boolean isRootNodeShown() {
          return treeBasedLspBuilder.isRootNodeShown();
        }
      };
    }
    return new TreeBasedStructureViewBuilder() {
      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new DartStructureViewModel(editor, psiFile);
      }

      @Override
      public @NotNull StructureView createStructureView(FileEditor fileEditor, @NotNull Project project, StructureViewModel model) {
        StructureView view = super.createStructureView(fileEditor, project, model);
        configureAutoScrollFromSource(view, project);
        return view;
      }

      @Override
      public boolean isRootNodeShown() {
        return false;
      }
    };
  }

  /**
   * Ensures the current editor element is selected in {@link StructureViewComponent} when:
   * <ol>
   *   <li>The component is attached to the tool window hierarchy and becomes showing (working around
   *       {@code StructureViewComponent.addEditorCaretListener()} calling {@code scrollToSelectedElement()}
   *       during construction while {@code isShowing()} is still {@code false}).</li>
   *   <li>The underlying {@link StructureViewModel} fires an initial model update while initial selection
   *       is still pending (e.g. when asynchronous outline data arrives for a newly opened file).</li>
   * </ol>
   */
  static void configureAutoScrollFromSource(@NotNull StructureView view, @NotNull Project project) {
    if (!(view instanceof StructureViewComponent component)) {
      return;
    }

    AtomicBoolean pendingInitialSelection = new AtomicBoolean(true);

    HierarchyListener hierarchyListener = e -> {
      if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && component.isShowing()) {
        pendingInitialSelection.set(true);
        scheduleScrollToSelectedElement(component, project, pendingInitialSelection);
      }
    };
    component.addHierarchyListener(hierarchyListener);
    Disposer.register(component, () -> component.removeHierarchyListener(hierarchyListener));

    ModelListener modelListener = () -> {
      if (pendingInitialSelection.get()) {
        UIUtil.invokeLaterIfNeeded(() -> {
          if (!component.isDisposed() && pendingInitialSelection.get()) {
            component.rebuildAndUpdate().onProcessed(ignore -> scheduleScrollToSelectedElement(component, project, pendingInitialSelection));
          }
        });
      }
    };

    StructureViewModel model = component.getTreeModel();
    model.addModelListener(modelListener);
    Disposer.register(component, () -> model.removeModelListener(modelListener));

    if (component.isShowing()) {
      scheduleScrollToSelectedElement(component, project, pendingInitialSelection);
    }
  }

  private static void scheduleScrollToSelectedElement(@NotNull StructureViewComponent component,
                                                      @NotNull Project project,
                                                      @NotNull AtomicBoolean pendingInitialSelection) {
    if (component.isDisposed() || project.isDisposed() || !component.isShowing()) {
      return;
    }
    if (StructureViewFactory.getInstance(project) instanceof StructureViewFactoryImpl factory &&
        !factory.getState().AUTOSCROLL_FROM_SOURCE) {
      return;
    }
    StructureViewModel model = component.getTreeModel();
    ReadAction.nonBlocking(model::getCurrentEditorElement)
      .withDocumentsCommitted(project)
      .expireWith(component)
      .finishOnUiThread(ModalityState.defaultModalityState(), element -> {
        if (element != null) {
          pendingInitialSelection.set(false);
        }
        if (element != null && !component.isDisposed() && !UIUtil.isFocusAncestor(component)) {
          component.select(element, false);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }
}