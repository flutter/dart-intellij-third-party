// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.dart.analysisServer;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.dartlsp.api.LspServer;
import com.intellij.platform.dartlsp.api.LspServerManager;
import com.intellij.platform.dartlsp.api.LspServerManagerListener;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.lsp.DartBridgeLspServerManager;
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DartServerIntentionsTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    DartAnalysisServerService.getInstance(getProject()).serverReadyForRequest();
    myFixture.setTestDataPath(DartTestUtils.BASE_TEST_DATA_PATH + getBasePath());
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
  }

  @Override
  protected String getBasePath() {
    return "/analysisServer/intentions";
  }

  private void doTest(@NotNull final String intentionName) {
    myFixture.configureByFile(getTestName(false) + ".dart");
    final IntentionAction intention = myFixture.findSingleIntention(intentionName);

    ApplicationManager.getApplication().runWriteAction(() -> intention.invoke(getProject(), getEditor(), getFile()));

    myFixture.checkResultByFile(getTestName(false) + ".after.dart");
  }

  public void testIntroduceVariableNoSelection() {
    doTest("Assign value to local variable");
  }

  public void testSurroundWithTryCatch() {
    // TODO selection in the 'after' file is incorrect
    doTest("Surround with 'try-catch'");
  }

  public void testIntentionsOrder() {
    myFixture.configureByText("foo.dart", """
      void f() {
        <selection><caret>var x = 3;</selection>
      }
      """);
    final List<String> intentions = ContainerUtil.map(myFixture.getAvailableIntentions(), IntentionAction::getText);
    assertOrderedEquals(intentions,
                        "Surround with block",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'if'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'while'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'for-in'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'for'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'do-while'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'try-catch'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Surround with 'try-finally'",
                        "Edit intention settings",
                        "Disable 'Quick assists powered by the Dart Analysis Server'",
                        "Assign shortcut…",
                        "Adjust code style settings",
                        "Assign shortcut…"
    );
  }

  public void testIntentionPreview() {
    myFixture.configureByText("foo.dart", """
      main ( )  {
       Foo<caret>
         }""");
    IntentionAction action = myFixture.findSingleIntention("Assign value to local variable");
    assertEquals("""
                   main ( )  {
                    var foo = Foo
                      }""", myFixture.getIntentionPreviewText(action));
  }

  public void testLspSourceActionsExcludedFromIntentions() {
    final DartSdk sdk = DartSdk.getDartSdk(getProject());
    assertNotNull(sdk);
    assertTrue(
      "Expected Dart SDK >= " + DartAnalysisServerService.MIN_LSP_CODE_ACTIONS_SDK_VERSION + " (got " + sdk.getVersion() + ")",
      DartAnalysisServerService.isDartSdkVersionSufficientForLspCodeActions(sdk.getVersion())
    );

    final VirtualFile file = myFixture.configureByText("foo.dart", """
      import 'dart:math';
      import 'dart:async';

      void b() {}
      void a() {
        <selection><caret>var x = 3;</selection>
      }
      """).getVirtualFile();

    final AtomicBoolean fileOpenedOnLspServer = new AtomicBoolean(false);
    LspServerManager.getInstance(getProject()).addLspServerManagerListener(new LspServerManagerListener() {
      @Override
      public void fileOpened(@NotNull LspServer lspServer, @NotNull VirtualFile openedFile) {
        if (lspServer.getProviderClass() == DartLspServerSupportProvider.class && file.equals(openedFile)) {
          fileOpenedOnLspServer.set(true);
        }
      }
    }, getTestRootDisposable(), false);

    final DartAnalysisServerService das = DartAnalysisServerService.getInstance(getProject());
    final DartBridgeLspServerManager bridgeManager = getProject().getService(DartBridgeLspServerManager.class);
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    try {
      assertTrue(DartConfigurable.isLspCodeActionsEnabled(getProject()));
      das.updateClientCapabilities();
      bridgeManager.startBridgeServer();

      PlatformTestUtil.waitWithEventsDispatching(
        "Timed out waiting for Dart LSP server to open " + file.getName(),
        fileOpenedOnLspServer::get,
        15
      );
      myFixture.doHighlighting();

      final List<String> intentions = ContainerUtil.map(myFixture.getAvailableIntentions(), IntentionAction::getText);
      assertContainsElements(intentions, "Surround with block");
      assertDoesntContain(intentions, "Sort Members", "Organize Imports", "Fix All");
    }
    finally {
      bridgeManager.stopBridgeServer();
      DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), false);
      das.updateClientCapabilities();
    }
  }
}
