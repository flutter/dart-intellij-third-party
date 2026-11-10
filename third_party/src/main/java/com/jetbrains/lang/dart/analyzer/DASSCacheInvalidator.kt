package com.jetbrains.lang.dart.analyzer

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.logging.PluginLogger
import java.io.File
import java.io.IOException
import java.util.concurrent.Future

private val LOG = PluginLogger.createLogger(DASSCacheInvalidator::class.java)

@JvmSynthetic
internal fun deleteDartServerCacheAsync(cacheDirectory: File?): Future<*>? {
    if (cacheDirectory == null || !cacheDirectory.exists()) {
        return null
    }
    val cachePath = cacheDirectory.toPath()
    return ApplicationManager.getApplication().executeOnPooledThread {
        try {
            NioFiles.deleteRecursively(cachePath)
        } catch (e: IOException) {
            LOG.warn("Failed to delete Dart analysis server cache at $cachePath", e)
        }
    }
}

/**
 * A CachesInvalidator for the Dart IntelliJ plugin, on the execution of this invalidator,
 * the `~./dartServer/` directory (or equivalent on Windows machines), is deleted.
 */
private class DASSCacheInvalidator : CachesInvalidator() {

    override fun getComment(): String = DartBundle.message("analysis.server.cache.invalidate.comment")

    override fun getDescription(): String = DartBundle.message("analysis.server.cache.invalidate.description")

    override fun optionalCheckboxDefaultValue(): Boolean = false

    override fun invalidateCaches() {
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            val serverService = DartAnalysisServerService.getInstance(project)
            serverService.stopServer()
        }
        deleteDartServerCacheAsync(getDartServerCacheDirectory())
    }

    private fun getDartServerCacheDirectory(): File? {
        return if (SystemInfoRt.isWindows) {
            val appData = System.getenv("LOCALAPPDATA")
            if (appData != null) File(appData, ".dartServer") else null
        } else {
            val userHome = System.getProperty("user.home")
            if (userHome != null) File(userHome, ".dartServer") else null
        }
    }
}
