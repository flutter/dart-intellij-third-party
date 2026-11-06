<!--* freshness: { reviewed: '2026-10-25' } *-->
# hints

## Overview
Declarative inlay hint providers for Dart. One provider renders closing labels at the end of code blocks. The other two are settings-only anchors for the "Parameter names" and "Types" checkboxes. Those hints are computed by the Analysis Server and rendered by `lsp/DartLspInlayHintSupport`.

## Interface
- com.jetbrains.lang.dart.hints.DartClosingLabelsInlayHintsProvider: provider ID `dart.closing.labels`.
- com.jetbrains.lang.dart.hints.DartParameterNamesInlayHintsProvider: provider ID `dart.parameter.names`, option `dart.parameter.names.only.literal`. Its `createCollector` returns `null`.
- com.jetbrains.lang.dart.hints.DartTypesInlayHintsProvider: provider ID `dart.types`, with options `dart.types.variable`, `.return`, `.parameter`, `.type.arguments`, and `.dot.shorthand`. Its `createCollector` returns `null`.

## Invariants
- All three implement the IntelliJ declarative `InlayHintsProvider` and are registered in `third_party/src/main/resources/META-INF/plugin.xml`.
- Closing labels: when `DartAnalysisServerService.isLspClosingLabelsEnabled(project)` is true, labels come from `lsp.DartLspClosingLabelsService.getClosingLabels()` (LSP ranges, placed on the range's end line). Otherwise they come from the legacy `DartAnalysisServerService.getClosingLabels()` (offset/length).
- The enabled state of the parameter-name and type providers is read by `com.jetbrains.lang.dart.lsp.DartLspInlayHintSupport` to decide which hints to request from the server and display.

## Side Effects
- Renders closing-label text at end-of-line positions in the editor. The settings-only providers have no runtime effect beyond their stored settings.

## Verification
- No dedicated tests in this package. Check manually through Settings | Editor | Inlay Hints | Dart.
