# PF-2 World Snapshot Performance Architecture

## Goal

PF-2 changes the optimization target from repeated source rendering to:

```text
authoritative .vcdbs
        ↓
one bounded ingest / derived indexing pass
        ↓
revision-scoped compact WorldDataSnapshot
        ↓
many render / inspect / analysis operations
```

The Vintage Story save remains authoritative and read-only. The snapshot is
derived, disposable and rebuildable.

PF-2 does **not** retain all decoded `ParsedChunk` values in memory and does
not keep a persistent JDBC connection to the source save.

## Explicit correctness boundary

Surface fallback semantics are preserved.

PF-2 must not introduce top-down early-stop fallback, change fallback Y
ordering, skip lower fallback chunks because a higher observation exists, or
otherwise change the set of source chunks used by the established fallback
algorithm unless a separate future correctness proof and reviewer decision
explicitly changes that contract.

Performance work targets source-query strategy, decode/allocation cost,
derived indexing and reuse instead.

## PF-2.0 — snapshot foundation and measurement

PF-2.0 introduces `WorldDataSnapshot` as the revision-scoped facade over the
existing PF-1.7 Terrain and Surface stores.

The snapshot is deliberately sparse: it represents whatever complete derived
tiles are currently available for the observed save revision. It does not
claim that the whole world has already been indexed.

`PrepareMapDataUseCase` consumes the snapshot facade rather than constructing
the two stores independently. This is the migration seam for later Terrain,
Surface, ROCK, mapregion and resource stores.

Snapshot identity continues to use:

```text
normalized save path
save size
last-modified timestamp
cache schema version
parser compatibility version
```

A changed revision cannot reuse artifacts from the prior namespace.

### Exact-position traversal metrics

PF-2.0 records the non-selective chunk-read cost that drives the current
`Reading chunks by exact position` stage:

- chosen strategy;
- unique requested positions;
- batch / statement count;
- rows found;
- parsed / failed chunks;
- payload bytes;
- adaptive strategy-probe time;
- source-query / row-materialization time;
- bounded-pipeline submit/backpressure time;
- final decode-drain time;
- total traversal time.

The latest observation is available from `VcdbsReader` and Surface
preparation copies fast/fallback observations into the existing render-data
diagnostics shown by the Workstation Inspector.

Instrumentation is observational only. A failing metrics probe is isolated and
must not alter source-read correctness.

## Planned sequence

### PF-2.1 — source ingest SQL strategy

Implemented on the PF-2.1 branch:

- repeated full-size point batches reuse a prepared statement; a distinct tail
  shape is prepared at most once;
- dense packed primary-key requests are compressed into exact consecutive runs;
- clearly beneficial run sets use batched `BETWEEN` predicates without
  reading gaps between runs;
- sparse requests retain the point-batch/table-stream decision;
- the table-cardinality probe preserves prior semantics while avoiding Java
  iteration over `requested + 1` JDBC rows;
- diagnostics distinguish statements prepared from statements executed.

The range strategy changes only SQL traversal shape. It does not change the
requested packed-position set, fallback ordering, chunk decode semantics, or
source authority.

### PF-2.2 — decode/allocation reduction

Implemented on the PF-2.2 branch:

- internal ServerChunk parsing keeps block/liquid protobuf fields as owned
  source-buffer slices and passes offset/length directly to Zstd; the public
  `ServerChunkPayload` compatibility API keeps its defensive-copy contract;
- empty and uniform decoded layers use constant storage instead of allocating
  an `int[32768]`;
- Surface source reads use an immutable compact palette + decoded-bitplane
  representation for non-uniform block/liquid layers. Point lookups preserve
  `ParsedChunk` semantics without publishing the reusable decoder workspace;
- both operation-scoped `PrepareMapDataUseCase` Surface reads and the legacy
  `ReadSurfaceMapUseCase` use the compact Surface decode path;
- normal adaptive traversal remains on the full materialized decoder and the
  selective Ore/ROCK/Prospecting paths retain their existing selective decode;
- PF-2.1 SQL strategy selection, requested chunk sets, Surface fallback
  ordering and Surface scanner semantics are unchanged;
- JMH now contains compact sparse-access and dense full-scan comparisons
  alongside the materialized decoder. The existing MAP JFR workload provides
  real-save allocation evidence for the compact Surface path.

Decode worker counts remain unchanged. Collect JFR/JMH allocation evidence
before considering worker-count or in-flight-limit tuning.

### PF-2.3 — Terrain + Surface world indexing

Implemented on the PF-2.3 branch:

- `snapshot prepare <save.vcdbs>` is the explicit reviewer-facing Prepare
  World operation; Workstation UX remains deferred to PF-2.7;
- one operation-scoped read-only `SaveSession` streams the authoritative
  main-world `mapchunk` table and publishes a revision-scoped catalog of
  observed `(x,z)` mapchunks;
- catalog membership records source existence before derived mapchunk parsing,
  so parser failures can never be misclassified as source absence;
- Terrain artifacts are published incrementally from that discovery stream.
  A completed revision reuses valid Terrain tiles and exact-reads only
  missing/corrupt observed coordinates;
- Surface indexing reuses Terrain tiles and processes missing Surface coverage
  in deterministic bounded spatial batches. It uses the same render profile
  (foliage ignored, liquid required), RainHeight fast path, fallback planner,
  fallback Y ordering and scanner semantics as the established Map path;
- complete Surface tiles are revision-scoped and reused by compatible renders,
  so a render fully inside prepared coverage performs no source mapchunk or
  server-chunk traversal;
- a complete mapchunk catalog may prove that a Terrain mapchunk is absent and
  suppress that point lookup. It does **not** prove that server chunks are
  absent, so Surface requests outside prepared Surface coverage retain the
  authoritative fallback and may publish additional complete tiles lazily;
- partial catalog rows and already-published Terrain/Surface tiles are safe to
  reuse after interruption. The catalog completion marker is written only
  after the authoritative discovery scan returns successfully;
- catalog and tile databases remain below the external revision cache root;
  no source JDBC connection, decoded source chunk or source payload is retained
  after the operation.

PF-2.3 completeness therefore means complete Terrain/Surface derived coverage
for the catalogued observed main-world mapchunks of one save revision. It does
not claim that every theoretical mapchunk in the world exists or that mapchunk
absence proves server-chunk absence.

Runtime tests and real-save validation remain reviewer-controlled.

### PF-2.4 — mapregion + ROCK indexing

Implemented on the PF-2.4 branch:

- `WorldDataSnapshot` now exposes revision-scoped mapregion and UPPER_ROCK
  stores beside Terrain, Surface and the observed-mapchunk catalog;
- authoritative mapregion rows are streamed through the operation-owned
  read-only `SaveSession`, filtered explicitly to main-world
  `dimension=0,y=0`, interpreted immediately, and persisted as compact
  `EnvironmentProfile` plus optional `GeologicProvinceSummary` state;
- mapregion source payloads and raw `ServerMapRegion` objects are never
  persisted. A scan-complete marker is removed before repair and restored only
  when the authoritative scan finishes without malformed/failed main-world
  rows. Other dimensions are ignored without making main-world coverage
  incomplete;
- mapregion snapshot codecs carry their own interpretation-profile version, so
  a future interpretation-format change can invalidate derived rows without
  changing source authority;
- UPPER_ROCK coverage is persisted as one compact 32x32-equivalent tile per
  observed mapchunk (edge tiles are clipped to world bounds). Each cell stores
  only `OBSERVED / NO_ROCK / UNAVAILABLE`, source rock block ID and rock Y
  where applicable;
- UPPER_ROCK indexing uses the existing selective palette-aware chunk reader.
  `DECODED` and `PALETTE_REJECTED` visits count as available coverage;
  `MISSING` and `FAILED` remain unavailable exactly as in
  `RockStreamingSession`;
- the highest-rock candidate is accepted only when source coverage above that
  candidate satisfies the established UPPER_ROCK contract. Missing coverage
  below an already-proven highest candidate does not invalidate it, matching
  the current request-shaped ROCK implementation;
- ROCK tiles are prepared in the same bounded spatial batches used by world
  Surface preparation. Valid tiles are HITs; only missing/corrupt/incompatible
  tiles are rebuilt. Completed batches survive interruption and the next
  prepare resumes from derived coverage;
- `snapshot prepare <save.vcdbs>` now reports Terrain, Surface, Mapregion and
  UPPER_ROCK coverage separately. Snapshot completeness requires all four
  layers plus the authoritative observed-mapchunk catalog;
- no decoded source chunk, mapregion payload or JDBC source connection is
  retained after the operation. All new databases remain below the external
  revision cache namespace.

PF-2.4 builds the derived data but does **not** yet route Geology/Map overlays
through it. Consumer routing remains PF-2.6, so source reads are still the
authoritative fallback until that stage.

Surface planning/fallback code is untouched by PF-2.4. In particular, the
explicit no-top-down-early-stop correctness boundary remains unchanged.

Runtime tests and real-save validation remain reviewer-controlled.

### PF-2.5 — resource index

Build compact source-derived membership/occurrence indexes needed by Ore and
Prospecting without fabricating ore presence from OreMaps.

### PF-2.6 — snapshot-backed operations

Route compatible Map, Surface, Geology and Prospecting operations to the
derived snapshot. Source reads remain the authoritative fallback for missing
snapshot coverage.

### PF-2.7 — Prepare World UX

Expose snapshot preparation, coverage and revision state in the Workstation
with cancellable progress. Rendering an already-prepared region must remain
separate from indexing work.

### PF-2.8 — cold-ingest / warm-render validation

Treat these as separate workloads:

```text
COLD SNAPSHOT BUILD
WARM RENDER FROM SNAPSHOT
```

Validate R1024/R2048/R4096, source safety, cache revision invalidation,
cancellation, memory, visual parity and warm-render source-read elimination.

## Non-goals

- modifying the source save;
- long-lived source JDBC sessions;
- loading all decoded chunks into heap;
- claiming snapshot coverage that has not been published;
- changing Surface fallback semantics as a performance shortcut;
- treating derived cache data as source authority.
