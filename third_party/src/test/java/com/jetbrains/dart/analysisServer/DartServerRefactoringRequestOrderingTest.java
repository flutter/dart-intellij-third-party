// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.dart.analysisServer;

import com.google.dart.server.AnalysisServerSocket;
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer;
import com.google.dart.server.DartLspWorkspaceConfigurationConsumer;
import com.google.dart.server.GetRefactoringConsumer;
import com.google.dart.server.ShowMessageRequestConsumer;
import com.google.dart.server.UpdateContentConsumer;
import com.google.dart.server.internal.remote.ByteLineReaderStream;
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl;
import com.google.dart.server.internal.remote.RequestSink;
import com.google.dart.server.internal.remote.ResponseStream;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.refactoring.ServerRenameRefactoring;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.dartlang.analysis.server.protocol.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class DartServerRefactoringRequestOrderingTest extends DartCodeInsightFixtureTestCase {
  private final List<GetRefactoringConsumer> requests = new ArrayList<>();
  private final List<Boolean> pendingStates = new ArrayList<>();
  private Consumer<GetRefactoringConsumer> onRequest;
  private ServerRenameRefactoring refactoring;
  private SourceChange latestChange;
  private SourceChange obsoleteChange;
  private Object previousSdkHome;
  private Object previousSdkVersion;
  private boolean sdkAligned;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(getModule(), myFixture.getProjectDisposable(), false);
    DartAnalysisServerService service = DartAnalysisServerService.getInstance(getProject());
    DartSdk sdk = DartSdk.getDartSdk(getProject());
    assertNotNull(sdk);
    previousSdkHome = replaceServiceField(service, "mySdkHome", sdk.getHomePath());
    previousSdkVersion = replaceServiceField(service, "mySdkVersion", sdk.getVersion());
    sdkAligned = true;
    service.setServer(new ControlledServer());

    var file = myFixture.configureByText("ordering.dart", "void main() { var original = 1; print(original); }").getVirtualFile();
    refactoring = new ServerRenameRefactoring(getProject(), file, 18, 8);
    latestChange = change(file.getPath(), "latest");
    obsoleteChange = change(file.getPath(), "obsolete");
    onRequest = consumer -> complete(consumer, "original", latestChange, List.of());
    RefactoringStatus initial = refactoring.checkInitialConditions();
    assertNotNull(initial);
    assertTrue(initial.toString(), initial.isOK());
    assertEquals(1, requests.size());
    requests.clear();
    refactoring.setListener((pending, status) -> pendingStates.add(pending));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (refactoring != null) {
        refactoring.setListener(null);
      }
      DartAnalysisServerService service = DartAnalysisServerService.getInstance(getProject());
      service.setServer(null);
      if (sdkAligned) {
        replaceServiceField(service, "mySdkHome", previousSdkHome);
        replaceServiceField(service, "mySdkVersion", previousSdkVersion);
      }
      onRequest = null;
      requests.clear();
      pendingStates.clear();
    }
    finally {
      super.tearDown();
    }
  }

  public void testObsoleteCancellationBeforeLatestSuccess() {
    assertObsoleteCancellationIgnored(true);
  }

  public void testObsoleteCancellationAfterLatestSuccess() {
    assertObsoleteCancellationIgnored(false);
  }

  private void assertObsoleteCancellationIgnored(boolean cancelFirst) {
    GetRefactoringConsumer obsolete = startValidation();
    onRequest = latest -> {
      if (cancelFirst) {
        cancel(obsolete);
      }
      complete(latest, "latest", latestChange, List.of());
      if (!cancelFirst) {
        cancel(obsolete);
      }
    };

    RefactoringStatus status = refactoring.checkFinalConditions();
    assertNotNull(status);
    assertTrue("Obsolete cancellation must not poison latest validation: " + status, status.isOK());
    assertLatestResult();
    assertAllRequestsCompleted();
  }

  public void testObsoleteSuccessCannotReplaceLatestFeedbackOrChange() {
    GetRefactoringConsumer obsolete = startValidation();
    onRequest = latest -> {
      complete(latest, "latest", latestChange, List.of());
      complete(obsolete, "obsolete", obsoleteChange, List.of());
    };

    RefactoringStatus status = refactoring.checkFinalConditions();
    assertNotNull(status);
    assertTrue(status.toString(), status.isOK());
    assertLatestResult();
    assertAllRequestsCompleted();
  }

  public void testObsoleteProblemsCannotReplaceLatestStatus() {
    GetRefactoringConsumer obsolete = startValidation();
    onRequest = latest -> {
      complete(latest, "latest", latestChange, List.of());
      complete(obsolete, "obsolete", obsoleteChange,
               List.of(new RefactoringProblem(RefactoringProblemSeverity.ERROR, "obsolete options", null)));
    };

    RefactoringStatus status = refactoring.checkFinalConditions();
    assertNotNull(status);
    assertTrue("Obsolete options problems must not replace latest status: " + status, status.isOK());
    assertLatestResult();
    assertAllRequestsCompleted();
  }

  public void testCurrentCancellationRemainsFatal() {
    assertCurrentErrorRemainsFatal(RequestErrorCode.REFACTORING_REQUEST_CANCELLED);
  }

  public void testCurrentServerErrorRemainsFatal() {
    assertCurrentErrorRemainsFatal(RequestErrorCode.SERVER_ERROR);
  }

  private void assertCurrentErrorRemainsFatal(String code) {
    onRequest = consumer -> consumer.onError(new RequestError(code, "current request failed", null));
    RefactoringStatus status = refactoring.checkFinalConditions();
    assertNotNull(status);
    assertTrue("Current request errors must remain fatal: " + status, status.hasFatalError());
    assertEquals("Server error: current request failed", status.getMessage());
    assertAllRequestsCompleted();
  }

  public void testSynchronousReentrantDispatchKeepsNewestResult() {
    // The nested request completes before the outer server dispatch returns.
    onRequest = outer -> {
      onRequest = inner -> complete(inner, "latest", latestChange, List.of());
      refactoring.setNewName("latest");
      complete(outer, "obsolete", obsoleteChange, List.of());
    };
    refactoring.setNewName("outer");

    assertEquals(2, requests.size());
    assertLatestResult();
    assertAllRequestsCompleted();
  }

  public void testReentrantDispatchReturnDoesNotRestoreObsoleteOwnership() {
    // Leave the nested request pending until both dispatch calls have returned.
    onRequest = outer -> refactoring.setNewName("latest");
    refactoring.setNewName("outer");
    assertEquals(2, requests.size());
    complete(requests.get(1), "latest", latestChange, List.of());
    complete(requests.get(0), "obsolete", obsoleteChange, List.of());

    assertLatestResult();
    assertAllRequestsCompleted();
  }

  private GetRefactoringConsumer startValidation() {
    refactoring.setNewName("latest");
    assertEquals(1, requests.size());
    assertFalse(pendingStates.isEmpty());
    assertEquals(Boolean.TRUE, pendingStates.getLast());
    return requests.getFirst();
  }

  private void assertLatestResult() {
    assertEquals("Latest feedback must be retained", "latest", refactoring.getOldName());
    assertSame("Latest source edits must be retained", latestChange, refactoring.getChange());
    assertEquals(Set.of("latest-edit"), refactoring.getPotentialEdits());
  }

  private void assertAllRequestsCompleted() {
    assertFalse("Completion must notify the listener", pendingStates.isEmpty());
    assertEquals("Even obsolete callbacks must finish their pending request", Boolean.FALSE, pendingStates.getLast());
  }

  private static void cancel(GetRefactoringConsumer consumer) {
    consumer.onError(new RequestError(RequestErrorCode.REFACTORING_REQUEST_CANCELLED, "obsolete request cancelled", null));
  }

  private static void complete(GetRefactoringConsumer consumer, String name, SourceChange change,
                               List<RefactoringProblem> optionsProblems) {
    consumer.computedRefactorings(List.of(), optionsProblems, List.of(),
                                 new RenameFeedback(18, 8, "local variable", name), change, List.of(name + "-edit"));
  }

  private static SourceChange change(String path, String replacement) {
    return new SourceChange("Rename to " + replacement,
                            List.of(new SourceFileEdit(path, 0, List.of(new SourceEdit(18, 8, replacement, null, null)))),
                            List.of(), null, null, null);
  }

  private static Object replaceServiceField(DartAnalysisServerService service, String name, Object value) throws Exception {
    var field = DartAnalysisServerService.class.getDeclaredField(name);
    field.setAccessible(true);
    Object previous = field.get(service);
    field.set(service, value);
    return previous;
  }

  private class ControlledServer extends RemoteAnalysisServerImpl {
    ControlledServer() {
      super(stubSocket());
    }

    @Override
    public boolean isSocketOpen() {
      return true;
    }

    @Override
    public void edit_getRefactoring(String kind, String file, int offset, int length, boolean validateOnly,
                                    RefactoringOptions options, GetRefactoringConsumer consumer) {
      requests.add(consumer);
      Consumer<GetRefactoringConsumer> callback = onRequest;
      onRequest = null;
      if (callback != null) {
        callback.accept(consumer);
      }
    }

    @Override
    public void analysis_updateContent(Map<String, Object> files, UpdateContentConsumer consumer) {
    }

    @Override
    public void lsp_workspaceApplyEdit(DartLspApplyWorkspaceEditParams params, DartLspWorkspaceApplyEditRequestConsumer consumer) {
    }

    @Override
    public void lsp_workspaceConfiguration(List<String> sections, DartLspWorkspaceConfigurationConsumer consumer) {
    }

    @Override
    public void server_showMessageRequest(String type, String message, List<MessageAction> actions,
                                          ShowMessageRequestConsumer consumer) {
    }

    @Override
    public void server_openUrlRequest(String url) {
    }
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
