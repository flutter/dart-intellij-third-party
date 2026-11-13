<!--* freshness: { reviewed: '2026-10-25' } *-->
# psi

## Overview
The hand-written part of the Dart PSI layer: base interfaces, `DartFile`, the reference contributors, and a few PSI services. Most Dart PSI interfaces (`DartClassDefinition`, `DartExpression`, `DartVisitor`, …) are generated from the grammar and live in [../../../../../../../gen/com/jetbrains/lang/dart/psi/ABOUT.md](../../../../../../../../gen/com/jetbrains/lang/dart/psi/ABOUT.md). Hand-written implementation bases are in `impl/`.

## Subdirectories & Boundaries
- [./impl/ABOUT.md](./impl/ABOUT.md): hand-written PSI implementation base classes and references.

## Interface
- com.jetbrains.lang.dart.psi.DartFile: concrete class (`extends PsiFileBase implements DartExecutionScope`), not an interface. Its use scope is `allScope`, and it delegates declaration processing to `DartPsiCompositeElementImpl`.
- Base interfaces:
  - `DartPsiCompositeElement`: root interface for Dart PSI.
  - `DartNamedElement`, `DartComponent`, and `DartClass`: named declarations, components, and class-like components.
  - `DartReference`: a `DartExpression` that is a `PsiReference`.
  - `DartExecutionScope`, `IDartBlock`, and `DartLazyParseableBlock`: scopes and blocks. Function bodies are parsed lazily.
  - `DartUriBasedDirective` and `DartImportOrExportStatement`: directives that carry a URI.
  - `DartDocComment` and `DartExpressionCodeFragment`: doc-comment PSI and expression code fragments (debugger, evaluate).
- com.jetbrains.lang.dart.psi.DartRecursiveVisitor: recursive variant of the generated `DartVisitor`.
- com.jetbrains.lang.dart.psi.DartReferenceContributor: package-private. Registered for Dart and XML. It adds references for string literals, doc comments, and HTML attributes/text, built from Analysis Server navigation regions. It is disabled when LSP navigation is enabled.
- com.jetbrains.lang.dart.psi.DartContributedReference: package-private `PsiPolyVariantReference` backed by a `DartNavigationRegion`.
- com.jetbrains.lang.dart.psi.PubspecYamlReferenceContributor: registered for YAML in `dart-yaml.xml`. It adds directory references for `path:` values of dependencies, dev-dependencies, and dependency overrides in `pubspec.yaml`. `isPathPackageDefinition` is public and is also used by `ide/inspections/DartPathPackageReferenceInspection`.
- com.jetbrains.lang.dart.psi.DartPackageAwareFileReference: package-private `FileReference` that resolves `packages` against the pubspec folder or package config. No code constructs it today (see notes).
- com.jetbrains.lang.dart.psi.DartClassResolveCache: project service holding a weak `DartClass` → `DartClassResolveResult` cache, cleared on every PSI change.
- com.jetbrains.lang.dart.psi.DartElementDescriptionProvider: describes elements (e.g. `class Foo`, `library bar`) for highlight-usages messages.

## Invariants
- Hand-written interfaces here must stay compatible with the generated PSI in `gen/` (the grammar's `implements`/`mixin` attributes refer to them).
- Server-backed references are produced only for local, analyzable files, and never for text containing `DartMultiHostInjector.STRING_TEMPLATE_PLACEHOLDER`.

## Side Effects
- Resolves file, package, and server-navigation references. `DartClassResolveCache` subscribes to `ANY_PSI_CHANGE_TOPIC`.

## Verification
- Exercised by the PSI, resolve, and navigation tests in the plugin's test sources.
