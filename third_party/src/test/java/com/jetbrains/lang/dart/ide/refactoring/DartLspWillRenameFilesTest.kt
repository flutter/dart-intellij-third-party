/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.refactoring

import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.Consumer
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.platform.dartlsp.impl.LspGlobMatcher
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.lsp.DartBridgeLspServerManager
import com.jetbrains.lang.dart.lsp.DartLspServerDescriptor
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.MessageAction
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileOperationPattern
import org.eclipse.lsp4j.FileOperationPatternKind
import org.eclipse.lsp4j.FileOperationPatternOptions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class DartLspWillRenameFilesTest : DartCodeInsightFixtureTestCase() {

    private val capturedWillRenameRequests = CopyOnWriteArrayList<JsonObject>()
    private val nextRequestId = AtomicInteger(1)
    private var willRenameHandler: ((JsonObject) -> JsonObject?)? = null
    private var tempContentRoot: VirtualFile? = null
    private lateinit var stubServer: StubRemoteAnalysisServer

    override fun createTempDirTestFixture(): TempDirTestFixture {
        // Use a real LocalFileSystem temp directory so LspWillRenameFilesHandler ("file" scheme)
        // and LspServerDescriptor.findFileByUri resolve files end-to-end.
        return IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    }

    override fun setUp() {
        super.setUp()
        TrustedProjects.setProjectTrusted(project, true)
        capturedWillRenameRequests.clear()
        nextRequestId.set(1)
        willRenameHandler = null

        val rootDir = myFixture.tempDirFixture.findOrCreateDir("")
        tempContentRoot = rootDir
        PsiTestUtil.addContentRoot(module, rootDir)

        val das = DartAnalysisServerService.getInstance(project)
        das.semanticTokensLegendFuture.complete(null)
        stubServer = StubRemoteAnalysisServer()
        das.setServer(stubServer)
    }

    override fun tearDown() {
        try {
            project.getService(DartBridgeLspServerManager::class.java).stopBridgeServer()
            DartAnalysisServerService.getInstance(project).setServer(null)
            DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
            tempContentRoot?.let { PsiTestUtil.removeContentEntry(module, it) }
            tempContentRoot = null
            capturedWillRenameRequests.clear()
            willRenameHandler = null
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testGlobMatcherDartAndFolderFilters() {
        val matcher = LspGlobMatcher()
        val dartFileFilter = FileOperationFilter(
            FileOperationPattern("**/*.dart").apply { matches = FileOperationPatternKind.File },
            "file"
        )
        val folderFilter = FileOperationFilter(
            FileOperationPattern("**/*").apply { matches = FileOperationPatternKind.Folder },
            "file"
        )

        assertTrue(matcher.pathMatchesFileOperationFilter("/project/lib/a.dart", "file", isDirectory = false, dartFileFilter))
        assertFalse(matcher.pathMatchesFileOperationFilter("/project/lib/a.dart", "file", isDirectory = true, dartFileFilter))
        assertFalse(matcher.pathMatchesFileOperationFilter("/project/README.md", "file", isDirectory = false, dartFileFilter))
        assertFalse(matcher.pathMatchesFileOperationFilter("/project/lib/a.dart", "untitled", isDirectory = false, dartFileFilter))

        assertTrue(matcher.pathMatchesFileOperationFilter("/project/lib/sub", "file", isDirectory = true, folderFilter))
        assertFalse(matcher.pathMatchesFileOperationFilter("/project/lib/a.dart", "file", isDirectory = false, folderFilter))
    }

    fun testGlobMatcherTrailingSlashFolderPattern() {
        val matcher = LspGlobMatcher()
        // Dart SDK's standalone LSP server advertises glob "**/" with FileOperationPatternKind.Folder.
        val dartStandaloneFolderFilter = FileOperationFilter(
            FileOperationPattern("**/").apply { matches = FileOperationPatternKind.Folder },
            "file"
        )
        val namedTrailingSlashFilter = FileOperationFilter(
            FileOperationPattern("**/lib/").apply {
                matches = FileOperationPatternKind.Folder
                options = FileOperationPatternOptions(true)
            },
            "file"
        )

        assertTrue(matcher.pathMatchesFileOperationFilter("/project/lib", "file", isDirectory = true, dartStandaloneFolderFilter))
        assertTrue(matcher.pathMatchesFileOperationFilter("/project/lib/nested", "file", isDirectory = true, dartStandaloneFolderFilter))
        assertFalse(matcher.pathMatchesFileOperationFilter("/project/lib/a.dart", "file", isDirectory = false, dartStandaloneFolderFilter))

        assertTrue(matcher.pathMatchesFileOperationFilter("/project/LIB", "file", isDirectory = true, namedTrailingSlashFilter))
        assertFalse(matcher.pathMatchesFileOperationFilter("/project/other", "file", isDirectory = true, namedTrailingSlashFilter))
    }

    fun testRenameDartFileUpdatesImportsBeforeRenameViaPlatformLsp() {
        val oldPsiFile = myFixture.addFileToProject("lib/old_name.dart", "class Foo {}")
        val mainPsiFile = myFixture.addFileToProject("lib/main.dart", "import 'old_name.dart';\n")
        val oldVirtualFile = oldPsiFile.virtualFile
        val mainVirtualFile = mainPsiFile.virtualFile

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        startBridgeAndAwaitRunning()

        val descriptor = DartLspServerDescriptor(project)
        val expectedOldUri = descriptor.getFileUri(oldVirtualFile)
        val expectedNewUri = expectedOldUri.removeSuffix("old_name.dart") + "new_name.dart"
        val mainUri = descriptor.getFileUri(mainVirtualFile)

        var fileNameDuringRequest: String? = null
        willRenameHandler = {
            fileNameDuringRequest = oldVirtualFile.name
            buildWorkspaceEditResult(
                uri = mainUri,
                startLine = 0,
                startChar = 7,
                endLine = 0,
                endChar = 22,
                newText = "'new_name.dart'"
            )
        }

        RenameProcessor(project, oldPsiFile, "new_name.dart", false, false).run()

        assertEquals(1, capturedWillRenameRequests.size)
        val files = capturedWillRenameRequests.single().getAsJsonObject("params").getAsJsonArray("files")
        assertEquals(1, files.size())
        assertEquals(expectedOldUri, files[0].asJsonObject.get("oldUri").asString)
        assertEquals(expectedNewUri, files[0].asJsonObject.get("newUri").asString)
        assertEquals("old_name.dart", fileNameDuringRequest)
        assertEquals("new_name.dart", oldVirtualFile.name)

        val mainDoc = checkNotNull(FileDocumentManager.getInstance().getDocument(mainVirtualFile))
        assertEquals("import 'new_name.dart';\n", mainDoc.text)
    }

    fun testMoveDirectoryUpdatesImportsViaPlatformLsp() {
        val childPsiFile = myFixture.addFileToProject("lib/old_dir/foo.dart", "class Foo {}")
        val mainPsiFile = myFixture.addFileToProject("lib/main.dart", "import 'old_dir/foo.dart';\n")
        val oldDir = checkNotNull(childPsiFile.containingDirectory)
        val libDir = checkNotNull(oldDir.parentDirectory)
        val targetVirtualDir = WriteAction.compute<VirtualFile, Throwable> {
            libDir.virtualFile.createChildDirectory(this, "dest")
        }
        val targetPsiDir = checkNotNull(PsiManager.getInstance(project).findDirectory(targetVirtualDir))

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        startBridgeAndAwaitRunning()

        val descriptor = DartLspServerDescriptor(project)
        val expectedOldUri = descriptor.getFileUri(oldDir.virtualFile)
        val expectedNewUri = descriptor.getFileUri(targetVirtualDir) + "/old_dir"
        val mainUri = descriptor.getFileUri(mainPsiFile.virtualFile)

        willRenameHandler = {
            buildWorkspaceEditResult(
                uri = mainUri,
                startLine = 0,
                startChar = 7,
                endLine = 0,
                endChar = 25,
                newText = "'dest/old_dir/foo.dart'"
            )
        }

        MoveFilesOrDirectoriesProcessor(
            project,
            arrayOf(oldDir),
            targetPsiDir,
            false,
            false,
            null,
            null
        ).run()

        assertEquals(1, capturedWillRenameRequests.size)
        val files = capturedWillRenameRequests.single().getAsJsonObject("params").getAsJsonArray("files")
        assertEquals(1, files.size())
        assertEquals(expectedOldUri, files[0].asJsonObject.get("oldUri").asString)
        assertEquals(expectedNewUri, files[0].asJsonObject.get("newUri").asString)
        assertEquals(targetVirtualDir, oldDir.virtualFile.parent)

        val mainDoc = checkNotNull(FileDocumentManager.getInstance().getDocument(mainPsiFile.virtualFile))
        assertEquals("import 'dest/old_dir/foo.dart';\n", mainDoc.text)
    }

    fun testRenameDirectoryUpdatesImportsViaPlatformLsp() {
        val childPsiFile = myFixture.addFileToProject("lib/old_dir/foo.dart", "class Foo {}")
        val mainPsiFile = myFixture.addFileToProject("lib/main.dart", "import 'old_dir/foo.dart';\n")
        val oldDir = checkNotNull(childPsiFile.containingDirectory)

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        startBridgeAndAwaitRunning()

        val descriptor = DartLspServerDescriptor(project)
        val expectedOldUri = descriptor.getFileUri(oldDir.virtualFile)
        val expectedNewUri = expectedOldUri.removeSuffix("old_dir") + "new_dir"
        val mainUri = descriptor.getFileUri(mainPsiFile.virtualFile)

        willRenameHandler = {
            buildWorkspaceEditResult(
                uri = mainUri,
                startLine = 0,
                startChar = 7,
                endLine = 0,
                endChar = 25,
                newText = "'new_dir/foo.dart'"
            )
        }

        RenameProcessor(project, oldDir, "new_dir", false, false).run()

        assertEquals(1, capturedWillRenameRequests.size)
        val files = capturedWillRenameRequests.single().getAsJsonObject("params").getAsJsonArray("files")
        assertEquals(1, files.size())
        assertEquals(expectedOldUri, files[0].asJsonObject.get("oldUri").asString)
        assertEquals(expectedNewUri, files[0].asJsonObject.get("newUri").asString)
        assertEquals("new_dir", oldDir.virtualFile.name)

        val mainDoc = checkNotNull(FileDocumentManager.getInstance().getDocument(mainPsiFile.virtualFile))
        assertEquals("import 'new_dir/foo.dart';\n", mainDoc.text)
    }

    fun testMoveMultipleFilesBatchesDartFilesInSingleWillRenameFilesRequest() {
        val firstFile = myFixture.addFileToProject("lib/first.dart", "class First {}")
        val secondFile = myFixture.addFileToProject("lib/second.dart", "class Second {}")
        val readmeFile = myFixture.addFileToProject("lib/README.md", "# Readme")
        val libDir = checkNotNull(firstFile.containingDirectory)
        val targetVirtualDir = WriteAction.compute<VirtualFile, Throwable> {
            libDir.virtualFile.createChildDirectory(this, "dest")
        }
        val targetPsiDir = checkNotNull(PsiManager.getInstance(project).findDirectory(targetVirtualDir))

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        startBridgeAndAwaitRunning()

        val descriptor = DartLspServerDescriptor(project)
        val firstOldUri = descriptor.getFileUri(firstFile.virtualFile)
        val secondOldUri = descriptor.getFileUri(secondFile.virtualFile)
        val targetUri = descriptor.getFileUri(targetVirtualDir)

        MoveFilesOrDirectoriesProcessor(
            project,
            arrayOf(firstFile, secondFile, readmeFile),
            targetPsiDir,
            false,
            false,
            null,
            null
        ).run()

        assertEquals(1, capturedWillRenameRequests.size)
        val files = capturedWillRenameRequests.single().getAsJsonObject("params").getAsJsonArray("files")
        assertEquals(2, files.size())
        assertEquals(firstOldUri, files[0].asJsonObject.get("oldUri").asString)
        assertEquals("$targetUri/first.dart", files[0].asJsonObject.get("newUri").asString)
        assertEquals(secondOldUri, files[1].asJsonObject.get("oldUri").asString)
        assertEquals("$targetUri/second.dart", files[1].asJsonObject.get("newUri").asString)
        assertEquals(targetVirtualDir, firstFile.virtualFile.parent)
        assertEquals(targetVirtualDir, secondFile.virtualFile.parent)
        assertEquals(targetVirtualDir, readmeFile.virtualFile.parent)
    }

    fun testNonDartFileRenameDoesNotSendWillRenameFiles() {
        // Ensure project has a file in content and start the bridge server
        myFixture.addFileToProject("lib/a.dart", "class A {}")
        val markdownFile = myFixture.addFileToProject("lib/README.md", "# Readme")

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        startBridgeAndAwaitRunning()

        RenameProcessor(project, markdownFile, "NOTES.md", false, false).run()

        assertTrue("Markdown file rename must not send workspace/willRenameFiles", capturedWillRenameRequests.isEmpty())
        assertEquals("NOTES.md", markdownFile.virtualFile.name)
    }

    fun testDisabledExperimentalLspDoesNotSendWillRenameFiles() {
        val dartFile = myFixture.addFileToProject("lib/a.dart", "class A {}")

        DartConfigurable.setExperimentalLspFeaturesEnabled(project, false)
        startBridgeAndAwaitRunning()

        RenameProcessor(project, dartFile, "b.dart", false, false).run()

        assertTrue("Disabled experimental LSP must not send workspace/willRenameFiles", capturedWillRenameRequests.isEmpty())
        assertEquals("b.dart", dartFile.virtualFile.name)
    }

    private fun startBridgeAndAwaitRunning() {
        val rootDir = checkNotNull(tempContentRoot)
        PlatformTestUtil.waitWithEventsDispatching(
            "Timed out waiting for BaseProjectDirectories to include temp content root",
            {
                val baseDirs = BaseProjectDirectories.getInstance(project)
                !baseDirs.isProcessing && baseDirs.contains(rootDir)
            },
            10
        )
        val bridgeManager = project.getService(DartBridgeLspServerManager::class.java)
        bridgeManager.startBridgeServer()
        val lspManager = LspServerManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching(
            "Timed out waiting for Dart Bridge LSP server to reach Running state",
            {
                val server = lspManager.getServersForProvider(DartLspServerSupportProvider::class.java).firstOrNull()
                server?.state == LspServerState.Running
            },
            10
        )
    }

    private fun buildWorkspaceEditResult(
        uri: String,
        startLine: Int,
        startChar: Int,
        endLine: Int,
        endChar: Int,
        newText: String,
    ): JsonObject {
        val start = JsonObject().apply {
            addProperty("line", startLine)
            addProperty("character", startChar)
        }
        val end = JsonObject().apply {
            addProperty("line", endLine)
            addProperty("character", endChar)
        }
        val edit = JsonObject().apply {
            add("range", JsonObject().apply {
                add("start", start)
                add("end", end)
            })
            addProperty("newText", newText)
        }
        val textDocEdit = JsonObject().apply {
            add("textDocument", JsonObject().apply {
                addProperty("uri", uri)
                add("version", JsonNull.INSTANCE)
            })
            add("edits", JsonArray().apply { add(edit) })
        }
        return JsonObject().apply {
            add("documentChanges", JsonArray().apply { add(textDocEdit) })
        }
    }

    private inner class StubRemoteAnalysisServer : RemoteAnalysisServerImpl(createStubSocket()) {
        override fun isSocketOpen(): Boolean = true

        override fun generateUniqueId(): String = "req_${nextRequestId.getAndIncrement()}"

        override fun sendRequestToServer(id: String, request: JsonObject) {
            handleOutgoingRequest(id, request)
        }

        override fun sendRequestToServer(id: String, request: JsonObject, consumer: Consumer?) {
            handleOutgoingRequest(id, request)
        }

        override fun sendResponseToServer(response: JsonObject) {}

        override fun sendNotificationToServer(notification: JsonObject) {}

        private fun handleOutgoingRequest(id: String, request: JsonObject) {
            val method = request.get("method")?.asString
            if (method == "lsp.handle") {
                val lspMessage = request.getAsJsonObject("params")?.getAsJsonObject("lspMessage")
                if (lspMessage != null && lspMessage.get("method")?.asString == "workspace/willRenameFiles") {
                    capturedWillRenameRequests.add(lspMessage)
                    val editResult = willRenameHandler?.invoke(lspMessage)
                    val responseJson = JsonObject().apply {
                        addProperty("id", id)
                        add("result", JsonObject().apply {
                            add("lspResponse", JsonObject().apply {
                                addProperty("jsonrpc", "2.0")
                                add("id", lspMessage.get("id"))
                                add("result", editResult)
                            })
                        })
                    }
                    ApplicationManager.getApplication().executeOnPooledThread {
                        processResponse(responseJson)
                    }
                    return
                }
            }
            // Reply with empty result to non-LSP legacy requests (e.g. server.setClientCapabilities)
            ApplicationManager.getApplication().executeOnPooledThread {
                val ok = JsonParser.parseString("""{"id":"$id","result":{}}""").asJsonObject
                processResponse(ok)
            }
        }

        override fun server_openUrlRequest(url: String?) {}

        override fun server_showMessageRequest(
            type: String?,
            message: String?,
            actions: MutableList<MessageAction>?,
            consumer: ShowMessageRequestConsumer?,
        ) {}

        override fun lsp_workspaceApplyEdit(
            params: DartLspApplyWorkspaceEditParams?,
            consumer: DartLspWorkspaceApplyEditRequestConsumer?,
        ) {}
    }
}

private fun createStubSocket(): AnalysisServerSocket = object : AnalysisServerSocket {
    override fun getErrorStream(): ByteLineReaderStream? = null
    override fun getRequestSink(): RequestSink? = null
    override fun getResponseStream(): ResponseStream? = null
    override fun isOpen(): Boolean = true
    override fun start() {}
    override fun stop() {}
}
