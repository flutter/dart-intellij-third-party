<!--* freshness: { reviewed: '2026-10-25' } *-->
# ui

## Overview
Shared Swing components for Dart plugin settings and dialogs.

## Interface
- `BasicComboBoxWithBrowseButton<T>`: editable `ComboBox` with a browse-folder extension.
  `addBrowseFolderListener` resolves the chosen path on a background executor and hands the
  result to an EDT consumer. Used by `DartConfigurable`, `DartSdkUtil`, `DartGeneratorPeer`,
  and `DartRemoteDebugConfigurationEditor`.

## Invariants
- Path resolution after browsing runs off the EDT; only the result consumer runs on the EDT.

## Side Effects
- None beyond showing a file chooser.
