<!--* freshness: { reviewed: '2026-08-04' } *-->
# api

## Overview
Public API interfaces and extension points for IntelliJ LSP (Language Server Protocol) integration.

## Interface
- com.intellij.platform.dartlsp.api.LspServer
- com.intellij.platform.dartlsp.api.LspServerManager
- com.intellij.platform.dartlsp.api.LspServerDescriptor
- com.intellij.platform.dartlsp.api.LspServerSupportProvider
- com.intellij.platform.dartlsp.api.Lsp4jClient
- com.intellij.platform.dartlsp.api.LspServerListener

## Invariants
- Defines public contracts for LSP server lifecycle management, language support registration, and lsp4j communication.

## Side Effects
- Manages LSP server process lifetimes and LSP request/notification handling.
