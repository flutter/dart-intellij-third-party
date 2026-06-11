---
name: patch-copied-lsp-sources
description: Patch JetBrains LSP sources copied into the Dart plugin to prevent conflicts and ensure proper configuration.
---

# Patch Copied LSP Sources

Use this skill whenever JetBrains LSP source files are copied or refreshed from `intellij-community` into the `dart-intellij-third-party` plugin. It applies custom adaptations to prevent conflicts with the built-in LSP client of IntelliJ IDEA, renaming resources, fixing notification groups, and setting correct registry key defaults.

## How to Apply

To apply the adjustments automatically:
1. Run the Python patch script located in the `scripts` directory from the repository root:
   ```bash
   python3 .agents/skills/patch-copied-lsp-sources/scripts/patch.py
   ```
2. Verify the changes using `git diff`.
3. Verify that the project compiles:
   ```bash
   ./gradlew clean compileKotlin --no-build-cache
   ```

## Record of Adjustments Applied

Here is the details of the changes made by the patch script:

1. **Package Namespace Rename**:
   - Rename package namespace `com.intellij.platform.lsp.dart` to `com.intellij.platform.dartlsp` across all `.kt`, `.java`, and `.xml` source files.
   - Moves source directory `third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/lsp/dart` to `com/intellij/platform/dartlsp`. This isolates the classes completely from the native IDE classloader, avoiding split-package collisions and accidental wrong-namespace imports.

2. **Registry Key & Timeout Key**:
   - Rename key `lsp.server.connect.timeout` to `dart.lsp.server.connect.timeout` in `dart-lsp-impl.xml` and `Lsp4jServerConnector.kt` to avoid conflicting with IntelliJ IDEA's default LSP settings.

3. **Notification Groups**:
   - Prefix notification group IDs with `Dart: ` in `dart-lsp-impl.xml` and `LspServerNotificationsHandlerImpl.kt` to avoid conflicts with IntelliJ's platform groups.
     - `LSP window/showMessage` -> `Dart: LSP window/showMessage`
     - `LSP window/logMessage: errors, warnings` -> `Dart: LSP window/logMessage: errors, warnings`
     - `LSP window/logMessage: info, log; $/logTrace` -> `Dart: LSP window/logMessage: info, log; $/logTrace`

4. **Extension Element IDs**:
   - Remove optional `id="..."` attributes from all extension definitions in `dart-lsp-impl.xml` (except `<notificationGroup>`), as they are optional and avoid ID collision warnings/conflicts.

5. **Resource Bundle**:
   - Change bundle reference from `messages.LspBundle` to `messages.DartLspBundle` in `dart-lsp-impl.xml` and `LspBundle.kt`.
   - Provide local `DartLspBundle.properties` resource file under `third_party/src/main/resources/messages/` containing the required localization keys.

6. **Log Registry Defaults**:
   - Set `defaultValue="false"` for registry key `lsp.communication.standard.log.file` in `dart-lsp-impl.xml` to avoid spamming the main `idea.log` with LSP logs by default, storing them in session-specific files instead.

7. **Intention Directory Names**:
   - Rename the intention descriptions folder `LspIntention` to `DartLspIntention` and update `descriptionDirectoryName` to `DartLspIntention` in `dart-lsp-impl.xml` to prevent collision.
