// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.ide.actions;

import com.google.dart.server.AnalysisServerSocket;
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer;
import com.google.dart.server.FormatConsumer;
import com.google.dart.server.ShowMessageRequestConsumer;
import com.google.dart.server.UpdateContentConsumer;
import com.google.dart.server.internal.remote.ByteLineReaderStream;
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl;
import com.google.dart.server.internal.remote.RequestSink;
import com.google.dart.server.internal.remote.ResponseStream;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsConstants;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.psi.DartFile;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams;
import org.dartlang.analysis.server.protocol.MessageAction;
import org.dartlang.analysis.server.protocol.SourceEdit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DartStyleActionTest extends DartCodeInsightFixtureTestCase {
  private static final String INPUT = "void main(){bad();}";
  private static final String LEGACY_FORMATTED = "void main() {\n  legacy();\n}";

  private final List<String> legacyFormatRequests = new ArrayList<>();
  private Object previousSdkHome;
  private Object previousSdkVersion;
  private boolean sdkAligned;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final DartAnalysisServerService service = DartAnalysisServerService.getInstance(getProject());
    final DartSdk sdk = DartSdk.getDartSdk(getProject());
    assertNotNull(sdk);
    previousSdkHome = replaceServiceField(service, "mySdkHome", sdk.getHomePath());
    previousSdkVersion = replaceServiceField(service, "mySdkVersion", sdk.getVersion());
    sdkAligned = true;
    service.setServer(new RemoteAnalysisServerImpl(stubSocket()) {
      @Override
      public boolean isSocketOpen() {
        return true;
      }

      @Override
      public void edit_format(String file, int offset, int length, int lineLength, FormatConsumer consumer) {
        legacyFormatRequests.add(file);
        consumer.computedFormat(List.of(new SourceEdit(0, Integer.MAX_VALUE, LEGACY_FORMATTED, null, null)), 0, 0);
      }

      @Override
      public void analysis_updateContent(Map<String, Object> files, UpdateContentConsumer consumer) {
      }

      @Override
      public void lsp_workspaceApplyEdit(DartLspApplyWorkspaceEditParams params,
                                         DartLspWorkspaceApplyEditRequestConsumer consumer) {
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

  @Override
  protected void tearDown() throws Exception {
    try {
      final DartAnalysisServerService service = DartAnalysisServerService.getInstance(getProject());
      service.setServer(null);
      if (sdkAligned) {
        replaceServiceField(service, "mySdkHome", previousSdkHome);
        replaceServiceField(service, "mySdkVersion", previousSdkVersion);
      }
      legacyFormatRequests.clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    try {
      PropertiesComponent.getInstance(getProject()).unsetValue("dart.lsp.experimental.enabled");
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testVisibleAndEnabledAcrossPlacesWhenExperimentalLspEnabled() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    myFixture.configureByText("main.dart", INPUT);
    final AnAction action = getRegisteredDartStyleAction();
    final DataContext context = editorContext();

    for (String place : List.of(
      ActionPlaces.MAIN_MENU,
      ActionPlaces.EDITOR_POPUP,
      ActionPlaces.KEYBOARD_SHORTCUT,
      ActionPlaces.ACTION_SEARCH
    )) {
      final AnActionEvent event = createEvent(action, context, place, ActionUiKind.NONE);
      action.update(event);
      assertTrue("Expected visible in " + place, event.getPresentation().isVisible());
      assertTrue("Expected enabled in " + place, event.getPresentation().isEnabled());
      assertEquals(DartBundle.message("action.Dart.DartStyle.text"), event.getPresentation().getText());
    }
  }

  public void testVisibleAndEnabledForProjectViewFilesAndDirectories() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    final VirtualFile first = myFixture.addFileToProject("dir_a/a.dart", INPUT).getVirtualFile();
    final VirtualFile second = myFixture.addFileToProject("dir_b/b.dart", INPUT).getVirtualFile();
    final VirtualFile dirA = first.getParent();
    final VirtualFile dirB = second.getParent();
    final AnAction action = getRegisteredDartStyleAction();

    for (VirtualFile[] selection : List.of(
      new VirtualFile[]{first},
      new VirtualFile[]{first, second},
      new VirtualFile[]{dirA},
      new VirtualFile[]{dirA, dirB}
    )) {
      final DataContext context = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, getProject())
        .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, selection)
        .build();
      final AnActionEvent event = createEvent(action, context, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP);
      action.update(event);
      assertTrue(event.getPresentation().isVisible());
      assertTrue(event.getPresentation().isEnabled());
      assertEquals(DartBundle.message("dart.style.action.name.ellipsis"), event.getPresentation().getText());
    }
  }

  public void testEditorActionPerformedFormatsHostDartFileViaReformatCodeProcessor() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    myFixture.configureByText("main.dart", "void main(){final html = '<div><caret><p>hi</p></div>';}");
    final Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.getEditor());
    assertNotSame("Expected HTML injection at caret", hostEditor, myFixture.getEditor());
    final PsiFile hostFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(hostEditor.getDocument());
    assertNotNull(hostFile);
    final List<PsiFile> formattedFiles = new ArrayList<>();
    final List<Boolean> hadSelection = new ArrayList<>();
    final DartStyleAction action = new DartStyleAction() {
      @Override
      void runEditorReformatCodeProcessor(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        formattedFiles.add(psiFile);
        hadSelection.add(editor.getSelectionModel().hasSelection());
      }
    };
    assertFalse("DartStyleAction must not opt into injected context", action.isInInjectedContext());

    final DataContext context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.EDITOR, hostEditor)
      .build();
    final AnActionEvent event = createEvent(action, context, ActionPlaces.MAIN_MENU, ActionUiKind.NONE);
    action.update(event);
    assertTrue(event.getPresentation().isVisible());
    assertTrue(event.getPresentation().isEnabled());
    action.actionPerformed(event);

    assertEquals(1, formattedFiles.size());
    assertTrue("Expected host DartFile even when caret is inside an injected string", formattedFiles.getFirst() instanceof DartFile);
    assertSame(hostFile.getVirtualFile(), formattedFiles.getFirst().getVirtualFile());
    assertEquals(List.of(false), hadSelection);
    assertTrue("Legacy edit.format must not run when experimental LSP is enabled", legacyFormatRequests.isEmpty());
  }

  public void testEditorActionPerformedPreservesSelectionWhenFormattingViaReformatCodeProcessor() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    myFixture.configureByText("main.dart", "void a(){}\n<selection>void b(){}</selection>\n");
    final List<String> selectedTexts = new ArrayList<>();
    final DartStyleAction action = new DartStyleAction() {
      @Override
      void runEditorReformatCodeProcessor(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        assertTrue(editor.getSelectionModel().hasSelection());
        selectedTexts.add(editor.getSelectionModel().getSelectedText());
      }
    };

    final AnActionEvent event = createEvent(action, editorContext(), ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP);
    action.actionPerformed(event);

    assertEquals(List.of("void b(){}"), selectedTexts);
    assertTrue("Legacy edit.format must not run when experimental LSP is enabled", legacyFormatRequests.isEmpty());
  }

  public void testProjectViewDirectoryFormatsOnlyDartFilesViaReformatCodeProcessor() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    final VirtualFile dartA = myFixture.addFileToProject("mixed/a.dart", INPUT).getVirtualFile();
    final VirtualFile dartB = myFixture.addFileToProject("mixed/sub/b.dart", INPUT).getVirtualFile();
    myFixture.addFileToProject("mixed/Main.java", "class Main{}");
    myFixture.addFileToProject("mixed/pubspec.yaml", "name: demo\n");
    myFixture.addFileToProject("mixed/README.md", "# readme\n");
    final VirtualFile mixedDir = dartA.getParent();

    final List<Integer> confirmedCounts = new ArrayList<>();
    final List<PsiFile[]> processedBatches = new ArrayList<>();
    final DartStyleAction action = new DartStyleAction() {
      @Override
      boolean confirmRunOverFiles(@NotNull Project project, int fileCount) {
        confirmedCounts.add(fileCount);
        return true;
      }

      @Override
      void runReformatCodeProcessor(@NotNull Project project, @NotNull PsiFile[] psiFiles) {
        processedBatches.add(psiFiles);
      }
    };

    final DataContext context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{mixedDir})
      .build();
    final AnActionEvent event = createEvent(action, context, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP);
    action.actionPerformed(event);

    assertEquals(List.of(2), confirmedCounts);
    assertEquals(1, processedBatches.size());
    final Set<VirtualFile> formattedFiles = Arrays.stream(processedBatches.getFirst())
      .map(PsiFile::getVirtualFile)
      .collect(Collectors.toSet());
    assertEquals(Set.of(dartA, dartB), formattedFiles);
    assertTrue("Legacy edit.format must not run when experimental LSP is enabled", legacyFormatRequests.isEmpty());
  }

  public void testProjectViewMixedFilesAndMultipleDirectoriesFilterToDartFilesOnly() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    final VirtualFile dartA = myFixture.addFileToProject("dir_a/a.dart", INPUT).getVirtualFile();
    myFixture.addFileToProject("dir_a/notes.txt", "notes");
    final VirtualFile dartB = myFixture.addFileToProject("dir_b/b.dart", INPUT).getVirtualFile();
    final VirtualFile readme = myFixture.addFileToProject("README.md", "# hello").getVirtualFile();

    final List<PsiFile[]> processedBatches = new ArrayList<>();
    final DartStyleAction action = new DartStyleAction() {
      @Override
      boolean confirmRunOverFiles(@NotNull Project project, int fileCount) {
        return true;
      }

      @Override
      void runReformatCodeProcessor(@NotNull Project project, @NotNull PsiFile[] psiFiles) {
        processedBatches.add(psiFiles);
      }
    };

    final DataContext context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{dartA.getParent(), dartB, readme})
      .build();
    final AnActionEvent event = createEvent(action, context, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP);
    action.actionPerformed(event);

    assertEquals(1, processedBatches.size());
    final Set<VirtualFile> formattedFiles = Arrays.stream(processedBatches.getFirst())
      .map(PsiFile::getVirtualFile)
      .collect(Collectors.toSet());
    assertEquals(Set.of(dartA, dartB), formattedFiles);
    assertTrue("Legacy edit.format must not run when experimental LSP is enabled", legacyFormatRequests.isEmpty());
  }

  public void testProjectViewCancelledDialogDoesNotReformat() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    final VirtualFile dartFile = myFixture.addFileToProject("lib/a.dart", INPUT).getVirtualFile();
    final List<PsiFile[]> processedBatches = new ArrayList<>();
    final DartStyleAction action = new DartStyleAction() {
      @Override
      boolean confirmRunOverFiles(@NotNull Project project, int fileCount) {
        return false;
      }

      @Override
      void runReformatCodeProcessor(@NotNull Project project, @NotNull PsiFile[] psiFiles) {
        processedBatches.add(psiFiles);
      }
    };

    final DataContext context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{dartFile})
      .build();
    final AnActionEvent event = createEvent(action, context, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP);
    action.actionPerformed(event);

    assertTrue(processedBatches.isEmpty());
    assertTrue(legacyFormatRequests.isEmpty());
  }

  public void testProjectViewRunsRealReformatCodeProcessorWithoutLegacyEditFormat() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    final VirtualFile dartFile = myFixture.addFileToProject("lib/real.dart", INPUT).getVirtualFile();
    final DartStyleAction action = new DartStyleAction() {
      @Override
      boolean confirmRunOverFiles(@NotNull Project project, int fileCount) {
        return true;
      }
    };

    final DataContext context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{dartFile})
      .build();
    final AnActionEvent event = createEvent(action, context, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP);
    action.actionPerformed(event);

    assertTrue("Legacy edit.format must not run when experimental LSP is enabled", legacyFormatRequests.isEmpty());
  }

  public void testNonDartProjectViewSelectionDoesNotReformatOrReportAnalytics() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    final VirtualFile readme = myFixture.addFileToProject("non_dart/README.md", "# hello").getVirtualFile();
    final List<PsiFile[]> processedBatches = new ArrayList<>();
    final DartStyleAction action = new DartStyleAction() {
      @Override
      boolean confirmRunOverFiles(@NotNull Project project, int fileCount) {
        fail("Confirmation dialog must not appear when no Dart files are selected");
        return false;
      }

      @Override
      void runReformatCodeProcessor(@NotNull Project project, @NotNull PsiFile[] psiFiles) {
        processedBatches.add(psiFiles);
      }
    };

    for (VirtualFile[] selection : List.of(new VirtualFile[]{readme}, new VirtualFile[]{readme.getParent()})) {
      final DataContext context = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, getProject())
        .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, selection)
        .build();
      final List<AnalyticsData> reported = new ArrayList<>();
      final AnActionEvent event = createEvent(action, context, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP);

      Analytics.withReportObserver(reported::add, () -> action.actionPerformed(event));
      assertTrue(processedBatches.isEmpty());
      assertTrue(reported.isEmpty());
      assertTrue(legacyFormatRequests.isEmpty());
    }
  }

  public void testRegisteredActionRunsRealEditorReformatWithAndWithoutSelection() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    final AnAction action = getRegisteredDartStyleAction();
    for (String text : List.of(INPUT, "void a(){}\n<selection>void b(){}</selection>\n")) {
      myFixture.configureByText("main.dart", text);
      final List<AnalyticsData> reported = new ArrayList<>();
      final AnActionEvent event = createEvent(action, editorContext(), ActionPlaces.MAIN_MENU, ActionUiKind.NONE);

      Analytics.withReportObserver(reported::add, () -> action.actionPerformed(event));

      assertEquals(1, reported.size());
      assertEquals("Dart.DartStyle", reported.getFirst().getId());
      assertEquals("action", reported.getFirst().getData().get(AnalyticsConstants.TYPE.getName()));
      assertEquals(ActionPlaces.MAIN_MENU, reported.getFirst().getData().get(AnalyticsConstants.PLACE.getName()));
      assertTrue("Legacy edit.format must not run when experimental LSP is enabled", legacyFormatRequests.isEmpty());
    }
  }

  public void testNonDartFileIsHiddenAndDoesNotReformatOrReportAnalytics() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);
    myFixture.configureByText("README.md", "# hello");
    final List<PsiFile> formattedFiles = new ArrayList<>();
    final DartStyleAction action = new DartStyleAction() {
      @Override
      void runEditorReformatCodeProcessor(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        formattedFiles.add(psiFile);
      }
    };
    final List<AnalyticsData> reported = new ArrayList<>();
    final AnActionEvent event = createEvent(action, editorContext(), ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP);

    action.update(event);
    assertFalse(event.getPresentation().isVisible());
    assertFalse(event.getPresentation().isEnabled());

    Analytics.withReportObserver(reported::add, () -> action.actionPerformed(event));
    assertTrue(formattedFiles.isEmpty());
    assertTrue(reported.isEmpty());
    assertTrue(legacyFormatRequests.isEmpty());
  }

  public void testNullProjectDisablesAndNoOps() {
    final AnAction action = getRegisteredDartStyleAction();
    final AnActionEvent event = createEvent(action, DataContext.EMPTY_CONTEXT, ActionPlaces.MAIN_MENU, ActionUiKind.NONE);

    action.update(event);
    assertFalse(event.getPresentation().isVisible());
    assertFalse(event.getPresentation().isEnabled());

    final List<AnalyticsData> reported = new ArrayList<>();
    Analytics.withReportObserver(reported::add, () -> action.actionPerformed(event));
    assertTrue(reported.isEmpty());
    assertTrue(legacyFormatRequests.isEmpty());
  }

  public void testLegacyPathStillUsesEditFormatWhenExperimentalLspDisabled() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), false);
    myFixture.configureByText("legacy.dart", INPUT);
    final AnAction action = getRegisteredDartStyleAction();
    final List<AnalyticsData> reported = new ArrayList<>();
    final AnActionEvent event = createEvent(action, editorContext(), ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP);

    action.update(event);
    assertTrue(event.getPresentation().isVisible());
    assertTrue(event.getPresentation().isEnabled());

    Analytics.withReportObserver(reported::add, () -> action.actionPerformed(event));

    assertEquals(1, reported.size());
    assertEquals("Dart.DartStyle", reported.getFirst().getId());
    assertEquals(ActionPlaces.EDITOR_POPUP, reported.getFirst().getData().get(AnalyticsConstants.PLACE.getName()));
    assertEquals(1, legacyFormatRequests.size());
    assertEquals(LEGACY_FORMATTED, myFixture.getEditor().getDocument().getText());
  }

  private DataContext editorContext() {
    return SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.EDITOR, myFixture.getEditor())
      .build();
  }

  private static @NotNull AnAction getRegisteredDartStyleAction() {
    final AnAction action = ActionManager.getInstance().getAction("Dart.DartStyle");
    assertNotNull("Dart.DartStyle must be registered", action);
    return action;
  }

  private static @NotNull AnActionEvent createEvent(@NotNull AnAction action,
                                                    @NotNull DataContext context,
                                                    @NotNull String place,
                                                    @NotNull ActionUiKind uiKind) {
    return AnActionEvent.createEvent(action, context, null, place, uiKind, null);
  }

  private static Object replaceServiceField(DartAnalysisServerService service, String name, Object value) throws Exception {
    final var field = DartAnalysisServerService.class.getDeclaredField(name);
    field.setAccessible(true);
    final Object previous = field.get(service);
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
