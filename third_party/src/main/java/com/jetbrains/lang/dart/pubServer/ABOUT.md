<!--* freshness: { reviewed: '2026-08-04' } *-->
# pubServer

## Overview
Pub development server manager (PubServerService, DartWebdev.kt).

## Interface
- com.jetbrains.lang.dart.pubServer.PubServerService
- com.jetbrains.lang.dart.pubServer.DartWebdev

## Invariants
- Manages local web server instances for Dart web debugging.

## Side Effects
- Launches webdev serve background processes and proxies HTTP requests.
