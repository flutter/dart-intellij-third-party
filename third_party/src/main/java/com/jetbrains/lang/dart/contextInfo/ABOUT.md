<!--* freshness: { reviewed: '2026-10-25' } *-->
# contextInfo

## Overview
Declaration-range handler used by the platform's context-info popup ("Show Context"). The package holds a single class, `DartDeclarationRangeHandler`. There is no parameter-info handler here.

## Interface
- com.jetbrains.lang.dart.contextInfo.DartDeclarationRangeHandler: `DeclarationRangeHandler<DartPsiCompositeElement>`.

## Invariants
- For `DartCallExpression`, the range ends at the callee expression. For `DartNewExpression`, it ends at the reference expression, type, or shorthand prefix (first non-null). For `DartComponent`, it ends at the component name.
- Returns `null` for other elements. The platform extends the returned range to full lines.

## Side Effects
- Read-only PSI range queries.

## Verification
- Exercised indirectly through the platform context-info action; no dedicated test lives in this package.
