// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.structure;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.DartComponentType;
import org.dartlang.analysis.server.protocol.Element;
import org.dartlang.analysis.server.protocol.ElementKind;
import org.dartlang.analysis.server.protocol.Outline;

public class DartStructureViewElementTest extends BasePlatformTestCase {

  public void testTypedefIcon() {
    PsiFile psiFile = myFixture.configureByText("test.dart", "");

    Element typeAlias = new Element(ElementKind.TYPE_ALIAS, "MyTypeAlias", null, 0, null, null, null, null);
    Outline typeAliasOutline = new Outline(null, typeAlias, 0, 10, 0, 10);
    DartStructureViewElement typeAliasElement = new DartStructureViewElement(psiFile, typeAliasOutline);
    assertEquals(DartComponentType.TYPEDEF.getIcon(), typeAliasElement.getIcon(false));

    Element functionTypeAlias = new Element(ElementKind.FUNCTION_TYPE_ALIAS, "MyFunctionTypeAlias", null, 0, null, null, null, null);
    Outline functionTypeAliasOutline = new Outline(null, functionTypeAlias, 0, 10, 0, 10);
    DartStructureViewElement functionTypeAliasElement = new DartStructureViewElement(psiFile, functionTypeAliasOutline);
    assertEquals(DartComponentType.TYPEDEF.getIcon(), functionTypeAliasElement.getIcon(false));
  }
}
