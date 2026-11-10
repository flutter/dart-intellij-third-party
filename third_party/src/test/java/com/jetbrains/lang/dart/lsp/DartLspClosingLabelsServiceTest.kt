// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.lsp

import com.intellij.platform.dartlsp.api.LspServerNotificationsHandler
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import java.lang.reflect.Proxy

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

    fun testCustomLsp4jClientHandlesPublishClosingLabelsViaLsp4jEndpoint() {
        val testFile = myFixture.addFileToProject(
            "lib/test_client_labels.dart",
            """
            void main() {
              runApp(
                Scaffold(),
              );
            }
            """.trimIndent()
        )
        val fileUri = "file://${testFile.virtualFile.path}"

        val noopHandler = Proxy.newProxyInstance(
            LspServerNotificationsHandler::class.java.classLoader,
            arrayOf(LspServerNotificationsHandler::class.java)
        ) { _, _, _ -> null } as LspServerNotificationsHandler

        val descriptor = DartLspServerDescriptor(project)
        val lsp4jClient = descriptor.createLsp4jClient(noopHandler)

        // Verify lsp4j discovers the @JsonNotification("dart/textDocument/publishClosingLabels") method on the client class
        val supportedMethods = ServiceEndpoints.getSupportedMethods(lsp4jClient.javaClass)
        val rpcMethod = requireNotNull(supportedMethods["dart/textDocument/publishClosingLabels"]) {
            "lsp4j should register dart/textDocument/publishClosingLabels on DartLsp4jClient"
        }
        assertTrue("Method should be a notification", rpcMethod.isNotification)

        // Dispatch notification through lsp4j Endpoint reflection mechanism
        val endpoint = ServiceEndpoints.toEndpoint(lsp4jClient)
        val params = DartPublishClosingLabelsParams(
            uri = fileUri,
            labels = listOf(
                DartLspClosingLabel(
                    label = "Scaffold",
                    range = Range(Position(1, 2), Position(3, 3))
                )
            )
        )
        endpoint.notify("dart/textDocument/publishClosingLabels", params)

        val stored = DartLspClosingLabelsService.getInstance(project).getClosingLabels(testFile.virtualFile)
        assertEquals(1, stored.size)
        assertEquals("Scaffold", stored[0].label)
        assertEquals(3, stored[0].range?.end?.line)
    }
}