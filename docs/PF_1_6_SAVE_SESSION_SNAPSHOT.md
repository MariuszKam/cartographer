# PF-1.6 SaveSession / SaveSnapshot

Status: IMPLEMENTED — VALIDATION PENDING.

PF-1.6 establishes an explicit operation-scoped lifecycle for related reads
from one immutable, read-only Vintage Story save. It is not a global cache,
connection pool, persistent session, or background refresh mechanism.

## Final architecture

```text
Path entry point
    ↓
SaveSessionFactory.open(path)
    ↓
one immutable/read-only SQLite connection
    ↓
SaveSnapshot
    ├── normalized save identity
    ├── WorldMetadata
    └── immutable block registry
    ↓
session-aware readers
    ↓
operation consumers
    ↓
SaveSession.close()
```

`SaveSessionFactory` normalizes the path, opens the existing SQLite
`mode=ro&immutable=1` connection, enables `PRAGMA query_only = ON` through the
existing connection implementation, and loads metadata and the block registry
once. Snapshot access performs no hidden I/O.

`SaveSnapshot` contains only stable save-level information: normalized save
identity, `WorldMetadata`, and one defensive immutable block-registry map. It
does not contain player state, mapregions, mapchunks, server chunks, decoded
chunks, tiles, ROCK results, Prospecting results, render output, HOME, markers,
or any cache state.

## Ownership and lifecycle

```text
Path APIs         own connections they open
SaveSession APIs  borrow the session connection
Connection cores  never close borrowed connections
SaveSession       owns and closes its session connection
```

Path APIs retain their convenience role and close connections opened for that
call. Session-aware APIs pass the borrowed connection into shared cores;
statements and result sets remain locally owned by the reader that creates
them. No reader uses `try (Connection ... = session.connection())`.

Sessions are operation-scoped and thread-confined. They are not generally
thread-safe. PF-1.2 decode workers receive copied payload/decode work only and
never receive a `SaveSession`, JDBC `Connection`, `Statement`, or `ResultSet`.
`SaveSession.close()` is idempotent, does not reopen a session, and preserves a
close failure as a `SaveException`. Snapshot, borrowed-connection, and identity
operations reject a closed session. Failed snapshot initialization closes the
already-open connection and preserves the original failure with close failure
suppression.

## Session-aware reader seams

`VcdbsReader` provides session forms for the PF-1.6 heavy operations:

- player-position reads;
- exact mapchunk traversal;
- adaptive exact/table server-chunk traversal;
- selective chunk coverage traversal;
- mapregion traversal.

Each path form owns its connection and each session form borrows the existing
connection. The path and session forms delegate to shared connection-based
implementations where applicable. Adaptive selection, table checks, parsing,
diagnostics, progress, and bounded PF-1.2 decode behavior remain in those
shared implementations.

## Migrated production flows

### Fused Prospecting and end-to-end Prospecting

`FusedProspectingEngine.analyze(path, ...)` owns one session and delegates to
its session form. `SavedOreObservationProvider` exposes the session-capable
fused contract and delegates directly to that form.

`AnalyzeProspectingAreaUseCase.execute(request)` now owns one session for the
whole saved/fused operation. Within that session it performs the implicit
player read, mapregion traversal, fused Prospecting analysis, and ROCK
compatibility fallback. The normal saved/fused route therefore has one
operation-scoped SQLite lifecycle, one snapshot, one mapregion traversal, and
one PF-1.5 fused server-chunk traversal by design.

The non-fused compatibility provider interface remains path-based where its
contract requires it. This is an explicit compatibility boundary, not a second
fused implementation.

### ROCK

`RenderRockMapUseCase` owns one session for a path operation and delegates to
its session execution seam. Metadata and registry come from the snapshot;
implicit player lookup and selective chunk coverage use the session. The
PF-1.3 `RockStreamingSession` remains authoritative and ROCK semantics are
unchanged.

### Surface

`RenderSurfaceResourceMapUseCase` owns one session across metadata, player,
registry, direct mapchunk traversal, fast adaptive chunk traversal, and
fallback adaptive chunk traversal. HOME and marker stores remain outside the
SQLite snapshot and are safe to use with the request path after identity
validation. PF-1.4 algorithms and phase separation are unchanged.

## Save identity contract

`SaveSession.requireSameSave(requestPath)` performs a no-I/O identity check
using the same absolute-and-normalized path rules as `SaveSessionFactory`. It
fails fast with `IllegalArgumentException` when a request path does not belong
to the session and rejects a closed session. The ROCK, Surface, and
Prospecting session execution boundaries invoke this guard before combining
session data with request-scoped state. Filesystem identity checks such as
`Files.isSameFile(...)` are intentionally not used.

## Explicit non-goals

PF-1.6 does not migrate every standalone command or reader. Independent
operations may continue using Path APIs where there is no related-read
lifecycle to consolidate. PF-1.6 does not introduce:

- a global session registry or mutable global cache;
- connection pooling or persistent connections;
- background refresh, watchers, or automatic save refresh;
- decoded chunk, mapregion, tile, render-result, or Prospecting caches;
- PF-1.7 render caching;
- changes to PF-1.2 concurrency, PF-1.3 ROCK semantics, or PF-1.4 Surface algorithms.

## Deferred validation contract

Implementation status is complete through Checkpoint J, but validation remains
pending. The following are intentionally `NOT RUN` / `PENDING` and must be
coordinated during the later PF-1.8 hardening campaign:

- compile/build validation and the full test suite;
- runtime lifecycle and correctness-parity validation;
- real-save validation, checksums, and SQLite sidecar inspection;
- runtime connection-count instrumentation;
- JFR, heap/RSS, allocation, and GC measurements;
- R128/R256/R512/R1024/R2048/R4096 workloads and performance comparisons.

No runtime, performance, or save-safety result is claimed by this document.

## PF-1.7 handoff

PF-1.7 Render Data Cache may reuse PF-1.6's stable save identity and snapshot
concepts. It must keep cache lifetime separate from session lifetime: it must
not turn `SaveSession` into a global cache, retain JDBC connections across
operations by default, retain decoded chunks in sessions, or conflate cached
render data with the immutable save snapshot.

PF-1.6 is not a PF-1.7 implementation and does not declare PF-1.7 started.
