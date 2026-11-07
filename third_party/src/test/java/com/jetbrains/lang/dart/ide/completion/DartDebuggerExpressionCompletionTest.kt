// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.completion

import com.google.gson.JsonParser
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.BoundVariable
import com.jetbrains.lang.dart.util.DartElementGenerator

class DartDebuggerExpressionCompletionTest : DartCodeInsightFixtureTestCase() {

  private fun completeInDebuggerFragment(fileTextWithBreakpointMarker: String, fragmentExpression: String): List<String> {
    val breakpointMarker = "/*breakpoint*/"
    val markerIndex = fileTextWithBreakpointMarker.indexOf(breakpointMarker)
    assertTrue("Expected $breakpointMarker in source text", markerIndex >= 0)
    val cleanFileText = fileTextWithBreakpointMarker.replaceFirst(breakpointMarker, "")

    val psiFile = myFixture.configureByText("test.dart", cleanFileText)
    val contextElement = XDebuggerUtil.getInstance().findContextElement(psiFile.virtualFile, markerIndex, project, false)
    assertNotNull("Expected valid context PsiElement at breakpoint", contextElement)

    val fragment = DartElementGenerator.createExpressionCodeFragment(project, fragmentExpression, contextElement)
    myFixture.configureFromExistingVirtualFile(fragment.virtualFile)
    myFixture.completeBasic()
    return myFixture.lookupElementStrings ?: emptyList()
  }

  fun testLexicalParametersAndVariablesBeforeBreakpoint() {
    val suggestions = completeInDebuggerFragment(
      """
      final int globalCounter = 0;
      String topLevelHelper() => 'ok';

      class BaseService {
        int inheritedField = 1;
        static int inheritedStatic = 99;
      }

      class MyService extends BaseService {
        final String serviceName = 'main';
        static int staticConfig = 42;

        int compute(int inputParam) {
          final beforeBreak = inputParam + 1;
          /*breakpoint*/final onBreakpointLine = beforeBreak * 2;
          final afterBreak = onBreakpointLine + 1;
          return afterBreak;
        }

        void instanceHelper() {}
      }
      """.trimIndent(),
      "<caret>",
    )

    assertContainsElements(
      suggestions,
      "inputParam",
      "beforeBreak",
      "this",
      "serviceName",
      "inheritedField",
      "staticConfig",
      "compute",
      "instanceHelper",
      "globalCounter",
      "topLevelHelper",
      "MyService",
    )
    assertDoesntContain(suggestions, "onBreakpointLine", "afterBreak", "inheritedStatic", "internal")
  }

  fun testBreakpointAtClosingBraceIncludesAllBlockLocals() {
    val suggestions = completeInDebuggerFragment(
      """
      void runTask(int param) {
        final firstLocal = param + 1;
        final secondLocal = firstLocal + 2;
      /*breakpoint*/}
      """.trimIndent(),
      "<caret>",
    )

    assertContainsElements(suggestions, "param", "firstLocal", "secondLocal")
  }

  fun testClosureInInitializerIncludesParametersAndLocals() {
    val suggestions = completeInDebuggerFragment(
      """
      final topLevelClosure = (int closureParam) {
        final closureLocal = closureParam * 2;
        /*breakpoint*/return closureLocal;
      };
      """.trimIndent(),
      "<caret>",
    )

    assertContainsElements(suggestions, "closureParam", "closureLocal", "topLevelClosure")
  }

  fun testStaticMethodExcludesInstanceMembersAndThis() {
    val suggestions = completeInDebuggerFragment(
      """
      class Counter {
        int instanceCount = 0;
        static int totalCreated = 1;

        void instanceMethod() {}
        static void staticHelper() {}

        static int createCount(int seed) {
          final localSeed = seed + 1;
          /*breakpoint*/return localSeed + totalCreated;
        }
      }
      """.trimIndent(),
      "<caret>",
    )

    assertContainsElements(suggestions, "seed", "localSeed", "totalCreated", "staticHelper", "createCount")
    assertDoesntContain(suggestions, "this", "instanceCount", "instanceMethod")
  }

  fun testFactoryConstructorExcludesInstanceMembersAndThis() {
    val suggestions = completeInDebuggerFragment(
      """
      class Counter {
        int instanceCount = 0;
        static int defaultInitial = 10;

        Counter._(this.instanceCount);

        factory Counter.create(int initialValue) {
          final normalized = initialValue.abs();
          /*breakpoint*/return Counter._(normalized + defaultInitial);
        }
      }
      """.trimIndent(),
      "<caret>",
    )

    assertContainsElements(suggestions, "initialValue", "normalized", "defaultInitial")
    assertDoesntContain(suggestions, "this", "instanceCount")
  }

  fun testInstanceMemberAccessSuggestsReceiverMembersOnly() {
    val suggestions = completeInDebuggerFragment(
      """
      final int globalValue = 1;

      class Counter {
        int value = 0;
        static int staticTotal = 5;

        void increment(int step) {
          final counter = this;
          /*breakpoint*/value += step;
        }
      }
      """.trimIndent(),
      "counter.<caret>",
    )

    assertContainsElements(suggestions, "value", "increment")
    assertDoesntContain(suggestions, "globalValue", "staticTotal", "step", "counter", "this")
  }

  fun testClassReceiverAccessSuggestsStaticMembersAndNamedConstructors() {
    val suggestions = completeInDebuggerFragment(
      """
      class Counter {
        int instanceValue = 0;
        static int instancesCreated = 0;

        Counter._(this.instanceValue);
        factory Counter.create(int initial) => Counter._(initial);

        static int computeInitial(int seed) => seed * 2;
        void increment() {}
      }

      void main() {
        /*breakpoint*/final c = Counter.create(1);
      }
      """.trimIndent(),
      "Counter.<caret>",
    )

    assertContainsElements(suggestions, "instancesCreated", "computeInitial", "create", "_")
    assertDoesntContain(suggestions, "instanceValue", "increment")
  }

  fun testEnumReceiverAndChainedCallAndCycleSafety() {
    val enumSuggestions = completeInDebuggerFragment(
      """
      enum Status { idle, running, stopped }

      void main() {
        /*breakpoint*/final current = Status.idle;
      }
      """.trimIndent(),
      "Status.<caret>",
    )
    assertContainsElements(enumSuggestions, "idle", "running", "stopped")

    val chainedSuggestions = completeInDebuggerFragment(
      """
      class Counter {
        int value = 0;
        static int staticField = 1;
        factory Counter.create(int seed) => Counter();
        void reset() {}
      }

      void main() {
        /*breakpoint*/final c = Counter.create(1);
      }
      """.trimIndent(),
      "Counter.create(1).<caret>",
    )
    assertContainsElements(chainedSuggestions, "value", "reset")
    assertDoesntContain(chainedSuggestions, "staticField", "create")

    val cyclicSuggestions = completeInDebuggerFragment(
      """
      void main() {
        var cyclic = cyclic;
        /*breakpoint*/return;
      }
      """.trimIndent(),
      "cyclic.<caret>",
    )
    assertEmpty(cyclicSuggestions)
  }

  fun testBoundVariableLookupsFilterSyntheticsAndSentinels() {
    val boundVariables = listOf(
      BoundVariable(
        JsonParser.parseString(
          """
          {
            "type": "BoundVariable",
            "name": "count",
            "value": {
              "type": "@Instance",
              "id": "instances/1",
              "kind": "Int",
              "class": {"type": "@Class", "id": "classes/1", "name": "_Smi"},
              "valueAsString": "42"
            }
          }
          """.trimIndent()
        ).asJsonObject
      ),
      BoundVariable(
        JsonParser.parseString(
          """
          {
            "type": "BoundVariable",
            "name": "user",
            "value": {
              "type": "@Instance",
              "id": "instances/2",
              "kind": "PlainInstance",
              "class": {"type": "@Class", "id": "classes/2", "name": "User"}
            }
          }
          """.trimIndent()
        ).asJsonObject
      ),
      BoundVariable(
        JsonParser.parseString(
          """
          {
            "type": "BoundVariable",
            "name": ":async_op",
            "value": {
              "type": "@Instance",
              "id": "instances/3",
              "kind": "Closure",
              "class": {"type": "@Class", "id": "classes/3", "name": "_Closure"}
            }
          }
          """.trimIndent()
        ).asJsonObject
      ),
      BoundVariable(
        JsonParser.parseString(
          """
          {
            "type": "BoundVariable",
            "name": "optimizedOutVar",
            "value": {
              "type": "Sentinel",
              "kind": "OptimizedOut",
              "valueAsString": "<optimized out>"
            }
          }
          """.trimIndent()
        ).asJsonObject
      ),
    )

    val seenNames = HashSet<String>()
    val lookups = DartDebuggerExpressionCompletionContributor.createBoundVariableLookups(boundVariables, seenNames)
    assertEquals(listOf("count", "user"), lookups.map { it.lookupString })

    val countPresentation = LookupElementPresentation()
    lookups[0].renderElement(countPresentation)
    assertEquals("int", countPresentation.typeText)

    val userPresentation = LookupElementPresentation()
    lookups[1].renderElement(userPresentation)
    assertEquals("User", userPresentation.typeText)
  }
}
