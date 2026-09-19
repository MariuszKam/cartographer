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

- remove redundant compressed-payload copies on internal trusted paths;
- add Surface-oriented compact decoding so Surface ingest does not require
  retaining two full `int[32768]` layers when the complete decoded chunk is
  unnecessary;
- validate with JFR allocation evidence before changing decode worker counts.

### PF-2.3 — Terrain + Surface world indexing

- support an explicit Prepare World operation;
- populate revision-scoped complete Terrain/Surface tiles;
- resume safely from already-published tiles;
- keep source reads bounded and operation-scoped;
- after coverage exists, compatible renders use snapshot tiles and do not
  reopen source chunks.

### PF-2.4 — mapregion + ROCK indexing

Persist compact interpreted/static mapregion and ROCK tile state in the same
revision namespace. Do not persist decoded source chunks.

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
