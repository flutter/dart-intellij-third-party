Contributing to Dart Plugin for IntelliJ
=======================

<!-- TOC -->
* [Contributing to Dart Plugin for IntelliJ](#contributing-to-dart-plugin-for-intellij)
  * [Contributing code](#contributing-code)
    * [Open Pull Request Limits](#open-pull-request-limits)
  * [Getting started](#getting-started)
  * [Environment set-up](#environment-set-up)
  * [IntelliJ set-up](#intellij-set-up)
    * [Open project and sync Gradle](#open-project-and-sync-gradle)
    * [Build and run the plugin](#build-and-run-the-plugin)
  * [Running plugin tests](#running-plugin-tests)
    * [Using the command line](#using-the-command-line)
    * [Using test run configurations in IntelliJ](#using-test-run-configurations-in-intellij)
  * [IntelliJ Plugin Verifier](#intellij-plugin-verifier)
  * [AI Coding Agent Skills](#ai-coding-agent-skills)
<!-- TOC -->

## Contributing code

![GitHub contributors](https://img.shields.io/github/contributors/flutter/dart-intellij-third-party.svg)

We gladly accept contributions via GitHub pull requests!
If you are new to coding IntelliJ plugins, here are a couple of links to get started:

- [INTRODUCTION TO CREATING INTELLIJ IDEA PLUGINS](https://developerlife.com/2020/11/21/idea-plugin-example-intro/)
- [ADVANCED GUIDE TO CREATING INTELLIJ IDEA PLUGINS](https://developerlife.com/2021/03/13/ij-idea-plugin-advanced/)

You must complete the [Contributor License Agreement](https://cla.developers.google.com/clas)
before any of your contributions with code get merged into the repo.

### Open Pull Request Limits

To ensure our maintainers can provide timely and high-quality feedback, public Flutter repositories limit open pull requests to 2 concurrent open pull requests for contributors without write access.

* **Draft PRs are exempt:** Work-in-progress draft PRs do not count toward your limit.
* **Focus on Quality:** Once you reach the limit, please focus on merging or closing your existing PRs before opening new ones.

## Getting started

1. Install the Dart SDK from [Dart SDK download](https://dart.dev/get-dart) or the Flutter SDK from [Flutter SDK download](https://flutter.dev/docs/get-started/install) (which includes the Dart SDK).
2. Fork `https://github.com/flutter/dart-intellij-third-party` into your own GitHub account.
   If you already have a fork and are now installing a development environment on a new machine,
   make sure you've updated your fork with the `main` branch
   so that you don't use stale configuration options from long ago.
3. Clone your fork:
   ```shell
   git clone https://github.com/<your_name_here>/dart-intellij-third-party
   ```
4. `cd dart-intellij-third-party`
5. `git remote add upstream https://github.com/flutter/dart-intellij-third-party`
   The name `upstream` can be whatever you want.

## Environment set-up

1. Install Java Development Kit 21 (JDK 21).
    - **[Googlers only]** Install Java from go/softwarecenter instead.

2. Set your `JAVA_HOME` directory in the configuration file for your shell environment.
    - For example, on macOS:
      Check what version of Java you have:
      ```shell
      /usr/libexec/java_home -V
      ```
      In your shell configuration file (e.g. `.bashrc` or `.zshrc`), set your `JAVA_HOME` env variable:
      ```shell
      export JAVA_HOME=`/usr/libexec/java_home -v 21`
      ```

3. Set your `DART_SDK` / `DART_HOME` path in the configuration file for your shell environment.
    - If using a standalone Dart SDK:
      ```shell
      export DART_SDK="/path/to/dart-sdk"
      export DART_HOME="$DART_SDK"
      ```
    - If using the Dart SDK embedded in the Flutter SDK:
      ```shell
      export FLUTTER_SDK="$HOME/path/to/flutter"
      export DART_SDK="$FLUTTER_SDK/bin/cache/dart-sdk"
      export DART_HOME="$DART_SDK"
      ```

4. Add `DART_SDK` and `JAVA_HOME` to your `PATH`:
    ```shell
    export PATH=$DART_SDK/bin:$JAVA_HOME/bin:$PATH
    ```

5. Update your current `PATH`.
    - Either restart your terminal or run `source ~/.zshrc` / `source ~/.bashrc` to add the new environment variables to your `PATH`.

## IntelliJ set-up

1. Make sure you're using the latest stable release of IntelliJ,
   or download and install [IntelliJ IDEA Ultimate](https://www.jetbrains.com/idea/buy) or [IntelliJ IDEA Community](https://www.jetbrains.com/idea/download).

### Open project and sync Gradle

2. Start IntelliJ IDEA and open the project:
   - From the "Welcome to IntelliJ IDEA" dialog, select **Open** and choose the `third_party` directory in this repository.
   - If you see a popup with "Gradle build scripts found", **confirm loading the Gradle project, and wait until syncing is done.**

### Build and run the plugin

3. Build and run the plugin instance:
   - Open **View > Tool Windows > Gradle**, and click **Sync All Gradle Projects**.
   - To launch a sandboxed IDE instance with the Dart plugin loaded, execute the following command (from the `third_party` directory):
     ```shell
     ./gradlew runIde
     ```

## Running plugin tests

The test suite is split between unit tests under `src/main/test/java/com/jetbrains/lang/dart` and Dart Analysis Server tests under `src/main/test/java/com/jetbrains/dart/analysisServer`.

### Using the command line

Run all tests:
```shell
cd third_party
./gradlew test
```

Run **unit tests**:
```shell
cd third_party
./gradlew test --tests "com.jetbrains.lang.dart.*"
```

Run **Dart Analysis Server tests** (requires `DART_HOME` or `DART_SDK` to be set):
```shell
cd third_party
./gradlew test --tests "com.jetbrains.dart.analysisServer.*"
```

### Using test run configurations in IntelliJ

- You can run or debug individual tests or test packages directly within IntelliJ IDEA by right-clicking test classes/methods or setting up Gradle test run configurations.

## IntelliJ Plugin Verifier

The project uses the [IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) to check binary compatibility against specified IntelliJ Platform builds.

To run the verifier locally:
```shell
cd third_party
./gradlew verifyPlugin
```

If new issues are found that match expected updates, update the baseline files:
```shell
# Linux / macOS
./third_party/tool/update_baselines.sh

# Windows
third_party\tool\update_baselines.bat
```

## AI Coding Agent Skills

This repository includes custom configuration and automation skills for AI coding agents (such as Gemini Code Assist / Antigravity) located in `.agents/skills/`:

* **[Code Review](.agents/skills/code-review/SKILL.md):** Performs a pedantic, multi-perspective code review on your uncommitted changes.
* **[Migrate DAS to LSP](.agents/skills/migrate-das-to-lsp/SKILL.md):** Guide for converting legacy Dart Analysis Server (DAS) feature implementations to JetBrains LSP.
* **[Monthly Release](.agents/skills/monthly-release/SKILL.md):** Step-by-step guide for preparing, validating, testing, and publishing monthly releases of the Dart plugin.
* **[Patch Copied LSP Sources](.agents/skills/patch-copied-lsp-sources/SKILL.md):** Automates copying and patching of JetBrains LSP sources.
* **[Port PR](.agents/skills/port-pr/SKILL.md):** Fetches a Pull Request from either `dart-intellij-third-party` or `flutter-intellij` and conceptually ports its changes to the other repository.
