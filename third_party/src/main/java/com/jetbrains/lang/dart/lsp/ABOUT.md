<!--* freshness: { reviewed: '2026-08-31' } *-->
# lsp

## Overview
Dart LSP server integration connecting IntelliJ Platform LSP infrastructure with Dart Analysis Server in LSP mode.

## Interface
- com.jetbrains.lang.dart.lsp.DartBridgeLspServer
- com.jetbrains.lang.dart.lsp.DartLspServerDescriptor
- com.jetbrains.lang.dart.lsp.DartBridgeLspServerManager
- com.jetbrains.lang.dart.lsp.DartLanguageServer
- com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider
- com.jetbrains.lang.dart.lsp.DartLspDiagnosticConverter
- com.jetbrains.lang.dart.lsp.LspMethod

## Invariants
- Connects IntelliJ LSP support to Dart Analysis Server LSP mode.
- Implements `DartLanguageServer` protocol extensions and diagnostic conversions.

## Side Effects
- Launches `dart language-server` background process and communicates over JSON-RPC.
