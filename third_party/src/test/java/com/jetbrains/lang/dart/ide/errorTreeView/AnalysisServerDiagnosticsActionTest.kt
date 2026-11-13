// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.errorTreeView

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import java.lang.reflect.Proxy

class AnalysisServerDiagnosticsActionTest : BasePlatformTestCase() {

    private var getServiceIfCreatedCalled = false
    private var getServiceCalled = false

    private fun createMockProject(): Project {
        getServiceIfCreatedCalled = false
        getServiceCalled = false
        val delegateProject = project
        val userData = UserDataHolderBase()
        return Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java)
        ) { proxy, method, args ->
            if (args?.firstOrNull() == DartAnalysisServerService::class.java) {
                when (method.name) {
                    "getServiceIfCreated" -> {
                        getServiceIfCreatedCalled = true
                        return@newProxyInstance null
                    }
                    "getService" -> {
                        getServiceCalled = true
                        return@newProxyInstance null
                    }
                }
            }
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "MockProject(${delegateProject.name})"
                else -> {
                    val target = if (method.declaringClass.isAssignableFrom(UserDataHolder::class.java)) userData else delegateProject
                    if (args == null) method.invoke(target) else method.invoke(target, *args)
                }
            }
        } as Project
    }

    fun testUpdateWhenNoServerRunning() {
        val mockProject = createMockProject()
        val action = AnalysisServerDiagnosticsAction()
        val dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, mockProject)
        val event = TestActionEvent.createTestEvent(dataContext)

        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
        assertTrue(getServiceIfCreatedCalled)
        assertFalse(getServiceCalled)
    }

    fun testRunDoesNotInstantiateLegacyServerService() {
        val mockProject = createMockProject()
        val action = AnalysisServerDiagnosticsAction()
        val dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, mockProject)
        val event = TestActionEvent.createTestEvent(dataContext)

        action.run(mockProject, event)

        assertFalse(getServiceCalled)
    }
}
