## Unreleased

### Added
- Analytics instrumentation (#230, #225)
- Logging with `PluginLogger` (#245, #222)

### Changed
- Migrate from deprecated `ProcessAdapter` to `ProcessListener` (#265)
- Build system: migrate from `community` to `intellijIdea` dependency (#209)

### Removed
- Stale `helpTopic` and `helpId` references (#232, #227)

### Fixed
- Improved cancellation handling for refactoring operations to prevent potential UI freezes (#264)
- Keep New Project dialog details in sync with the selected Dart SDK (#215)
- Downgrade Kotlin to be compatible with a 2.1.0 compiler (#254)

## 502.0.0

### Added

### Changed

- Update stagehand mentions to dart create (#171)

### Removed

- Dropped support for Dart SDK versions older than 2.12.
- Unsupported Dart in HTML logic (#173)
- Outdated web template (#172)

### Fixed

- Fixed resolution for Dart dot shorthands (e.g. `.new`, `.named`). (#89)
- UI freeze during refactoring operations (e.g. Move File) when Analysis Server is slow (#122)

## 500.0.0

### Added

- Support in the "Invalidate Caches..." dialog to remove ~/.dartServer cache

### Removed

- "Scope analysis to the current package" feature from the Dart problem view
- Code Coverage support, all references to com.intellij.coverage.*
- Dart embedded and syntax highlighting in HTML support

### Changed

- Vendor change from "JetBrains" to "Google"
- Build system change from Bazel to Gradle

## 251.27812.12

### Added

 - New Dart language feature: Dot Shorthands (https://youtrack.jetbrains.com/issue/IDEA-370100)
 - Support new 'Null-Aware Elements’ syntax (https://youtrack.jetbrains.com/issue/IDEA-374053)

### Removed

### Changed

 - Fix: IDE freezes after inserting a piece of Dart code (https://youtrack.jetbrains.com/issue/IDEA-231468)
 - Fix: DartParser performance issue: sluggish typing in the editor (https://youtrack.jetbrains.com/issue/IDEA-365957)
 - Fix: Format of very long line gives uncompilable code (https://youtrack.jetbrains.com/issue/IDEA-328345)
 - Fix: Cannot save Dart line length in Settings (https://youtrack.jetbrains.com/issue/IDEA-322573)
 - Closing labels should look like inlay hints, not like real comments (https://youtrack.jetbrains.com/issue/IDEA-289260)