<!--* freshness: { reviewed: '2026-08-04' } *-->
# assists

## Overview
Quick assist intentions (DartQuickAssistIntention, AssistUtils) powered by Analysis Server.

## Interface
- com.jetbrains.lang.dart.assists.DartQuickAssistIntention
- com.jetbrains.lang.dart.assists.AssistUtils
- com.jetbrains.lang.dart.assists.DartQuickAssistSet

## Invariants
- Translates Analysis Server source edits into IntelliJ WriteActions.

## Side Effects
- Modifies document text upon applying quick assists.
