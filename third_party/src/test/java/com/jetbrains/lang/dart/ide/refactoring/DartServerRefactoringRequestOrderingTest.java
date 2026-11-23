// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.ide.refactoring;

import com.google.dart.server.AnalysisServerSocket;
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer;
import com.google.dart.server.GetRefactoringConsumer;
import com.google.dart.server.ShowMessageRequestConsumer;
import com.google.dart.server.UpdateContentConsumer;
import com.google.dart.server.internal.remote.ByteLineReaderStream;
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl;
import com.google.dart.server.internal.remote.RequestSink;
import com.google.dart.server.internal.remote.ResponseStream;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.sdk.DartSdk;
import org.dartlang.analysis.server.protocol.*;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class DartServerRefactoringRequestOrderingTest extends DartCodeInsightFixtureTestCase {
  private final List<GetRefactoringConsumer> requests = new ArrayList<>();
  private final List<Boolean> pendingStates = new ArrayList<>();
  private Consumer<GetRefactoringConsumer> onRequest;
  private ServerRenameRefactoring refactoring;
  private Runnable feedbackGate;
  private SourceChange latestChange;
  private SourceChange obsoleteChange;
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
    service.setServer(new ControlledServer());

    var file = myFixture.configureByText("ordering.dart", "void main() { var original = 1; print(original); }").getVirtualFile();
    refactoring = new ServerRenameRefactoring(getProject(), file, 18, 8) {
      @Override
      protected void setFeedback(RefactoringFeedback feedback) {
        super.setFeedback(feedback);
        if (feedbackGate != null) {
          feedbackGate.run();
        }
      }
    };
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
    catch (Throwable e) {
      addSuppressedException(e);
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
        assertRequestsStillPending();
      }
      complete(latest, "latest", latestChange, List.of());
      if (!cancelFirst) {
        assertRequestsStillPending();
        cancel(obsolete);
      }
      assertAllRequestsCompleted();
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
      assertRequestsStillPending();
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
      assertRequestsStillPending();
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
      onRequest = inner -> {
        complete(inner, "latest", latestChange, List.of());
        assertRequestsStillPending();
      };
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
    assertRequestsStillPending();
    complete(requests.get(1), "latest", latestChange, List.of());
    assertRequestsStillPending();
    complete(requests.get(0), "obsolete", obsoleteChange, List.of());

    assertLatestResult();
    assertAllRequestsCompleted();
  }

  public void testBlockedFeedbackSerializesCallbacksAndRecovers() throws Exception {
    refactoring.setNewName("obsolete");
    refactoring.setNewName("latest");
    assertEquals(2, requests.size());
    assertRequestsStillPending();
    GetRefactoringConsumer obsolete = requests.get(0);
    GetRefactoringConsumer latest = requests.get(1);

    // Replace the ArrayList-backed UI listener before any concurrent callbacks.
    AtomicReference<Boolean> hasPendingRequests = new AtomicReference<>(true);
    AtomicReference<RefactoringStatus> latestStatus = new AtomicReference<>();
    refactoring.setListener((pending, status) -> {
      hasPendingRequests.set(pending);
      latestStatus.set(status);
    });
    CountDownLatch feedbackEntered = new CountDownLatch(1);
    CountDownLatch releaseFeedback = new CountDownLatch(1);
    feedbackGate = () -> {
      feedbackEntered.countDown();
      try {
        assertTrue("Artificial feedback gate timed out", releaseFeedback.await(30, TimeUnit.SECONDS));
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Feedback worker interrupted", e);
      }
    };
    ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
    CountDownLatch callbacksCompleted = new CountDownLatch(2);
    Thread latestWorker = callbackThread("latest-refactoring-callback", () -> {
      complete(latest, "latest", latestChange, List.of());
      callbacksCompleted.countDown();
    }, failures);
    Thread obsoleteWorker = callbackThread("obsolete-refactoring-callback", () -> {
      complete(obsolete, "obsolete", obsoleteChange,
               List.of(new RefactoringProblem(RefactoringProblemSeverity.ERROR, "obsolete options", null)));
      callbacksCompleted.countDown();
    }, failures);
    try {
      latestWorker.start();
      assertTrue("Latest callback must enter feedback", feedbackEntered.await(5, TimeUnit.SECONDS));
      obsoleteWorker.start();
      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      boolean observedContention = false;
      while (System.nanoTime() - deadline < 0) {
        ThreadInfo info = threads.getThreadInfo(obsoleteWorker.threadId());
        if (info != null && info.getThreadState() == Thread.State.BLOCKED &&
            info.getLockOwnerId() == latestWorker.threadId()) {
          observedContention = true;
          break;
        }
        Thread.onSpinWait();
      }
      // This deliberately blocking override diagnoses serialization, not a production deadlock.
      assertTrue("Stale callback must contend on a monitor owned by the feedback worker", observedContention);
      assertEquals("Neither callback can finish while feedback holds the monitor", 2L, callbacksCompleted.getCount());
    }
    finally {
      releaseFeedback.countDown();
      awaitCallbackWorkers(latestWorker, obsoleteWorker, failures);
    }

    assertEquals("Both callbacks must return normally after release", 0L, callbacksCompleted.getCount());
    assertEquals("Both pending requests must be removed", Boolean.FALSE, hasPendingRequests.get());
    assertNotNull(latestStatus.get());
    assertTrue("Obsolete problems must not replace latest status: " + latestStatus.get(), latestStatus.get().isOK());
    assertLatestResult();
  }

  private static Thread callbackThread(String name, Runnable callback, ConcurrentLinkedQueue<Throwable> failures) {
    return new Thread(() -> {
      try {
        callback.run();
      }
      catch (Throwable failure) {
        failures.add(failure);
      }
    }, name);
  }

  private static void awaitCallbackWorkers(Thread latest, Thread obsolete, ConcurrentLinkedQueue<Throwable> failures) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    boolean interrupted = false;
    for (Thread worker : List.of(latest, obsolete)) {
      long remaining;
      while (worker.isAlive() && (remaining = deadline - System.nanoTime()) > 0) {
        try {
          TimeUnit.NANOSECONDS.timedJoin(worker, remaining);
        }
        catch (InterruptedException e) {
          interrupted = true;
          failures.add(e);
        }
      }
      if (worker.isAlive()) {
        failures.add(new AssertionError("Callback did not terminate: " + worker.getName()));
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    if (!failures.isEmpty()) {
      AssertionError error = new AssertionError("Concurrent callback failure");
      failures.forEach(error::addSuppressed);
      throw error;
    }
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

  private void assertRequestsStillPending() {
    assertFalse("Pending state must be observable through the listener", pendingStates.isEmpty());
    assertEquals("Completing one request must not hide another pending request", Boolean.TRUE, pendingStates.getLast());
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
