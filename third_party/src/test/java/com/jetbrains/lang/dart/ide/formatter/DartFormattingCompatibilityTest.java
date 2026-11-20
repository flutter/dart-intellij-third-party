// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.ide.formatter;

import com.google.dart.server.AnalysisServerSocket;
import com.google.dart.server.FormatConsumer;
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer;
import com.google.dart.server.ShowMessageRequestConsumer;
import com.google.dart.server.UpdateContentConsumer;
import com.google.dart.server.internal.remote.ByteLineReaderStream;
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl;
import com.google.dart.server.internal.remote.RequestSink;
import com.google.dart.server.internal.remote.ResponseStream;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.actions.DartStyleAction;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams;
import org.dartlang.analysis.server.protocol.MessageAction;
import org.dartlang.analysis.server.protocol.RequestError;
import org.dartlang.analysis.server.protocol.RequestErrorCode;
import org.dartlang.analysis.server.protocol.SourceEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Core ownership and public batch compatibility, independent of action registration. */
public class DartFormattingCompatibilityTest extends DartCodeInsightFixtureTestCase {
  private static final String INPUT = "void main(){bad();}";
  private static final String FORMATTED = "void main() {\n  formatted();\n}";
  private final List<String> requests = new ArrayList<>();
  private Object previousSdkHome;
  private Object previousSdkVersion;
  private boolean sdkAligned;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DartAnalysisServerService service = DartAnalysisServerService.getInstance(getProject());
    DartSdk sdk = DartSdk.getDartSdk(getProject());
    assertNotNull(sdk);
    previousSdkHome = replaceServiceField(service, "mySdkHome", sdk.getHomePath());
    previousSdkVersion = replaceServiceField(service, "mySdkVersion", sdk.getVersion());
    sdkAligned = true;
    service.setServer(new RemoteAnalysisServerImpl(stubSocket()) {
      @Override public boolean isSocketOpen() { return true; }
      @Override public void edit_format(String file, int offset, int length, int lineLength, FormatConsumer consumer) {
        requests.add(file);
        if (file.endsWith("failure.dart")) {
          consumer.onError(new RequestError(RequestErrorCode.FORMAT_WITH_ERRORS, "format failed", null));
        }
        else {
          consumer.computedFormat(List.of(new SourceEdit(0, Integer.MAX_VALUE, FORMATTED, null, null)), 0, 0);
        }
      }
      @Override public void analysis_updateContent(Map<String, Object> files, UpdateContentConsumer consumer) { }
      @Override public void lsp_workspaceApplyEdit(DartLspApplyWorkspaceEditParams params,
                                                  DartLspWorkspaceApplyEditRequestConsumer consumer) { }
      @Override public void server_showMessageRequest(String type, String message, List<MessageAction> actions,
                                                      ShowMessageRequestConsumer consumer) { }
      @Override public void server_openUrlRequest(String url) { }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      DartAnalysisServerService service = DartAnalysisServerService.getInstance(getProject());
      service.setServer(null);
      if (sdkAligned) {
        replaceServiceField(service, "mySdkHome", previousSdkHome);
        replaceServiceField(service, "mySdkVersion", previousSdkVersion);
      }
      requests.clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    try {
      PropertiesComponent.getInstance(getProject()).unsetValue("dart.lsp.experimental.enabled");
      assertFalse(PropertiesComponent.getInstance(getProject()).isValueSet("dart.lsp.experimental.enabled"));
      assertTrue(DartConfigurable.isExperimentalLspFeaturesEnabled(getProject()));
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testPublicBatchApiRemainsAvailableUnderLspAndPreservesPartialSuccess() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    VirtualFile success = myFixture.addFileToProject("lib/success.dart", INPUT).getVirtualFile();
    VirtualFile failure = myFixture.addFileToProject("lib/failure.dart", "void main(){broken").getVirtualFile();
    DartStyleAction.runDartfmt(getProject(), List.of(failure, success));
    assertEquals(FORMATTED, document(success).getText());
    assertEquals("void main(){broken", document(failure).getText());
    assertEquals(List.of(fileUri(failure), fileUri(success)), requests);
  }

  public void testPostprocessorYieldsOnlyWhenExperimentalLspOwnsFormatting() {
    for (boolean enabled : new boolean[]{true, false}) {
      DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), enabled);
      myFixture.configureByText("postprocessor.dart", INPUT);
      requests.clear();
      TextRange range = TextRange.allOf(INPUT);
      TextRange result = new DartPostFormatProcessor().processText(myFixture.getFile(), range, CodeStyle.getSettings(getProject()));
      assertEquals(enabled ? INPUT : FORMATTED, myFixture.getEditor().getDocument().getText());
      assertEquals(enabled ? range : TextRange.allOf(FORMATTED), result);
      assertEquals(enabled ? List.of() : List.of(fileUri(myFixture.getFile().getVirtualFile())), requests);
    }
  }

  private String fileUri(VirtualFile file) {
    return DartAnalysisServerService.getInstance(getProject()).getFileUri(file);
  }

  private Document document(VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    return document;
  }

  private static Object replaceServiceField(DartAnalysisServerService service, String name, Object value) throws Exception {
    var field = DartAnalysisServerService.class.getDeclaredField(name);
    field.setAccessible(true);
    Object previous = field.get(service);
    field.set(service, value);
    return previous;
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
}
