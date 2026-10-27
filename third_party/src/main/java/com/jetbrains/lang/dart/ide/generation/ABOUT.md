<!--* freshness: { reviewed: '2026-10-25' } *-->
# generation

## Overview
Code > Generate support for Dart classes: constructors, named constructors, getters/setters, `==`/`hashCode`, `toString`, and override/implement members. Built on PSI with member-chooser dialogs.

## Interface
- `BaseDartGenerateAction` - abstract `AnAction` base; subclasses `DartGenerateConstructorAction`, `DartGenerateNamedConstructorAction`, `DartGenerateGetterAction`, `DartGenerateSetterAction`, `DartGenerateGetterSetterAction`, `DartGenerateEqualsAndHashcodeAction`, `DartGenerateToStringAction`.
- `BaseDartGenerateHandler` - abstract `LanguageCodeInsightActionHandler` (member chooser + fix); subclasses `DartGenerateConstructorHandler`, `DartGenerateNamedConstructorHandler`, `DartGenerateAccessorHandler` (abstract), `DartGenerateEqualsAndHashcodeHandler`, `DartGenerateToStringHandler`, `DartOverrideMethodHandler`, `DartImplementMethodHandler`.
- `BaseCreateMethodsFix<T>` - abstract template-based code inserter; subclasses `CreateConstructorFix`, `CreateNamedConstructorFix`, `CreateGetterSetterFix`, `CreateEqualsAndHashcodeFix`, `CreateToStringFix`, `OverrideImplementMethodFix`.

## Invariants
- Candidates come from PSI; `CreateEqualsAndHashcodeFix` additionally queries `search_getTypeHierarchy` to detect a superclass `==`.

## Side Effects
- Inserts generated members into the class body via live templates (write action).

## Verification
- No dedicated tests in this repo; verify via Code > Generate in a Dart class.
