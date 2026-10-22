/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.findUsages

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.GTDUOutcome
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.Lsp4jServer
import com.intellij.platform.dartlsp.api.LspServerManagerListener
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.platform.dartlsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.dartlsp.impl.LspServerImpl
import com.intellij.platform.dartlsp.impl.LspServerManagerImpl
import com.intellij.platform.dartlsp.impl.connector.Lsp4jServerConnector
import com.intellij.platform.dartlsp.impl.documentSync.LspDocumentSyncManager
import com.intellij.platform.dartlsp.impl.features.navigation.CurrentActionHolder
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.lsp.DartLspServerDescriptor
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider
import com.jetbrains.lang.dart.sdk.DartConfigurable
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture

class DartTargetElementEvaluatorTest : DartCodeInsightFixtureTestCase() {

    override fun tearDown() {
        try {
            PropertiesComponent.getInstance(project).unsetValue("dart.lsp.experimental.enabled")
            service<CurrentActionHolder>().currentActionClass = null
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    private fun createMockLspServer(
        vFile: VirtualFile,
        definitionResponder: (DefinitionParams) -> Either<List<Location>, List<LocationLink>> = {
            Either.forRight(emptyList())
        },
    ): Pair<LspServerImpl, MutableCollection<LspServerImpl>> {
        val dartDescriptor = DartLspServerDescriptor(project)
        val descriptor = object : ProjectWideLspServerDescriptor(project, "Dart (Bridge)") {
            override fun isSupportedFile(file: VirtualFile): Boolean = dartDescriptor.isSupportedFile(file)
            override fun getFileUri(file: VirtualFile): String = dartDescriptor.getFileUri(file)
            override fun findFileByUri(fileUri: String): VirtualFile? {
                if (fileUri == getFileUri(vFile)) return vFile
                return dartDescriptor.findFileByUri(fileUri)
            }
            override val lspCustomization = dartDescriptor.lspCustomization
        }
        val server = LspServerImpl(
            DartLspServerSupportProvider::class.java,
            descriptor,
            object : LspServerManagerListener {}
        )

        val initResult = InitializeResult(
            ServerCapabilities().apply {
                setDefinitionProvider(true)
                setReferencesProvider(true)
            }
        )

        val textDocumentService = Proxy.newProxyInstance(
            TextDocumentService::class.java.classLoader,
            arrayOf(TextDocumentService::class.java)
        ) { _, method, args ->
            if (method.name == "definition") {
                CompletableFuture.completedFuture(definitionResponder(args[0] as DefinitionParams))
            } else {
                CompletableFuture.completedFuture(null)
            }
        } as TextDocumentService

        val lsp4jServer = Proxy.newProxyInstance(
            Lsp4jServer::class.java.classLoader,
            arrayOf(Lsp4jServer::class.java)
        ) { _, method, _ ->
            if (method.name == "getTextDocumentService") {
                textDocumentService
            } else {
                CompletableFuture.completedFuture(null)
            }
        } as Lsp4jServer

        val connector = object : Lsp4jServerConnector(server) {
            override val ideToServerStream: OutputStream get() = OutputStream.nullOutputStream()
            override val serverToIdeStream: InputStream get() = InputStream.nullInputStream()
            override fun prepareConnect() {}
            override fun isConnectionAlive(): Boolean = true
            override fun disconnect() {}
        }.apply {
            this.lsp4jServer = lsp4jServer
        }

        LspServerImpl::class.java.getDeclaredField("lsp4jServerConnector").apply {
            isAccessible = true
            set(server, connector)
        }
        LspServerImpl::class.java.getDeclaredField("initializeResult").apply {
            isAccessible = true
            set(server, initResult)
        }
        LspServerImpl::class.java.getDeclaredField("state").apply {
            isAccessible = true
            set(server, LspServerState.Running)
        }
        LspDocumentSyncManager::class.java.getDeclaredField("openedFiles").apply {
            isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (get(server.documentSyncManager) as MutableSet<VirtualFile>).add(vFile)
        }

        val manager = LspServerManagerImpl.getInstanceImpl(project)
        val lspServersField = LspServerManagerImpl::class.java.getDeclaredField("lspServers").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val lspServers = lspServersField.get(manager) as MutableCollection<LspServerImpl>
        lspServers.add(server)

        return Pair(server, lspServers)
    }

    fun testTargetCandidatesSuppressedWhenLspReferencesEnabled() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void helper() {}
            void main() {
              help<caret>er();
            }
            """.trimIndent()
        )

        val reference = file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Reference should be found at caret", reference)
        if (reference == null) return

        val evaluator = DartTargetElementEvaluator()

        // Enable experimental LSP features
        PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", true, true)
        assertTrue(DartAnalysisServerService.isLspReferencesEnabled(project))

        val candidates = evaluator.getTargetCandidates(reference)
        assertNotNull(candidates)
        assertTrue(candidates?.isEmpty() == true)

        val utilCandidates = TargetElementUtil.getInstance().getTargetCandidates(reference)
        assertTrue(utilCandidates.isEmpty())

        // Disable experimental LSP features
        PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", false, true)
        assertFalse(DartConfigurable.isExperimentalLspFeaturesEnabled(project))
        assertNull(evaluator.getTargetCandidates(reference))
    }

    fun testLocationLinkSelfDefinitionLeadsToShowUsages() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void help<caret>er() {}
            void main() {
              helper();
            }
            """.trimIndent()
        )
        val selfRange = Range(Position(0, 5), Position(0, 11))
        val (server, lspServers) = createMockLspServer(file.virtualFile) {
            val fileUri = DartLspServerDescriptor(project).getFileUri(file.virtualFile)
            Either.forRight(listOf(LocationLink(fileUri, selfRange, selfRange, selfRange)))
        }

        try {
            PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", true, true)
            assertTrue(
                DartAnalysisServerService.isLspReferencesEnabled(project) &&
                    DartAnalysisServerService.isLspNavigationEnabled(project)
            )
            service<CurrentActionHolder>().currentActionClass = GotoDeclarationAction::class.java
            val outcome = GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction(
                myFixture.editor,
                file,
                myFixture.caretOffset
            )
            assertEquals(GTDUOutcome.SU, outcome)
        } finally {
            server.requestExecutor.shutdownNow()
            lspServers.remove(server)
        }
    }

    fun testLocationSelfDefinitionLeadsToShowUsages() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void help<caret>er() {}
            """.trimIndent()
        )
        val selfRange = Range(Position(0, 5), Position(0, 11))
        val (server, lspServers) = createMockLspServer(file.virtualFile) {
            val fileUri = DartLspServerDescriptor(project).getFileUri(file.virtualFile)
            Either.forLeft(listOf(Location(fileUri, selfRange)))
        }

        try {
            PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", true, true)
            assertTrue(
                DartAnalysisServerService.isLspReferencesEnabled(project) &&
                    DartAnalysisServerService.isLspNavigationEnabled(project)
            )
            service<CurrentActionHolder>().currentActionClass = GotoDeclarationAction::class.java
            val outcome = GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction(
                myFixture.editor,
                file,
                myFixture.caretOffset
            )
            assertEquals(GTDUOutcome.SU, outcome)
        } finally {
            server.requestExecutor.shutdownNow()
            lspServers.remove(server)
        }
    }

    fun testDefinitionAtAnotherPositionLeadsToNavigation() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void helper() {}
            void main() {
              help<caret>er();
            }
            """.trimIndent()
        )
        val targetRange = Range(Position(0, 5), Position(0, 11))
        val originRange = Range(Position(2, 2), Position(2, 8))
        val (server, lspServers) = createMockLspServer(file.virtualFile) {
            val fileUri = DartLspServerDescriptor(project).getFileUri(file.virtualFile)
            Either.forRight(listOf(LocationLink(fileUri, targetRange, targetRange, originRange)))
        }

        try {
            PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", true, true)
            assertTrue(
                DartAnalysisServerService.isLspReferencesEnabled(project) &&
                    DartAnalysisServerService.isLspNavigationEnabled(project)
            )
            service<CurrentActionHolder>().currentActionClass = GotoDeclarationAction::class.java
            val outcome = GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction(
                myFixture.editor,
                file,
                myFixture.caretOffset
            )
            assertEquals(GTDUOutcome.GTD, outcome)
        } finally {
            server.requestExecutor.shutdownNow()
            lspServers.remove(server)
        }
    }

    fun testMultiLineLocationAroundCaretLeadsToNavigation() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void foo() {
              <caret>foo();
            }
            """.trimIndent()
        )
        // A multi-line range is the full declaration body, not the name range. The caret is on a usage inside the body.
        val bodyRange = Range(Position(0, 0), Position(2, 1))
        val (server, lspServers) = createMockLspServer(file.virtualFile) {
            val fileUri = DartLspServerDescriptor(project).getFileUri(file.virtualFile)
            Either.forLeft(listOf(Location(fileUri, bodyRange)))
        }

        try {
            PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", true, true)
            assertTrue(
                DartAnalysisServerService.isLspReferencesEnabled(project) &&
                    DartAnalysisServerService.isLspNavigationEnabled(project)
            )
            service<CurrentActionHolder>().currentActionClass = GotoDeclarationAction::class.java
            val outcome = GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction(
                myFixture.editor,
                file,
                myFixture.caretOffset
            )
            assertEquals(GTDUOutcome.GTD, outcome)
        } finally {
            server.requestExecutor.shutdownNow()
            lspServers.remove(server)
        }
    }

    fun testSelfDefinitionWhenLspDisabledFallsBackToLegacyShowUsages() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void help<caret>er() {}
            void main() {
              helper();
            }
            """.trimIndent()
        )
        val selfRange = Range(Position(0, 5), Position(0, 11))
        val (server, lspServers) = createMockLspServer(file.virtualFile) {
            val fileUri = DartLspServerDescriptor(project).getFileUri(file.virtualFile)
            Either.forRight(listOf(LocationLink(fileUri, selfRange, selfRange, selfRange)))
        }

        try {
            PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", false, true)
            service<CurrentActionHolder>().currentActionClass = GotoDeclarationAction::class.java
            val outcome = GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction(
                myFixture.editor,
                file,
                myFixture.caretOffset
            )
            assertEquals(GTDUOutcome.SU, outcome)
        } finally {
            server.requestExecutor.shutdownNow()
            lspServers.remove(server)
        }
    }
}
