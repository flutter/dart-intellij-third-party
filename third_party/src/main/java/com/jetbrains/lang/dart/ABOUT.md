<!--* freshness: { reviewed: '2026-08-04' } *-->
# dart

## Overview
Core Dart language definition (DartLanguage, DartFileType, DartParserDefinition, DartFileListener, DartStartupActivity).

## Interface
- com.jetbrains.lang.dart.DartLanguage
- com.jetbrains.lang.dart.DartFileType
- com.jetbrains.lang.dart.DartParserDefinition
- com.jetbrains.lang.dart.DartFileListener
- com.jetbrains.lang.dart.DartStartupActivity
- ./analytics/ABOUT.md
- ./analyzer/ABOUT.md
- ./assists/ABOUT.md
- ./contextInfo/ABOUT.md
- ./dtd/ABOUT.md
- ./fixes/ABOUT.md
- ./flutter/ABOUT.md
- ./folding/ABOUT.md
- ./highlight/ABOUT.md
- ./hints/ABOUT.md
- ./ide/ABOUT.md
- ./injection/ABOUT.md
- ./lexer/ABOUT.md
- ./logging/ABOUT.md
- ./lsp/ABOUT.md
- ./projectView/ABOUT.md
- ./projectWizard/ABOUT.md
- ./psi/ABOUT.md
- ./pubServer/ABOUT.md
- ./resolve/ABOUT.md
- ./test/ABOUT.md

## Invariants
- Main entry point for Dart language plugin support in IntelliJ.

## Side Effects
- Registers file types, parser definitions, startup activities, and VFS file listeners.
