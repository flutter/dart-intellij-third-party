package com.jetbrains.lang.dart.ide.errorTreeView

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase

class AnalysisServerDiagnosticsActionTest : DartCodeInsightFixtureTestCase() {

    fun testUpdateWhenNoServerRunning() {
        val action = AnalysisServerDiagnosticsAction()
        val dataContext = SimpleDataContext.getProjectContext(project)
        val event = TestActionEvent.createTestEvent(dataContext)
        
        // Neither LSP nor legacy server is running by default in tests
        action.update(event)
        
        assertFalse(event.presentation.isEnabledAndVisible)
    }
}
