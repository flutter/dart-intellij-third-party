<!--* freshness: { reviewed: '2026-10-25' } *-->
# utilities

## Overview
JSON serialization and request/response utility methods (RequestUtilities, ResponseUtilities).

## Interface
- com.google.dart.server.internal.remote.utilities.RequestUtilities
- com.google.dart.server.internal.remote.utilities.ResponseUtilities

## Invariants
- Constructs standard DAS JSON request envelopes.
- `RequestUtilities.generateClientCapabilities` builds the `server.setClientCapabilities` handshake (`METHOD_SERVER_SET_CAPABILITIES`); this is the main reason the file is edited locally (see [../../../../../../../../README.md](../../../../../../../../README.md)).

## Side Effects
- Read-only JSON object construction.
