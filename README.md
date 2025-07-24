## Project and repository history

This repository was originally a copy of the [Dart Plugin for IntelliJ from JetBrains](https://github.com/JetBrains/intellij-plugins/tree/master/Dart) through the commit https://github.com/JetBrains/intellij-plugins/commit/c912b13b1dd8acccd6259dedc4063848a8f87b2b.

Before the move to this repository, the project had 4 working and active branches which were used to build different IntelliJ versions:
- `243` - 2024.3
- `251` - 2025.1
- `252` - 2025.2
- `master` - the current master for intellij-community, targeting the next version 2025.3

In its original location the project was built with Bazel; however, in github.com/flutter, the project is built using Gradle (see https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).