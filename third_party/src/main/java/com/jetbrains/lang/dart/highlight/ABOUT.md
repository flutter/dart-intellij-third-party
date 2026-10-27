<!--* freshness: { reviewed: '2026-10-25' } *-->
# highlight

## Overview
Lexer-based syntax highlighting for Dart: the token-to-color map, the text attribute keys, the Colors & Fonts settings page, and a filter that suppresses PSI parser-error highlighting where Analysis Server diagnostics take over.

## Interface
- com.jetbrains.lang.dart.highlight.DartSyntaxHighlighter: `SyntaxHighlighterBase` built on `lexer.DartLexer`. It also implements `EmbeddedTokenHighlighter`, so Dart token colors apply inside other languages' files that embed Dart.
- com.jetbrains.lang.dart.highlight.DartSyntaxHighlighterColors: defines every Dart `TextAttributesKey` constant that the highlighter, the annotators, and the colors page use.
- com.jetbrains.lang.dart.highlight.DartColorsAndFontsPage: the Settings | Editor | Color Scheme | Dart page, with demo text and attribute descriptors.
- com.jetbrains.lang.dart.highlight.DartHighlightingErrorFilter: `HighlightErrorFilter` for Dart PSI error elements.

## Invariants
- Maps Dart token types (keywords, operators, strings, numbers, brackets, comments, etc.) to `DartSyntaxHighlighterColors` keys. Context-dependent cases such as the `:` operator are left to annotators.
- `DartHighlightingErrorFilter` hides PSI parser errors for files in the local file system that are in project content, because the Analysis Server reports errors for those files. Parser errors still show for non-local and out-of-content files.

## Side Effects
- Supplies highlighting attributes to the editor. No writes.

## Verification
- Covered by `DartHighlightingTest` (`src/test/java/com/jetbrains/lang/dart/highlighting/`).
