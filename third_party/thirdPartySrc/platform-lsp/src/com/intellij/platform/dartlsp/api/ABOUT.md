<!--* freshness: { reviewed: '2026-10-25' } *-->
# api

## Overview
Public API interfaces and extension points for IntelliJ LSP (Language Server Protocol) integration.

## Interface
- com.intellij.platform.dartlsp.api.LspServer - IntelliJ model of a started LSP server.
- com.intellij.platform.dartlsp.api.LspServerManager - Project service for starting/stopping/querying servers.
- com.intellij.platform.dartlsp.api.LspServerManagerListener - Listener for server manager events.
- com.intellij.platform.dartlsp.api.LspServerDescriptor - Abstract per-server configuration (command line, file filters, customization).
- com.intellij.platform.dartlsp.api.ProjectWideLspServerDescriptor - Descriptor base for one server per project.
- com.intellij.platform.dartlsp.api.LspServerSupportProvider - Extension point that decides when to start servers for a file.
- com.intellij.platform.dartlsp.api.LspServerState - Lifecycle enum (`Initializing`, `Running`, `ShutdownNormally`, `ShutdownUnexpectedly`).
- com.intellij.platform.dartlsp.api.LspCommunicationChannel - Sealed transport choice: `StdIO` or `Socket(port, startProcess)`.
- com.intellij.platform.dartlsp.api.Lsp4jClient - lsp4j `LanguageClient` implementation; the same file declares the `LspServerNotificationsHandler` interface it delegates to.
- com.intellij.platform.dartlsp.api.Lsp4jServer - Alias for lsp4j's `LanguageServer` interface.
- com.intellij.platform.dartlsp.api.LspServerListener
- com.intellij.platform.dartlsp.api.LspBundle - Message bundle accessor.
- [./customization/ABOUT.md](./customization/ABOUT.md) - Per-feature customizer classes.
- [./lsWidget/ABOUT.md](./lsWidget/ABOUT.md) - Language-services status bar widget API.

## Invariants
- Defines public contracts for LSP server lifecycle management, language support registration, and lsp4j communication.

## Side Effects
- None directly; `api/` declares interfaces and contracts. Server process lifetimes and request handling are implemented in `../impl/` (`LspServerImpl`, `LspServerManagerImpl`, `impl/connector/`).
