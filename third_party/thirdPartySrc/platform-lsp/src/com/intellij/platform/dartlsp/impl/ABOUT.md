<!--* freshness: { reviewed: '2026-08-04' } *-->
# impl

## Overview
Internal implementation classes for managing LSP server processes, document synchronization, and request execution.

## Interface
- com.intellij.platform.dartlsp.impl.LspServerImpl
- com.intellij.platform.dartlsp.impl.LspServerManagerImpl
- com.intellij.platform.dartlsp.impl.LspRequestExecutor
- com.intellij.platform.dartlsp.impl.DefaultLspDocumentAdapter
- com.intellij.platform.dartlsp.impl.LspDynamicCapabilities

## Invariants
- Handles lsp4j RPC invocation, document state sync, dynamic capabilities registration, and error recovery.

## Side Effects
- Launches LSP server subprocesses, sends JSON-RPC requests via stdio/sockets, and updates IDE document buffers.
