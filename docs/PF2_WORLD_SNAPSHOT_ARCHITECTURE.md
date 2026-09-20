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

PF-2.4 builds the derived data; PF-2.6 now routes compatible Geology/Map
consumers through it while preserving authoritative source fallback.

Surface planning/fallback code is untouched by PF-2.4. In particular, the
explicit no-top-down-early-stop correctness boundary remains unchanged.

Runtime tests and real-save validation remain reviewer-controlled.

### PF-2.5 — resource index

Implemented on the PF-2.5 branch:

- `WorldDataSnapshot` exposes a revision-scoped `ResourceIndexStore`
  alongside Terrain, Surface, mapregion and UPPER_ROCK state;
- the indexed block catalog is derived only from the authoritative save block
  registry. A block is eligible when its normalized block-code path starts
  with `ore-`; OreMaps are not consulted as occurrence authority and cannot
  fabricate actual ore presence;
- preparation covers the full vertical server-chunk range beneath every
  authoritative observed main-world mapchunk in the PF-2.3 catalog. This is
  prepared snapshot coverage, not a claim that mapchunk absence proves
  server-chunk absence outside that catalog;
- source reads use the existing palette-aware selective chunk reader. A
  `PALETTE_REJECTED` visit is an authoritative AVAILABLE negative for the
  indexed ore IDs. `DECODED`, `MISSING` and `FAILED` remain distinct
  terminal coverage states;
- decoded matching chunks are reduced immediately. For each actual ore block
  ID and local X/Z column the index stores one 32-bit local-Y occurrence mask,
  preserving exact count/minY/maxY and future Y-filter semantics without one
  database row per matching voxel;
- membership rows are derived from actual decoded occurrences, not merely from
  palette membership. A registry entry or unused palette entry therefore does
  not prove that ore occurs in a chunk;
- coverage, block catalog, membership and occurrence state live in
  cache-local `resource-index-v1.sqlite` under the same revision namespace.
  Decoded source chunks, source payloads and JDBC resources are never retained;
- indexing is bounded by the existing world spatial batches. Valid terminal
  coverage rows are HITs on a repeated prepare; only missing/corrupt coverage
  positions are sent back to the authoritative source reader. Completed
  batches survive interruption;
- the resource scan-complete marker is published only after every expected
  server-chunk position under the observed mapchunk catalog has a terminal
  derived coverage entry. AVAILABLE-empty, MISSING and FAILED are all explicit
  source observations rather than invented absence;
- `snapshot prepare` reports registry ore IDs, resource chunk HITs,
  republished chunks and compact occurrence-column counts separately;
- PF-2.5 builds the index only. Ore and Prospecting still use their established
  source-authoritative consumers until PF-2.6 routes compatible requests to
  the snapshot with source fallback for missing/incompatible coverage;
- the current index is intentionally compatible with actual `ore-*`
  resource semantics. Generic non-ore substring overlays are outside PF-2.5
  snapshot authority and must retain source fallback when consumer routing is
  introduced.

PF-2.5 does not change Surface planning or fallback behavior. The explicit
no-top-down-early-stop boundary remains unchanged.

Runtime tests and real-save validation remain reviewer-controlled.

### PF-2.6 — snapshot-backed operations

Implemented on the PF-2.6 branch:

- the revision namespace now persists a small `WorldSnapshotHeader` containing
  `WorldMetadata`, the block registry and optional PLAYER position. The
  header is derived during snapshot preparation and is never source authority;
- `SnapshotPreparedMapDataReader` can compose existing
  `PreparedMapData` entirely from Terrain/Surface snapshot stores. It
  requires a compatible header, complete authoritative mapchunk catalog, all
  required Terrain evidence and complete Surface tiles. A missing/corrupt
  required artifact returns a snapshot MISS instead of guessing;
- known-unobserved mapchunks may satisfy missing Terrain rows only when the
  authoritative mapchunk catalog has published its scan-complete marker.
  Missing mapchunks are deliberately **not** used to infer server-chunk or
  Surface absence;
- compatible Map renders are resolved before opening a source
  `SaveSession`. Terrain/Surface, mapregion Environment/Geology and
  `ORE_CODE` actual-ore overlays can therefore render from the current
  revision snapshot. Generic substring ore queries remain source-authoritative;
- Surface resource renders use the same snapshot-prepared compact Surface
  state and existing analyzers/renderers. A complete warm Surface render no
  longer enters the source exact-position chunk reader;
- full-range `UPPER_ROCK` Geology resolves metadata/registry/center from the
  snapshot header and ROCK tiles before source-session creation. `AT_Y` and
  custom vertical ranges remain source-authoritative because PF-2.4 does not
  claim authority for those views;
- Prospecting can consume snapshot mapregion OreMaps, UPPER_ROCK and the PF-2.5
  actual-resource occurrence index without opening the game database. The same
  `ResourceAnalyzer` and evaluation logic are reused so worldgen-signal
  semantics are unchanged;
- every snapshot consumer keeps the established source implementation as the
  authoritative fallback. Derived read errors are treated as MISS/CORRUPT, but
  cancellation/interruption is propagated and must never be converted into a
  hidden source fallback;
- use-case-level tests pin the core warm-path invariant by using source
  connections that fail if opened, plus incomplete-coverage tests that require
  fallback.

PF-2.6 changes only where compatible data are read from. It does **not** change
Surface planning, fallback Y ordering, fallback scan semantics or the explicit
ban on top-down early-stop fallback.

The intended warm path is now:

```text
revision check
    -> WorldSnapshotHeader
    -> compact Terrain / Surface / Mapregion / ROCK / Resource stores
    -> existing analysis/renderers
    -> raster/result
```

rather than:

```text
open source SQLite
    -> parse/decode source chunks
    -> analyze
    -> render
```

Any missing compatibility/coverage proof selects the second path.

### PF-2.7 — Prepare World UX

Implemented on the PF-2.7 branch:

- the Workstation World Bar exposes an explicit **Prepare world** action;
- preparation is a normal cancellable `FOREGROUND` operation and therefore
  reuses the P12 operation coordinator, progress reporting, stale-result
  suppression and cooperative interruption semantics;
- `Prepare world` invokes the existing `PrepareWorldSnapshotUseCase`; Render
  remains a separate action and never starts whole-world indexing implicitly;
- preparation progress is mapped into six monotonic top-level phases:
  Header, Terrain, Surface, Map regions, Geology and Resources. Nested reader
  progress is scaled into the active phase rather than resetting the bar;
- `WorldSnapshotPreparationSummary` is checkpointed after each verified phase,
  so cancellation preserves honest resumable coverage;
- refresh of an already READY immutable revision preserves previously verified
  later-phase flags until those phases are actually revisited. Cancelling a
  refresh therefore cannot downgrade still-valid untouched coverage;
- snapshot status inspection uses `WorldDataSnapshot.openExisting(...)` and
  derived-cache metadata/store presence only; displaying status does not open
  the source SQLite database or create a cache manifest;
- the World Bar distinguishes `NOT_PREPARED`, `PARTIAL` and `READY`,
  shows the current revision plus compact per-layer coverage, and changes the
  action label between Prepare / Resume / Refresh;
- existing PF-2.3–2.6 derived stores without a PF-2.7 summary are recognized as
  resumable PARTIAL state and can be verified/reused by Prepare World;
- changing the source revision selects a new immutable namespace, so an old
  READY summary cannot make a changed save appear prepared;
- the Inspector presents per-layer coverage and current-run hit/publish counts.

The preparation summary is UX metadata, not source authority. PF-2.6 consumers
still prove their own required compatibility/coverage and fall back to source
when those proofs fail.

PF-2.7 does not change Surface fallback ordering or scanning semantics and does
not introduce top-down early-stop fallback.

Runtime and real-save validation remain reviewer-controlled and are deferred
to PF-2.8.

### PF-2.8 — cold-ingest / warm-render validation

PF-2.8 implements an executable real-save validation harness that treats these
as separate workloads:

```text
COLD SNAPSHOT BUILD
WARM RENDER FROM SNAPSHOT
```

The harness deliberately creates a fresh derived-cache namespace under the
requested evidence directory. It then:

- runs one full `PrepareWorldSnapshotUseCase` cold build;
- records cold elapsed time plus process CPU, peak-heap and GC evidence where
  the platform exposes those counters;
- requires the resulting Terrain, Surface, mapregion, UPPER_ROCK and resource
  snapshot coverage to be complete;
- renders Map + Surface at R1024, R2048 and R4096 through the PF-2.6 snapshot
  consumer path;
- injects a recording `SaveSessionLifecycleProbe` into the warm renderer and
  requires exactly zero source SaveSession connections for every warm render;
- renders the same request through the source-authoritative path and requires
  exact viewport-geometry and logical ARGB image-fingerprint parity;
- captures warm elapsed/resource evidence separately from the source parity
  render;
- protects the real save with before/after SHA-256 + metadata + SQLite sidecar
  snapshots;
- performs an isolated revision-invalidation probe through the real
  `WorldDataSnapshot` facade: a marker header is published in revision A,
  the source identity is changed to revision B, `openExisting` must miss B
  before publication, and a newly created B namespace must not expose A's
  derived header.

The Gradle entry point is:

```powershell
.\gradlew.bat pf28SnapshotValidation `
  -Psave="C:\path\world.vcdbs" `
  -PgitSha=<full-40-character-sha> `
  -PoutputRoot="C:\path\fresh-pf28-evidence"
```

The task depends on the full unit-test suite. PF-2.7 deterministic cancellation
tests therefore remain the automated cancellation evidence and execute before
the expensive real-save campaign. No scheduler-time assumptions are added.

The evidence directory must be isolated from the source save directory and
must be new or already empty. Reusing a non-empty evidence directory is
rejected so a prior cache, report or revision probe cannot contaminate the
cold-build campaign.

The final report is written to
`pf28-validation-report.txt`. PASS requires:

- complete cold snapshot coverage;
- source-safety PASS;
- revision invalidation PASS;
- exactly one warm sample for each of R1024/R2048/R4096;
- zero source connections opened/closed by every warm render; this lifecycle
  evidence is the hard proof of source-read elimination;
- complete requested Surface tile HIT coverage and complete Terrain proof
  (tile HIT plus authoritative known-absent mapchunks) for every warm render;
- exact geometry parity;
- exact logical image-fingerprint parity.

PF-2.8 does not impose invented timing or memory thresholds. Runtime/resource
values are factual evidence for comparison; correctness and source-read
elimination are the hard gate.

The PF-2.8 implementation candidate is under controller review on its branch.
Real-save execution remains reviewer-controlled; the milestone is not DONE
until automated tests, the harness run and the resulting evidence have been
reviewed.

## Non-goals

- modifying the source save;
- long-lived source JDBC sessions;
- loading all decoded chunks into heap;
- claiming snapshot coverage that has not been published;
- changing Surface fallback semantics as a performance shortcut;
- treating derived cache data as source authority.
