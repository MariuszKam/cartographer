# PF-1.7 Render Data Cache

Status: **IMPLEMENTATION IN PROGRESS — VALIDATION PENDING**

This document records the PF-1.7 A+B foundation plus the C+D, E and F
implementation. It defines the persistent render-data cache namespace, compact
terrain and full-mapchunk Surface artifact contracts, and main-render session
lifecycle. The cache is still not consumed by the renderer; cache HIT/MISS
integration is intentionally deferred.

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

## Checkpoint C — main render SaveSession migration

`RenderActualOreMapUseCase` now owns one `SaveSession` for a path-based render
and delegates to a session execution seam. The session identity is checked
before request state is combined with save data. Within that operation:

* metadata and the block registry come from `SaveSnapshot`;
* player lookup uses the session reader;
* the union of terrain and Surface mapchunk coordinates is read once through
  the session reader;
* Surface fast and fallback chunk phases borrow the same connection;
* optional mapregion overlays use the session reader only when enabled; and
* actual-ore overlays use the snapshot registry and session-aware adaptive
  selective traversal.

The adaptive selective reader now opens one connection for a path call, makes
its strategy decision on that connection, and routes exact or table-stream
selective decoding through connection cores. A session call borrows the same
connection and never closes it. A failure while probing the adaptive strategy
is conservatively treated as `tableStream == false`, so the exact-position
selective core is used; failures during the selected traversal still surface
with reader error context. PF-1.2 bounded decoding and PF-1.4 phase semantics
are unchanged. HOME and marker stores remain external to the save snapshot.

The architectural source-read invariant is therefore one operation-scoped
read-only `.vcdbs` connection lifecycle for the main render. This is a static
design invariant, not runtime connection-count evidence.

## Checkpoint D — compact terrain artifact

`TerrainHeightTile` is an immutable representation for one
`MapChunkCoordinate`. It stores one effective primitive `int[1024]` height
array when available, plus fixed metadata indicating whether RainHeightMap was
available. Rain heights are preferred; otherwise world-generation heights are
used; when neither exists the tile contains no fabricated values and an empty
array. The object owns a defensive copy and contains no `MapChunk`, session,
registry, or per-cell objects.

`TerrainHeightTileCodec` defines a deterministic big-endian binary format with
magic, artifact version, coordinate, flags, exact height count, and values. It
rejects wrong magic/version, unknown or impossible flags, invalid counts,
truncated payloads, and trailing bytes. `TerrainTileLookup` distinguishes
HIT, MISS, and CORRUPT so later integration can fall back to source reads.

`TerrainTileStore` is a cache-local SQLite artifact database at
`terrain-cache.sqlite` below the already-published revision directory. It is
not the game save database: it is writable cache data strictly beneath the
injected cache root, and it never receives the `.vcdbs` path as its database
target. It stores codec payloads in a coordinate-addressable primary-key table,
reads requested coordinates in bounded batches, and publishes tile batches in
a transaction with deterministic coordinate UPSERT behavior. A valid
republish therefore replaces a corrupt or stale row for the same revision and
coordinate without touching the source save. Missing
databases, rows, malformed payloads, and SQL read failures are optional-cache
miss/corrupt states; they do not alter or fail source-save analysis. A
compatible published manifest is required before terrain artifacts are
trusted.

Checkpoint D does not make the renderer look up terrain tiles, skip mapchunk
reads, mix cached and uncached terrain, write from render callbacks, or report
cache diagnostics. The production renderer still reads source mapchunks through
its SaveSession.

## Checkpoint E — reusable full-mapchunk Surface artifact

`SurfaceCacheTile` is separate from PF-1.4 `SurfaceTile` and `SurfaceMap`.
The latter are request-shaped and contain an `ACTIVE` circle mask; the former
represents every valid local column of one mapchunk and never persists ACTIVE,
center, radius, layout, pixels-per-block, render style, or render layers. A
full tile can therefore be projected into multiple overlapping requests later.

The reusable domain is proven by the mapchunk coordinate plus the save's
absolute `worldSizeX` and `worldSizeZ`. The tile start is `coordinate * 32`;
the coordinate must intersect the world, and width/height are derived as
`min(32, worldSize - tileStart)` for each axis. Interior tiles are therefore
32x32, while smaller tiles are legal only at an actual world edge. Request
clipping never changes persistent artifact geometry, and an arbitrary clipped
interior fragment cannot be constructed.

The cache tile owns primitive arrays for state, surface Y, block ID, liquid
block ID, and the explicit stable `SurfaceClassCode` mapping. It records the
whole-tile source mode (`RAIN_HEIGHT_FAST` or `FALLBACK`) and the final
diagnostic summary (`columnsScanned`, `emptyColumns`, and
`liquidUnavailableColumns`). State and unresolved payloads are validated and
canonicalized by contract; decoded server chunks and registry metadata are
never retained.

`SurfaceCacheTileCodec` uses the independent `surface-render-v1` profile and a
deterministic big-endian binary format. It persists world dimensions and
re-derives the expected domain during decode rather than trusting serialized
width/height. Invalid magic, versions, profile, world geometry, states, class
codes, counters, cell counts, truncation, and trailing bytes are rejected.
`SurfaceCacheTile.matchesWorld(WorldMetadata)` provides the narrow geometry
compatibility check required at the future G+H hit boundary. `SurfaceTileLookup`
distinguishes HIT, MISS, and CORRUPT without exposing fabricated data.

## Checkpoint F — persistent Surface store

`SurfaceTileStore` is bound immutably to one `RenderDataCacheRevision` and
stores codec payloads in `surface-cache.sqlite` below that revision directory.
It is cache-local writable SQLite data, distinct from the read-only `.vcdbs`.
Reads deduplicate coordinates in first-occurrence order and use bounded batches;
publication uses one transaction per batch and deterministic SQLite UPSERT so a
corrupt row can be healed by a valid republish. Missing databases/rows and
invalid payloads remain optional-cache MISS/CORRUPT states for later fallback.
No registry, request geometry, player, HOME, marker, connection, or session is
persisted.

## Legacy cache compatibility

PF-1.7 does not redefine or delete the existing `RenderCache`, `CacheKey`,
`IncrementalRenderIndex`, `IncrementalState`, or the `cache warm/status` and
`incremental status/update` commands. Those remain legacy/coarse SaveIndex and
table-level facilities with their existing semantics. The PF-1.7 namespace and
manifest are intentionally separate.

## No renderer integration in E+F

`RenderSurfaceResourceMapUseCase` and other rendering paths still perform their
existing source reads. The main `RenderActualOreMapUseCase` source path now
uses the C SaveSession lifecycle, but no renderer consumes terrain or Surface
cache artifacts.
No decoded input is retained by this foundation.

## Roadmap

The intended remaining implementation pairs are:

* **A+B** — identity and manifest foundation;
* **C** — main `RenderActualOreMapUseCase` SaveSession migration;
* **D** — compact terrain/mapchunk cache model and persistent store (this work);
* **E** — reusable full-mapchunk Surface artifact and codec;
* **F** — persistent Surface store and cache-row healing;
* **G+H** — production cache integration, mixed hit/miss behavior, and diagnostics;
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
