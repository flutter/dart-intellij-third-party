<!--* freshness: { reviewed: '2026-10-25' } *-->
# pubServer

## Overview
Serves Dart web apps through the IDE's built-in web server by proxying requests to `webdev serve` processes. One process runs per served directory (for example `web/` or `test/` next to a `pubspec.yaml`). Includes webdev activation and a stop action.

## Interface
- com.jetbrains.lang.dart.pubServer.PubServerManager: public project service (`Disposable`) and the entry point.
  - `getInstance(project)`
  - `send(channel, request, headers, servedDir, path)`: lazily creates one `PubServerService` per served directory.
  - `hasAlivePubServerProcesses()`, `stopAllPubServerProcesses()`
- com.jetbrains.lang.dart.pubServer.PubServerService: package-private `NetService` (`final class`). Each instance runs one `pub global run webdev serve <dir>:<port>` process and proxies HTTP over Netty. It also parses process output to raise error/warning notifications. Not public API.
- com.jetbrains.lang.dart.pubServer.PubServerPathHandler: private Kotlin `WebServerPathHandler`. It maps built-in web server request paths to the served directory and the path inside it, then forwards to `PubServerManager`. The top-level `getServedDirAndPathForPubServer(...)` functions in the same file are public.
- com.jetbrains.lang.dart.pubServer.DartWebdev: Kotlin `object`. `ensureWebdevActivated(project)` runs `pub global activate webdev` once per session, with a progress dialog on the EDT.
- com.jetbrains.lang.dart.pubServer.StopDartWebdevServerAction: `DumbAwareAction` (ID `Dart.stop.dart.webdev.server`) that stops all webdev processes. It is also shown in the webdev console toolbar.

## Invariants
- Served directories are direct children of the folder containing `pubspec.yaml`.
- A change, rename, move, or deletion of a local `pubspec.yaml` disposes that package's running services. A Dart SDK version change stops all services.
- Webdev activation failures and timeouts are logged and treated as "possibly already activated". Only user cancellation returns `false`.

## Side Effects
- May run `pub global activate webdev`.
- Launches `pub global run webdev serve` background processes, opens local ports, and proxies HTTP requests.
- Shows console output and balloon notifications.

## Verification
- No dedicated tests in this package. Check manually by opening a `web/*.html` file of a Dart web project in a browser through the IDE.
