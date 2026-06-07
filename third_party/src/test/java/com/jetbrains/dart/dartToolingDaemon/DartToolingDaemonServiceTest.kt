package com.jetbrains.dart.dartToolingDaemon

import com.google.gson.JsonParser
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonServiceListener
import com.intellij.util.ui.UIUtil
import com.jetbrains.lang.dart.util.DartTestUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createTempDirectory

class DartToolingDaemonServiceTest : BasePlatformTestCase() {

    private companion object {
        const val INITIALIZATION_TIMEOUT_SECONDS = 5L
    }

    private lateinit var dartToolingDaemonService: DartToolingDaemonService

    override fun setUp() {
        super.setUp()
        DartTestUtils.configureDartSdk(module, myFixture.projectDisposable, true)
        val tempDir = createTempDirectory("dtd_test_root").toFile()
        ModuleRootModificationUtil.addContentRoot(module, tempDir.absolutePath)
    }

    override fun tearDown() {
        try {
            if (::dartToolingDaemonService.isInitialized) {
                dartToolingDaemonService.dispose()
            }
        } finally {
            super.tearDown()
        }
    }

    fun testInitializationRequestsAreSentOnWebSocketOpen() {
        val allRequestsAcknowledged = CountDownLatch(3)
        val getActiveLocationRegistered = AtomicBoolean(false)
        val navigateToCodeRegistered = AtomicBoolean(false)
        val workspaceRootsSet = AtomicBoolean(false)

        dartToolingDaemonService = DartToolingDaemonService.getInstance(project)
        dartToolingDaemonService.listener = object : DartToolingDaemonServiceListener {
            override fun onWebSocketMessage(text: String) {
                val json = JsonParser.parseString(text).asJsonObject
                val result = json["result"]?.asJsonObject ?: return

                if (result["type"]?.asString == "Success") {
                    val id = json["id"]?.asInt
                    when (id) {
                        1 -> getActiveLocationRegistered.set(true)
                        2 -> navigateToCodeRegistered.set(true)
                        3 -> workspaceRootsSet.set(true)
                    }
                    allRequestsAcknowledged.countDown()
                }
            }
        }

        dartToolingDaemonService.startService()

        val deadline = System.currentTimeMillis() + INITIALIZATION_TIMEOUT_SECONDS * 1000
        while (!allRequestsAcknowledged.await(100, TimeUnit.MILLISECONDS)) {
            assertTrue(
                "Did not receive all 3 acknowledgements within ${INITIALIZATION_TIMEOUT_SECONDS}s",
                System.currentTimeMillis() < deadline
            )
            UIUtil.dispatchAllInvocationEvents()
        }
        assertTrue("Editor.getActiveLocation should be registered", getActiveLocationRegistered.get())
        assertTrue("Editor.navigateToCode should be registered", navigateToCodeRegistered.get())
        assertTrue("IDE workspace roots should be set", workspaceRootsSet.get())
    }
}
