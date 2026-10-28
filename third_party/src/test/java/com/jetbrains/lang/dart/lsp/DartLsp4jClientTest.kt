/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.platform.dartlsp.api.LspServerNotificationsHandler
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.ide.errorTreeView.DartProblemsView
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import java.lang.reflect.Proxy

class DartLsp4jClientTest : DartCodeInsightFixtureTestCase() {

    private fun createMockHandler(onPublish: (PublishDiagnosticsParams) -> Unit): LspServerNotificationsHandler {
        return Proxy.newProxyInstance(
            LspServerNotificationsHandler::class.java.classLoader,
            arrayOf(LspServerNotificationsHandler::class.java)
        ) { _, method, args ->
            if (method.name == "publishDiagnostics" && args != null && args.isNotEmpty()) {
                onPublish(args[0] as PublishDiagnosticsParams)
            }
            null
        } as LspServerNotificationsHandler
    }

    fun testPublishDiagnosticsCallsSuperAndUpdatesProblemsView() {
        val testFile = myFixture.addFileToProject(
            "lib/test_diagnostics.dart",
            """
            void main() {
              int x = "string";
            }
            """.trimIndent()
        )
        val fileUri = "file://${testFile.virtualFile.path}"

        var forwardedParams: PublishDiagnosticsParams? = null
        val handler = createMockHandler { forwardedParams = it }
        val client = DartLsp4jClient(project, handler)

        val diagnostic = Diagnostic(
            Range(Position(1, 10), Position(1, 18)),
            "A value of type 'String' can't be assigned to a variable of type 'int'.",
            DiagnosticSeverity.Error,
            "dart",
            "invalid_assignment"
        )
        val params = PublishDiagnosticsParams(fileUri, listOf(diagnostic))

        client.publishDiagnostics(params)

        // 1. Verify super.publishDiagnostics was called and received params
        assertNotNull("super.publishDiagnostics should have been called", forwardedParams)
        assertEquals(fileUri, forwardedParams?.uri)
        assertEquals(1, forwardedParams?.diagnostics?.size)

        // 2. Verify DartProblemsView received and retained the converted errors
        val problemsView = DartProblemsView.getInstance(project)
        val currentErrors = problemsView.currentFilePathToErrors
        val filePath = testFile.virtualFile.path
        assertTrue("DartProblemsView should contain errors for file", currentErrors.containsKey(filePath))
        val errors = requireNotNull(currentErrors[filePath])
        assertEquals(1, errors.size)
        assertEquals("invalid_assignment", errors[0].code)
        assertEquals("A value of type 'String' can't be assigned to a variable of type 'int'.", errors[0].message)
    }

    fun testPublishEmptyDiagnosticsClearsErrorsInProblemsView() {
        val testFile = myFixture.addFileToProject(
            "lib/clear_test.dart",
            "void main() {}"
        )
        val fileUri = "file://${testFile.virtualFile.path}"
        val filePath = testFile.virtualFile.path

        val handler = createMockHandler { }
        val client = DartLsp4jClient(project, handler)
        val problemsView = DartProblemsView.getInstance(project)

        // Publish error first
        val diagnostic = Diagnostic(
            Range(Position(0, 0), Position(0, 4)),
            "Error",
            DiagnosticSeverity.Error,
            "dart",
            "some_error"
        )
        client.publishDiagnostics(PublishDiagnosticsParams(fileUri, listOf(diagnostic)))
        assertTrue(problemsView.currentFilePathToErrors.containsKey(filePath))

        // Publish empty diagnostics
        client.publishDiagnostics(PublishDiagnosticsParams(fileUri, emptyList()))
        assertFalse(
            "Errors should be cleared for file after empty diagnostics published",
            problemsView.currentFilePathToErrors.containsKey(filePath)
        )
    }

    fun testProblemsViewClearAll() {
        val testFile = myFixture.addFileToProject(
            "lib/clear_all_test.dart",
            "void main() {}"
        )
        val fileUri = "file://${testFile.virtualFile.path}"
        val filePath = testFile.virtualFile.path

        val handler = createMockHandler { }
        val client = DartLsp4jClient(project, handler)
        val problemsView = DartProblemsView.getInstance(project)

        val diagnostic = Diagnostic(
            Range(Position(0, 0), Position(0, 4)),
            "Error",
            DiagnosticSeverity.Error,
            "dart",
            "some_error"
        )
        client.publishDiagnostics(PublishDiagnosticsParams(fileUri, listOf(diagnostic)))
        assertTrue(problemsView.currentFilePathToErrors.containsKey(filePath))

        problemsView.clearAll()
        assertTrue(
            "clearAll should remove all errors across all files",
            problemsView.currentFilePathToErrors.isEmpty()
        )
    }

    fun testIsFileWithErrorsForFileAndDirectory() {
        val testFile = myFixture.addFileToProject(
            "lib/dir/sub/error_test.dart",
            "void main() {}"
        )
        val fileUri = "file://${testFile.virtualFile.path}"
        val vFile = testFile.virtualFile
        val parentDir = vFile.parent

        val handler = createMockHandler { }
        val client = DartLsp4jClient(project, handler)
        val problemsView = DartProblemsView.getInstance(project)

        val diagnostic = Diagnostic(
            Range(Position(0, 0), Position(0, 4)),
            "Error",
            DiagnosticSeverity.Error,
            "dart",
            "compile_error"
        )
        client.publishDiagnostics(PublishDiagnosticsParams(fileUri, listOf(diagnostic)))

        assertTrue("File should be reported as having errors", problemsView.isFileWithErrors(vFile))
        assertTrue("Parent directory should be reported as having errors", problemsView.isFileWithErrors(parentDir))

        // Empty diagnostics clears errors
        client.publishDiagnostics(PublishDiagnosticsParams(fileUri, emptyList()))
        assertFalse("File should no longer have errors", problemsView.isFileWithErrors(vFile))
        assertFalse("Parent directory should no longer have errors", problemsView.isFileWithErrors(parentDir))
    }

    fun testPublishDiagnosticsFiltersOutTodoDiagnostics() {
        val testFile = myFixture.addFileToProject(
            "lib/todo_filter_test.dart",
            "// TODO: something"
        )
        val fileUri = "file://${testFile.virtualFile.path}"
        val filePath = testFile.virtualFile.path

        val handler = createMockHandler { }
        val client = DartLsp4jClient(project, handler)
        val problemsView = DartProblemsView.getInstance(project)

        val todoDiagnostic = Diagnostic(
            Range(Position(0, 0), Position(0, 18)),
            "TODO: something",
            DiagnosticSeverity.Information,
            "dart",
            "todo"
        )
        client.publishDiagnostics(PublishDiagnosticsParams(fileUri, listOf(todoDiagnostic)))

        assertFalse(
            "TODO diagnostics should be filtered out from DartProblemsView",
            problemsView.currentFilePathToErrors.containsKey(filePath)
        )
    }

    fun testPublishDiagnosticsIgnoredWhenProjectDisposed() {
        val testFile = myFixture.addFileToProject(
            "lib/disposed_test.dart",
            "void main() {}"
        )
        val fileUri = "file://${testFile.virtualFile.path}"
        var handlerCalled = false
        val handler = createMockHandler { handlerCalled = true }

        val disposedProject = Proxy.newProxyInstance(
            com.intellij.openapi.project.Project::class.java.classLoader,
            arrayOf(com.intellij.openapi.project.Project::class.java)
        ) { _, method, _ ->
            if (method.name == "isDisposed") true else null
        } as com.intellij.openapi.project.Project

        val client = DartLsp4jClient(disposedProject, handler)
        val diagnostic = Diagnostic(
            Range(Position(0, 0), Position(0, 1)),
            "Error",
            DiagnosticSeverity.Error,
            "dart",
            "error_code"
        )
        client.publishDiagnostics(PublishDiagnosticsParams(fileUri, listOf(diagnostic)))

        assertFalse("super.publishDiagnostics should not be called if project is disposed", handlerCalled)
    }
}
