<!--* freshness: { reviewed: '2026-10-25' } *-->
# impl

## Overview
Internal implementation classes for managing LSP server processes, document synchronization, request execution, and IDE feature integration.

## Interface
- `LspServerImpl` - Concrete `LspServer`: lifecycle, state, and lsp4j connection.
- `LspServerManagerImpl` - Concrete `LspServerManager` project service.
- `LspServerNotificationsHandlerImpl` - Handles server-to-client notifications/requests (patched by `patch.py`).
- `LspRequestExecutor` / `LspRequestExecutorBase` - Typed feature requests (hover, document/workspace symbols, signature help, ...) with response caches registered from `./cache/`.
- `LspDocument`, `LspDocumentPosition`, `LspDocumentRange` - Abstraction over an LSP document (a regular file or a notebook cell) and positions within it.
- `LspDocumentAdapter` / `DefaultLspDocumentAdapter` - Extension point (and default 1:1 implementation) mapping IDE files/offsets to LSP documents.
- `LspDocumentMapping` - Picks the adapter per file/URL and converts IntelliJ file/offset coordinates to LSP document coordinates.
- `LspResponseMapping` - Maps document-relative results (locations, symbols, edits, folding/selection ranges) back to host-file coordinates.
- `LspDynamicCapabilities` - Tracks dynamically registered server capabilities.
- `LspGlobMatcher` - Glob matching for LSP file patterns.
- `LspCoroutineScopeService` - Holds the coroutine scope for LSP work.
- [./cache/ABOUT.md](./cache/ABOUT.md) - Per-file and single-slot response caches.
- [./connector/ABOUT.md](./connector/ABOUT.md) - Stdio/socket transports to the server process.
- [./documentSync/ABOUT.md](./documentSync/ABOUT.md) - `didOpen`/`didChange`/`didClose` synchronization.
- [./features/ABOUT.md](./features/ABOUT.md) - Editor feature implementations (completion, highlighting, navigation, ...).
- [./fileEvents/ABOUT.md](./fileEvents/ABOUT.md) - Watched-files notifications.
- [./logging/ABOUT.md](./logging/ABOUT.md) - Language service traffic logging.
- [./lsWidget/ABOUT.md](./lsWidget/ABOUT.md) - Language Services widget implementation.
- [./platformListeners/ABOUT.md](./platformListeners/ABOUT.md) - IDE document/file/editor listeners.
- [./structureView/ABOUT.md](./structureView/ABOUT.md) - Structure view from `documentSymbol`.
- [./util/ABOUT.md](./util/ABOUT.md) - `Lsp4jServiceImpl`, converters, workspace edit applier.

## Invariants
- Handles lsp4j RPC invocation, document state sync, dynamic capabilities registration, and error recovery.
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- Launches LSP server subprocesses, sends JSON-RPC requests via stdio/sockets, and updates IDE document buffers.
