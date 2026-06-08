package com.intellij.platform.lsp.dart.impl.connector

import com.intellij.platform.lsp.dart.api.Lsp4jServer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface Lsp4jServerWrapperCreator {
  fun wrapLsp4jServer(lsp4jServer: Lsp4jServer): Lsp4jServer
}