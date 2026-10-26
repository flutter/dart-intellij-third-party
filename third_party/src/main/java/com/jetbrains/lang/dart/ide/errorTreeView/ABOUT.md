<!--* freshness: { reviewed: '2026-10-25' } *-->
# errorTreeView

## Overview
The Dart Analysis tool window (problems table with filters and settings) and actions that control the Analysis Server.

## Interface
- `DartAnalysisToolWindowFactory` - `ToolWindowFactory` creating the Dart Analysis tool window.
- `DartProblemsView` - project `PersistentStateComponent<DartProblemsViewSettings>` owning the problems panel and its state.
- `DartProblemsViewPanel` - tool window panel (table, toolbar, copy support).
- `DartProblemsTableModel` / `DartProblem` / `DartProblemsPresentationHelper` - table rows and filtering/presentation logic.
- `DartProblemsFilterForm` / `DartAnalysisServerSettingsForm` (`.java`/`.form`) - filter and server-settings popups.
- `DartProblemsViewSettings` - persisted filter/scope settings bean.
- `ReanalyzeDartSourcesAction` - `analysis_reanalyze`, or `restartServer()` when the server process isn't active; reports action analytics.
- `RestartDartAnalysisServerAction` - `restartServer()`.
- `AnalysisServerDiagnosticsAction` - gets the port via `diagnostic_getServerPort` and opens `http://localhost:<port>/status`.

## Invariants
- Problems are populated from Analysis Server error notifications.

## Side Effects
- Shows a tool window; persists filter settings.
- Restarts or reanalyzes the Analysis Server; opens a browser for diagnostics.

## Verification
- No dedicated tests; verify via the Dart Analysis tool window and its toolbar actions.
