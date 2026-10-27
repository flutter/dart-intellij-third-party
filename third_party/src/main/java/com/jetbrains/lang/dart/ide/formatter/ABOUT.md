<!--* freshness: { reviewed: '2026-10-25' } *-->
# formatter

## Overview
IntelliJ formatting model for Dart (blocks, indent, spacing, wrapping), a post-format hook that delegates to the Dart Style formatter, and Dart code style settings UI in `settings/`.

## Interface
- `DartFormattingModelBuilder` - `FormattingModelBuilder` creating the `DartBlock` tree.
- `DartBlock` / `DartBlockContext` (package-private) - formatting blocks and shared context.
- `DartIndentProcessor`, `DartSpacingProcessor`, `DartWrappingProcessor` - indent, spacing, and wrap rules per PSI node.
- `DartPostFormatProcessor` (package-private) - `PostFormatProcessor` that calls `DartStyleAction.reformatRangeAsPostFormatProcessor` for local, in-content Dart files in SDK-enabled modules when the Analysis Server is ready.
- `settings/DartLanguageCodeStyleSettingsProvider` - `LanguageCodeStyleSettingsProvider` for Dart.
- `settings/DartCodeStylePanel` (`.java`/`.form`) - `CodeStyleAbstractPanel` for Dart code style options.

## Invariants
- When the post-format processor applies, the final formatting comes from the Analysis Server (Dart Style), not the IntelliJ model.

## Side Effects
- Reformats documents on Reformat Code; post-format step sends `edit_format` requests via `DartStyleAction`.

## Verification
- No dedicated tests in this repo; verify via Code > Reformat Code and Settings > Code Style > Dart.
