<!--* freshness: { reviewed: '2026-11-06' } *-->

# info

## Overview

Parameter info popups (Ctrl+P) for Dart calls.

## Interface

- `DartParameterInfoHandler`: registered as `codeInsight.parameterInfo` for
  Dart.
- `DartFunctionDescription`: the signature model of a function and its
  parameters.
- `DartParameterDescription` / `DartOptionalParameterDescription`: the
  rendering model for each parameter.

## Invariants

- Read-only. The handler never modifies documents.
- `DartParameterInfoHandler.findElementForParameterInfo(CreateParameterInfoContext)`
  returns `null` when experimental LSP features are enabled so LSP
  `textDocument/signatureHelp` serves parameter info instead.

## Side Effects

- None.

## Verification

- `com.jetbrains.dart.analysisServer.DartParameterInfoTest`
