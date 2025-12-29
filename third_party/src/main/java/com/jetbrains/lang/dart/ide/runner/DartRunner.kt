// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.runner

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.ide.runner.base.DartRunConfigurationBase
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunConfiguration
import com.jetbrains.lang.dart.ide.runner.server.DartRemoteDebugConfiguration
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcess
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcess.DebugType
import com.jetbrains.lang.dart.ide.runner.server.webdev.DartWebdevConfiguration
import com.jetbrains.lang.dart.ide.runner.test.DartTestRunConfiguration
import com.jetbrains.lang.dart.util.DartUrlResolver

class DartRunner : GenericProgramRunner<RunnerSettings>() {
  override fun getRunnerId(): String = "DartRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean =
    executorId == DefaultDebugExecutor.EXECUTOR_ID && (
        profile is DartCommandLineRunConfiguration ||
            profile is DartTestRunConfiguration ||
            profile is DartRemoteDebugConfiguration ||
            profile is DartWebdevConfiguration
        )

  @Throws(ExecutionException::class)
  override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    val executorId = environment.executor.id
    if (executorId != DefaultDebugExecutor.EXECUTOR_ID) {
      LOG.error("Unexpected executor id: $executorId")
      return null
    }

    try {
      val runConfig = environment.runProfile
      val project = environment.project
      val analysisServer = DartAnalysisServerService.getInstance(project)
      val dasExecutionContextId = if (
        runConfig is DartRunConfigurationBase &&
        analysisServer.serverReadyForRequest()
      ) {
        val path = checkNotNull(runConfig.runnerParameters.filePath)
        analysisServer.execution_createContext(path)
      } else {
        // No context ID if remote debugging or can't start DAS.
        null
      }

      return doExecuteDartDebug(state, environment, dasExecutionContextId)
    } catch (e: RuntimeConfigurationError) {
      throw ExecutionException(e)
    }
  }

  @Throws(RuntimeConfigurationError::class, ExecutionException::class)
  private fun doExecuteDartDebug(
    state: RunProfileState,
    env: ExecutionEnvironment,
    dasExecutionContextId: String?
  ): RunContentDescriptor? {
    val runConfiguration = env.runProfile
    val project = env.project

    val executionInfo = when (runConfiguration) {
      is DartRunConfigurationBase -> {
        val contextFileOrDir = runConfiguration.runnerParameters.dartFileOrDirectory
        val cwd = runConfiguration.runnerParameters.computeProcessWorkingDirectory(project)
        val currentWorkingDirectory = LocalFileSystem.getInstance().findFileByPath(cwd)
        val executionResult = state.execute(env.executor, this) ?: return null
        ExecutionInfo(contextFileOrDir, currentWorkingDirectory, executionResult, DebugType.CLI)
      }

      is DartRemoteDebugConfiguration -> {
        val path = runConfiguration.parameters.dartProjectPath
        val contextDirectory = LocalFileSystem.getInstance().findFileByPath(path)
          ?: throw RuntimeConfigurationError(
            DartBundle.message(
              "dialog.message.folder.not.found",
              FileUtil.toSystemDependentName(path)
            )
          )
        ExecutionInfo(contextDirectory, contextDirectory, null, DebugType.REMOTE)
      }

      is DartWebdevConfiguration -> {
        val contextFile = runConfiguration.parameters.htmlFile
        val currentWorkingDirectory = runConfiguration.parameters.getWorkingDirectory(project)
        val executionResult = state.execute(env.executor, this) ?: return null
        ExecutionInfo(contextFile, currentWorkingDirectory, executionResult, DebugType.WEBDEV)
      }

      else -> {
        LOG.error("Unexpected run configuration: ${runConfiguration.name}")
        return null
      }
    }

    FileDocumentManager.getInstance().saveAllDocuments()

    val debugSession = XDebuggerManager
      .getInstance(project)
      .startSession(env, object : XDebugProcessStarter() {
        override fun start(session: XDebugSession): XDebugProcess {
          val dartUrlResolver = DartUrlResolver
            .getInstance(project, executionInfo.contextFileOrDir)
          val dartVmServiceDebugProcess = DartVmServiceDebugProcess(
            session,
            executionInfo.executionResult,
            dartUrlResolver,
            dasExecutionContextId,
            executionInfo.debugType,
            VM_SERVICE_TIMEOUT_IN_MS,
            executionInfo.currentWorkingDirectory
          )

          dartVmServiceDebugProcess.start()
          return dartVmServiceDebugProcess
        }
      })

    return debugSession.runContentDescriptor
  }

  private data class ExecutionInfo(
    val contextFileOrDir: VirtualFile,
    val currentWorkingDirectory: VirtualFile?,
    val executionResult: ExecutionResult?,
    val debugType: DebugType,
  )

  companion object {
    private val LOG = Logger.getInstance(DartRunner::class.java)
    private const val VM_SERVICE_TIMEOUT_IN_MS = 5000
  }
}
