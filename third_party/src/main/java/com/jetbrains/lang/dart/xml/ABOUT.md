<!--* freshness: { reviewed: '2026-10-25' } *-->
# xml

## Overview
XML editor integration for Dart files.

## Interface
- `DartXmlTypedHandlersAdditionalSupport`: `XmlTypedHandlersAdditionalSupport` that enables the
  platform's XML typed handlers (for example tag auto-closing) when the language is Dart.

## Invariants
- `isAvailable` returns true only for languages that are kinds of `DartLanguage`.
- Registered in `META-INF/plugin.xml` as `xml.xmlTypedHandlersAdditionalSupport`.

## Side Effects
- None.
