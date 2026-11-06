<!--* freshness: { reviewed: '2026-10-25' } *-->
# connector

## Overview
Transports connecting `LspServerImpl` to the language server process via lsp4j.

## Interface
- `Lsp4jServerConnector` - Abstract base that builds the lsp4j launcher and runs initialization (throws `LspInitializationException`); patched by `patch.py`.
- `Lsp4jServerConnectorStdio` - Connects over the process's stdin/stdout.
- `Lsp4jServerConnectorSocket` - Connects over a TCP socket (`LspCommunicationChannel.Socket`).
- `Lsp4jServerWrapperCreator` - Hook for wrapping the lsp4j server proxy.
- `LspProcessHandlerCreatingDescriptor` - Descriptor hook for supplying a custom process handler.
- `LspServerProcessListenerBase` - Process listener reacting to server process termination.

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- Starts language server processes and opens stdio/socket streams.

## Verification
- No tests in this directory.
