<!--* freshness: { reviewed: '2026-10-25' } *-->
# lexer

## Overview
JFlex grammar sources and the hand-written lexer wrappers for Dart code and Dart doc comments. The generated scanners `_DartLexer` and `_DartDocLexer` are produced from `Dart.flex` and `DartDoc.flex` and checked in under [../../../../../../../gen/com/jetbrains/lang/dart/lexer/ABOUT.md](../../../../../../../../gen/com/jetbrains/lang/dart/lexer/ABOUT.md).

## Interface
- com.jetbrains.lang.dart.lexer.DartLexer: extends `MergingLexerAdapterBase` and wraps a `FlexAdapter` around `_DartLexer`.
- com.jetbrains.lang.dart.lexer.DartDocLexer: extends `MergingLexerAdapter` and wraps a `FlexAdapter` around `_DartDocLexer`.
- `Dart.flex`, `DartDoc.flex`: JFlex grammar sources. Edit these, then regenerate the `gen/` lexers. Never hand-edit the generated files.

## Invariants
- `DartLexer` merges consecutive `WHITE_SPACE` tokens and consecutive `REGULAR_STRING_PART` tokens. It collapses `MULTI_LINE_(DOC_)COMMENT_START … BODY* … END` into a single `MULTI_LINE_COMMENT` or `MULTI_LINE_DOC_COMMENT` token. Doc-comment content is lazily parsed separately.
- `DartLexer` resets the brace counter and state stack of `_DartLexer` on every `reset`, so incremental relexing starts clean.
- `DartDocLexer` merges consecutive `MULTI_LINE_COMMENT_BODY` tokens.

## Side Effects
- Scans source text into `IElementType` tokens. Pure; no I/O.

## Verification
- No dedicated lexer tests exist. The lexers are exercised by the parser, highlighting, and folding tests (e.g. `DartHighlightingTest`, `DartFoldingTest`).
