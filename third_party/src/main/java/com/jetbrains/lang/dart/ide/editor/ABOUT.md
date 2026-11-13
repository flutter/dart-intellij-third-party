<!--* freshness: { reviewed: '2026-10-25' } *-->
# editor

## Overview
Editor typing and selection behaviour for Dart: typed/enter handlers, quotes, indentation, wrapping, word selection, trailing-space stripping, import-aware copy/paste, and related settings UI.

## Interface
- `DartTypeHandler` - `TypedHandlerDelegate` for auto-closing and smart typing.
- `DartEnterInStringHandler` / `DartEnterInDocLineCommentHandler` - `EnterHandlerDelegate`s for strings and `///` comments.
- `DartQuoteHandler` - `SimpleTokenSetQuoteHandler` for paired quotes.
- `DartLineIndentProvider` - `JavaLikeLangLineIndentProvider` for smart indent.
- `DartLineWrapPositionStrategy` - wrap positions for hard wrap.
- `DartWordSelectionHandler` / `DartSelectionFilter` - extend-selection behaviour.
- `DartStripTrailingSpacesFilterFactory` - keeps trailing spaces inside multiline strings.
- `DartCopyPasteProcessor` - `CopyPastePostProcessor` that uses `analysis_getImportedElements` / `edit_importElements` to add imports on paste.
- `DartSmartKeysConfigurable` (Kotlin) / `DartAutoImportOptionsProvider` (Kotlin) - settings UI for smart keys and add-imports-on-paste (`codeInsight/DartCodeInsightSettings`).

## Invariants
- Copy/paste import transfer requires the Analysis Server and honours `ADD_IMPORTS_ON_PASTE`.

## Side Effects
- Modifies documents while typing and pasting (inserted closers, indentation, added imports).
- The add-imports-on-paste dialog can persist the user's choice to `ADD_IMPORTS_ON_PASTE`.

## Verification
- `third_party/src/test/java/com/jetbrains/lang/dart/ide/editor/DartWordSelectionTest.java`.
