# PF-1.6 SaveSession / SaveSnapshot — Checkpoints A+B

Status: IMPLEMENTATION IN PROGRESS — VALIDATION PENDING.

## Motivation

Current readers intentionally provide independent operations, and each such
operation opens a new immutable SQLite connection. Related analyses therefore
repeat stable save-level reads such as world metadata and the block registry.
PF-1.6 establishes an explicit operation-scoped lifecycle for sharing those
stable values without introducing a global cache or long-lived save state.

## Lifecycle and ownership

`SaveSessionFactory.open(path)` normalizes the path, opens one connection using
the existing `SqliteSaveConnection` read-only contract, loads the snapshot, and
returns a usable `SaveSession`. The session owns that JDBC connection and is
`AutoCloseable`:

```java
try (SaveSession session = sessions.open(savePath)) {
    SaveSnapshot snapshot = session.snapshot();
    // related analysis work
}
```

Close is deterministic and idempotent. If snapshot initialization fails, the
factory closes the already-open connection before propagating the original
failure. No half-initialized session escapes.

Sessions are operation-scoped and thread-confined. They are not generally
thread-safe, and decode workers must not share the session JDBC connection.
PF-1.2 payload workers remain bounded and operate on decoded work rather than
performing concurrent JDBC access through this abstraction.

## SaveSnapshot contents

The immutable snapshot contains the normalized save path, `WorldMetadata`, and
one defensive immutable copy of the block registry. `WorldMetadata` and
`BlockInfo` are immutable records; callers cannot mutate the registry map.
Snapshot access returns already-loaded state and performs no hidden I/O.

Mapregions, chunks, decoded payloads, tiles, render output, prospecting
results, and other request-scale collections are intentionally excluded. They
remain later operation/result layers, not save-level snapshot state.

## Read-only guarantees

The session uses the existing `SqliteSaveConnection`, which opens with
`mode=ro&immutable=1` and enables `PRAGMA query_only = ON`. No write
transaction, WAL/SHM creation, save copy, watcher, cache, or connection pool
is introduced.

## Migration boundary

Checkpoints A+B establish the lifecycle and a same-connection loading seam.
At that boundary, existing use cases and reader methods were intentionally not
migrated yet; later PF-1.6 checkpoints adopt sessions in scoped groups. The
existing path-based reader APIs remain available and preserve their ownership.

## Status after A+B

At the A+B boundary, C–J remained open for later work, including scoped
consumer migration, session reader integration, diagnostics/progress decisions,
characterization, concurrency/lifecycle tests, save-safety validation, and
final hardening. PF-1.6 was not declared complete by A+B.

## Checkpoint C — session-aware selective reader

`VcdbsReader` now exposes a `SaveSession` overload for selective chunk coverage.
The path overload opens and owns a path connection; the session overload
borrows `session.connection()` and never closes it. Both delegate to the same
coverage implementation and the existing PF-1.2 bounded decode pipeline.

Adaptive table-stream selection probes the connection already supplied to that
shared core. The session path therefore does not call a path overload or open
another connection for table-existence or row-density decisions. Decode worker
count, in-flight bounds, palette filtering, visit statuses, diagnostics, and
completion handling remain unchanged.

## Checkpoint D — fused prospecting migration

`FusedProspectingEngine.analyze(path, ...)` now owns one `SaveSession` lifecycle
and delegates to `analyze(session, ...)`. The session variant reads metadata and
registry from `SaveSnapshot`, compiles the same PF-1.5 classifiers, and invokes
the session-aware selective reader. The PF-1.3 rock consumer and fused ore
accumulator remain the same operation and decoded chunks are not retained.

For one path-based fused operation the intended lifecycle is therefore one
metadata load, one registry load, one session-owned read-only connection, and
one selective physical chunk traversal. Exceptions propagate through
try-with-resources so the session closes before the original failure is
reported.

## Status after C+D

At the C+D boundary, E–J remained open. They included additional scoped consumer migrations,
characterization and lifecycle tests, diagnostics/progress decisions,
save-safety validation, and final PF-1.6/PF-1.8 hardening. Surface, rendering,
CLI-wide, mapregion-wide, and caching migrations are intentionally out of
scope for C+D.

## Checkpoint E — ROCK migration

`RenderRockMapUseCase` now owns one `SaveSession` for each path-based
operation and delegates business logic to its session variant. Metadata and
the immutable registry come from `SaveSnapshot`; an implicit center uses the
session-aware player reader; and ROCK uses the session-aware selective
coverage traversal. The PF-1.3 `RockStreamingSession` and all coordinate and
coverage semantics remain unchanged.

## Checkpoint F — Surface migration

`RenderSurfaceResourceMapUseCase` now uses one session for metadata, implicit
player lookup, registry access, direct mapchunk reads, and both logically
separate adaptive server-chunk phases. `VcdbsReader` provides session-aware
mapchunk and adaptive chunk APIs that share their path implementations through
connection cores. Adaptive strategy selection probes the supplied connection
and exact/table streaming cores borrow it without closing it.

HOME and marker stores remain outside the session snapshot and continue to
use their existing non-SQLite storage paths. Surface algorithms, fast/fallback
phases, diagnostics, and bounded PF-1.2 decode behavior were not redesigned.

For one path-based ROCK operation, and for one path-based Surface operation,
the migrated save-backed work is performed under one session-owned SQLite
connection lifecycle. Statements, result sets, decode workspaces, and bounded
pipelines retain their existing ownership; only `SaveSession.close()` closes
the session connection.

## Remaining G-J work

G-J remain open for additional scoped migrations, characterization and
lifecycle tests, diagnostics/progress review, save-safety validation, and
final PF-1.6/PF-1.8 hardening. CLI-wide migration, global mapregion/resource
access, caching, refresh, and connection pooling remain intentionally out of
scope.
