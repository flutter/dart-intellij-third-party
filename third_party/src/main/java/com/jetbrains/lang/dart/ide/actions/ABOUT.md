<!--* freshness: { reviewed: '2026-10-25' } *-->
# actions

## Overview
Dart IDE actions (Pub commands, Reformat with Dart Style, Sort Members, Create Dart File), plus related non-action extensions: the server-backed Go to Super handler, the inheritors searcher, and the Pub/SDK editor notification banner.

## Interface
- `AbstractDartFileProcessingAction` - abstract `AnAction` (DumbAware) that runs over the current editor or over selected files/dirs once the Analysis Server is ready.
- `DartStyleAction` - Reformat with Dart Style; formats via `DartAnalysisServerService.edit_format`.
- `DartSortMembersAction` - Sort Members; applies `DartAnalysisServerService.edit_sortMembers` edits.
- `DartFixAction` - experimental stub (empty bodies), not registered in `plugin.xml`.
- `DartPubActionBase` (Kotlin) - abstract `AnAction, DumbAware` base that runs a `pub` command for a `pubspec.yaml` in the console; uses private `DartPubActionsService` coroutine scope.
- `DartPubGetAction`, `DartPubUpgradeAction`, `DartPubOutdatedAction`, `DartPubCacheRepairAction` - `pub get` / `upgrade` / `outdated` / `cache repair` (repair asks for confirmation).
- `DartPubBuildAction` + `DartPubBuildDialog` (`.java`/`.form`) - runs `pub global run webdev build --output=<in>:<out>` after a folder dialog.
- `CreateDartFileAction` - extends `CreateFileFromTemplateAction` for new Dart files.
- `DartServerGotoSuperHandler` - `LanguageCodeInsightActionHandler` using `search_getTypeHierarchy`.
- `DartInheritorsSearcher` - `QueryExecutorBase` for `DefinitionsScopedSearch` backed by the Analysis Server (not an action).
- `DartEditorNotificationsProvider` - `EditorNotificationProvider` for pubspec/SDK banners (not an action).

## Invariants
- Server-backed actions require `DartAnalysisServerService.serverReadyForRequest()`.
- File processing actions extend `AbstractDartFileProcessingAction`; Pub actions extend `DartPubActionBase`; `CreateDartFileAction` extends `CreateFileFromTemplateAction`.

## Side Effects
- Runs `pub get`, `upgrade`, `outdated`, `cache repair`, and `global run webdev build` processes.
- Rewrites documents with Analysis Server format and sort-members edits.
- Creates new Dart files from templates.
- Shows editor notification banners.

## Verification
- No unit tests under `third_party/src/test/java/.../ide/actions`; verify manually via the Pub banner/actions on a `pubspec.yaml` and Code > Reformat with Dart Style / Sort Members.
