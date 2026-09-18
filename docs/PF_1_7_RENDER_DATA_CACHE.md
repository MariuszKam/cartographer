# PF-1.7 Render Data Cache

Status: **IMPLEMENTATION IN PROGRESS — VALIDATION PENDING**

This document records the PF-1.7 A+B foundation. It defines a persistent
render-data cache namespace and manifest contract, but it does not integrate
the cache into any renderer or analysis use case yet.

## Motivation and boundary

PF-1.6 provides an operation-scoped, read-only `SaveSession` and immutable
`SaveSnapshot`. PF-1.7 separates that short-lived access lifecycle from
persistent render-data artifacts that may survive between operations.

The future cache is intended for compact, deterministic render intermediates,
such as mapchunk-derived terrain/height data, compact PF-1.4 Surface tiles,
and other stable render-ready representations where a later checkpoint proves
the semantics. The data flow remains:

```text
SQLite/raw payload
    -> bounded decode
    -> compact cacheable representation
    -> decoded input released
```

The cache must never retain `ParsedChunk`, decoded full server chunks, a JDBC
`Connection`, `SaveSession`, `ResultSet`, `Statement`, decoder workspace, or a
`BufferedImage` as the primary reusable data model. It also must not become a
global mutable in-memory save state or a global unbounded memory cache.

`SaveSession` and `SaveSnapshot` are not cache objects. A cache hit must not
require an old session or connection to remain alive; later cache-miss
integration may open a new session for source analysis.

## A+B identity and revision contract

`RenderDataCacheIdentity` is a new PF-1.7 identity model. It normalizes the
save path with the shared PF-1.6 no-I/O rule (`toAbsolutePath().normalize()`) and
derives a lowercase SHA-256 hash of that normalized path string. The hash is
the filesystem namespace, so two saves with the same filename in different
directories cannot share a namespace. Java `hashCode()` and raw filenames are
not persistent identities.

`RenderDataCacheRevision` observes the following invalidation inputs:

* normalized save identity and its namespace hash;
* save byte size;
* last-modified time in milliseconds;
* dedicated render-data schema version;
* parser/data compatibility version.

The initial versions are `render-data-v1` and `parser-data-v1`. The revision
directory includes a SHA-256 representation of these fields, but the save
itself is not content-hashed on normal requests. This token is therefore an
invalidation signal, not cryptographic proof of file contents: two deliberately
manipulated files with identical size and modification time are not proven
identical. Analysis assumes the save is offline and quiescent. PF-1.8 may
evaluate stronger validation and its cost.

## Persistent manifest store

`RenderDataCacheStore` receives its cache root explicitly. It does not choose a
user-specific location and never writes beside the `.vcdbs` file. Its layout is
conceptually:

```text
<cacheRoot>/
    <save-namespace-sha256>/
        <revision-sha256>/
            manifest.properties
            ...future render artifacts...
```

The manifest records only compatibility metadata:

* cache schema version;
* normalized save path (encoded in the serialized form for unambiguous lines);
* save namespace hash;
* save size and last-modified time;
* parser/data compatibility version.

It does not contain decoded chunks, a block registry dump, player position,
HOME, markers, session state, or analysis/render results.

`find` returns a compatible manifest or a miss. Missing, stale, incompatible,
corrupt, incomplete, or malformed metadata is never accepted optimistically;
the caller can continue with uncached analysis. Unknown/newer schema or
compatibility versions are misses. Cache metadata failures do not modify or
invalidate the source save.

Manifest publication writes a temporary sibling under the revision directory,
writes all bytes, forces and closes the file, then prefers an atomic move to the
final name. If atomic move is unsupported, a non-atomic move of the already
closed temporary file is allowed. Neither publication path replaces an
already-published deterministic revision; a concurrent publisher of the same
revision therefore cannot overwrite valid metadata. Temporary files are
cleaned up when possible, and a final manifest is not considered valid until
the move has completed. A valid published manifest is treated as immutable
cache metadata. A malformed or incompatible final manifest remains a cache
miss. This foundation does not add a writer-locking system.

The store has no background threads, global synchronization, session registry,
connection pool, static cache, or persistent JDBC resource. Later
artifact-writer checkpoints must preserve the same publication boundary.

## Legacy cache compatibility

PF-1.7 does not redefine or delete the existing `RenderCache`, `CacheKey`,
`IncrementalRenderIndex`, `IncrementalState`, or the `cache warm/status` and
`incremental status/update` commands. Those remain legacy/coarse SaveIndex and
table-level facilities with their existing semantics. The PF-1.7 namespace and
manifest are intentionally separate.

## No renderer integration in A+B

`RenderActualOreMapUseCase`, `RenderSurfaceResourceMapUseCase`, and other
rendering paths still perform their existing source reads. A+B does not skip
SQLite reads, return cached terrain or Surface data, or claim a cache speedup.
No decoded input is retained by this foundation.

## Roadmap

The intended remaining implementation pairs are:

* **A+B** — identity, revision, and persistent manifest foundation (this work);
* **C+D** — compact terrain/mapchunk cache representation and storage;
* **E+F** — PF-1.4 Surface tile cache representation and storage;
* **G+H** — main render-pipeline integration and cache hit/miss diagnostics;
* **I+J** — invalidation, cleanup, static audit, and implementation closure.

PF-1.7 must not turn `SaveSession` into a global cache, retain JDBC
connections across operations by default, retain decoded chunks, or conflate
session lifetime with cache lifetime. PF-1.7 does not implement connection
pooling, filesystem watchers, background refresh, or a global mapregion cache.

## Validation status

Implementation status remains **IMPLEMENTATION IN PROGRESS — VALIDATION
PENDING**. Build/compile validation, tests, runtime cache-hit/miss validation,
real-save safety/checksum validation, performance measurement, JFR, heap/RSS,
and PNG inspection are intentionally deferred to the reviewer and the PF-1.8
hardening campaign. No cache speedup, reduced SQLite reads, correctness parity,
or real-save safety result is claimed here.
