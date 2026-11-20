/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.annotator

import com.google.dart.server.AnalysisServerListener
import com.google.dart.server.AnalysisServerSocket
import com.google.dart.server.Consumer
import com.google.dart.server.DartLspWorkspaceApplyEditRequestConsumer
import com.google.dart.server.GetAssistsConsumer
import com.google.dart.server.ShowMessageRequestConsumer
import com.google.dart.server.internal.remote.ByteLineReaderStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.RequestSink
import com.google.dart.server.internal.remote.ResponseStream
import com.google.gson.JsonObject
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.highlight.DartSyntaxHighlighterColors
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.util.DartTestUtils
import org.dartlang.analysis.server.protocol.DartLspApplyWorkspaceEditParams
import org.dartlang.analysis.server.protocol.Element
import org.dartlang.analysis.server.protocol.ElementKind
import org.dartlang.analysis.server.protocol.HighlightRegion
import org.dartlang.analysis.server.protocol.HighlightRegionType
import org.dartlang.analysis.server.protocol.MessageAction
import org.dartlang.analysis.server.protocol.Outline

/**
 * Verifies that [DartAnnotator] suppresses legacy Analysis Server semantic highlighting
 * ([DartAnalysisServerService.getHighlight]) while LSP semantic tokens highlighting is active,
 * and restores it when inactive.
 */
class DartAnnotatorTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {

    private lateinit var analysisServerListener: AnalysisServerListener
    private var previousExperimentalEnabled: Boolean? = null
    private var originalSdkVersion: String? = null
    private var nextRequestId: Int = 1

    override fun setUp() {
        super.setUp()
        DartTestUtils.configureDartSdk(myModule, myFixture.testRootDisposable, false)
        previousExperimentalEnabled = DartConfigurable.isExperimentalLspFeaturesEnabled(project)

        val das = DartAnalysisServerService.getInstance(project)
        val sdk = requireNotNull(DartSdk.getDartSdk(project)) { "Dart SDK not found" }
        originalSdkVersion = sdk.version

        setServiceField(das, "mySdkHome", sdk.homePath)
        setServiceField(das, "mySdkVersion", sdk.version)

        val mockServer = object : RemoteAnalysisServerImpl(createStubSocket()) {
            override fun addAnalysisServerListener(listener: AnalysisServerListener) {
                analysisServerListener = listener
                super.addAnalysisServerListener(listener)
            }

            override fun generateUniqueId(): String = (nextRequestId++).toString()

            override fun isSocketOpen(): Boolean = true

            override fun edit_getAssists(file: String?, offset: Int, length: Int, consumer: GetAssistsConsumer) {
                consumer.computedSourceChanges(emptyList())
            }

            override fun sendRequestToServer(id: String, request: JsonObject) {}

            override fun sendRequestToServer(id: String, request: JsonObject, consumer: Consumer) {}

            override fun sendResponseToServer(response: JsonObject) {}

            override fun sendNotificationToServer(notification: JsonObject) {}

            override fun server_openUrlRequest(url: String?) {}

            override fun server_showMessageRequest(
                messageType: String?,
                message: String?,
                messageActions: MutableList<MessageAction>?,
                consumer: ShowMessageRequestConsumer?,
            ) {}

            override fun lsp_workspaceApplyEdit(
                params: DartLspApplyWorkspaceEditParams?,
                consumer: DartLspWorkspaceApplyEditRequestConsumer?,
            ) {}
        }
        das.setServer(mockServer)
    }

    override fun tearDown() {
        try {
            val das = DartAnalysisServerService.getInstance(project)
            val sdk = DartSdk.getDartSdk(project)
            val savedVersion = originalSdkVersion
            if (sdk != null && savedVersion != null) {
                setSdkVersion(sdk, savedVersion)
            }
            val savedExperimental = previousExperimentalEnabled
            if (savedExperimental != null) {
                DartConfigurable.setExperimentalLspFeaturesEnabled(project, savedExperimental)
            }
            das.setServer(null)
            setServiceField(das, "mySdkHome", null)
            setServiceField(das, "mySdkVersion", "")
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testLegacyServerHighlightsSkippedWhenLspHighlightingIsActive() {
        val source = "class Foo { final s = #mySym; }\n"
        val classNameOffset = source.indexOf("Foo")
        val classNameEnd = classNameOffset + "Foo".length
        val symbolOffset = source.indexOf("#mySym")
        val symbolEnd = symbolOffset + "#mySym".length

        val psiFile = myFixture.addFileToProject("annotator_lsp_suppression.dart", source)
        val vFile = psiFile.virtualFile
        val das = DartAnalysisServerService.getInstance(project)
        val sdk = requireNotNull(DartSdk.getDartSdk(project)) { "Dart SDK not found" }

        assertTrue("Fixture file must be on LocalFileSystem so DartAnnotator queries DAS", vFile.isInLocalFileSystem)
        assertTrue("Fixture file must be inside Dart included roots", das.isInIncludedRoots(vFile))

        // Populate server data through the real AnalysisServerListener pipeline before opening the
        // editor so waitForAnalysisToComplete_TESTS_ONLY completes immediately with zero sleeping.
        val filePath = vFile.path
        analysisServerListener.computedHighlights(
            filePath,
            listOf(HighlightRegion(HighlightRegionType.CLASS, classNameOffset, "Foo".length)),
        )
        analysisServerListener.computedNavigation(filePath, emptyList())
        analysisServerListener.computedOverrides(filePath, emptyList())
        analysisServerListener.computedImplemented(filePath, emptyList(), emptyList())
        analysisServerListener.computedOutline(
            filePath,
            Outline(
                null,
                Element(ElementKind.COMPILATION_UNIT, vFile.name, null, 0, null, null, null, null),
                0,
                source.length,
                0,
                source.length,
            ),
        )
        analysisServerListener.computedClosingLabels(filePath, emptyList())
        myFixture.openFileInEditor(vFile)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertNotEmpty(das.getHighlight(vFile))

        // 1. Baseline: LSP highlighting inactive -> DartAnnotator applies both cached legacy
        //    CLASS highlight and local #mySym symbol literal highlight.
        assertFalse(DartAnalysisServerService.isLspHighlightingActive(project))
        val legacyHighlights = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
        assertTrue(
            "Expected legacy CLASS highlight when LSP highlighting is inactive",
            legacyHighlights.any {
                it.startOffset == classNameOffset &&
                    it.endOffset == classNameEnd &&
                    it.forcedTextAttributesKey == DartSyntaxHighlighterColors.CLASS
            },
        )
        assertTrue(
            "Expected local SYMBOL_LITERAL highlight from DartAnnotator",
            legacyHighlights.any {
                it.startOffset == symbolOffset &&
                    it.endOffset == symbolEnd &&
                    it.forcedTextAttributesKey == DartSyntaxHighlighterColors.SYMBOL_LITERAL
            },
        )

        // 2. Activate LSP highlighting: pin SDK >= 3.15.0-67.0.dev (aligned on both DartSdk and
        //    DartAnalysisServerService so serverReadyForRequest does not restart the server),
        //    enable experimental LSP features, and mark semantic tokens advertised.
        setSdkVersion(sdk, "3.15.0")
        setServiceField(das, "mySdkVersion", "3.15.0")
        DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
        das.setSemanticTokensAdvertised(true)
        assertTrue(DartAnalysisServerService.isLspHighlightingActive(project))
        assertNotEmpty(das.getHighlight(vFile))

        // Flush the DaemonCodeAnalyzer.restart() scheduled by setSemanticTokensAdvertised(true).
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        val lspActiveHighlights = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
        assertTrue(
            "DartAnnotator must still run local annotations when LSP highlighting is active",
            lspActiveHighlights.any {
                it.startOffset == symbolOffset &&
                    it.endOffset == symbolEnd &&
                    it.forcedTextAttributesKey == DartSyntaxHighlighterColors.SYMBOL_LITERAL
            },
        )
        assertFalse(
            "DartAnnotator must skip legacy server highlights when LSP highlighting is active",
            lspActiveHighlights.any {
                it.startOffset == classNameOffset &&
                    it.endOffset == classNameEnd &&
                    it.forcedTextAttributesKey == DartSyntaxHighlighterColors.CLASS
            },
        )

        // 3. Deactivate LSP highlighting -> DartAnnotator resumes applying legacy highlights.
        das.setSemanticTokensAdvertised(false)
        assertFalse(DartAnalysisServerService.isLspHighlightingActive(project))

        // Flush the DaemonCodeAnalyzer.restart() scheduled by setSemanticTokensAdvertised(false).
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        val restoredHighlights = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
        assertTrue(
            "Expected legacy CLASS highlight to resume once LSP highlighting becomes inactive",
            restoredHighlights.any {
                it.startOffset == classNameOffset &&
                    it.endOffset == classNameEnd &&
                    it.forcedTextAttributesKey == DartSyntaxHighlighterColors.CLASS
            },
        )
    }

    private fun setSdkVersion(sdk: DartSdk, version: String) {
        val field = DartSdk::class.java.getDeclaredField("myVersion").apply { isAccessible = true }
        field.set(sdk, version)
    }

    private fun setServiceField(service: DartAnalysisServerService, fieldName: String, value: Any?) {
        val field = DartAnalysisServerService::class.java.getDeclaredField(fieldName).apply { isAccessible = true }
        field.set(service, value)
    }

    private fun createStubSocket(): AnalysisServerSocket = object : AnalysisServerSocket {
        override fun getErrorStream(): ByteLineReaderStream? = null
        override fun getRequestSink(): RequestSink? = null
        override fun getResponseStream(): ResponseStream? = null
        override fun isOpen(): Boolean = true
        override fun start() {}
        override fun stop() {}
    }
}
