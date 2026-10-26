<!--* freshness: { reviewed: '2026-10-25' } *-->
# impl

## Overview
Hand-written PSI implementation bases that the generated PSI implementations in `gen/com/jetbrains/lang/dart/psi/impl` extend (through the grammar's `extends`/`mixin` attributes), plus the server-backed URI file reference. Parent: [../ABOUT.md](../ABOUT.md).

## Interface
- Root and named-element bases:
  - `DartPsiCompositeElementImpl`: concrete `ASTWrapperPsiElement`, the root base for most Dart PSI impls. Also holds the shared `processDeclarationsImpl` used by `DartFile`.
  - `DartNamedElementImpl`: abstract.
  - `AbstractDartComponentImpl`: abstract base for components (icon, presentation, modifiers).
  - `AbstractDartMethodDeclarationImpl`: abstract.
  - `AbstractDartPsiClass`: abstract base for class-like declarations.
- Reference expressions (all concrete):
  - `DartReferenceImpl`: concrete `public class` (`extends DartExpressionImpl implements DartReference, PsiPolyVariantReference`), not abstract.
  - `DartClassReferenceImpl`, `DartOperatorExpressionImpl`: references that resolve to classes or operators.
  - `DartLibraryIdBase`, `DartLibraryComponentReferenceExpressionBase`: library-name and library-component references.
- Other bases:
  - `DartStringLiteralExpressionBase`: abstract; extends `DartClassReferenceImpl`.
  - `DartFunctionExpressionBase`: abstract; function-expression icon and presentation.
  - `DartLibraryNameElementBase`: abstract; the library-name declaration element.
  - `DartUriElementBase`: abstract; URI elements in directives. Supplies a `DartFileReference`.
- Code-fragment and doc-comment impls:
  - `DartExpressionCodeFragmentImpl`: extends `DartFile`; expression code fragments.
  - `DartLazyParseableBlockImpl`: lazily parsed block.
  - `DartDocCommentImpl`: doc-comment element.
- `DartFileReference`: `PsiPolyVariantReference` for directive URIs. It is resolved through `ResolveCache` from Analysis Server navigation regions.

## Invariants
- Reference resolution goes through `ResolveCache.resolveWithCaching` with `resolve.DartResolver.INSTANCE`. `DartFileReference` uses its own poly-variant resolver over `DartAnalysisServerService` navigation data.
- Icons and presentation come from `DartComponentType` and `ItemPresentation` implementations in these bases.

## Side Effects
- PSI reference resolution and class/type resolution through cached resolvers. No writes.

## Verification
- Exercised by the PSI, resolve, rename, and navigation tests in the plugin's test sources.
