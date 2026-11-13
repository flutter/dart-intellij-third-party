<!--* freshness: { reviewed: '2026-10-25' } *-->
# injection

## Overview
Multi-host language injector (`DartMultiHostInjector`) that injects RegExp or HTML into Dart string literals using heuristics. It does not use annotations and does not inject SQL.

## Interface
- com.jetbrains.lang.dart.injection.DartMultiHostInjector: `MultiHostInjector` over `DartStringLiteralExpression`.
  - `STRING_TEMPLATE_PLACEHOLDER`: public constant substituted for `$foo` / `${foo}` interpolations in injected HTML.

## Invariants
- RegExp: injects `JSRegexp` only when the JavaScript plugin provides that language. The string must be the first argument of `RegExp(...)` or `new RegExp(...)` and must be a single raw single-line string (`r'...'`).
- HTML: otherwise, injects `HTMLLanguage` into any string literal whose concatenated text passes `looksLikeHtml`. The test requires `<` as the first non-space character and a real tag or comment. Text such as `List<int>` or `<foo> and <bar>` is rejected unless it contains `</` or `/>`.
- String-template interpolations become `STRING_TEMPLATE_PLACEHOLDER`. Its trailing space is intentional.

## Side Effects
- Registers injected language fragments with the IntelliJ PSI injection framework. No writes.

## Verification
- Covered by `DartInjectionTest` (`src/test/java/com/jetbrains/lang/dart/injection/`).
