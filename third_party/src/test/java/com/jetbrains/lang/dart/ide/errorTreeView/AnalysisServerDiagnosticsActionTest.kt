// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.errorTreeView

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService

class AnalysisServerDiagnosticsActionTest : BasePlatformTestCase() {

    fun testUpdateWhenNoServerRunning() {
        val action = AnalysisServerDiagnosticsAction()
        val dataContext = SimpleDataContext.getProjectContext(project)
        val event = TestActionEvent.createTestEvent(dataContext)
        
        // Neither LSP nor legacy server is running by default in tests
        action.update(event)
        
        assertFalse(event.presentation.isEnabledAndVisible)
        assertNull(project.getServiceIfCreated(DartAnalysisServerService::class.java))
    }

    fun testRunDoesNotInstantiateLegacyServerService() {
        val action = AnalysisServerDiagnosticsAction()
        val dataContext = SimpleDataContext.getProjectContext(project)
        val event = TestActionEvent.createTestEvent(dataContext)

        action.run(project, event)

        assertNull(project.getServiceIfCreated(DartAnalysisServerService::class.java))
    }
}
