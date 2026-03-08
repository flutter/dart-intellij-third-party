// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer

import com.google.dart.server.GetServerPortConsumer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.jetbrains.lang.dart.util.DartTestUtils
import org.dartlang.analysis.server.protocol.RequestError
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Integration test that exercises the service-side overlaid content pipeline against
 * a real Dart LSP server process.
 *
 * Requires DART_HOME to point at a valid Dart SDK.
 */
class LspContentSyncIntegrationTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {
    companion object {
        private const val PORT_REQUEST_TIMEOUT_SECONDS = 30L
    }

    private val tempDirs = mutableListOf<Path>()

    override fun setUp() {
        super.setUp()
        DartTestUtils.configureDartSdk(myModule, myFixture.testRootDisposable, true)
    }

    override fun tearDown() {
        try {
            if (project != null && !project.isDisposed) {
                DartAnalysisServerService.getInstance(project).stopServer()
            }
        } catch (t: Throwable) {
            addSuppressedException(t)
        } finally {
            try {
                tempDirs.forEach { dir ->
                    val walk = Files.walk(dir)
                    try {
                        walk.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                    } finally {
                        walk.close()
                    }
                }
            } catch (t: Throwable) {
                addSuppressedException(t)
            } finally {
                tempDirs.clear()
                super.tearDown()
            }
        }
    }

    fun testSingleDocumentLifecycle() {
        val service = startLspServer()
        val file = createLocalDartFile("main.dart", "void main() {}")
        val document = getDocument(file)

        replaceDocumentText(document, "void main() { print('hello'); }")
        service.updateFilesContent()
        assertServerAlive(service, "after initial overlay sync")
        assertTrackedFiles(service, file.path)

        val firstTrackedTimestamp = trackedOverlayTimestamps(service)[file.path]
        assertEquals(document.modificationStamp, firstTrackedTimestamp)

        replaceDocumentText(document, "void main() { print('updated'); }")
        service.updateFilesContent()
        assertServerAlive(service, "after updated overlay sync")
        assertTrackedFiles(service, file.path)
        assertEquals(document.modificationStamp, trackedOverlayTimestamps(service)[file.path])

        saveDocument(document)
        service.updateFilesContent()
        assertServerAlive(service, "after overlay removal")
        assertTrackedFiles(service)

        val port = requestDiagnosticPort(service)
        assertTrue("Diagnostic port should be valid after full lifecycle, got: $port", port > 0)
        assertPortReachable(port)
    }

    fun testMultipleDocuments() {
        val service = startLspServer()
        val fileA = createLocalDartFile("a.dart", "class A {}")
        val fileB = createLocalDartFile("b.dart", "class B {}")
        val documentA = getDocument(fileA)
        val documentB = getDocument(fileB)

        replaceDocumentText(documentA, "class A { void run() {} }")
        replaceDocumentText(documentB, "class B { void run() {} }")
        service.updateFilesContent()
        assertServerAlive(service, "after initial multi-file sync")
        assertTrackedFiles(service, fileA.path, fileB.path)

        val initialTracked = trackedOverlayTimestamps(service)
        val trackedBVersion = initialTracked[fileB.path]

        saveDocument(documentA)
        service.updateFilesContent()
        assertServerAlive(service, "after saving A")
        assertTrackedFiles(service, fileB.path)
        assertEquals(trackedBVersion, trackedOverlayTimestamps(service)[fileB.path])

        replaceDocumentText(documentB, "class B { void run() {} void close() {} }")
        service.updateFilesContent()
        assertServerAlive(service, "after changing B while A is saved")
        assertTrackedFiles(service, fileB.path)
        assertEquals(documentB.modificationStamp, trackedOverlayTimestamps(service)[fileB.path])

        val port = requestDiagnosticPort(service)
        assertTrue("Diagnostic port should be valid after multi-file lifecycle, got: $port", port > 0)
    }

    fun testReopenAfterClose() {
        val service = startLspServer()
        val file = createLocalDartFile("reopen.dart", "// v0")
        val document = getDocument(file)

        replaceDocumentText(document, "// v1")
        service.updateFilesContent()
        assertTrackedFiles(service, file.path)

        saveDocument(document)
        service.updateFilesContent()
        assertTrackedFiles(service)

        replaceDocumentText(document, "// v2")
        service.updateFilesContent()
        assertServerAlive(service, "after re-adding overlay")
        assertTrackedFiles(service, file.path)
        assertEquals(document.modificationStamp, trackedOverlayTimestamps(service)[file.path])
    }

    private fun assertServerAlive(
        service: DartAnalysisServerService,
        context: String,
    ) {
        assertTrue("Server should be alive $context", service.isServerProcessActive)
    }

    private fun startLspServer(): DartAnalysisServerService {
        Registry.get("dart.use.lsp.client").setValue(true, myFixture.testRootDisposable)
        return DartAnalysisServerService.getInstance(project).also { service ->
            service.stopServer()
            assertTrue("Failed to start LSP-protocol server", service.serverReadyForRequest())
        }
    }

    private fun createLocalDartFile(
        fileName: String,
        content: String,
    ): VirtualFile {
        val dir = Files.createTempDirectory("dart-lsp-sync")
        tempDirs.add(dir)
        val filePath = dir.resolve(fileName)
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, content)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
            ?: throw AssertionError("Failed to create local test file for $fileName")
    }

    private fun getDocument(file: VirtualFile): Document {
        return FileDocumentManager.getInstance().getDocument(file)
            ?: throw AssertionError("Expected document for ${file.path}")
    }

    private fun replaceDocumentText(
        document: Document,
        text: String,
    ) {
        ApplicationManager.getApplication().runWriteAction {
            document.setText(text)
        }
    }

    private fun saveDocument(document: Document) {
        ApplicationManager.getApplication().runWriteAction {
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun trackedOverlayTimestamps(service: DartAnalysisServerService): Map<String, Long> {
        val field = DartAnalysisServerService::class.java.getDeclaredField("myFilePathWithOverlaidContentToTimestamp")
        field.isAccessible = true
        return LinkedHashMap(field.get(service) as Map<String, Long>)
    }

    private fun assertTrackedFiles(
        service: DartAnalysisServerService,
        vararg expectedPaths: String,
    ) {
        assertEquals(expectedPaths.toSet(), trackedOverlayTimestamps(service).keys)
    }

    private fun requestDiagnosticPort(service: DartAnalysisServerService): Int {
        val latch = CountDownLatch(1)
        val port = AtomicInteger(-1)
        val error = AtomicReference<RequestError>()

        service.diagnostic_getServerPort(
            object : GetServerPortConsumer {
                override fun computedServerPort(value: Int) {
                    port.set(value)
                    latch.countDown()
                }

                override fun onError(requestError: RequestError) {
                    error.set(requestError)
                    latch.countDown()
                }
            },
        )

        assertTrue("Timed out waiting for diagnostic port response", latch.await(PORT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertNull("Failed to get diagnostic port: ${toErrorMessage(error.get())}", error.get())
        return port.get()
    }

    private fun toErrorMessage(error: RequestError?): String {
        return if (error == null) "" else "${error.code}: ${error.message}"
    }

    private fun assertPortReachable(port: Int) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 5000)
        }
    }
}
