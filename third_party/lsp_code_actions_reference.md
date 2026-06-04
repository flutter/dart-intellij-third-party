# Dart LSP Code Actions Reference Guide

This guide provides a complete reference for all features implemented by LSP Code Actions in the Dart IntelliJ plugin (via `lsp4ij` and the Dart Analysis Server). Use this document to verify and test each feature, understand its UI lifecycle, and manage the transition from legacy implementations.

---

## 1. Quick Fixes (`quickfix`)

Automated error and warning corrections provided directly by the Dart analyzer.

### Feature Details
* **Capabilities Covered**: Importing missing libraries, creating missing classes/methods/functions, adding required parameters, correcting syntax/type mismatches, removing dead code.
* **When to Surface in UI**: Surfaces automatically in the `Alt+Enter` (Intention) popup menu when the editor cursor is placed on or within the range of an active diagnostic error or warning (indicated by a red or yellow wavy underline).
* **Expected Result on Invocation**: The underlying AST/document edits are applied immediately to the editor buffer, resolving the diagnostic error. (Executes either via client-side direct `edit` or the `dart.edit.codeAction.apply` command).

### Legacy Comparison & Disablement
* **Comparable Legacy Feature**: `edit.getFixes` (managed by `DartQuickFix.java`).
* **How to Turn Legacy Off**: 
  * **Programmatic (Active)**: We have added an explicit guard (`if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) return false;`) to `DartQuickFix.isAvailable(...)`. When experimental LSP features are enabled in settings, legacy quick fixes are automatically disabled to prevent duplicate menu entries.

---

## 2. Assists & Modern Refactorings (`refactor.*`)

Context-sensitive code transformations and structural improvements that do not require an active error or warning.

### Feature Details
* **Capabilities Covered**: 
  * Flutter structural assists: `Wrap with Widget`, `Wrap with Builder`, `Wrap with Column/Row`, `Remove this Widget`, `Convert to StatefulWidget`.
  * Dart AST transformations: `Convert Getter to Method`, `Convert Method to Getter`, `Convert Positional Parameters to Named`, `Add/Remove Import Prefix`, `Move Top Level to File`.
* **When to Surface in UI**: Surfaces in the `Alt+Enter` popup menu when the cursor is placed on an applicable AST node (e.g., a Flutter widget name, a method/getter declaration, a parameter list, or a top-level class/function declaration).
* **Expected Result on Invocation**: The structural AST transformation is executed cleanly, updating the editor buffer. (Executes via `dart.edit.codeAction.apply` or specific modern refactoring commands like `dart.refactor.add_constructor_name`).

### Legacy Comparison & Disablement
* **Comparable Legacy Feature**: `edit.getAssists` (managed by `DartQuickAssistIntention.java` and `DartQuickAssistSet`).
* **How to Turn Legacy Off**:
  * **UI / User**: Uncheck **"Quick assists powered by the Dart Analysis Server"** in `Settings > Editor > Intentions > Dart`.
  * **Programmatic (Active)**: We have added an explicit guard (`if (DartConfigurable.isExperimentalLspFeaturesEnabled(project)) return false;`) to `DartQuickAssistIntention.isAvailable(...)`. When experimental LSP features are enabled, legacy assists are automatically disabled.

---

## 3. Legacy Interactive Refactorings (`refactor.perform` / `refactor.validate`)

Complex refactoring operations that span multiple occurrences or require validation.

### Feature Details
* **Capabilities Covered**: `Extract Method`, `Extract Widget`, `Extract Local Variable`, `Inline Method`, `Inline Local Variable`.
* **When to Surface in UI**: Surfaces in the `Alt+Enter` menu when the user highlights a valid expression/block of code or places the cursor on a variable/method invocation. (Also accessible via standard IDE refactoring shortcuts like `Cmd+Opt+M` / `Ctrl+Alt+M`).
* **Expected Result on Invocation**: `lsp4ij` sends a `workspace/executeCommand` request (`refactor.perform`). The Dart Analysis Server validates the refactoring parameters on the backend and sends a `workspace/applyEdit` request back to IntelliJ, modifying all affected files across the workspace simultaneously.

### Legacy Comparison & Disablement
* **Comparable Legacy Feature**: `edit.getRefactoring` (managed by `ServerRefactoring.java` and explicit IDE action handlers like `DartServerExtractMethodHandler`).
* **How to Turn Legacy Off**: In legacy mode, these are bound to explicit IDE refactoring handlers. In LSP mode, `lsp4ij` integrates them directly into the intention and refactoring action menus.

---

## 4. Source Actions (Batch Operations) (`source.*`)

File-wide and workspace-wide batch maintenance and formatting operations.

### Feature Details
* **Capabilities Covered**:
  * `source.organizeImports`: Removes unused imports and sorts remaining import directives alphabetically/logically.
  * `source.sortMembers`: Sorts class members alphabetically or by logical grouping.
  * `source.fixAll`: Automatically computes and applies all available quick fixes across a file or workspace in a single batch.
* **When to Surface in UI**: Available via the `Alt+Enter` menu at the root/top-level of a file, or triggered via standard IntelliJ Code menu actions (e.g., `Code > Optimize Imports` or `Code > Reformat Code`).
* **Expected Result on Invocation**: The server executes the batch command (`Commands.organizeImports`, `Commands.sortMembers`, `Commands.fixAll`) on the backend and sends `workspace/applyEdit` to update the entire file or workspace buffer instantly.

### Legacy Comparison & Disablement
* **Comparable Legacy Feature**: `edit.organizeDirectives`, `edit.sortMembers` (managed by `DartImportOptimizer.java`, `DartSortMembersAction.java`).
* **How to Turn Legacy Off**: In legacy mode, these are triggered by explicit IDE actions (`DartSortMembersAction`, `DartImportOptimizer`). In LSP mode, `lsp4ij` surfaces them seamlessly as LSP source actions.

---

## Summary Verification Matrix

Use this matrix during manual testing in IntelliJ to verify each feature behaves correctly under LSP:

| Feature Category | Action Example | How to Trigger in IDE | Expected LSP Command / Execution | Legacy Disablement Mechanism |
| :--- | :--- | :--- | :--- | :--- |
| **Quick Fixes** | `Import library 'dart:async'` | Place cursor on unresolved class name, press `Alt+Enter` | Direct `edit` or `dart.edit.codeAction.apply` | Programmatic guard in `DartQuickFix.isAvailable` |
| **Assists** | `Wrap with Widget` | Place cursor on Flutter widget name, press `Alt+Enter` | `dart.edit.codeAction.apply` | Programmatic guard in `DartQuickAssistIntention` |
| **Modern Refactor** | `Convert to named parameters` | Place cursor on parameter list, press `Alt+Enter` | `dart.refactor.*` command | Programmatic guard in `DartQuickAssistIntention` |
| **Interactive Refactor** | `Extract Method` | Highlight code block, press `Alt+Enter` or `Cmd+Opt+M` | `refactor.perform` ──► `workspace/applyEdit` | Replaced by LSP action bindings |
| **Source Actions** | `Organize Imports` | Root of file `Alt+Enter` or `Code > Optimize Imports` | `dart.edit.organizeImports` ──► `workspace/applyEdit` | Replaced by LSP source action bindings |
