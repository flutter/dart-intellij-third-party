// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.ide.hierarchy

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.dartlang.analysis.server.protocol.Element
import org.dartlang.analysis.server.protocol.ElementKind
import org.dartlang.analysis.server.protocol.Location
import org.dartlang.analysis.server.protocol.TypeHierarchyItem

class DartHierarchyUtilTest : BasePlatformTestCase() {

  fun testFilterSuperClassesEmptyList() {
    assertEmpty(DartHierarchyUtil.filterSuperClasses(project, emptyList()))
  }

  fun testFilterSuperClassesWalksExtendsChainAndIgnoresInterfacesAndMixins() {
    val file = myFixture.configureByText(
      "hierarchy.dart",
      """
      class GrandParent {}
      class Parent extends GrandParent {}
      mixin MixinA {}
      class InterfaceA {}
      class Child extends Parent with MixinA implements InterfaceA {}
      """.trimIndent()
    )
    val fileUrl = file.virtualFile.url
    val text = file.text

    val items = listOf(
      typeHierarchyItem(
        name = "Child",
        fileUrl = fileUrl,
        offset = text.indexOf("Child"),
        superclass = 1,
        interfaces = intArrayOf(3),
        mixins = intArrayOf(2)
      ),
      typeHierarchyItem(
        name = "Parent",
        fileUrl = fileUrl,
        offset = text.indexOf("Parent extends"),
        superclass = 4
      ),
      typeHierarchyItem(
        name = "MixinA",
        fileUrl = fileUrl,
        offset = text.indexOf("MixinA {")
      ),
      typeHierarchyItem(
        name = "InterfaceA",
        fileUrl = fileUrl,
        offset = text.indexOf("InterfaceA {")
      ),
      typeHierarchyItem(
        name = "GrandParent",
        fileUrl = fileUrl,
        offset = text.indexOf("GrandParent {")
      )
    )

    val superClasses = DartHierarchyUtil.filterSuperClasses(project, items)
    assertEquals(listOf("Parent", "GrandParent"), superClasses.map { it.name })
  }

  fun testFilterSuperClassesStopsOnCycle() {
    val file = myFixture.configureByText(
      "cycle.dart",
      """
      class A extends B {}
      class B extends A {}
      """.trimIndent()
    )
    val fileUrl = file.virtualFile.url
    val text = file.text

    val items = listOf(
      typeHierarchyItem(name = "A", fileUrl = fileUrl, offset = text.indexOf("A extends"), superclass = 1),
      typeHierarchyItem(name = "B", fileUrl = fileUrl, offset = text.indexOf("B extends"), superclass = 0)
    )

    val superClasses = DartHierarchyUtil.filterSuperClasses(project, items)
    assertEquals(listOf("B", "A"), superClasses.map { it.name })
  }

  fun testFilterSuperClassesStopsOnOutOfBoundsSuperIndex() {
    val file = myFixture.configureByText(
      "bounds.dart",
      """
      class Parent {}
      class Child extends Parent {}
      """.trimIndent()
    )
    val fileUrl = file.virtualFile.url
    val text = file.text

    val negativeSuperItems = listOf(
      typeHierarchyItem(name = "Child", fileUrl = fileUrl, offset = text.indexOf("Child"), superclass = -1)
    )
    assertEmpty(DartHierarchyUtil.filterSuperClasses(project, negativeSuperItems))

    val tooLargeSuperItems = listOf(
      typeHierarchyItem(name = "Child", fileUrl = fileUrl, offset = text.indexOf("Child"), superclass = 1),
      typeHierarchyItem(name = "Parent", fileUrl = fileUrl, offset = text.indexOf("Parent {"), superclass = 99)
    )
    val superClasses = DartHierarchyUtil.filterSuperClasses(project, tooLargeSuperItems)
    assertEquals(listOf("Parent"), superClasses.map { it.name })
  }

  fun testFilterSuperClassesSkipsNonClassOrMissingLocations() {
    val file = myFixture.configureByText(
      "missing.dart",
      """
      void topLevelFunction() {}
      class GrandParent {}
      class Child {}
      """.trimIndent()
    )
    val fileUrl = file.virtualFile.url
    val text = file.text

    val items = listOf(
      typeHierarchyItem(name = "Child", fileUrl = fileUrl, offset = text.indexOf("Child"), superclass = 1),
      typeHierarchyItem(name = "Missing", fileUrl = "temp:///src/does_not_exist.dart", offset = 0, superclass = 2),
      typeHierarchyItem(name = "topLevelFunction", fileUrl = fileUrl, offset = text.indexOf("topLevelFunction"), superclass = 3),
      typeHierarchyItem(name = "GrandParent", fileUrl = fileUrl, offset = text.indexOf("GrandParent {"))
    )

    val superClasses = DartHierarchyUtil.filterSuperClasses(project, items)
    assertEquals(listOf("GrandParent"), superClasses.map { it.name })
  }

  private fun typeHierarchyItem(
    name: String,
    fileUrl: String,
    offset: Int,
    superclass: Int? = null,
    interfaces: IntArray = IntArray(0),
    mixins: IntArray = IntArray(0),
    subclasses: IntArray = IntArray(0),
  ): TypeHierarchyItem {
    val location = Location(fileUrl, offset, name.length, 1, 1, 1, 1 + name.length)
    val element = Element(ElementKind.CLASS, name, location, 0, null, null, null, null)
    return TypeHierarchyItem(element, null, null, superclass, interfaces, mixins, subclasses)
  }
}
