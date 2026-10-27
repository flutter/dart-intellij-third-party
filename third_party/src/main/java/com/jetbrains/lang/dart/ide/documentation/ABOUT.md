<!--* freshness: { reviewed: '2026-10-25' } *-->
# documentation

## Overview
Dart documentation provider for completion popups and external API doc URLs.

## Interface
- `DartDocumentationProvider` - `DocumentationProvider`; generates docs only for `DartLookupObject` completion items and builds `https://api.dart.dev/stable/...` URLs for public SDK components.
- `DartDocUtil` - renders HTML documentation (signature + doc comment) from PSI.

## Invariants
- `generateDoc` returns null outside of an active Dart completion lookup; hover/quick docs come from elsewhere (Analysis Server / LSP).
- `getQuickNavigateInfo` always returns null.

## Side Effects
- None; read-only PSI rendering.

## Verification
- No dedicated tests; verify by opening Quick Documentation on a completion item and External Documentation on an SDK class.
