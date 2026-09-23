// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.ide.formatter;

import com.google.dart.server.AnalysisServerSocket;
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer;
import com.google.dart.server.FormatConsumer;
import com.google.dart.server.ShowMessageRequestConsumer;
import com.google.dart.server.UpdateContentConsumer;
import com.google.dart.server.internal.remote.ByteLineReaderStream;
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl;
import com.google.dart.server.internal.remote.RequestSink;
import com.google.dart.server.internal.remote.ResponseStream;
import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.actions.DartStyleAction;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams;
import org.dartlang.analysis.server.protocol.MessageAction;
import org.dartlang.analysis.server.protocol.RequestError;
import org.dartlang.analysis.server.protocol.RequestErrorCode;
import org.dartlang.analysis.server.protocol.SourceEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DartFormattingCompatibilityTest extends DartCodeInsightFixtureTestCase {
  private final List<String> formattedRequests = new ArrayList<>();
  private FormatMode formatMode;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(getModule(), myFixture.getProjectDisposable(), false);
    alignSdkAndInstallFakeServer();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      DartAnalysisServerService.getInstance(getProject()).setServer(null);
      clearSdkAlignment();
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
    }
    finally {
      super.tearDown();
    }
  }

  public void testDirectEditorRoutingChangesOnlyWhenLegacyOwnsFormatting() {
    Document document = configure("void main(){bad();}");
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), false);
    myFixture.getEditor().getSelectionModel().setSelection(12, 17);
    exposedAction().runEditor(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertEquals("void main() {\n  formatted();\n}", document.getText());
    assertEquals(1, formattedRequests.size());

    document = configure("void main(){bad();}");
    formattedRequests.clear();
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    exposedAction().runEditor(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertEquals("void main(){bad();}", document.getText());
    assertTrue(formattedRequests.isEmpty());
  }

  public void testPostProcessorYieldsToLspOwnedEditorFormatting() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    Document document = configure("void main(){bad();}");
    TextRange inputRange = TextRange.allOf(document.getText());

    TextRange result = new DartPostFormatProcessor().processText(myFixture.getFile(), inputRange, CodeStyle.getSettings(getProject()));

    assertEquals(inputRange, result);
    assertEquals("void main(){bad();}", document.getText());
    assertTrue(formattedRequests.isEmpty());
  }

  public void testPostProcessorUsesDasWhenLspOwnershipIsDisabled() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), false);
    Document document = configure("void main(){bad();}");
    formatMode = FormatMode.SUCCESS;
    TextRange result = new DartPostFormatProcessor().processText(myFixture.getFile(), TextRange.allOf(document.getText()), CodeStyle.getSettings(getProject()));
    assertEquals(document.getTextLength(), result.getLength());
    assertEquals("void main() {\n  formatted();\n}", document.getText());
    assertEquals(1, formattedRequests.size());

    formattedRequests.clear();
    formatMode = FormatMode.EMPTY;
    document = configure("void main() {}\n");
    new DartPostFormatProcessor().processText(myFixture.getFile(), TextRange.allOf(document.getText()), CodeStyle.getSettings(getProject()));
    assertEquals("void main() {}\n", document.getText());
    assertEquals(1, formattedRequests.size());

    formattedRequests.clear();
    formatMode = FormatMode.ERROR;
    document = configure("void main(){broken");
    new DartPostFormatProcessor().processText(myFixture.getFile(), TextRange.allOf(document.getText()), CodeStyle.getSettings(getProject()));
    assertEquals("void main(){broken", document.getText());
    assertEquals(1, formattedRequests.size());
  }

  public void testPublicFormattingKeepsPartialSuccessUnderLspFlag() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    VirtualFile success = myFixture.addFileToProject("lib/success.dart", "void main(){bad();}").getVirtualFile();
    VirtualFile failure = myFixture.addFileToProject("lib/failure.dart", "void main(){broken").getVirtualFile();
    DartStyleAction.runDartfmt(getProject(), List.of(success, failure));
    assertEquals("void main() {\n  formatted();\n}", getDocument(success).getText());
    assertEquals("void main(){broken", getDocument(failure).getText());
    assertEquals(2, formattedRequests.size());

  }

  public void testLegacyBatchEntryPointCannotRunWithLspEnabled() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    VirtualFile file = myFixture.addFileToProject("lib/batch.dart", "void main(){bad();}").getVirtualFile();
    withOkDialog(() -> exposedAction().runFiles(getProject(), List.of(file)));
    assertEquals("void main(){bad();}", getDocument(file).getText());
    assertTrue(formattedRequests.isEmpty());
  }

  public void testLegacyBatchStillRunsWithLspDisabled() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), false);
    VirtualFile file = myFixture.addFileToProject("lib/batch.dart", "void main(){bad();}").getVirtualFile();
    withOkDialog(() -> exposedAction().runFiles(getProject(), List.of(file)));
    assertEquals("void main() {\n  formatted();\n}", getDocument(file).getText());
    assertEquals(1, formattedRequests.size());
  }

  public void testRegisteredLspActionNeverFallsBackToLegacyOnDirectInvocation() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    Document document = configure("void main(){bad();}");
    var action = ActionManager.getInstance().getAction("Dart.DartStyle");
    VirtualFile file = myFixture.getFile().getVirtualFile();
    for (boolean includeEditor : new boolean[]{false, true}) {
      for (String place : new String[]{ActionPlaces.PROJECT_VIEW_POPUP, ActionPlaces.EDITOR_POPUP}) {
        var context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject())
          .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{file});
        if (includeEditor) context.add(CommonDataKeys.EDITOR, myFixture.getEditor());
        var event = AnActionEvent.createEvent(action, context.build(), null, place, ActionUiKind.NONE, null);
        withOkDialog(() -> action.actionPerformed(event));
        assertEquals("void main(){bad();}", document.getText());
        assertTrue(formattedRequests.isEmpty());
      }
    }
  }

  public void testSdkDisabledPostProcessorDoesNotRequestFormatting() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), false);
    Document document = configure("void main(){bad();}");
    WriteAction.run(() -> DartSdkLibUtil.disableDartSdk(List.of(getModule())));
    new DartPostFormatProcessor().processText(myFixture.getFile(), TextRange.allOf(document.getText()), CodeStyle.getSettings(getProject()));
    assertEquals("void main(){bad();}", document.getText());
    assertTrue(formattedRequests.isEmpty());
  }

  public void testDirectoryExpansionRecursivelyFiltersDartFiles() throws Exception {
    VirtualFile nestedDart = myFixture.addFileToProject("lib/nested/child.dart", "void main() {}").getVirtualFile();
    myFixture.addFileToProject("lib/nested/ignored.txt", "ignored");
    var method = Class.forName("com.jetbrains.lang.dart.ide.actions.AbstractDartFileProcessingAction")
      .getDeclaredMethod("getApplicableVirtualFiles", Project.class, VirtualFile[].class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<VirtualFile> files = (List<VirtualFile>)method.invoke(null, getProject(), new VirtualFile[]{nestedDart.getParent().getParent()});
    assertEquals(List.of("child.dart"), files.stream().map(VirtualFile::getName).collect(Collectors.toList()));
  }

  private Document configure(String text) {
    myFixture.configureByText("matrix.dart", text);
    return getDocument(myFixture.getFile().getVirtualFile());
  }

  private Document getDocument(VirtualFile file) {
    return com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file);
  }

  private void withOkDialog(Runnable action) {
    TestDialogManager.setTestDialog(message -> Messages.OK);
    try {
      action.run();
    }
    finally {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
    }
  }

  private ExposedStyleAction exposedAction() {
    return new ExposedStyleAction();
  }

  private void alignSdkAndInstallFakeServer() throws Exception {
    DartAnalysisServerService service = DartAnalysisServerService.getInstance(getProject());
    DartSdk sdk = DartSdk.getDartSdk(getProject());
    assertNotNull(sdk);
    setServiceField(service, "mySdkHome", sdk.getHomePath());
    setServiceField(service, "mySdkVersion", sdk.getVersion());
    service.setServer(new RemoteAnalysisServerImpl(stubSocket()) {
      @Override
      public boolean isSocketOpen() {
        return true;
      }

      @Override
      public void edit_format(String file, int offset, int length, int lineLength, FormatConsumer consumer) {
        formattedRequests.add(file);
        if (file.contains("failure") || formatMode == FormatMode.ERROR) {
          consumer.onError(new RequestError(RequestErrorCode.FORMAT_WITH_ERRORS, "format failed", null));
        }
        else if (formatMode == FormatMode.EMPTY) {
          consumer.computedFormat(List.of(), offset, length);
        }
        else {
          consumer.computedFormat(List.of(new SourceEdit(0, Integer.MAX_VALUE, "void main() {\n  formatted();\n}", null, null)), 0, 0);
        }
      }

      @Override
      public void analysis_updateContent(java.util.Map<String, Object> files, UpdateContentConsumer consumer) {
      }

      @Override
      public void lsp_workspaceApplyEdit(DartLspApplyWorkspaceEditParams params, DartLspWorkspaceApplyEditRequestConsumer consumer) {
      }

      @Override
      public void server_showMessageRequest(String type, String message, List<MessageAction> actions,
                                            ShowMessageRequestConsumer consumer) {
      }

      @Override
      public void server_openUrlRequest(String url) {
      }
    });
  }

  private void clearSdkAlignment() throws Exception {
    DartAnalysisServerService service = DartAnalysisServerService.getInstance(getProject());
    setServiceField(service, "mySdkHome", null);
    setServiceField(service, "mySdkVersion", null);
  }

  private static void setServiceField(DartAnalysisServerService service, String name, Object value) throws Exception {
    var field = DartAnalysisServerService.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(service, value);
  }

  private static AnalysisServerSocket stubSocket() {
    return new AnalysisServerSocket() {
      @Override public ByteLineReaderStream getErrorStream() { return null; }
      @Override public RequestSink getRequestSink() { return null; }
      @Override public ResponseStream getResponseStream() { return null; }
      @Override public boolean isOpen() { return true; }
      @Override public void start() { }
      @Override public void stop() { }
    };
  }

  private enum FormatMode { SUCCESS, EMPTY, ERROR }

  private static class ExposedStyleAction extends DartStyleAction {
    void runEditor(Project project, com.intellij.openapi.editor.Editor editor, com.intellij.psi.PsiFile file) {
      runOverEditor(project, editor, file);
    }

    void runFiles(Project project, List<VirtualFile> files) {
      runOverFiles(project, files);
    }
  }
}
