# Cartographer Performance Architecture

## 1. Purpose and status

This is the authoritative current-state description of Cartographer's
performance architecture. It describes the implementation that exists in the
repository today: read-only save access, bounded streaming decode, compact
analysis state, operation-scoped lifecycle, persistent render-data caching,
and the evidence infrastructure used to validate them.

The implementation architecture is complete. The final real-save,
performance, profiling, and visual validation campaign is still in progress.
The performance foundation must not be called validated until the applicable
correctness, source-safety, macro, resource, JFR, and manual-render evidence
has been reviewed and accepted. This document does not make that acceptance.

## 2. Performance invariants

These are architectural invariants, not optional tuning advice:

- Vintage Story `.vcdbs` files are opened read-only and remain the source of
  truth.
- Decoded source chunks are transient. They are consumed by the operation and
  are not retained as an input-sized collection.
- Memory is bounded by compact result state plus explicitly bounded queues,
  workers, buffers, and other working state; it does not grow with the total
  number of decoded chunks.
- Compatible analyses share or fuse traversal where their semantics permit it.
- Block and column hot loops prefer primitive/indexed state over repeated
  object allocation and string classification.
- Spatially selective reads are preferred where the operation can define a
  selective range or block-ID filter.
- Concurrency has explicit worker and in-flight bounds and backpressure.
- The common ForkJoinPool is not used for core backend decode processing.
- Persistent cache data is derived data, never authority over the save.
- Missing, corrupt, incompatible, or unavailable cache data falls back to
  authoritative source processing.
- Correctness, diagnostics, coordinate meaning, missing-data semantics, and
  source safety take priority over elapsed time.

## 3. High-level data flow

```text
read-only SQLite save
        |
        v
operation-scoped SaveSession / immutable SaveSnapshot
        |
        v
selective SQLite reads and bounded decode pipeline
        |
        +--> compact ROCK aggregation
        |
        +--> compact Surface tile aggregation
        |
        +--> fused prospecting classification and aggregation
        |
        v
compact render/analysis state
        |
        +<-- optional persistent render-data cache for static render inputs
        |
        v
renderer, overlay, or analysis result
```

The cache-backed map-data preparation pipeline is shared by the main
actual-ore/base-map render path and the Workstation Surface render path. It
does not replace the source session and does not cache dynamic player, HOME,
marker, mapregion, actual-ore analysis state, or Surface resource-analysis
results as if they were static tile data.

## 4. Source access and operation lifecycle

`SaveSessionFactory` normalizes the save path, opens one SQLite connection in
read-only immutable mode, enables query-only access through the source
connection implementation, and loads stable metadata and the block registry
into an immutable `SaveSnapshot`. `SaveSnapshot` owns no JDBC resources and
contains the normalized save identity, `WorldMetadata`, and an immutable copy
of the registry.

Integrated heavy operations normally open one `SaveSession` for the operation
and close it at the operation boundary. `SaveSession` owns the connection;
session-aware reader methods borrow it and never close it. Closing the session
is idempotent. Failed session initialization closes an already-open connection
before propagating the failure. There is no application-wide JDBC pool,
global session, or public connection escape hatch.

Path-based APIs may own a session internally. Session-based APIs validate that
the requested normalized path is the same save before reading. `VcdbsReader`
session-aware paths therefore share the operation's source connection across
metadata, mapchunk, server-chunk, registry, and related reads. Decode workers
do not use the JDBC connection and no decoded chunk belongs in `SaveSession`.

This separation keeps connection lifetime short and observable, prevents one
connection per individual read, and makes source-save safety a property of a
complete operation rather than an accidental property of one query.

## 5. Bounded streaming decode engine

`BoundedStreamingDecodePipeline` is operation-owned completion-driven work. A
producer/control thread performs SQLite iteration and submits decode tasks to a
fixed set of platform workers. An `ExecutorCompletionService` lets the control
thread consume whichever task completes next; downstream consumers remain
serialized on that control thread.

The total outstanding bound is explicit:

```text
running + queued + completed-but-unconsumed <= maxInFlight
```

The completion queue may be technically unbounded, but the logical
outstanding-future count is authoritative and prevents more than the bounded
number of outcomes from accumulating. Backpressure is applied before another
task is submitted. Decode workspaces are borrowed exclusively and are not
aliased into published results.

The pipeline drains outstanding work before successful finish, aborts and
cancels work on fatal worker/consumer failure, restores interruption when
coordination is interrupted, and waits for owned workers to terminate before
workspace resources are destroyed. Expected row/chunk parse failures retain
their existing diagnostic semantics; infrastructure failures remain failures.
There is no global executor, virtual-thread-per-chunk strategy, common-pool
fallback, or unbounded completed-result collection.

## 6. ROCK architecture

`RenderRockMapUseCase` opens one session, derives the center from the request
or player data, builds a save-registry-derived `RockCatalog`, plans selective
chunk positions, and feeds coverage-aware reader visits to
`RockStreamingSession`. The session consumes decoded chunks immediately and
finishes a compact immutable `RockMap`.

`RockStreamingSession` supports `UPPER_ROCK` and the currently supported
`AT_Y` mode. It tracks seen/available vertical chunk coverage in primitive bit
arrays, so an absent or failed span remains unavailable rather than silently
becoming `NO_ROCK`. Candidate rock identity is represented by catalog ordinal
and Y. Equal-Y conflicts are deterministic and recognized rock IDs come from
the save registry, never hard-coded names.

`RockMapBuilder` owns primitive packed cell storage during aggregation and
transfers that storage once to `RockMap`. The final map stores geometry,
packed state, ordinal counts, and bounded diagnostics. `RockMap` exposes
primitive/indexed access and materializes a `RockColumnSample` only on demand
for compatibility or tests; production storage is not a list of one object per
column.

The circle geometry stores row spans rather than a boxed coordinate map. The
work is qualitatively proportional to requested circle cells plus the
selectively decoded input that intersects them. The full rendered raster is a
separate output cost. There is no retain-all-decoded-chunks ROCK model in the
current pipeline.

## 7. Surface architecture

`SurfaceStreamingSession` separates planning from consumption. The planner
uses mapchunk geometry and rain-height information to establish which world
columns require consideration. `finishPlanning()` promotes missing targets,
then creates a streaming scanner. Decoded fast-path and fallback chunks are
consumed into compact state, and cached complete tiles can be accepted through
the same session.

`SurfaceTileAccumulator` owns primitive arrays for state, surface Y, block ID,
liquid block ID, and compact Surface class codes. State records active,
considered, resolved, and liquid-unavailable semantics. Finalization transfers
tile-array ownership to immutable `SurfaceMap`; it does not create a bulk list
of `SurfaceBlock` objects. Deterministic tie-breaking preserves the preferred
surface observation when multiple observations address a cell.

The rain-height fast path supplies candidate columns cheaply. Columns lacking
usable fast-path data are promoted to fallback processing, where the required
server chunks are decoded and consumed. Diagnostics retain scanned, empty, and
liquid-unavailable information. Compact object/material discovery paths use
streaming plans and primitive/indexed accumulation rather than materializing
millions of per-cell objects.

`SurfaceTile` is request-shaped and may contain an `ACTIVE` circle mask. It is
not automatically a reusable persistent artifact. The persistent cache uses a
separate full-mapchunk `SurfaceCacheTile`; clipped request tiles are rejected
for publication.

## 8. Fused prospecting architecture

`FusedProspectingEngine` loads metadata and the registry once through the
operation session, derives a save-registry rock catalog, compiles one
`CompiledProspectingClassifier`, and plans one selective traversal for the
rock IDs and all requested ore IDs.

The classifier is an immutable sparse ID-to-resource-membership table. The
block loop performs indexed integer lookup and updates primitive boolean
matched/unavailable state. The same decoded visit feeds the ROCK streaming
session and all compatible ore observations. Results are assembled in the
declared resource order with existing `OBSERVED`, `NOT_OBSERVED`, and
`UNAVAILABLE` semantics. `AnalyzeProspectingAreaUseCase` reuses the fused
provider when available and otherwise retains its compatibility path.

This avoids repeating a whole selective traversal once per requested resource
without changing coordinate, missing-data, or diagnostic meaning.

## 9. Persistent render-data cache

The optional render-data cache is external, revision-scoped derived data used
by the shared terrain/Surface preparation pipeline. The main actual-ore/base
map render and the Workstation Surface render both use that preparation path.

### Identity and revision

`RenderDataCacheIdentity` normalizes the save path and derives a namespace
hash. `RenderDataCacheRevision` combines that identity with save size, last
modified time, render-data schema version, and parser/data compatibility
version. The normal revision identity does not full-content-hash the save. It
therefore assumes an offline, quiescent source while a cache is prepared or
used; source safety validation separately checks content and sidecars.

### Manifest

`RenderDataCacheStore` receives the writable cache root explicitly. It stores a
deterministic manifest under a namespace/revision directory. The manifest
records compatibility metadata and normalized save identity. Missing,
malformed, incomplete, stale, or incompatible manifests are misses and are
not trusted. Publication is deterministic and uses temporary publication plus
atomic-or-safe move; temporary files are cleaned when possible.

### Terrain artifacts

`TerrainHeightTile` is compact immutable data for one mapchunk's effective
height values and rain-height availability. `TerrainTileStore` writes
cache-local `terrain-cache.sqlite` below the published revision directory and
uses deterministic codec payloads. A lookup is `HIT`, `MISS`, or `CORRUPT`.
Corrupt or missing rows fall back to the source and a successful source result
can deterministically republish the row, healing it for the next lookup.

### Surface artifacts

`SurfaceCacheTile` is a full reusable mapchunk artifact independent of the
request-shaped `SurfaceTile`/`SurfaceMap`. It stores validated primitive state,
surface Y, block IDs, liquid IDs, Surface class codes, source mode, and
diagnostic summaries. It validates world geometry, including edge tile sizes,
and does not persist the request's active circle mask.

`SurfaceTileStore` writes cache-local `surface-cache.sqlite` under the same
revision directory. Terrain and Surface identities are independent. Missing,
corrupt, incompatible, or world-mismatched rows become source fallback and may
be deterministically republished. A clipped request result is not published as
a reusable full tile.

### Production integration and HIT/MISS meaning

`PrepareMapDataUseCase` still operates inside one operation-scoped source
`SaveSession` even when one or both heavy render layers hit cache. It plans
the relevant mapchunk coordinates, looks up Surface and terrain artifacts, and
records separate requested/HIT/MISS/CORRUPT/source-loaded/published diagnostics.
Render use cases retain ownership of their session and reuse the prepared
compact result for painting and analysis.

- A terrain `HIT` can remove the corresponding mapchunk from source terrain
  work. Its source mapchunk request and source-loaded count are absent/zero for
  that hit coordinate.
- A Surface `HIT` can remove the corresponding Surface tile from planning and
  avoid its server-chunk decode/source work.
- A `MISS` or `CORRUPT` entry remains authoritative-source work and can publish
  a replacement artifact after successful complete processing.
- A cache `HIT` does not mean the `.vcdbs` file is never opened; the operation
  still owns its session and reads other required source data.
- Player/HOME/markers, mapregions, actual-ore overlays, and other dynamic or
  non-integrated state are not incorrectly treated as cached static tiles.

Cache preparation/storage failure disables cache use or writes for the
operation and preserves source rendering. The source save is never a cache
database target.

## 10. Memory and concurrency model

The current rules are consolidated here:

- decode queues and in-flight tasks are bounded;
- worker scratch is reusable and exclusively owned while borrowed;
- decoded chunks are released after consumer aggregation;
- ROCK and Surface results use compact primitive/indexed storage;
- cache lookup and publication use bounded batches;
- JFR analysis uses bounded heavy-hitter storage and streaming event reading;
- no global mutable decoded-world or cache/session state exists;
- no background cache refresh or filesystem watcher extends operation lifetime;
- no source JDBC connection is retained by cache data or worker state; and
- retained state is result state or explicitly bounded working state.

### Workstation retained map frame

The Workstation retains the compact result state associated with the currently
displayed map in a single `MapFrame` slot. Map/Ore/Surface frames retain
`PreparedMapData`; Geology retains `RockMap`; Coverage retains geometry
only. Switching saves clears the slot before new source work begins.

The retained frame deliberately does not keep a second `BufferedImage`,
`SaveSession`, JDBC connection, decoded chunk collection, or other
source-lifetime state. The displayed raster remains owned by the JavaFX map
viewport. Retention is therefore bounded by the compact result state already
produced by the operation.

For Map/Ore/Surface frames, the Workstation can locally recompose the current
raster from retained state when the requested layers are already represented
by that frame. The current local layer contract covers `TERRAIN`, `SURFACE`,
`SOIL_FERTILITY`, and `MARKERS`. Ore and Surface analysis overlays are
repainted from their retained compact results, while HOME and user-marker state
are retained as small decoration state. The local compositor does not open the
save, render-data cache, HOME store, or marker store.

If a layer is enabled but its required compact Terrain/Surface input was not
prepared by the original operation, the Workstation does not perform hidden
source IO. It leaves the retained frame unchanged and asks the user to run a
full Render. Local recomposition creates one new bounded raster (up to the
current 4096×4096 contract) and replaces the JavaFX viewport image without
retaining an additional `BufferedImage` or resetting viewport zoom/pan.
Recomposition is local work, not a claim of zero-millisecond rendering or LOD.

### Workstation retained-operation reuse

Compatible Map/Ore/Surface operations may reuse the current frame's
`PreparedMapData` instead of repeating Terrain/Surface preparation.

- Surface analysis/rendering can run entirely from retained compact
  Terrain/Surface/registry/decorations when save, center, radius, raster scale,
  style, and required Surface availability match. That path opens no
  `SaveSession` and reports fresh empty source-read diagnostics.
- Ore rendering may reuse the retained base map, but a newly requested ore is
  still scanned from the authoritative save in a new operation-scoped
  `SaveSession`. The retained path therefore removes repeated base-map work;
  it does not cache or guess actual ore observations.
- Environment and geologic-province map-region overlays retain interpreted
  `EnvironmentProfile` / `GeologicProvinceSummary` data, not raw
  `ServerMapRegion` payloads. Already prepared overlays can be toggled locally.
  If a newly enabled overlay was never prepared, a retained-base Map/Ore render
  reads only the missing map-region source data and preserves previously
  retained interpreted overlays.
- Retained reuse is save-bound. A retained result from one normalized save path
  must never be combined with source reads from another save.
- Surface-object discovery remains a distinct selective source operation. This
  architecture does not claim parity between that discovery scan and the
  compact `SurfaceMap`, so it is not silently replaced by retained data.

If compatibility fails, the Workstation falls back to the normal authoritative
operation rather than performing hidden partial reuse.

### Geology and fused Prospecting workspace

Geology and Prospecting share the compact ROCK result contract without adding a
persistent ROCK cache.

- ROCK remains source-authoritative. A Geology Y/radius/center change starts a
  new selective source operation.
- Prospecting accepts All resources or a multi-resource selection in one
  request. The fused provider receives the complete resource list once, opens
  one operation-scoped `SaveSession`, builds one `RockCatalog`, compiles one
  classifier, and performs one selective traversal that feeds both ROCK and
  ore observations. The Workstation must not loop one heavy scan per resource.
- `ProspectingAreaResult` retains the compact `RockMap` produced by that
  fused operation. The central viewport renders that retained result locally.
- Geology and Prospecting `MapFrame` values may retain only the compact
  `RockMap` plus geometry; they never retain a `SaveSession`, JDBC
  connection, decoded chunk collection, or an extra raster.
- Cursor inspection uses indexed `RockMap.sampleAt(...)` access. Rock
  highlighting rerenders from the retained `RockMap` and preserves viewport
  geometry, with zero save/cache/HOME/marker-store IO.
- The 4096 x 4096 raster cap still applies to ROCK rendering. Large radii are
  sampled into the bounded raster instead of allocating world-diameter images.

A local highlight or hover is not a new geology analysis. Changing ROCK mode,
Y, center or radius remains an explicit new source operation.

### Responsive operation orchestration and cancellation

The Workstation owns operation lifecycle through three explicit scopes:

- `FOREGROUND` — save loading and authoritative Map/Ore/Surface/Geology/
  Prospecting/Coverage work;
- `DISCOVERY` — Surface Object discovery;
- `LOCAL` — retained layer recomposition and retained ROCK highlighting.

Each coordinated operation records a generation, type, request summary, state,
stage/progress, result-delivery flag and failure. A completion is eligible to
update the UI only while it is still the current operation for its scope.
Superseded or cancelled work cannot publish a stale result.

Busy state is scoped rather than application-global:

- the existing map viewport, pan/zoom, cursor inspection and Result Inspector
  remain usable while heavy source work runs;
- retained layer toggles and ROCK highlight remain local operations and may
  operate on the previous frame while a foreground source request is running;
- a foreground request disables source-request controls that could conflict
  with it, but does not disable the map or retained inspector;
- Surface discovery leaves the viewport, inspector, tool navigation and local
  retained interactions available while blocking save/radius/new source Render
  actions that would conflict with that selective scan.

Cancellation is cooperative but real. The status-bar Cancel action marks the
current operation `CANCEL_REQUESTED` and interrupts the owned operation
thread. It does not declare `CANCELLED` merely because the UI requested
cancellation. The terminal cancellation state is published only after the
operation call returns, so operation-scoped try-with-resources cleanup has
completed first.

The UI coordinator does not create decode worker pools. Existing backend
pipelines remain responsible for bounded parallel decode. On interruption the
bounded streaming decoder cancels outstanding futures, calls
`shutdownNow()`, waits for worker termination and restores interruption as
documented by its tests. `SaveSession` remains operation-scoped and closes
when the interrupted use case exits.

A cancelled operation is not reported as a user-facing analysis failure.
Stale/cancelled callbacks are suppressed, the previous valid frame remains
visible, and the next operation may start from a clean coordinator state.

### Final Workstation presentation shell

GUI-P13 changes presentation hierarchy only. The Tool Rail, collapsible
source-control dock, tabbed Inspector, floating map toolbar and split
operation/telemetry bar remain consumers of the P1-P12 contracts.

Collapsing or revealing UI docks is not an analytical operation. It must not
open the save, consult the render-data cache, alter retained compact state,
allocate a replacement map raster, or reset viewport navigation. The map is
the primary expanding region and continues to display the same bounded raster
owned by `MapPanel`.

The Inspector separates retained/local view controls from source-request
controls by placing Layers beside Inspect/Results/Diagnostics. This is a UX
representation of the existing architectural distinction; it does not change
layer authority or cache semantics.

## 11. Correctness and safety

### Correctness

Semantic fingerprints serialize stable analytical meaning, including
coordinates, states, unresolved/missing semantics, and relevant diagnostics.
Image correctness uses a logical ARGB fingerprint, not compressed PNG bytes.
Authoritative source, cache MISS, cache HIT, and mixed HIT/MISS executions are
compared where they are semantically equivalent. A semantic or image
fingerprint mismatch is an unconditional correctness failure.

Benchmark and profiling evidence also require deterministic iteration
fingerprints, complete declared samples, and factual per-iteration cache/source
work evidence. Operational counters may differ when their documented meaning
is work performed by the current path; they are reviewed separately from
semantic identity.

### Save safety

The source `.vcdbs` remains read-only. Safety snapshots compare source
existence/type, size, mtime, streaming SHA-256 content identity, and the
`-wal`, `-shm`, and `-journal` sidecar states before and after an operation.
The writable cache root, benchmark evidence, JFR recording, and rendered PNGs
must be outside the source directory. A source mutation or prohibited sidecar
creation/mutation overrides any performance result. If the after inspection is
unavailable, the safety verdict is unavailable/inconclusive rather than a
fabricated pass.

## 12. Performance measurement architecture

Macro evidence uses the real production workflows and a named workload. JMH
remains microbenchmark-only evidence for isolated decoder/parser/classifier
costs; it does not establish end-to-end save or render performance, and JUnit
wall-clock assertions are not benchmark evidence.

### Execution modes

- `PROCESS_COLD` launches one fresh Java 25 child JVM per measured sample. It
  is a fresh-process boundary, not a claim of cold OS filesystem caches; the
  report states that OS filesystem cache state is uncontrolled.
- `JVM_WARM` runs two unmeasured warmups and five measured iterations through
  the in-process benchmark runner. Preparation is outside timing.
- `CACHE_WARM` is currently a MAP-only mode. A dedicated campaign cache is
  populated and verified outside timing, then an unmeasured HIT/parity
  preflight precedes five measured iterations, each of which must prove HIT.
  ROCK remains source-authoritative and does not support CACHE_WARM.

### Radius ladder

`R128`, `R256`, `R512`, `R1024`, `R2048`, and `R4096` describe workload
radius, not a performance threshold:

- R128 is smoke/sanity evidence;
- R256 is development/sanity evidence;
- R512 is intermediate scaling evidence;
- R1024 is the mandatory serious canonical reference;
- R2048 is the primary scalability target; and
- R4096 is stretch/headroom observation.

Failures and OOMs are retained as scalability evidence. No arbitrary timing,
throughput, memory, or speedup threshold is invented by this architecture.

### Evidence tools

- `perfBaseline` runs the established ROCK macro baseline workflow.
- `pf18Macro` records the declared workload, execution mode, fingerprints,
  source safety, per-iteration evidence, resource values, and timing samples.
- `pf18SourceSafety` wraps a representative production operation with before
  and after source snapshots and requires qualifying external PF-1.7 cache
  evidence for a safety pass.
- `pf18Jfr` performs a separate diagnostic profile. MAP profiling is prepared
  as cache-warm and ROCK profiling is source-authoritative. JFR timings are
  never merged into normal macro timing percentiles.

Reports retain min/p50/p95/max, every declared sample, failures, OOMs,
correctness identities, and unavailable measurements explicitly. CPU, heap,
GC, allocation, and RSS methods are labeled; unsupported values remain
`UNAVAILABLE`, never zero. Heap evidence is an aggregate per-heap-pool peak-used
sum and not a simultaneous process high-water mark.

### Historical ROCK comparison

The reproducible historical ROCK reference identity is:

```text
3f62bc0a1f76901de92bb585358fe6e02f150b08
```

It supports the R256/R512/R1024/R2048/R4096 ROCK ladder. A reviewer must use
an isolated checkout/worktree and independently verify that the executed
worktree is actually the declared commit. The command-line `-PgitSha` is
evidence metadata; it does not checkout or verify a Git commit. A report with
the wrong worktree identity is not valid baseline evidence.

The MAP cache comparison has a different meaning: candidate SHA plus
`JVM_WARM` cache-disabled/source-authoritative configuration is compared with
the same candidate SHA plus `CACHE_WARM`. It is a same-SHA cache effect
comparison, not a historical cross-SHA MAP comparison.

## GUI-P14 integrated release evidence

GUI-P14 adds an executable acceptance layer over the existing performance and
source-safety tooling; it does not create a second benchmark engine.

The final evidence workflow consists of:

- `guiValidationInit` — creates the SHA-bound manual evidence template;
- `guiValidationPreflight` — depends on the full unit-test suite and writes a
  PASS marker only after tests complete successfully;
- `guiSourceSafetyEvidence` — runs the narrow real-save safety smoke and the
  PF-1.8 production render/cache source-safety workload;
- `guiMacroEvidence` — uses the production PF-1.8 macro harness for factual
  R2048 Map/ROCK evidence plus R4096 stretch attempts;
- `guiReleaseGate` — checks SHA identity and completeness across automated,
  source-safety, macro and reviewer-entered manual evidence.

Mandatory R2048 macro reports must be factual. R4096 remains stretch/headroom
evidence: a terminal attempt is mandatory, but `FAILED` or
`OUT_OF_MEMORY` is retained as that factual outcome rather than converted
into a fabricated performance pass or threshold failure.

The manual manifest covers final Workstation behavior that unit/macro tooling
cannot establish by itself: visual alignment, retained local recomposition,
fused Prospecting UX, responsive cancellation, source safety after cancel, and
the P13 map-first dock/viewport interaction contract.

All evidence roots remain outside the protected source-save directory. A
declared `-PgitSha` is evidence metadata and does not itself prove the local
checkout identity; the reviewer must independently confirm the executed
worktree SHA.

## 13. Current validation status

At documentation-cleanup time:

- the performance architecture implementation is complete;
- the final implementation candidate has a clean build and full automated
  test-suite pass according to reviewer evidence;
- the real-save PF-1.8 source-safety gate has passed with qualifying terrain
  and Surface artifacts under an external cache root and no reported
  violations;
- historical cross-SHA ROCK comparison is still being collected;
- full macro scaling evidence is still being collected;
- JFR review remains pending; and
- manual PNG validation remains pending.

The overall performance foundation remains `VALIDATION PENDING`. This
documentation-only cleanup does not create new runtime evidence and does not
mark PF-1.6, PF-1.7, PF-1.8, or the performance foundation `VALIDATED` or
`DONE`.

## 13a. PF-2 World Snapshot direction

The next performance generation is documented in
[`docs/PF2_WORLD_SNAPSHOT_ARCHITECTURE.md`](PF2_WORLD_SNAPSHOT_ARCHITECTURE.md).

PF-2 shifts the target from repeatedly optimizing every source-backed render to
building revision-scoped compact derived world data once and rendering many
times from that snapshot. PF-2.0 reuses the existing PF-1.7 Terrain/Surface
stores through a `WorldDataSnapshot` facade and adds exact-position traversal
cost instrumentation.

PF-2.3 adds explicit Terrain/Surface world preparation for the observed
main-world mapchunk coverage of a revision. A revision-scoped catalog records
authoritative mapchunk existence, Terrain and complete Surface tiles are
published incrementally in bounded batches, and compatible warm renders reuse
those artifacts without reopening source mapchunk/server-chunk traversal.
Surface outside prepared coverage keeps the existing authoritative fallback;
mapchunk absence is never treated as proof that server chunks are absent.

PF-2.4 extends the same revision namespace with compact interpreted mapregion
state and UPPER_ROCK tiles. Mapregion source rows are streamed and immediately
reduced to Environment/GeologicProvince summaries; raw mapregion payloads are
not persisted. ROCK preparation stores one bounded tile per observed
mapchunk, preserving the existing `OBSERVED / NO_ROCK / UNAVAILABLE`
coverage semantics while discarding decoded source chunks after each bounded
batch. Valid mapregion/ROCK artifacts are reused by repeated
`snapshot prepare` operations; consumer routing to these artifacts is
deferred to PF-2.6.

PF-2.5 extends the revision snapshot with a source-derived actual-resource
index. Eligible ore block IDs come from the save registry's real `ore-*`
block codes, never from OreMaps. The index persists terminal server-chunk
coverage plus actual occurrence membership. Decoded occurrences are compacted
to one 32-bit local-Y mask per block ID and local X/Z column, so exact Y-filter
semantics remain recoverable without retaining decoded chunks or one row per
voxel. Palette rejection is an available negative; missing and failed source
coverage remain distinct. Repeated snapshot preparation reuses terminal
coverage and rebuilds only missing/corrupt chunk entries. Consumer routing is
still deferred to PF-2.6, where unsupported or incomplete snapshot queries
must fall back to source authority.

This remains derived-cache architecture: source authority, read-only safety,
operation-scoped source sessions and fallback-to-source correctness are
unchanged. No global collection of decoded source chunks is retained.


### PF-2.6 snapshot-backed warm operations

PF-2.6 turns the PF-2.3/2.4/2.5 stores into runtime consumers rather than only
preparation artifacts. A small revision-scoped world header supplies metadata,
registry and optional PLAYER state so compatible warm operations can prove
their inputs before opening the game database.

Map/Surface prepared data, mapregion overlays, full-range UPPER_ROCK and
exact-ore occurrence queries are composed from the derived snapshot when all
required coverage is present and compatible. Prospecting uses the persisted
mapregion OreMaps together with snapshot ROCK and actual-resource evidence.

Snapshot data never become authority. MISS, CORRUPT, incompatible registry,
incomplete catalog/coverage, unsupported query mode or absent PLAYER state
selects the existing source-backed operation. Cancellation/interruption crosses
the snapshot boundary unchanged rather than being reinterpreted as a cache
miss.

PF-2.6 does not modify Surface fallback ordering or scanning behavior. In
particular, it introduces no top-down early-stop fallback.

The performance target is therefore explicitly split into cold ingest and warm
render. After a compatible region has been prepared, the warm render path
avoids source SQLite/BLOB/protobuf/Zstd traversal and reuses the existing
analysis/rendering code over compact derived state.


### PF-2.7 Prepare World Workstation UX

PF-2.7 exposes the existing snapshot builder as an explicit Workstation
operation. The World Bar shows revision-scoped preparation state and coverage,
while the existing operation bar supplies progress and Cancel.

```text
Prepare world
    -> source ingest / derived indexing / repair

Render
    -> query compatible prepared data when available
    -> otherwise preserve PF-2.6 source fallback
```

Render never performs a hidden full-world prepare.

Preparation progress is normalized into six monotonic phases: Header, Terrain,
Surface, Map regions, Geology and Resources. Nested source-reader progress is
scaled into the active phase, so an inner read cannot make the user-facing bar
jump to 100% before later preparation work runs.

A small revision-local preparation summary is checkpointed after each verified
phase. Cancellation can therefore leave safe partial derived artifacts and
honest resumable coverage. For an already READY immutable revision, verified
later phases remain valid until those phases are actually rechecked, so a
cancelled refresh cannot erase still-valid evidence simply because it stopped
early.

Snapshot status inspection reads only derived-cache metadata/store presence
and does not open the source game database. Existing PF-2.3–2.6 stores without
the new summary are exposed as resumable PARTIAL coverage.

The badge states are `NOT_PREPARED`, `PARTIAL` and `READY`. They are
revision-specific UX evidence only. Individual snapshot consumers still prove
their own compatible coverage before avoiding source IO.

PF-2.7 changes no Surface fallback ordering/scanning semantics and introduces
no top-down early-stop fallback.

## 14. Explicit non-goals and current boundaries

- There is no global decoded-world cache.
- There is no persistent source JDBC connection pool.
- There is no background cache refresher or filesystem watcher.
- The render-data cache is not a replacement for source authority.
- Unrelated render paths may retain their own source-read architecture; the
  integrated render-data cache contract applies to render paths that delegate
  terrain/Surface preparation to `PrepareMapDataUseCase`.
- Complete real incremental rendering is not claimed merely because legacy
  cache/incremental commands exist.
- This architecture does not justify GC tuning, thread-count tuning, off-heap
  storage, JNI, SIMD, GPU work, or invented performance targets.
