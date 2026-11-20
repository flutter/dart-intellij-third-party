// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.lsp

import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.InlayPosition
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.dartlsp.api.LspServerNotificationsHandler
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.hints.DartClosingLabelsInlayHintsProvider
import org.dartlang.analysis.server.protocol.ClosingLabel
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DartLspClosingLabelsServiceTest: DartCodeInsightFixtureTestCase() {
    fun testUpdateAndGetClosingLabels() {
        val code = """
                    void main() {
                      runApp(
                        Center(
                          child: Text('Hi'),
                        ),
                      );
                    }
                """.trimIndent()

        val testFile = myFixture.addFileToProject("lib/test_labels.dart", code)
        val fileUri = "file://${testFile.virtualFile.path}"
        val service = DartLspClosingLabelsService.getInstance(project)

        val lspLabels = listOf(
            DartLspClosingLabel(
                label = "Center",
                range = Range(Position(2, 4), Position(4, 5))
            ),
            // Malformed / blank labels should be filtered out safely
            DartLspClosingLabel(
                label = "",
                range = Range(Position(1, 2), Position(5, 3))
            ),
            DartLspClosingLabel(
                label = "MissingRange",
                range = null
            )
        )

        service.updateClosingLabels(fileUri, lspLabels)

        val stored = service.getClosingLabels(testFile.virtualFile)
        assertEquals(1, stored.size)
        val label = stored[0]
        assertEquals("Center", label.label)
        assertEquals(2, label.range?.start?.line)
        assertEquals(4, label.range?.end?.line)
    }

    fun testUpdateEmptyClosingLabels() {
        val testFile = myFixture.addFileToProject("lib/test_empty.dart", "void main() {}")
        val fileUri = "file://${testFile.virtualFile.path}"
        val service = DartLspClosingLabelsService.getInstance(project)

        service.updateClosingLabels(fileUri, emptyList())

        val stored = service.getClosingLabels(testFile.virtualFile)
        assertTrue(stored.isEmpty())
    }

    fun testDartLsp4jClientPublishClosingLabels() {
        val code = """
            void main() {
              runApp(
                MyWidget(
                  child: Text('Hello'),
                ),
              );
            }
        """.trimIndent()
        val testFile = myFixture.addFileToProject("lib/test_client_labels.dart", code)
        val fileUri = "file://${testFile.virtualFile.path}"

        val descriptor = DartLspServerDescriptor(project)
        val noOpHandler = Proxy.newProxyInstance(
            LspServerNotificationsHandler::class.java.classLoader,
            arrayOf(LspServerNotificationsHandler::class.java)
        ) { _, method, _ ->
            if (method.returnType == CompletableFuture::class.java) {
                CompletableFuture.completedFuture(null)
            } else {
                null
            }
        } as LspServerNotificationsHandler

        val client = descriptor.createLsp4jClient(noOpHandler)
        assertTrue("createLsp4jClient should return DartLsp4jClient", client is DartLsp4jClient)

        val supportedMethods = ServiceEndpoints.getSupportedMethods(client.javaClass)
        assertTrue(
            "Lsp4jClient subclass must expose dart/textDocument/publishClosingLabels to lsp4j",
            supportedMethods.containsKey("dart/textDocument/publishClosingLabels")
        )

        val params = DartPublishClosingLabelsParams(
            uri = fileUri,
            labels = listOf(
                DartLspClosingLabel(
                    label = "MyWidget",
                    range = Range(Position(2, 4), Position(4, 5))
                )
            )
        )
        ServiceEndpoints.toEndpoint(client).notify("dart/textDocument/publishClosingLabels", params)

        val stored = DartLspClosingLabelsService.getInstance(project).getClosingLabels(testFile.virtualFile)
        assertEquals(1, stored.size)
        assertEquals("MyWidget", stored[0].label)
        assertEquals(2, stored[0].range?.start?.line)
        assertEquals(4, stored[0].range?.end?.line)
    }

    fun testClosingLabelsInlayHintsProvider() {
        val code = """
            void main() {
              runApp(
                MyWidget(
                  child: Text('Hello'),
                ),
              );
            }
        """.trimIndent()
        val testFile = myFixture.configureByText("test_inlay_provider.dart", code)
        val fileUri = "file://${testFile.virtualFile.path}"

        DartLspClosingLabelsService.getInstance(project).updateClosingLabels(
            fileUri,
            listOf(
                DartLspClosingLabel(
                    label = "// MyWidget",
                    range = Range(Position(2, 4), Position(4, 5))
                )
            )
        )

        val collectedPositions = mutableListOf<InlayPosition>()
        val sink = Proxy.newProxyInstance(
            InlayTreeSink::class.java.classLoader,
            arrayOf(InlayTreeSink::class.java)
        ) { _, method, args ->
            if (method.name == "addPresentation" && args != null && args.isNotEmpty() && args[0] is InlayPosition) {
                collectedPositions.add(args[0] as InlayPosition)
            }
            null
        } as InlayTreeSink

        val provider = DartClosingLabelsInlayHintsProvider()
        val collector = provider.createCollector(testFile, myFixture.editor) as OwnBypassCollector
        collector.collectHintsForFile(testFile, sink)

        assertEquals(1, collectedPositions.size)
        val pos = collectedPositions[0] as EndOfLinePosition
        assertEquals(4, pos.line)
    }

    fun testBuildLspCapabilitiesWithClosingLabels() {
        assertTrue(DartAnalysisServerService.isDartSdkVersionSufficientForLspClosingLabels("3.14.0-219.0.dev"))
        assertFalse(DartAnalysisServerService.isDartSdkVersionSufficientForLspClosingLabels("3.14.0-143.0.dev"))

        val capsEnabled = DartAnalysisServerService.buildLspCapabilities("3.14.0-219.0.dev", false, true)
        val experimental = capsEnabled.getAsJsonObject("experimental")
        assertNotNull("experimental capability object should exist", experimental)
        assertNotNull("closingLabels capability should be present when enabled", experimental.getAsJsonObject("closingLabels"))

        val capsDisabled = DartAnalysisServerService.buildLspCapabilities("3.14.0-219.0.dev", false, false)
        assertNull("experimental capability should NOT exist when disabled", capsDisabled.getAsJsonObject("experimental"))
    }

    fun testLegacyComputedClosingLabelsAdapterForOlderSdks() {
        val code = """
            void main() {
              runApp(
                MyWidget(
                  child: Text('Hello'),
                ),
              );
            }
        """.trimIndent()
        val testFile = myFixture.configureByText("test_legacy_adapter.dart", code)
        val fileUri = "file://${testFile.virtualFile.path}"

        // The test fixture SDK version is 3.14.0-65.0.dev (< 3.14.0-219.0.dev), so LSP closing labels are disabled.
        assertFalse(DartAnalysisServerService.isLspClosingLabelsEnabled(project))

        val startOffset = code.indexOf("MyWidget(")
        val endOffset = code.indexOf("),\n  );") + 2
        val legacyLabel = ClosingLabel(
            startOffset,
            endOffset - startOffset,
            "MyWidget"
        )

        DartAnalysisServerService.getInstance(project).computedClosingLabels(fileUri, listOf(legacyLabel))

        val stored = DartLspClosingLabelsService.getInstance(project).getClosingLabels(testFile.virtualFile)
        assertEquals(1, stored.size)
        assertEquals("MyWidget", stored[0].label)
        assertEquals(4, stored[0].range?.end?.line)
    }

    fun testLegacyComputedClosingLabelsDoesNotDeadlockDuringWriteAction() {
        val code = """
            void main() {
              runApp(
                MyWidget(
                  child: Text('Hello'),
                ),
              );
            }
        """.trimIndent()
        val testFile = myFixture.configureByText("test_legacy_write_lock.dart", code)
        val fileUri = "file://${testFile.virtualFile.path}"

        assertFalse(DartAnalysisServerService.isLspClosingLabelsEnabled(project))

        val startOffset = code.indexOf("MyWidget(")
        val endOffset = code.indexOf("),\n  );") + 2
        val legacyLabel = ClosingLabel(
            startOffset,
            endOffset - startOffset,
            "MyWidget"
        )

        // Simulate the EDT holding the write lock (e.g. during MoveFileRefactoring) while the
        // Analysis Server reader thread processes an analysis.closingLabels notification.
        ApplicationManager.getApplication().runWriteAction {
            val future = CompletableFuture.runAsync(
                { DartAnalysisServerService.getInstance(project).computedClosingLabels(fileUri, listOf(legacyLabel)) },
                AppExecutorUtil.getAppExecutorService()
            )
            future.get(5, TimeUnit.SECONDS)
        }

        val stored = DartLspClosingLabelsService.getInstance(project).getClosingLabels(testFile.virtualFile)
        assertEquals(1, stored.size)
        assertEquals("MyWidget", stored[0].label)
        assertEquals(4, stored[0].range?.end?.line)
    }
}
