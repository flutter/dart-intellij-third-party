// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import com.google.dart.server.AnalysisServerListener
import com.google.dart.server.AnalysisServerStatusListener
import com.google.dart.server.Consumer
import com.google.dart.server.CreateContextConsumer
import com.google.dart.server.DartLspTextDocumentContentConsumer
import com.google.dart.server.ExtendedRequestErrorCode
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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.analyzer.DartClient
import com.jetbrains.lang.dart.sdk.DartSdk
import org.dartlang.analysis.server.protocol.AddContentOverlay
import org.dartlang.analysis.server.protocol.AnalysisOptions
import org.dartlang.analysis.server.protocol.ImportedElements
import org.dartlang.analysis.server.protocol.RemoveContentOverlay
import org.dartlang.analysis.server.protocol.RefactoringOptions
import org.dartlang.analysis.server.protocol.RequestError
import org.dartlang.analysis.server.protocol.RuntimeCompletionExpression
import org.dartlang.analysis.server.protocol.RuntimeCompletionVariable
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import java.util.UUID
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal class LspDartAnalysisClient(
  project: Project,
  sdk: DartSdk,
  suppressAnalytics: Boolean,
) : DartClient {
  private companion object {
    private val LOG = Logger.getInstance(LspDartAnalysisClient::class.java)
    private const val DIAGNOSTIC_SERVER_TIMEOUT_MS = 30_000L
    private const val DART_LANGUAGE_ID = "dart"
  }

  private val lifecycle = LspClientConnectionManager(project, sdk, suppressAnalytics)
  private val documentLock = Any()
  private val openDocuments = mutableMapOf<String, OpenDocumentState>()

  private data class OpenDocumentState(
    val version: Int,
    val content: String,
  )

  override fun start() {
    lifecycle.start()
  }

  override fun isSocketOpen(): Boolean = lifecycle.isSocketOpen()

  override fun addAnalysisServerListener(listener: AnalysisServerListener) {}

  override fun removeAnalysisServerListener(listener: AnalysisServerListener) {}

  override fun addRequestListener(listener: RequestListener) {}

  override fun removeRequestListener(listener: RequestListener) {}

  override fun addResponseListener(listener: ResponseListener) {}

  override fun removeResponseListener(listener: ResponseListener) {}

  override fun addStatusListener(listener: AnalysisServerStatusListener) {
    lifecycle.addStatusListener(listener)
  }

  override fun completion_setSubscriptions(subscriptions: List<String>) {}

  override fun analysis_updateContent(files: Map<String, Any>, consumer: UpdateContentConsumer) {
    try {
      synchronized(documentLock) {
        files.forEach { (uri, overlay) ->
          applyOverlay(uri, overlay)
        }
      }
    }
    catch (t: Throwable) {
      LOG.warn("Failed to synchronize overlaid Dart document content over LSP", t)
    }
    finally {
      // DartAnalysisServerService currently uses this callback for overlay cleanup and cache refresh.
      // The real fix for partial-send failures is to defer its state commits until after a confirmed send.
      consumer.onResponse()
    }
  }

  override fun analysis_setAnalysisRoots(included: List<String>, excluded: List<String>, packageRoots: Map<String, String>?) {}

  override fun analysis_getHover(file: String, offset: Int, consumer: GetHoverConsumer) {}

  override fun analysis_getNavigation(file: String, offset: Int, length: Int, consumer: GetNavigationConsumer) {}

  override fun edit_getAssists(file: String, offset: Int, length: Int, consumer: GetAssistsConsumer) {}

  override fun edit_isPostfixCompletionApplicable(
    file: String,
    key: String,
    offset: Int,
    consumer: IsPostfixCompletionApplicableConsumer,
  ) {}

  override fun edit_listPostfixCompletionTemplates(consumer: ListPostfixCompletionTemplatesConsumer) {}

  override fun edit_getPostfixCompletion(file: String, key: String, offset: Int, consumer: GetPostfixCompletionConsumer) {}

  override fun edit_getStatementCompletion(file: String, offset: Int, consumer: GetStatementCompletionConsumer) {}

  override fun diagnostic_getServerPort(consumer: GetServerPortConsumer) {
    val future = try {
      lifecycle.requestDiagnosticServer()
    }
    catch (t: Throwable) {
      consumer.onError(toRequestError(t))
      return
    }

    future.orTimeout(DIAGNOSTIC_SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS).whenComplete { result, throwable ->
      if (throwable != null) {
        consumer.onError(toRequestError(throwable))
        return@whenComplete
      }

      val port = result?.port
      if (port == null || port <= 0) {
        consumer.onError(
          RequestError(
            ExtendedRequestErrorCode.INVALID_SERVER_RESPONSE,
            DartBundle.message("analysis.server.show.diagnostics.error"),
            null,
          ),
        )
        return@whenComplete
      }

      consumer.computedServerPort(port)
    }
  }

  private fun toRequestError(throwable: Throwable): RequestError {
    val rootCause = unwrapCause(throwable)
    val message = when {
      rootCause is IllegalStateException -> DartBundle.message("analysis.server.not.running")
      rootCause.message.isNullOrBlank() -> DartBundle.message("analysis.server.show.diagnostics.error")
      else -> rootCause.message!!
    }
    return RequestError(ExtendedRequestErrorCode.INVALID_SERVER_RESPONSE, message, null)
  }

  private fun unwrapCause(throwable: Throwable): Throwable {
    var current = throwable
    while ((current is CompletionException || current is ExecutionException) && current.cause != null) {
      current = current.cause!!
    }
    return current
  }

  override fun edit_getFixes(file: String, offset: Int, consumer: GetFixesConsumer) {}

  override fun search_findElementReferences(
    file: String,
    offset: Int,
    includePotential: Boolean,
    consumer: FindElementReferencesConsumer,
  ) {}

  override fun search_getTypeHierarchy(file: String, offset: Int, superOnly: Boolean, consumer: GetTypeHierarchyConsumer) {}

  override fun completion_getSuggestionDetails(
    file: String,
    id: Int,
    label: String,
    offset: Int,
    consumer: GetSuggestionDetailsConsumer,
  ) {}

  override fun completion_getSuggestionDetails2(
    file: String,
    offset: Int,
    completion: String,
    libraryUri: String,
    consumer: GetSuggestionDetailsConsumer2,
  ) {}

  override fun completion_getSuggestions(file: String, offset: Int, consumer: GetSuggestionsConsumer) {}

  override fun completion_getSuggestions2(
    file: String,
    offset: Int,
    maxResults: Int,
    completionCaseMatchingMode: String,
    completionMode: String,
    invocationCount: Int,
    timeout: Int,
    consumer: GetSuggestionsConsumer2,
  ) {}

  override fun edit_format(file: String, selectionOffset: Int, selectionLength: Int, lineLength: Int, consumer: FormatConsumer) {}

  override fun analysis_getImportedElements(file: String, offset: Int, length: Int, consumer: GetImportedElementsConsumer) {}

  override fun edit_importElements(file: String, elements: List<ImportedElements>, offset: Int, consumer: ImportElementsConsumer) {}

  override fun edit_getRefactoring(
    kind: String,
    file: String,
    offset: Int,
    length: Int,
    validateOnly: Boolean,
    options: RefactoringOptions?,
    consumer: GetRefactoringConsumer,
  ) {}

  override fun edit_organizeDirectives(file: String, consumer: OrganizeDirectivesConsumer) {}

  override fun edit_sortMembers(file: String, consumer: SortMembersConsumer) {}

  override fun analysis_reanalyze() {}

  override fun analysis_setPriorityFiles(files: List<String>) {}

  override fun analysis_setSubscriptions(subscriptions: Map<String, List<String>>) {}

  override fun execution_createContext(contextRoot: String, consumer: CreateContextConsumer) {}

  override fun execution_deleteContext(contextId: String) {}

  override fun execution_getSuggestions(
    code: String,
    offset: Int,
    contextFile: String,
    contextOffset: Int,
    variables: List<RuntimeCompletionVariable>,
    expressions: List<RuntimeCompletionExpression>?,
    consumer: GetRuntimeCompletionConsumer,
  ) {}

  override fun execution_mapUri(id: String, file: String?, uri: String?, consumer: MapUriConsumer) {}

  override fun server_setSubscriptions(subscriptions: List<String>) {}

  override fun analysis_updateOptions(options: AnalysisOptions) {}

  override fun server_setClientCapabilities(requests: List<String>, supportsUris: Boolean, supportsWorkspaceApplyEdits: Boolean) {}

  override fun server_shutdown() {
    synchronized(documentLock) {
      openDocuments.clear()
    }
    lifecycle.shutdown()
  }

  override fun generateUniqueId(): String = UUID.randomUUID().toString()

  override fun sendRequestToServer(id: String, request: JsonObject) {}

  override fun sendRequestToServer(id: String, request: JsonObject, consumer: Consumer) {}

  override fun lspMessage_dart_textDocumentContent(uri: String, consumer: DartLspTextDocumentContentConsumer) {}

  override fun lsp_connectToDtd(uri: String) {}

  private fun applyOverlay(uri: String, overlay: Any) {
    when (overlay) {
      is AddContentOverlay -> applyContent(uri, overlay.content)
      is RemoveContentOverlay -> removeContent(uri)
      else -> throw IllegalArgumentException("Unsupported content overlay type ${overlay::class.java.name} for $uri")
    }
  }

  private fun applyContent(uri: String, newContent: String) {
    val currentState = openDocuments[uri]
    if (currentState == null) {
      lifecycle.didOpen(
        DidOpenTextDocumentParams(
          TextDocumentItem(uri, DART_LANGUAGE_ID, 1, newContent),
        ),
      )
      openDocuments[uri] = OpenDocumentState(1, newContent)
      return
    }

    if (currentState.content == newContent) return

    val nextVersion = currentState.version + 1
    lifecycle.didChange(
      DidChangeTextDocumentParams(
        VersionedTextDocumentIdentifier(uri, nextVersion),
        listOf(createChangeEvent(currentState.content, newContent)),
      ),
    )
    openDocuments[uri] = OpenDocumentState(nextVersion, newContent)
  }

  private fun removeContent(uri: String) {
    if (openDocuments.containsKey(uri)) {
      lifecycle.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri)))
      openDocuments.remove(uri)
    }
  }

  private fun createChangeEvent(previousContent: String, newContent: String): TextDocumentContentChangeEvent {
    return if (lifecycle.textDocumentSyncKind() == TextDocumentSyncKind.Incremental) {
      //Behavior is just like the Full for now
      //The only problem is performance
      TextDocumentContentChangeEvent().apply {
        range = fullDocumentRange(previousContent)
        text = newContent
      }
    }
    else {
      TextDocumentContentChangeEvent(newContent)
    }
  }

  private fun fullDocumentRange(content: String): Range {
    return Range(Position(0, 0), endPosition(content))
  }

  private fun endPosition(content: String): Position {
    var line = 0
    var character = 0
    var index = 0
    while (index < content.length) {
      val ch = content[index]
      if (ch == '\n') {
        line++
        character = 0
      }
      else if (ch == '\r') {
        line++
        character = 0
        if (index + 1 < content.length && content[index + 1] == '\n') {
          index++
        }
      }
      else {
        character++
      }
      index++
    }
    return Position(line, character)
  }
}
