package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Project-level service to manage the lifecycle of the [DartMessageProducer].
 * 
 * Ensures that the producer is stopped and resources are released when the project is closed.
 */
@Service(Service.Level.PROJECT)
class DartLspProjectService(private val project: Project) : Disposable {
    var producer: DartMessageProducer? = null

    override fun dispose() {
        producer?.stop()
        producer = null
    }
}
