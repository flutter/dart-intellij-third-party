package com.jetbrains.lang.dart.lsp

import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.DefaultLauncherBuilder
import org.eclipse.lsp4j.jsonrpc.MessageIssueHandler
import org.eclipse.lsp4j.jsonrpc.MessageProducer
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.services.LanguageServer
import java.io.InputStream

class DartLauncherBuilder<S : LanguageServer>(clientFeatures: LSPClientFeatures) : DefaultLauncherBuilder<S>(
    clientFeatures
) {
    override fun createMessageProducer(
        input: InputStream,
        jsonHandler: MessageJsonHandler,
        issueHandler: MessageIssueHandler
    ): MessageProducer {
        val producer = DartMessageProducer(jsonHandler)
        DartMessageProducer.registerProducer(clientFeatures.project, producer)
        return producer
    }
}
