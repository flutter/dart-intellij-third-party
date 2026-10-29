// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.completion;

import com.intellij.icons.AllIcons;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.dartlang.analysis.server.protocol.Element;
import org.dartlang.analysis.server.protocol.ElementKind;

public class DartServerCompletionContributorTest extends BasePlatformTestCase {

  public void testTypedefBaseImage() {
    Element typeAlias = new Element(ElementKind.TYPE_ALIAS, "MyTypeAlias", null, 0, null, null, null, null);
    assertEquals(AllIcons.Nodes.Annotationtype, DartServerCompletionContributor.getBaseImage(typeAlias));

    Element functionTypeAlias = new Element(ElementKind.FUNCTION_TYPE_ALIAS, "MyFunctionTypeAlias", null, 0, null, null, null, null);
    assertEquals(AllIcons.Nodes.Annotationtype, DartServerCompletionContributor.getBaseImage(functionTypeAlias));
  }
}
