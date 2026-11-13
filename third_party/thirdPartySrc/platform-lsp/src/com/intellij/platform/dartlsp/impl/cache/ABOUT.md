<!--* freshness: { reviewed: '2026-10-25' } *-->
# cache

## Overview
Caches for LSP responses, used by `LspRequestExecutor`.

## Interface
- `LspCache` - Internal cache interface.
- `LspPerFileCache` - Per-file keyed cache.
- `LspSingleSlotCache` - Cache holding a single key/value (e.g. last workspace-symbol query).

## Invariants
- Upstream-synced; changes must be codified in `patch.py`.

## Side Effects
- In-memory caching only.

## Verification
- No tests in this directory.
