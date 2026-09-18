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
Existing use cases and reader methods are intentionally not migrated yet;
later PF-1.6 checkpoints will adopt sessions in scoped groups. The existing
path-based reader APIs remain available and preserve their current ownership.

## Remaining PF-1.6 checkpoints

C–J remain open for later work, including scoped consumer migration, session
reader integration, diagnostics/progress decisions, characterization,
concurrency/lifecycle tests, save-safety validation, and final hardening.
PF-1.6 is not declared complete by A+B.
