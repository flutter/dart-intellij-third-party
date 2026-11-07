/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.structure

import com.intellij.ide.structureView.FileEditorPositionListener
import com.intellij.ide.structureView.ModelListener
import com.intellij.ide.structureView.StructureViewFactory
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.Grouper
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.awt.event.HierarchyEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import javax.swing.tree.TreePath

class DartStructureViewFactoryTest : DartCodeInsightFixtureTestCase() {

  private var previousAutoscrollFromSource: Boolean = false

  override fun setUp() {
    super.setUp()
    val factory = StructureViewFactory.getInstance(project) as? StructureViewFactoryImpl
    if (factory != null) {
      previousAutoscrollFromSource = factory.state.AUTOSCROLL_FROM_SOURCE
    }
  }

  override fun tearDown() {
    try {
      val factory = StructureViewFactory.getInstance(project) as? StructureViewFactoryImpl
      if (factory != null) {
        factory.state.AUTOSCROLL_FROM_SOURCE = previousAutoscrollFromSource
      }
      DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
    }
    finally {
      super.tearDown()
    }
  }

  fun testLegacyBuilderIsTreeBasedAndHidesRootNode() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
    val psiFile = myFixture.addFileToProject("lib/sample.dart", "int calculate() => 42;")

    val builder = DartStructureViewFactory().getStructureViewBuilder(psiFile)
    assertTrue("Expected TreeBasedStructureViewBuilder in legacy mode", builder is TreeBasedStructureViewBuilder)
    val treeBuilder = builder as TreeBasedStructureViewBuilder
    assertFalse("Root node should be hidden", treeBuilder.isRootNodeShown)

    val model = treeBuilder.createStructureViewModel(null)
    try {
      assertTrue("Expected DartStructureViewModel in legacy mode", model is DartStructureViewModel)
    }
    finally {
      Disposer.dispose(model)
    }
  }

  fun testSelectsCurrentElementWhenComponentBecomesShowing() {
    setAutoscrollFromSource(true)
    val model = FakeStructureViewModel(currentElement = "calculateSymbol")
    val component = RecordingStructureViewComponent(model)
    try {
      // Initially not showing during construction (mirrors StructureViewWrapperImpl.rebuildImpl)
      component.showing = false
      DartStructureViewFactory.configureAutoScrollFromSource(component, project)
      drainAsyncTasks()
      assertTrue("Should not select while component is not showing", component.selectedElements.isEmpty())

      // Simulate ContentManager.addContent attaching component to tool window hierarchy
      component.showing = true
      fireShowingChanged(component)
      drainAsyncTasks()

      assertEquals(listOf("calculateSymbol" to false), component.selectedElements)
    }
    finally {
      Disposer.dispose(component)
      Disposer.dispose(model)
    }
  }

  fun testDoesNotSelectWhenAutoscrollFromSourceIsDisabled() {
    setAutoscrollFromSource(false)
    val model = FakeStructureViewModel(currentElement = "calculateSymbol")
    val component = RecordingStructureViewComponent(model)
    try {
      component.showing = false
      DartStructureViewFactory.configureAutoScrollFromSource(component, project)

      component.showing = true
      fireShowingChanged(component)
      drainAsyncTasks()

      assertTrue(
        "Should not auto-select when AUTOSCROLL_FROM_SOURCE is disabled",
        component.selectedElements.isEmpty()
      )
    }
    finally {
      Disposer.dispose(component)
      Disposer.dispose(model)
    }
  }

  fun testColdCacheModelUpdateSelectsOnceAndIgnoresSubsequentTypingUpdates() {
    setAutoscrollFromSource(true)
    // Simulate cold cache on first open: outline is not yet available (currentElement == null)
    val model = FakeStructureViewModel(currentElement = null)
    val component = RecordingStructureViewComponent(model)
    try {
      component.showing = false
      DartStructureViewFactory.configureAutoScrollFromSource(component, project)

      // Component becomes showing while outline is still null
      component.showing = true
      fireShowingChanged(component)
      drainAsyncTasks()
      assertTrue("No selection yet while outline is null", component.selectedElements.isEmpty())

      // Asynchronous outline arrives from DAS and triggers fireModelUpdate()
      model.currentElement = "calculateSymbol"
      model.fireModelUpdate()
      drainAsyncTasks()

      assertEquals(1, component.rebuildAndUpdateCount)
      assertEquals(listOf("calculateSymbol" to false), component.selectedElements)

      // Subsequent model updates while typing in the editor should NOT trigger extra rebuildAndUpdate/select
      model.currentElement = "editedSymbol"
      model.fireModelUpdate()
      drainAsyncTasks()

      assertEquals(
        "Subsequent typing model updates must not trigger extra rebuildAndUpdate calls",
        1,
        component.rebuildAndUpdateCount
      )
      assertEquals(
        "Subsequent typing model updates must not override selection via initial-selection listener",
        listOf("calculateSymbol" to false),
        component.selectedElements
      )
    }
    finally {
      Disposer.dispose(component)
      Disposer.dispose(model)
    }
  }

  fun testReShowingComponentReTriggersSelection() {
    setAutoscrollFromSource(true)
    val model = FakeStructureViewModel(currentElement = "firstSymbol")
    val component = RecordingStructureViewComponent(model)
    try {
      component.showing = true
      DartStructureViewFactory.configureAutoScrollFromSource(component, project)
      drainAsyncTasks()
      assertEquals(listOf("firstSymbol" to false), component.selectedElements)

      // Hide and re-show tool window after caret moved to secondSymbol
      component.showing = false
      fireShowingChanged(component)
      model.currentElement = "secondSymbol"
      component.showing = true
      fireShowingChanged(component)
      drainAsyncTasks()

      assertEquals(
        listOf("firstSymbol" to false, "secondSymbol" to false),
        component.selectedElements
      )
    }
    finally {
      Disposer.dispose(component)
      Disposer.dispose(model)
    }
  }

  fun testListenersRemovedOnComponentDispose() {
    setAutoscrollFromSource(true)
    val model = FakeStructureViewModel(currentElement = null)
    val component = RecordingStructureViewComponent(model)
    DartStructureViewFactory.configureAutoScrollFromSource(component, project)
    assertTrue("ModelListener should be registered", model.modelListeners.isNotEmpty())

    Disposer.dispose(component)
    Disposer.dispose(model)
    assertTrue("ModelListener should be removed upon component disposal", model.modelListeners.isEmpty())
  }

  private fun setAutoscrollFromSource(enabled: Boolean) {
    val factory = StructureViewFactory.getInstance(project) as StructureViewFactoryImpl
    factory.state.AUTOSCROLL_FROM_SOURCE = enabled
  }

  private fun fireShowingChanged(component: StructureViewComponent) {
    val event = HierarchyEvent(
      component,
      HierarchyEvent.HIERARCHY_CHANGED,
      component,
      component.parent,
      HierarchyEvent.SHOWING_CHANGED.toLong()
    )
    for (listener in component.hierarchyListeners) {
      listener.hierarchyChanged(event)
    }
  }

  private fun drainAsyncTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private inner class RecordingStructureViewComponent(
    model: StructureViewModel,
  ) : StructureViewComponent(null, model, project, false) {
    var showing: Boolean = false
    var rebuildAndUpdateCount: Int = 0
    val selectedElements = CopyOnWriteArrayList<Pair<Any?, Boolean>>()

    override fun isShowing(): Boolean = showing

    override fun select(element: Any?, requestFocus: Boolean): Promise<TreePath> {
      selectedElements.add(element to requestFocus)
      return AsyncPromise<TreePath>().apply { setResult(null) }
    }

    override fun rebuildAndUpdate(): AsyncPromise<Void> {
      rebuildAndUpdateCount++
      val promise = AsyncPromise<Void>()
      promise.setResult(null)
      return promise
    }
  }

  private class FakeStructureViewModel(
    @Volatile var currentElement: Any?,
  ) : StructureViewModel {
    val modelListeners = CopyOnWriteArrayList<ModelListener>()

    override fun getCurrentEditorElement(): Any? = currentElement

    override fun addEditorPositionListener(listener: FileEditorPositionListener) {}

    override fun removeEditorPositionListener(listener: FileEditorPositionListener) {}

    override fun addModelListener(modelListener: ModelListener) {
      modelListeners.add(modelListener)
    }

    override fun removeModelListener(modelListener: ModelListener) {
      modelListeners.remove(modelListener)
    }

    fun fireModelUpdate() {
      for (listener in modelListeners) {
        listener.onModelChanged()
      }
    }

    override fun getRoot(): StructureViewTreeElement = object : StructureViewTreeElement {
      override fun getValue(): Any = "root"
      override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = "root"
        override fun getIcon(unused: Boolean): Icon? = null
      }
      override fun getChildren(): Array<StructureViewTreeElement> = emptyArray()
      override fun navigate(requestFocus: Boolean) {}
      override fun canNavigate(): Boolean = false
      override fun canNavigateToSource(): Boolean = false
    }

    override fun getGroupers(): Array<Grouper> = Grouper.EMPTY_ARRAY

    override fun getSorters(): Array<Sorter> = Sorter.EMPTY_ARRAY

    override fun getFilters(): Array<Filter> = Filter.EMPTY_ARRAY

    override fun shouldEnterElement(element: Any?): Boolean = false

    override fun dispose() {}
  }
}
