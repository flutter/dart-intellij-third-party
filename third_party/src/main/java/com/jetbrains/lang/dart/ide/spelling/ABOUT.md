<!--* freshness: { reviewed: '2026-10-25' } *-->

# spelling

## Overview

Spell-checking integration for Dart.

## Interface

- `DartSpellcheckingStrategy`: chooses which Dart tokens get spell-checked.
- `DartBundledDictionaryProvider`: adds the bundled Dart dictionary.

## Invariants

- Registered as `spellchecker.support` and
  `spellchecker.bundledDictionaryProvider` in `META-INF/plugin.xml`.

## Side Effects

- None.

## Verification

- Highlighting test data `testData/highlighting/Spelling.dart`.
