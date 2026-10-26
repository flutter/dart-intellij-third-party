<!--* freshness: { reviewed: '2026-10-25' } *-->
# analytics

## Overview
Reports Dart plugin usage to Dart unified analytics (not the IntelliJ/FUS statistics service).
Events go to the DTD-hosted `UnifiedAnalytics` service. Consent and telemetry state are read
from a short-lived [`DTDProcess`](../dtd/ABOUT.md).

## Interface
- `Analytics` (`Analytics.kt`): entry point. `getConfiguration(sdk, project)`, `report(data)`,
  `updateEnvironment(...)`, and the `suppressAnalytics` flag.
- `AnalyticsData` and subclasses `ActionData`, `AssistData`, `FixData`,
  `LegacyRefactoringData`, `SettingsData`, `SessionData`: typed event payloads.
- `AnalyticsConstants`: shared event field names (for example `DURATION_MS`).
- `AnalyticsConfiguration`: consent prompt and telemetry state for the session.
- `AnalyticsReporter`: delivery strategy chosen from the configuration.
- `DataValue`, `StringValue`, `IntValue`, `BooleanValue`: typed event field descriptors.
- `DartNavigationActionListener`: `AnActionListener` that reports goto-super,
  goto-implementation, and type/method hierarchy actions in a Dart context.
- `DartNavigationAnalytics`: enum of gutter-icon navigation events (`dart.gutter.*`).

## Invariants
- Analytics are suppressed by default (`suppressAnalytics = true`) until DTD reports that
  telemetry is enabled and no consent prompt is pending.
- In unit-test mode, `getConfiguration` returns a default, suppressed configuration without
  starting DTD.
- Events are delivered with `DartToolingDaemonService.sendRequest("UnifiedAnalytics.send")`.

## Side Effects
- On first `getConfiguration`, starts a `DTDProcess` on a pooled thread and blocks the caller
  for up to 700 ms while consent and telemetry state are queried.
- When DTD says a consent message must be shown, posts a notification in the
  "Dart Analysis Server" group. Its OK action confirms with `clientShowedMessage` and then
  terminates the DTD process.
- `updateEnvironment` adds `DASH__*` variables (tool, IDE, plugin, suppression) to child
  process environments.
- Sends telemetry events when analytics are not suppressed.

## Verification
- Tests live under `third_party/src/test/java/com/jetbrains/dart/analytics`.
