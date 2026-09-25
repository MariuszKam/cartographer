# Cartographer Runtime Performance Architecture

## 1. Purpose

This document describes the current runtime architecture used to keep VS
Cartographer responsive and memory-bounded while reading large Vintage Story
saves.

It documents product runtime behavior, not historical optimization campaigns.
Benchmark and profiling experiments are development activities and do not form a
runtime package or release subsystem.

The current runtime performance infrastructure lives in normal product packages:

```text
cartographer.cache      persistent derived render-data cache
cartographer.index      compact resource occurrence/index state
cartographer.snapshot   revision-scoped prepared-world snapshot state
```

The repository's opt-in microbenchmarks live separately under `src/jmh`.

## 2. Runtime invariants

These are product correctness requirements, not optional tuning guidance:

- Vintage Story `.vcdbs` files are opened read-only and remain authoritative.
- Derived cache, index and snapshot data never become authority over the save.
- Missing, corrupt, incomplete or incompatible derived data falls back to
  authoritative source processing.
- Decoded source chunks are transient and are not retained as an input-sized
  collection.
- Memory is bounded by compact result state plus explicitly bounded queues,
  workers, buffers and other operation state.
- Compatible analyses share or fuse traversal where semantics permit it.
- Hot loops prefer primitive/indexed state over repeated object allocation and
  string classification.
- Spatially selective reads are preferred where an operation can define a
  selective range or block-ID filter.
- Core backend decode work does not use the common ForkJoinPool.
- Correctness, diagnostics, coordinate meaning and missing-data semantics take
  priority over elapsed time.

## 3. High-level data flow

```text
read-only SQLite save
        |
        v
operation-scoped SaveSession / immutable SaveSnapshot
        |
        v
selective source reads + bounded decode pipeline
        |
        +--> compact Terrain / Surface state
        +--> compact ROCK state
        +--> fused prospecting/resource evidence
        |
        v
compact render / analysis state
        |
        +<-- optional derived cache / prepared-world snapshot
        |
        v
renderer / overlay / analysis result
```

The derived stores reduce repeated source work. They do not replace the
operation-scoped source model when authoritative source access is required.

## 4. Source access and operation lifecycle

`SaveSessionFactory` normalizes the save path, opens one SQLite connection in
read-only immutable mode, enables query-only access through the source
connection implementation, and loads stable metadata plus the block registry
into an immutable `SaveSnapshot`.

A heavy source-backed operation normally opens one `SaveSession` and closes it
at the operation boundary. Session-aware readers borrow that connection and do
not close it themselves. Decode workers do not own JDBC connections.

Path-bearing application requests are resolved to a session at the use-case
boundary. Request-scoped code can use `SaveSession.requireSameSave(...)` to
prevent accidental cross-save reuse.

## 5. Bounded streaming decode

`BoundedStreamingDecodePipeline` is operation-owned work with explicit worker
and in-flight limits.

A producer/control thread performs SQLite iteration and submits decode work to a
fixed set of platform workers. Completion-driven consumption prevents slow tasks
from forcing submission-order waits while the logical outstanding count keeps
work bounded:

```text
running + queued + completed-but-unconsumed <= maxInFlight
```

The pipeline applies backpressure before further submission, drains outstanding
work before successful completion, cancels owned work on fatal failure, restores
interrupt status when coordination is interrupted, and waits for owned workers
to terminate before destroying operation resources.

Expected row/chunk parse failures retain diagnostics rather than becoming global
process failures where the existing reader contract permits partial analysis.

## 6. Compact ROCK architecture

ROCK analysis uses selective chunk planning and streaming aggregation rather
than retaining all decoded chunks.

`RockStreamingSession` tracks observed/unavailable vertical coverage in compact
primitive state. Candidate rock identity is represented by catalog ordinal and
Y. Equal-Y conflicts are deterministic and rock IDs come from the save registry,
not hard-coded names.

`RockMap` stores compact packed result state and materializes higher-level sample
objects only on demand. Rendering is bounded by the requested geometry and the
configured raster cap.

Compatible full-height UPPER_ROCK operations can use prepared snapshot tiles.
Unsupported modes, missing coverage or corrupt/incompatible snapshot data fall
back to authoritative source processing.

## 7. Surface architecture

Surface processing separates planning from streaming consumption.

Rain-height/mapchunk data establish candidate columns cheaply. Missing or
unusable fast-path data is promoted to fallback server-chunk decoding.

`SurfaceTileAccumulator` owns primitive arrays for state, surface Y, block ID,
liquid ID and compact Surface class codes. Finalization transfers compact state
into immutable results rather than creating one object per world column.

Request-shaped `SurfaceTile`/`SurfaceMap` state is distinct from reusable
persistent `SurfaceCacheTile` data. A clipped request result is never published
as a full reusable cache tile.

Surface fallback ordering and missing/liquid-unavailable semantics are
correctness contracts and must not be weakened for speed.

## 8. Fused prospecting

`FusedProspectingEngine` resolves metadata and the registry once, builds a
save-registry-derived rock catalog, compiles resource membership once and plans a
single selective traversal for compatible ROCK and ore observations.

The same decoded visit can feed ROCK state and multiple requested ore resources.
Results preserve `OBSERVED`, `NOT_OBSERVED` and `UNAVAILABLE` semantics.

This avoids repeating a full selective source traversal once per resource
without changing evidence meaning.

## 9. Persistent render-data cache

Runtime cache infrastructure is owned by `cartographer.cache`.

### 9.1 Identity and revision

`RenderDataCacheIdentity` normalizes the save path and derives the cache
namespace. `RenderDataCacheRevision` combines that identity with save size,
last-modified time and compatibility/schema versions.

The revision model assumes an offline/quiescent save while derived data is being
prepared or reused. The save remains authoritative.

### 9.2 Manifest

`RenderDataCacheStore` receives the writable cache root explicitly and stores a
deterministic revision manifest outside the game save.

Missing, malformed, stale or incompatible manifests are cache misses.
Publication uses temporary files and atomic-or-safe move semantics where
available.

### 9.3 Terrain cache

`TerrainHeightTile` is compact immutable height data for one mapchunk.
`TerrainTileStore` persists deterministic tile payloads in the cache namespace.

A lookup is `HIT`, `MISS` or `CORRUPT`. Missing/corrupt rows fall back to source
and a successful source result may republish the derived row.

### 9.4 Surface cache

`SurfaceCacheTile` is a full reusable mapchunk artifact independent of a
request-shaped Surface result. It stores validated primitive state, heights,
block/liquid IDs and compact Surface classification.

`SurfaceTileStore` uses the same revision namespace. Missing, corrupt or
world-incompatible rows fall back to authoritative source processing.

### 9.5 Runtime integration

`PrepareMapDataUseCase` coordinates cache lookup with source fallback inside the
normal operation model.

A Terrain HIT can eliminate the corresponding source mapchunk read. A complete
Surface HIT can eliminate the corresponding server-chunk work. A MISS or
CORRUPT result remains source work and can be healed after successful complete
processing.

A cache HIT does not mean that the `.vcdbs` can never be opened; unrelated or
dynamic data may still require source access.

## 10. Resource index

Runtime resource indexing is owned by `cartographer.index`.

Eligible actual-resource block IDs come from the save registry's real block
codes, not from OreMaps. The index persists terminal chunk coverage and compact
actual occurrences.

Occurrences retain exact local-Y membership using compact bit masks so Y-filter
semantics remain recoverable without keeping decoded chunks or one row per
voxel.

Missing/failed source coverage remains distinct from confirmed negative
coverage. Incomplete or unsupported snapshot queries fall back to source
authority.

## 11. Prepared-world snapshot

Runtime snapshot infrastructure is owned by `cartographer.snapshot`.

`WorldDataSnapshot` is a revision-scoped facade over derived Terrain, Surface,
mapregion, UPPER_ROCK, resource-index and preparation-summary stores. It owns no
source JDBC connection.

A small world header can supply stable metadata, registry and optional player
state for compatible warm operations before source access is considered.

Prepared mapregion data stores compact interpreted Environment and geologic
province summaries rather than raw source payloads. UPPER_ROCK snapshot tiles
preserve existing observed/no-rock/unavailable coverage meaning.

Snapshot consumers prove the exact coverage and compatibility they require.
`MISS`, `CORRUPT`, incompatible registry/revision, incomplete catalog coverage,
unsupported query mode or unavailable required state selects the normal
source-backed route.

### Prepare World UX

The Workstation exposes snapshot construction as an explicit `Prepare world`
operation. Rendering never performs a hidden full-world preparation.

Preparation reports monotonic phases and supports cancellation. Verified partial
artifacts may remain safely reusable, and a revision-local preparation summary
records resumable coverage.

Snapshot status inspection reads derived-state metadata and does not need to
open the source game database merely to determine `NOT_PREPARED`, `PARTIAL` or
`READY` status.

These badge states are revision-specific UX evidence only; individual consumers
still validate the exact derived coverage they intend to reuse.

## 12. Workstation retained state

The Workstation retains compact result state for the currently displayed map in
a single frame slot.

It does not retain a `SaveSession`, JDBC connection, decoded chunk collection or
an additional long-lived source model.

Compatible map/surface/ore operations can reuse prepared compact base data when
save identity, geometry, style and required layer availability match. Newly
requested source-authoritative evidence, such as a different actual ore query,
still opens the required operation-scoped source path.

Local recomposition changes presentation from retained compact state and does
not imply hidden save IO. If required compact input was never prepared, the UI
requires a full render rather than silently opening the save from a local
presentation operation.

## 13. Cancellation and concurrency

Foreground, local-recomposition and discovery work are separate Workstation
operation categories with generation/stale-result protection.

Cancellation is explicit and must cross source, snapshot and decode boundaries
without being reinterpreted as a cache miss. Test synchronization uses semantic
lifecycle handshakes rather than wall-clock sleeps.

Owned executors, pipelines and other operation resources must terminate on
success, cancellation and failure paths.

## 14. Correctness and save safety

Derived-state performance work must preserve:

- read-only `.vcdbs` access;
- coordinate contracts;
- missing-data semantics;
- parser diagnostics;
- source fallback on missing/corrupt/incompatible derived data;
- exact resource occurrence/Y-filter meaning;
- deterministic cancellation/lifecycle behavior.

Manual real-save validation uses the repository's existing Before/After release
helper when save integrity must be demonstrated:

```powershell
powershell -ExecutionPolicy Bypass -File tools/validate-windows-release.ps1 -Mode Before -SavePath "<save.vcdbs>"
# exercise the desktop workflow
powershell -ExecutionPolicy Bypass -File tools/validate-windows-release.ps1 -Mode After -SavePath "<save.vcdbs>"
```

The helper records/compares save hash and size and checks for newly created
`-wal` / `-shm` sidecars. A skipped manual check is never implied to have
passed.

## 15. Performance measurement

Persistent benchmark tooling belongs outside normal runtime source.

The supported repository benchmark source set is JMH under `src/jmh`:

```powershell
.\gradlew.bat jmh
```

JMH is appropriate for repeatable microbenchmark work. It is not evidence for
full desktop end-to-end latency by itself.

Any future baseline-versus-candidate macro comparison must define its method in
the task being evaluated and, at minimum:

- pin the exact baseline and candidate SHA;
- use the same immutable input for the comparison;
- record the Java/runtime and relevant machine environment;
- use equivalent operation parameters and cache state;
- separate correctness evidence from timing evidence;
- mark changed-input or otherwise invalid comparisons `INCONCLUSIVE` rather
  than interpreting them as performance results.

No historical performance campaign framework is part of the shipped
application or a permanent release gate.

## 16. Current boundaries

The architecture intentionally does not claim:

- a global in-memory decoded-world cache;
- application-wide JDBC pooling;
- source authority for derived cache/snapshot data;
- complete incremental rendering by changed source chunk;
- zero-source-IO for every warm operation;
- that JMH predicts end-to-end GUI latency;
- that every large-radius raster can be retained without the configured render
  limits.

Future performance changes should preserve the runtime invariants in this
document and add tooling only when it has a clear recurring engineering use.
