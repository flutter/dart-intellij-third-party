/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.analyzer

import com.google.dart.server.internal.remote.utilities.RequestUtilities
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.PlatformTestUtil
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import org.dartlang.analysis.server.protocol.MessageType

class DartServerMessageRequestTest : DartCodeInsightFixtureTestCase() {

    private val actions = listOf("Rename Anyway", "Cancel")
    private val answers = mutableListOf<String?>()

    override fun tearDown() {
        try {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
        catch (e: Throwable) {
            addSuppressedException(e)
        }
        finally {
            super.tearDown()
        }
    }

    private fun show(isModalContext: Boolean) {
        showServerMessageRequest(
            project, MessageType.WARNING, "Class 'Greeter' already declares method with name 'hello'.", actions,
            isModalContext = { isModalContext },
        ) { answers += it }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    fun testModalContextShowsDialogAndAnswersChoice() {
        val shownMessages = mutableListOf<String>()
        TestDialogManager.setTestDialog { message -> shownMessages += message; 0 }

        show(isModalContext = true)

        assertEquals(listOf("Class 'Greeter' already declares method with name 'hello'."), shownMessages)
        assertEquals(listOf<String?>("Rename Anyway"), answers)
    }

    fun testModalDialogClosedAnswersNull() {
        TestDialogManager.setTestDialog { -1 }

        show(isModalContext = true)

        assertEquals(listOf<String?>(null), answers)
    }

    fun testNonModalContextShowsNotification() {
        TestDialogManager.setTestDialog { fail("No dialog expected"); 0 }
        val notifications = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications += notification
            }
        })

        show(isModalContext = false)

        val notification = notifications.single()
        assertEquals("Class 'Greeter' already declares method with name 'hello'.", notification.content)
        assertEquals(listOf("Rename Anyway", "Cancel"), notification.actions.map { it.templatePresentation.text })
        assertEmpty(answers)
    }

    fun testNoChoiceIsSentWithoutAction() {
        val response = RequestUtilities.generateShowMessageRequestResponse("7", null)

        assertEquals("""{"id":"7","result":{}}""", response.toString())
    }
}
