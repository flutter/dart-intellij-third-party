<!--* freshness: { reviewed: '2026-10-30' } *-->
# editor

## Overview
Editor typing and selection behaviour for Dart: typed/enter handlers, quotes, indentation, wrapping, word selection, trailing-space stripping, and related settings UI.

## Interface
- `DartTypeHandler` - `TypedHandlerDelegate` for auto-closing and smart typing.
- `DartEnterInStringHandler` / `DartEnterInDocLineCommentHandler` - `EnterHandlerDelegate`s for strings and `///` comments.
- `DartQuoteHandler` - `SimpleTokenSetQuoteHandler` for paired quotes.
- `DartLineIndentProvider` - `JavaLikeLangLineIndentProvider` for smart indent.
- `DartLineWrapPositionStrategy` - wrap positions for hard wrap.
- `DartWordSelectionHandler` / `DartSelectionFilter` - extend-selection behaviour.
- `DartStripTrailingSpacesFilterFactory` - keeps trailing spaces inside multiline strings.
- `DartSmartKeysConfigurable` (Kotlin) - settings UI for smart keys (`codeInsight/DartCodeInsightSettings`).

## Side Effects
- Modifies documents while typing (inserted closers, indentation).

## Verification
- `third_party/src/test/java/com/jetbrains/lang/dart/ide/editor/DartWordSelectionTest.java`.
