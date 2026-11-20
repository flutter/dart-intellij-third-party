// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.workflow;

import com.intellij.execution.ExecutionResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.DartStartupActivityKt;
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcess;
import com.jetbrains.lang.dart.util.DartTestUtils;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import kotlin.Unit;

/**
 * Test the Dart workflow.
 * <p>
 * This class tests the Dart workflow, such as the exclusion of special folders and the Dart URL resolver.
 */
public class DartWorkflowTest extends DartCodeInsightFixtureTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      DartTestUtils.resetModuleRoots(getModule());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSpecialFoldersExclusion() {
    final String rootUrl = ModuleRootManager.getInstance(getModule()).getContentEntries()[0].getUrl();

    myFixture.addFileToProject("dir1/pubspec.yaml", "name: project1");
    myFixture.addFileToProject("dir1/lib/foo.txt", "");
    myFixture.addFileToProject("dir1/someFolder/lib/foo.dart", "");

    final VirtualFile pubspec2 = myFixture.addFileToProject("dir2/pubspec.yaml", """
      name: project2
      dependencies:
        project1:
          path: ../dir1""").getVirtualFile();
    myFixture.addFileToProject("dir2/.pub/foo.txt", "");
    myFixture.addFileToProject("dir2/bin/foo.dart", "");
    myFixture.addFileToProject("dir2/bin/sub/foo.dart", "");
    myFixture.addFileToProject("dir2/lib/foo.dart", "");
    myFixture.addFileToProject("dir2/lib/sub/foo.dart", "");
    myFixture.addFileToProject("dir2/benchmark/foo.dart", "");
    myFixture.addFileToProject("dir2/benchmark/sub/foo.dart", "");
    myFixture.addFileToProject("dir2/test/foo.dart", "");
    myFixture.addFileToProject("dir2/test/sub/foo.dart", "");
    myFixture.addFileToProject("dir2/tool/foo.dart", "");
    myFixture.addFileToProject("dir2/tool/sub/foo.dart", "");
    myFixture.addFileToProject("dir2/web/foo.dart", "");
    myFixture.addFileToProject("dir2/web/sub/foo.dart", "");
    myFixture.addFileToProject("dir2/build/foo.dart", "");
    myFixture.addFileToProject("dir2/build/sub/foo.dart", "");

    final VirtualFile pubspec3 = myFixture.addFileToProject("dir2/example/pubspec.yaml",
                                                            """
                                                              name: project3
                                                              dependencies:
                                                                project2:
                                                                  path: ..
                                                                outside_project:
                                                                  path: ../../dir1/someFolder""").getVirtualFile();
    myFixture.addFileToProject("dir2/example/lib/foo.dart", "");
    myFixture.addFileToProject("dir2/example/lib/sub/foo.dart", "");
    myFixture.addFileToProject("dir2/example/web/foo.dart", "");
    myFixture.addFileToProject("dir2/example/web/sub/foo.dart", "");

    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
      final ContentEntry contentEntry = model.getContentEntries()[0];
      contentEntry.clearExcludeFolders();
      contentEntry.addExcludeFolder(rootUrl + "/dir1/someFolder");
      contentEntry.addExcludeFolder(rootUrl + "/dir1/packages/project1");
      contentEntry.addExcludeFolder(rootUrl + "/dir1/web/packages");
      contentEntry.addExcludeFolder(rootUrl + "/dir2/packages/oldProject2Name");
      contentEntry.addExcludeFolder(rootUrl + "/dir2/someFolder");
      contentEntry.addExcludeFolder(rootUrl + "/dir2/packages");
      contentEntry.addExcludeFolder(rootUrl + "/dir2/web/packages");
      contentEntry.addExcludeFolder(rootUrl + "/dir2/lib/someFolder");
      contentEntry.addExcludeFolder(rootUrl + "/dir2/example/nonexistent/packages");
      contentEntry.addExcludeFolder(rootUrl + "/dir2/example/packages/oldProject3Name");
      model.commit();
    });

    DartStartupActivityKt.excludeBuildAndToolCacheFolders(getModule(), pubspec2);

    assertSameElements(ModuleRootManager.getInstance(getModule()).getContentEntries()[0].getExcludeFolderUrls(),
                       rootUrl + "/dir1/someFolder",
                       rootUrl + "/dir2/.pub",
                       rootUrl + "/dir2/.dart_tool",
                       rootUrl + "/dir2/build",
                       rootUrl + "/dir2/someFolder",
                       rootUrl + "/dir2/lib/someFolder",
                       rootUrl + "/dir2/example/packages/oldProject3Name",
                       rootUrl + "/dir1/someFolder",
                       rootUrl + "/dir1/packages/project1",
                       rootUrl + "/dir1/web/packages",
                       rootUrl + "/dir2/packages/oldProject2Name",
                       rootUrl + "/dir2/someFolder",
                       rootUrl + "/dir2/packages",
                       rootUrl + "/dir2/web/packages",
                       rootUrl + "/dir2/lib/someFolder",
                       rootUrl + "/dir2/example/nonexistent/packages",
                       rootUrl + "/dir2/example/packages/oldProject3Name"
    );

    DartStartupActivityKt.excludeBuildAndToolCacheFolders(getModule(), pubspec3);

    assertSameElements(ModuleRootManager.getInstance(getModule()).getContentEntries()[0].getExcludeFolderUrls(),
                       rootUrl + "/dir1/someFolder",
                       rootUrl + "/dir2/.pub",
                       rootUrl + "/dir2/.dart_tool",
                       rootUrl + "/dir2/build",
                       rootUrl + "/dir2/someFolder",
                       rootUrl + "/dir2/lib/someFolder",
                       rootUrl + "/dir2/example/.pub",
                       rootUrl + "/dir2/example/.dart_tool",
                       rootUrl + "/dir2/example/build",
                       rootUrl + "/dir1/packages/project1",
                       rootUrl + "/dir1/web/packages",
                       rootUrl + "/dir2/packages/oldProject2Name",
                       rootUrl + "/dir2/someFolder",
                       rootUrl + "/dir2/packages",
                       rootUrl + "/dir2/web/packages",
                       rootUrl + "/dir2/lib/someFolder",
                       rootUrl + "/dir2/example/nonexistent/packages",
                       rootUrl + "/dir2/example/packages/oldProject3Name"
    );

    final Map<Module, Map<String, Set<String>>> exclusionsByModule =
      DartStartupActivityKt.collectExclusionsByModule(getProject());
    final WorkspaceModel workspaceModel = WorkspaceModel.getInstance(getProject());
    ApplicationManager.getApplication().runWriteAction(() -> {
      workspaceModel.updateProjectModel("Test batch exclude Dart folders", storage -> {
        DartStartupActivityKt.applyExclusionsToWorkspaceModel(storage, exclusionsByModule);
        return Unit.INSTANCE;
      });
    });

    assertSameElements(ModuleRootManager.getInstance(getModule()).getContentEntries()[0].getExcludeFolderUrls(),
                       rootUrl + "/dir1/someFolder",
                       rootUrl + "/dir1/.pub",
                       rootUrl + "/dir1/.dart_tool",
                       rootUrl + "/dir1/build",
                       rootUrl + "/dir2/.pub",
                       rootUrl + "/dir2/.dart_tool",
                       rootUrl + "/dir2/build",
                       rootUrl + "/dir2/someFolder",
                       rootUrl + "/dir2/lib/someFolder",
                       rootUrl + "/dir2/example/.pub",
                       rootUrl + "/dir2/example/.dart_tool",
                       rootUrl + "/dir2/example/build",
                       rootUrl + "/dir1/packages/project1",
                       rootUrl + "/dir1/web/packages",
                       rootUrl + "/dir2/packages/oldProject2Name",
                       rootUrl + "/dir2/packages",
                       rootUrl + "/dir2/web/packages",
                       rootUrl + "/dir2/example/nonexistent/packages",
                       rootUrl + "/dir2/example/packages/oldProject3Name"
    );

    assertTrue(DartStartupActivityKt.collectExclusionsByModule(getProject()).isEmpty());
  }

  public void testDartUrlResolver() {
    final String rootPath = ModuleRootManager.getInstance(getModule()).getContentRoots()[0].getPath();
    final String rootUrl = "file:///" + StringUtil.trimLeading(rootPath, '/');

    myFixture.addFileToProject("pubspec.yaml", "name: RootProject");
    myFixture.addFileToProject("lib/rootlib.dart", "");
    final VirtualFile nestedPubspec = myFixture.addFileToProject("example/pubspec.yaml", """
      name: NestedProject
      dependencies:
        RootProject:
          path: ../
        SomePackage: any""").getVirtualFile();
    myFixture.addFileToProject("example/lib/src/nestedlib.dart", "");
    myFixture.addFileToProject("example/packages/NestedProject/nestedlib.dart", "");
    myFixture.addFileToProject("example/packages/RootProject/rootlib.dart", "");
    myFixture.addFileToProject("pub/global/cache/SomePackage/lib/somepack.dart", "");
    myFixture.saveText(myFixture.addFileToProject("example/.dart_tool/package_config.json", "").getVirtualFile(),
        String.format("""
            {
              "configVersion": 2,
              "packages": [
                {
                  "name": "SomePackage",
                  "rootUri": "%s",
                  "packageUri": "lib/"
                },
                {
                  "name": "RootProject",
                  "rootUri": "../../",
                  "packageUri": "lib/"
                },
                {
                  "name": "NestedProject",
                  "rootUri": "../",
                  "packageUri": "lib/"
                }
              ]
            }""", rootUrl + "/pub/global/cache/SomePackage"));

    DartUrlResolver resolver = DartUrlResolver.getInstance(getProject(), nestedPubspec);
    VirtualFile file;

    file = resolver.findFileByDartUrl("dart:collection");
    assertNotNull(file);
    assertEquals(DartTestUtils.SDK_HOME_PATH + "/lib/collection/collection.dart", file.getPath());
    assertEquals("dart:collection", resolver.getDartUrlForFile(file));

    file = resolver.findFileByDartUrl("dart:collection/hash_map.dart");
    assertNotNull(file);
    assertEquals(DartTestUtils.SDK_HOME_PATH + "/lib/collection/hash_map.dart", file.getPath());
    assertEquals("dart:collection/hash_map.dart", resolver.getDartUrlForFile(file));

    file = resolver.findFileByDartUrl("package:NestedProject/src/nestedlib.dart");
    assertNotNull(file);
    assertEquals(rootPath + "/example/lib/src/nestedlib.dart", file.getPath());
    assertEquals("package:NestedProject/src/nestedlib.dart", resolver.getDartUrlForFile(file));

    file = resolver.findFileByDartUrl("package:RootProject/rootlib.dart");
    assertNotNull(file);
    assertEquals(rootPath + "/lib/rootlib.dart", file.getPath());
    assertEquals("package:RootProject/rootlib.dart", resolver.getDartUrlForFile(file));

    file = resolver.findFileByDartUrl("package:SomePackage/somepack.dart");
    assertNotNull(file);
    assertEquals(rootPath + "/pub/global/cache/SomePackage/lib/somepack.dart", file.getPath());
    assertEquals("package:SomePackage/somepack.dart", resolver.getDartUrlForFile(file));
  }

  public void testDebugProcessUriResolution() throws Exception {
    final String rootPath = ModuleRootManager.getInstance(getModule()).getContentRoots()[0].getPath();
    final String rootUrl = "file:///" + StringUtil.trimLeading(rootPath, '/');

    myFixture.addFileToProject("pubspec.yaml", "name: workspace_root");
    final VirtualFile rootLibFile = myFixture.addFileToProject("lib/root_lib.dart", "").getVirtualFile();
    final VirtualFile gitDepFile = myFixture.addFileToProject("pub/git_cache/git_pkg-abc123/lib/git_lib.dart", "").getVirtualFile();

    // Workspace member / nested pubspec without its own .dart_tool/package_config.json (inherits root config).
    final VirtualFile memberPubspec = myFixture.addFileToProject("pkgs/app/pubspec.yaml", """
      name: app
      resolution: workspace""").getVirtualFile();
    final VirtualFile memberBinFile = myFixture.addFileToProject("pkgs/app/bin/main.dart", "").getVirtualFile();
    final VirtualFile memberLibFile = myFixture.addFileToProject("pkgs/app/lib/app.dart", "").getVirtualFile();

    myFixture.saveText(myFixture.addFileToProject(".dart_tool/package_config.json", "").getVirtualFile(),
        String.format("""
            {
              "configVersion": 2,
              "packages": [
                {
                  "name": "workspace_root",
                  "rootUri": "../",
                  "packageUri": "lib/"
                },
                {
                  "name": "app",
                  "rootUri": "../pkgs/app",
                  "packageUri": "lib/"
                },
                {
                  "name": "git_pkg",
                  "rootUri": "%s",
                  "packageUri": "lib/"
                }
              ]
            }""", rootUrl + "/pub/git_cache/git_pkg-abc123"));

    final DartUrlResolver resolver = DartUrlResolver.getInstance(getProject(), memberBinFile);
    final XDebugSession debugSession = (XDebugSession)Proxy.newProxyInstance(
      XDebugSession.class.getClassLoader(),
      new Class<?>[]{XDebugSession.class},
      (proxy, method, args) -> {
        if ("getProject".equals(method.getName())) return getProject();
        if ("isStopped".equals(method.getName())) return false;
        return null;
      }
    );
    final ExecutionResult executionResult = (ExecutionResult)Proxy.newProxyInstance(
      ExecutionResult.class.getClassLoader(),
      new Class<?>[]{ExecutionResult.class},
      (proxy, method, args) -> null
    );
    final DartVmServiceDebugProcess debugProcess = new DartVmServiceDebugProcess(
      debugSession,
      executionResult,
      resolver,
      DartVmServiceDebugProcess.DebugType.CLI,
      5000,
      memberPubspec.getParent()
    );

    // 1. In-package lib/ file resolves to both package: and file:/// URIs.
    assertSameElements(
      debugProcess.getUrisForFile(memberLibFile),
      "package:app/app.dart",
      threeSlashFileUri(memberLibFile)
    );
    assertEquals(memberLibFile, resolver.findFileByDartUrl("package:app/app.dart"));

    // 2. Sibling/path dependency outside the sub-package resolves via workspace package_config.json.
    assertSameElements(
      debugProcess.getUrisForFile(rootLibFile),
      "package:workspace_root/root_lib.dart",
      threeSlashFileUri(rootLibFile)
    );
    assertEquals(rootLibFile, resolver.findFileByDartUrl("package:workspace_root/root_lib.dart"));

    // 3. Git / hosted dependency via file:/// rootUri in package_config.json.
    assertSameElements(
      debugProcess.getUrisForFile(gitDepFile),
      "package:git_pkg/git_lib.dart",
      threeSlashFileUri(gitDepFile)
    );
    assertEquals(gitDepFile, resolver.findFileByDartUrl("package:git_pkg/git_lib.dart"));

    // 4. Files outside any package lib/ (bin/main.dart and standalone LocalFileSystem file) resolve via file:/// URI.
    assertSameElements(debugProcess.getUrisForFile(memberBinFile), threeSlashFileUri(memberBinFile));
    final File tempStandaloneIoFile = FileUtil.createTempFile("standalone_script", ".dart", true);
    final VirtualFile standaloneFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempStandaloneIoFile);
    assertNotNull(standaloneFile);
    final String standaloneFileUri = threeSlashFileUri(standaloneFile);
    assertSameElements(debugProcess.getUrisForFile(standaloneFile), standaloneFileUri);
    assertEquals(standaloneFile, resolver.findFileByDartUrl(standaloneFileUri));

    // 5. SDK dart: library short-circuits to dart: URI.
    final VirtualFile sdkCollectionFile = resolver.findFileByDartUrl("dart:collection");
    assertNotNull(sdkCollectionFile);
    assertSameElements(debugProcess.getUrisForFile(sdkCollectionFile), "dart:collection");
  }

  private static String threeSlashFileUri(final VirtualFile file) {
    final String rawUri = new File(file.getPath()).toURI().toString();
    return rawUri.startsWith("file:///") ? rawUri : "file:///" + StringUtil.trimLeading(rawUri.substring("file:".length()), '/');
  }
}
