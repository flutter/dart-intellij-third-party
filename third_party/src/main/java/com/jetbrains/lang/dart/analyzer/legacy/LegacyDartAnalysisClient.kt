// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.legacy

import com.google.dart.server.AnalysisServerListener
import com.google.dart.server.AnalysisServerStatusListener
import com.google.dart.server.Consumer
import com.google.dart.server.CreateContextConsumer
import com.google.dart.server.DartLspTextDocumentContentConsumer
import com.google.dart.server.FindElementReferencesConsumer
import com.google.dart.server.FormatConsumer
import com.google.dart.server.GetAssistsConsumer
import com.google.dart.server.GetFixesConsumer
import com.google.dart.server.GetHoverConsumer
import com.google.dart.server.GetImportedElementsConsumer
import com.google.dart.server.GetNavigationConsumer
import com.google.dart.server.GetPostfixCompletionConsumer
import com.google.dart.server.GetRefactoringConsumer
import com.google.dart.server.GetRuntimeCompletionConsumer
import com.google.dart.server.GetServerPortConsumer
import com.google.dart.server.GetStatementCompletionConsumer
import com.google.dart.server.GetSuggestionDetailsConsumer
import com.google.dart.server.GetSuggestionDetailsConsumer2
import com.google.dart.server.GetSuggestionsConsumer
import com.google.dart.server.GetSuggestionsConsumer2
import com.google.dart.server.GetTypeHierarchyConsumer
import com.google.dart.server.ImportElementsConsumer
import com.google.dart.server.IsPostfixCompletionApplicableConsumer
import com.google.dart.server.ListPostfixCompletionTemplatesConsumer
import com.google.dart.server.MapUriConsumer
import com.google.dart.server.OrganizeDirectivesConsumer
import com.google.dart.server.RequestListener
import com.google.dart.server.ResponseListener
import com.google.dart.server.SortMembersConsumer
import com.google.dart.server.UpdateContentConsumer
import com.google.dart.server.internal.remote.DebugPrintStream
import com.google.dart.server.internal.remote.RemoteAnalysisServerImpl
import com.google.dart.server.internal.remote.StdioServerSocket
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerImpl
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.DartClient
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkUtil
import org.dartlang.analysis.server.protocol.AnalysisOptions
import org.dartlang.analysis.server.protocol.ImportedElements
import org.dartlang.analysis.server.protocol.RefactoringOptions
import org.dartlang.analysis.server.protocol.RuntimeCompletionExpression
import org.dartlang.analysis.server.protocol.RuntimeCompletionVariable
import java.util.MissingResourceException

internal class LegacyDartAnalysisClient(
    project: Project,
    sdk: DartSdk,
    debugStream: DebugPrintStream,
    suppressAnalytics: Boolean,
) : DartClient {
    private val serverSocket: StdioServerSocket =
        run {
            val runtimePath = FileUtil.toSystemDependentName(DartSdkUtil.getDartExePath(sdk))

            val vmArguments =
                try {
                    StringUtil.split(Registry.stringValue("dart.server.vm.options"), " ")
                } catch (_: MissingResourceException) {
                    emptyList()
                }

            val useDartLangServerCall = DartAnalysisServerService.isDartSdkVersionSufficientForDartLangServer(sdk)
            val analysisServerPath =
                System.getProperty(
                    "dart.server.path",
                    FileUtil.toSystemDependentName(sdk.homePath + "/bin/snapshots/analysis_server.dart.snapshot"),
                )
            val firstArgument = if (useDartLangServerCall) "language-server" else analysisServerPath

            val serverArgsRaw =
                buildString {
                    append(if (useDartLangServerCall) "--protocol=analyzer" else "")
                    if (suppressAnalytics) {
                        if (isNotEmpty()) append(" ")
                        append("--suppress-analytics")
                    }
                    try {
                        val extra = Registry.stringValue("dart.server.additional.arguments")
                        if (extra.isNotBlank()) {
                            if (isNotEmpty()) append(" ")
                            append(extra)
                        }
                    } catch (_: MissingResourceException) {
                    }
                }
            val serverArguments = StringUtil.split(serverArgsRaw, " ")

            val clientId = ApplicationNamesInfo.getInstance().fullProductName.replace(' ', '-')
            val clientVersion = ApplicationInfo.getInstance().apiVersion

            StdioServerSocket(runtimePath, vmArguments, firstArgument, serverArguments, debugStream).apply {
                setClientId(clientId)
                setClientVersion(clientVersion)
            }
        }

    private val remoteAnalysisClient: RemoteAnalysisServerImpl = DartAnalysisServerImpl(project, serverSocket)

    private companion object {
        private val LOG = Logger.getInstance(LegacyDartAnalysisClient::class.java)
        private const val SHUTDOWN_TIMEOUT_MS = 1_000L
        private const val CHECK_PERIOD_MS = 10L
    }

    override fun start() {
        remoteAnalysisClient.start()
    }

    override fun isSocketOpen(): Boolean = remoteAnalysisClient.isSocketOpen()

    override fun addAnalysisServerListener(listener: AnalysisServerListener) {
        remoteAnalysisClient.addAnalysisServerListener(listener)
    }

    override fun removeAnalysisServerListener(listener: AnalysisServerListener) {
        remoteAnalysisClient.removeAnalysisServerListener(listener)
    }

    override fun addRequestListener(listener: RequestListener) {
        remoteAnalysisClient.addRequestListener(listener)
    }

    override fun removeRequestListener(listener: RequestListener) {
        remoteAnalysisClient.removeRequestListener(listener)
    }

    override fun addResponseListener(listener: ResponseListener) {
        remoteAnalysisClient.addResponseListener(listener)
    }

    override fun removeResponseListener(listener: ResponseListener) {
        remoteAnalysisClient.removeResponseListener(listener)
    }

    override fun addStatusListener(listener: AnalysisServerStatusListener) {
        remoteAnalysisClient.addStatusListener(listener)
    }

    override fun completion_setSubscriptions(subscriptions: List<String>) {
        remoteAnalysisClient.completion_setSubscriptions(subscriptions)
    }

    override fun analysis_updateContent(
        files: Map<String, Any>,
        consumer: UpdateContentConsumer,
    ) {
        remoteAnalysisClient.analysis_updateContent(files, consumer)
    }

    override fun analysis_setAnalysisRoots(
        included: List<String>,
        excluded: List<String>,
        packageRoots: Map<String, String>?,
    ) {
        remoteAnalysisClient.analysis_setAnalysisRoots(included, excluded, packageRoots)
    }

    override fun analysis_getHover(
        file: String,
        offset: Int,
        consumer: GetHoverConsumer,
    ) {
        remoteAnalysisClient.analysis_getHover(file, offset, consumer)
    }

    override fun analysis_getNavigation(
        file: String,
        offset: Int,
        length: Int,
        consumer: GetNavigationConsumer,
    ) {
        remoteAnalysisClient.analysis_getNavigation(file, offset, length, consumer)
    }

    override fun edit_getAssists(
        file: String,
        offset: Int,
        length: Int,
        consumer: GetAssistsConsumer,
    ) {
        remoteAnalysisClient.edit_getAssists(file, offset, length, consumer)
    }

    override fun edit_isPostfixCompletionApplicable(
        file: String,
        key: String,
        offset: Int,
        consumer: IsPostfixCompletionApplicableConsumer,
    ) {
        remoteAnalysisClient.edit_isPostfixCompletionApplicable(file, key, offset, consumer)
    }

    override fun edit_listPostfixCompletionTemplates(consumer: ListPostfixCompletionTemplatesConsumer) {
        remoteAnalysisClient.edit_listPostfixCompletionTemplates(consumer)
    }

    override fun edit_getPostfixCompletion(
        file: String,
        key: String,
        offset: Int,
        consumer: GetPostfixCompletionConsumer,
    ) {
        remoteAnalysisClient.edit_getPostfixCompletion(file, key, offset, consumer)
    }

    override fun edit_getStatementCompletion(
        file: String,
        offset: Int,
        consumer: GetStatementCompletionConsumer,
    ) {
        remoteAnalysisClient.edit_getStatementCompletion(file, offset, consumer)
    }

    override fun diagnostic_getServerPort(consumer: GetServerPortConsumer) {
        remoteAnalysisClient.diagnostic_getServerPort(consumer)
    }

    override fun edit_getFixes(
        file: String,
        offset: Int,
        consumer: GetFixesConsumer,
    ) {
        remoteAnalysisClient.edit_getFixes(file, offset, consumer)
    }

    override fun search_findElementReferences(
        file: String,
        offset: Int,
        includePotential: Boolean,
        consumer: FindElementReferencesConsumer,
    ) {
        remoteAnalysisClient.search_findElementReferences(file, offset, includePotential, consumer)
    }

    override fun search_getTypeHierarchy(
        file: String,
        offset: Int,
        superOnly: Boolean,
        consumer: GetTypeHierarchyConsumer,
    ) {
        remoteAnalysisClient.search_getTypeHierarchy(file, offset, superOnly, consumer)
    }

    override fun completion_getSuggestionDetails(
        file: String,
        id: Int,
        label: String,
        offset: Int,
        consumer: GetSuggestionDetailsConsumer,
    ) {
        remoteAnalysisClient.completion_getSuggestionDetails(file, id, label, offset, consumer)
    }

    override fun completion_getSuggestionDetails2(
        file: String,
        offset: Int,
        completion: String,
        libraryUri: String,
        consumer: GetSuggestionDetailsConsumer2,
    ) {
        remoteAnalysisClient.completion_getSuggestionDetails2(file, offset, completion, libraryUri, consumer)
    }

    override fun completion_getSuggestions(
        file: String,
        offset: Int,
        consumer: GetSuggestionsConsumer,
    ) {
        remoteAnalysisClient.completion_getSuggestions(file, offset, consumer)
    }

    override fun completion_getSuggestions2(
        file: String,
        offset: Int,
        maxResults: Int,
        completionCaseMatchingMode: String,
        completionMode: String,
        invocationCount: Int,
        timeout: Int,
        consumer: GetSuggestionsConsumer2,
    ) {
        remoteAnalysisClient.completion_getSuggestions2(
            file,
            offset,
            maxResults,
            completionCaseMatchingMode,
            completionMode,
            invocationCount,
            timeout,
            consumer,
        )
    }

    override fun edit_format(
        file: String,
        selectionOffset: Int,
        selectionLength: Int,
        lineLength: Int,
        consumer: FormatConsumer,
    ) {
        remoteAnalysisClient.edit_format(file, selectionOffset, selectionLength, lineLength, consumer)
    }

    override fun analysis_getImportedElements(
        file: String,
        offset: Int,
        length: Int,
        consumer: GetImportedElementsConsumer,
    ) {
        remoteAnalysisClient.analysis_getImportedElements(file, offset, length, consumer)
    }

    override fun edit_importElements(
        file: String,
        elements: List<ImportedElements>,
        offset: Int,
        consumer: ImportElementsConsumer,
    ) {
        remoteAnalysisClient.edit_importElements(file, elements, offset, consumer)
    }

    override fun edit_getRefactoring(
        kind: String,
        file: String,
        offset: Int,
        length: Int,
        validateOnly: Boolean,
        options: RefactoringOptions?,
        consumer: GetRefactoringConsumer,
    ) {
        remoteAnalysisClient.edit_getRefactoring(kind, file, offset, length, validateOnly, options, consumer)
    }

    override fun edit_organizeDirectives(
        file: String,
        consumer: OrganizeDirectivesConsumer,
    ) {
        remoteAnalysisClient.edit_organizeDirectives(file, consumer)
    }

    override fun edit_sortMembers(
        file: String,
        consumer: SortMembersConsumer,
    ) {
        remoteAnalysisClient.edit_sortMembers(file, consumer)
    }

    override fun analysis_reanalyze() {
        remoteAnalysisClient.analysis_reanalyze()
    }

    override fun analysis_setPriorityFiles(files: List<String>) {
        remoteAnalysisClient.analysis_setPriorityFiles(files)
    }

    override fun analysis_setSubscriptions(subscriptions: Map<String, List<String>>) {
        remoteAnalysisClient.analysis_setSubscriptions(subscriptions)
    }

    override fun execution_createContext(
        contextRoot: String,
        consumer: CreateContextConsumer,
    ) {
        remoteAnalysisClient.execution_createContext(contextRoot, consumer)
    }

    override fun execution_deleteContext(contextId: String) {
        remoteAnalysisClient.execution_deleteContext(contextId)
    }

    override fun execution_getSuggestions(
        code: String,
        offset: Int,
        contextFile: String,
        contextOffset: Int,
        variables: List<RuntimeCompletionVariable>,
        expressions: List<RuntimeCompletionExpression>?,
        consumer: GetRuntimeCompletionConsumer,
    ) {
        remoteAnalysisClient.execution_getSuggestions(code, offset, contextFile, contextOffset, variables, expressions, consumer)
    }

    override fun execution_mapUri(
        id: String,
        file: String?,
        uri: String?,
        consumer: MapUriConsumer,
    ) {
        remoteAnalysisClient.execution_mapUri(id, file, uri, consumer)
    }

    override fun server_setSubscriptions(subscriptions: List<String>) {
        remoteAnalysisClient.server_setSubscriptions(subscriptions)
    }

    override fun analysis_updateOptions(options: AnalysisOptions) {
        remoteAnalysisClient.analysis_updateOptions(options)
    }

    override fun server_setClientCapabilities(
        requests: List<String>,
        supportsUris: Boolean,
        supportsWorkspaceApplyEdits: Boolean,
    ) {
        remoteAnalysisClient.server_setClientCapabilities(requests, supportsUris, supportsWorkspaceApplyEdits)
    }

    override fun server_shutdown() {
        try {
            remoteAnalysisClient.server_shutdown()
        } finally {
            val startTime = System.currentTimeMillis()
            while (serverSocket.isOpen) {
                if (System.currentTimeMillis() - startTime > SHUTDOWN_TIMEOUT_MS) {
                    try {
                        serverSocket.stop()
                    } catch (t: Throwable) {
                        LOG.warn("Failed to stop analysis server socket", t)
                    }
                    break
                }
                try {
                    Thread.sleep(CHECK_PERIOD_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

    override fun generateUniqueId(): String = remoteAnalysisClient.generateUniqueId()

    override fun sendRequestToServer(
        id: String,
        request: JsonObject,
    ) {
        remoteAnalysisClient.sendRequestToServer(id, request)
    }

    override fun sendRequestToServer(
        id: String,
        request: JsonObject,
        consumer: Consumer,
    ) {
        remoteAnalysisClient.sendRequestToServer(id, request, consumer)
    }

    override fun lspMessage_dart_textDocumentContent(
        uri: String,
        consumer: DartLspTextDocumentContentConsumer,
    ) {
        remoteAnalysisClient.lspMessage_dart_textDocumentContent(uri, consumer)
    }

    override fun lsp_connectToDtd(uri: String) {
        remoteAnalysisClient.lsp_connectToDtd(uri)
    }
}
