<!--* freshness: { reviewed: '2026-10-25' } *-->
# gen

## Overview
Root output directory for auto-generated lexer, parser, PSI, and icon source files.

## Subdirectories & Boundaries
- `com`: [./com/ABOUT.md](./com/ABOUT.md) - GrammarKit parser/PSI and JFlex lexer output under `com.jetbrains.lang.dart`.
- `icons`: [./icons/ABOUT.md](./icons/ABOUT.md) - Generated `DartIcons` constants.

## Generator Inputs
- Parser and PSI: [Dart.bnf](../src/main/java/com/jetbrains/lang/dart/Dart.bnf) (GrammarKit).
- Lexers: [Dart.flex](../src/main/java/com/jetbrains/lang/dart/lexer/Dart.flex) and [DartDoc.flex](../src/main/java/com/jetbrains/lang/dart/lexer/DartDoc.flex) (JFlex).
- Icons: `DartIcons` is regenerated with the "Generate icon classes" IDE run configuration from the SVGs in `src/main/resources/icons/`.

## Invariants
- Contains generated sources only; edit the generator inputs and regenerate instead of editing these files.
