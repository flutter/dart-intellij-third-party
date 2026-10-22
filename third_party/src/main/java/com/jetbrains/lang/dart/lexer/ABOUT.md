<!--* freshness: { reviewed: '2026-08-04' } *-->
# lexer

## Overview
Lexer wrappers (DartLexer, DartDocLexer) integrating JFlex lexers with IntelliJ FlexAdapter.

## Interface
- com.jetbrains.lang.dart.lexer.DartLexer
- com.jetbrains.lang.dart.lexer.DartDocLexer

## Invariants
- Extends FlexAdapter wrapping _DartLexer and _DartDocLexer.

## Side Effects
- Scans source text into IElementType tokens.
