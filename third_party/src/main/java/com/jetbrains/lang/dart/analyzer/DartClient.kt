// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer

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
import com.google.gson.JsonObject
import org.dartlang.analysis.server.protocol.AnalysisOptions
import org.dartlang.analysis.server.protocol.ImportedElements
import org.dartlang.analysis.server.protocol.RefactoringOptions
import org.dartlang.analysis.server.protocol.RuntimeCompletionExpression
import org.dartlang.analysis.server.protocol.RuntimeCompletionVariable

internal interface DartClient {
  @Throws(Exception::class)
  fun start()

  fun isSocketOpen(): Boolean

  fun addAnalysisServerListener(listener: AnalysisServerListener)

  fun removeAnalysisServerListener(listener: AnalysisServerListener)

  fun addRequestListener(listener: RequestListener)

  fun removeRequestListener(listener: RequestListener)

  fun addResponseListener(listener: ResponseListener)

  fun removeResponseListener(listener: ResponseListener)

  fun addStatusListener(listener: AnalysisServerStatusListener)

  fun completion_setSubscriptions(subscriptions: List<String>)

  fun analysis_updateContent(files: Map<String, Any>, consumer: UpdateContentConsumer)

  fun analysis_setAnalysisRoots(included: List<String>, excluded: List<String>, packageRoots: Map<String, String>?)

  fun analysis_getHover(file: String, offset: Int, consumer: GetHoverConsumer)

  fun analysis_getNavigation(file: String, offset: Int, length: Int, consumer: GetNavigationConsumer)

  fun edit_getAssists(file: String, offset: Int, length: Int, consumer: GetAssistsConsumer)

  fun edit_isPostfixCompletionApplicable(file: String, key: String, offset: Int, consumer: IsPostfixCompletionApplicableConsumer)

  fun edit_listPostfixCompletionTemplates(consumer: ListPostfixCompletionTemplatesConsumer)

  fun edit_getPostfixCompletion(file: String, key: String, offset: Int, consumer: GetPostfixCompletionConsumer)

  fun edit_getStatementCompletion(file: String, offset: Int, consumer: GetStatementCompletionConsumer)

  fun diagnostic_getServerPort(consumer: GetServerPortConsumer)

  fun edit_getFixes(file: String, offset: Int, consumer: GetFixesConsumer)

  fun search_findElementReferences(file: String, offset: Int, includePotential: Boolean, consumer: FindElementReferencesConsumer)

  fun search_getTypeHierarchy(file: String, offset: Int, superOnly: Boolean, consumer: GetTypeHierarchyConsumer)

  fun completion_getSuggestionDetails(file: String, id: Int, label: String, offset: Int, consumer: GetSuggestionDetailsConsumer)

  fun completion_getSuggestionDetails2(
    file: String,
    offset: Int,
    completion: String,
    libraryUri: String,
    consumer: GetSuggestionDetailsConsumer2,
  )

  fun completion_getSuggestions(file: String, offset: Int, consumer: GetSuggestionsConsumer)

  fun completion_getSuggestions2(
    file: String,
    offset: Int,
    maxResults: Int,
    completionCaseMatchingMode: String,
    completionMode: String,
    invocationCount: Int,
    timeout: Int,
    consumer: GetSuggestionsConsumer2,
  )

  fun edit_format(file: String, selectionOffset: Int, selectionLength: Int, lineLength: Int, consumer: FormatConsumer)

  fun analysis_getImportedElements(file: String, offset: Int, length: Int, consumer: GetImportedElementsConsumer)

  fun edit_importElements(file: String, elements: List<ImportedElements>, offset: Int, consumer: ImportElementsConsumer)

  fun edit_getRefactoring(
    kind: String,
    file: String,
    offset: Int,
    length: Int,
    validateOnly: Boolean,
    options: RefactoringOptions?,
    consumer: GetRefactoringConsumer,
  )

  fun edit_organizeDirectives(file: String, consumer: OrganizeDirectivesConsumer)

  fun edit_sortMembers(file: String, consumer: SortMembersConsumer)

  fun analysis_reanalyze()

  fun analysis_setPriorityFiles(files: List<String>)

  fun analysis_setSubscriptions(subscriptions: Map<String, List<String>>)

  fun execution_createContext(contextRoot: String, consumer: CreateContextConsumer)

  fun execution_deleteContext(contextId: String)

  fun execution_getSuggestions(
    code: String,
    offset: Int,
    contextFile: String,
    contextOffset: Int,
    variables: List<RuntimeCompletionVariable>,
    expressions: List<RuntimeCompletionExpression>?,
    consumer: GetRuntimeCompletionConsumer,
  )

  fun execution_mapUri(id: String, file: String?, uri: String?, consumer: MapUriConsumer)

  fun server_setSubscriptions(subscriptions: List<String>)

  fun analysis_updateOptions(options: AnalysisOptions)

  fun server_setClientCapabilities(requests: List<String>, supportsUris: Boolean, supportsWorkspaceApplyEdits: Boolean)

  fun server_shutdown()

  fun generateUniqueId(): String

  fun sendRequestToServer(id: String, request: JsonObject)

  fun sendRequestToServer(id: String, request: JsonObject, consumer: Consumer)

  fun lspMessage_dart_textDocumentContent(uri: String, consumer: DartLspTextDocumentContentConsumer)

  fun lsp_connectToDtd(uri: String)
}
