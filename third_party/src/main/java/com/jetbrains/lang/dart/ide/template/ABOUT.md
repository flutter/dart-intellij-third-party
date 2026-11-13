<!--* freshness: { reviewed: '2026-10-25' } *-->

# template

## Overview

Live templates and postfix templates for Dart.

## Interface

- `DartTemplateContextType`: the `DART` (`Generic`) and `DART_STATEMENT`
  (`Statement`) live-template contexts.
- `macro/`: live-template macros built on `DartMacroBase`:
  `DartClassNameMacro`, `DartMethodNameMacro`,
  `DartClassNameMethodNameMacro`, `DartMethodParametersMacro`,
  `DartMethodReturnTypeMacro`, `DartListVariableMacro`,
  `DartIterableVariableMacro`, `DartSuggestIndexNameMacro`,
  `DartSuggestVariableNameMacro`, and `DartFilterByClassMacro`.
- `postfix/DartPostfixTemplateProvider`: postfix templates whose
  definitions come from the analysis server.
- `postfix/DartRemotePostfixTemplate`: applies one server-defined postfix
  template through `DartAnalysisServerService`.

## Invariants

- No postfix templates are available until the analysis server is running.
- Macros and contexts are registered in `META-INF/plugin.xml`.

## Side Effects

- Postfix expansion sends requests to the analysis server.

## Verification

- `com.jetbrains.lang.dart.ide.DartLiveTemplatesTest`,
  `com.jetbrains.lang.dart.ide.DartTemplateMacrosTest`
