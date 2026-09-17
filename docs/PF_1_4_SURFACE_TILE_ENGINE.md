# PF-1.4 Surface Tile Engine

## 1. Status, authority, and scope

This document is the normative architecture contract for PF-1.4 Surface Tile
Engine. Checkpoint A defined the architecture, semantic inventory, caller
inventory, test plan, and review gates. Checkpoint B is implemented as the
test-only characterization/oracle layer and compact tile/layout/result model.
Checkpoints C, D, E, and F are implemented as the compact fast path, streaming
fallback session, SurfaceMap render integration, and compact streaming Surface
Object discovery respectively. F received controller static review PASS at
`9ed9e643feb2cd08438e15b561e8d9296ca8e1d6`. Checkpoint G removes remaining
production legacy Surface callers, centralizes non-render Surface reading,
removes fake legacy result boundaries, and uses registry-derived primitive
lookups in the compact hot path. G is **IMPLEMENTED — STATIC REVIEW PENDING**.
Runtime validation remains NOT RUN. This document contains no runtime evidence.

PF-1.2 remains authoritative for bounded decode, completion-driven
consumption, worker lifecycle, serialized consumer mutation, and backpressure.
PF-1.3 is implemented through its implementation checkpoints, but its runtime
validation is still pending. PF-1.4 must preserve both contracts.

Checkpoint A changed only this document and the PF roadmap status. B adds the
model and test-only characterization files listed in the implementation
commit. C/D add the compact session and streaming fallback. E wires the two
Surface render pipelines and primitive consumers to the compact result. F wires
both Surface Object discovery callers to compact planning and visit streaming.
No
Gradle command, test, application run,
benchmark, JFR capture, real-save validation, or PNG inspection is performed
in this checkpoint.

## 2. Problem and objective

Surface processing currently has a compact bounded decoder but not a compact
bounded surface result. The operation builds Java objects, boxed collections,
decoded-chunk collections, and whole-result lists proportional to the circle
area. PF-1.4 changes the surface data layout and consumption topology so that
mapchunks plan compact tile state, decoded chunks are consumed immediately,
and renderers/analyzers read an immutable primitive-backed `SurfaceMap`.

The target is correctness-preserving architecture, not a promised speedup.
No heap, allocation, wall-time, GC, or JFR result may be claimed until H.

For a radius `r`, the active world-column count is approximately:

```text
N(r) ≈ πr²
```

The exact/reference R1024 circle count used by this project is `3,294,097`.
R2048 is approximately four times the area. One Java object per active column
would put millions of object headers, references, allocation events, and GC
work in the foundation. A boxed collection entry adds node/table/reference
overhead and destroys locality. This is unacceptable for R1024/R2048 even
before decoded chunk working memory and the output raster are considered.
These are architectural reasons, not measured heap findings.

### Checkpoint-B model established

`SurfaceTileLayout` uses `MapChunk.SIZE` for tile/local geometry and keeps
server-chunk arithmetic as a separate caller concern. It rounds fractional
centers with `Math.round`, uses checked `long` arithmetic for geometry and
allocation products, and orders tiles Z/X and cells local-Z/local-X.
`SurfaceTileAccumulator` owns primitive byte/int arrays per tile, with
explicit state bits for active, considered, resolved, and liquid-unavailable
cells. `SurfaceTile` and `SurfaceMap` are immutable primitive-backed views.
Finalization transfers array ownership and rejects later accumulator mutation;
the new model has no bulk `List<SurfaceBlock>` adapter. Surface classes use an
explicit stable byte mapping, not enum ordinal storage.

The model's equal-Y tie rule is a deterministic primitive tuple tie-breaker
(block ID, liquid ID, then explicit surface-class code). It is not wired into
legacy production behavior; C/D must compare any equal-Y legacy cases before
using it for production accumulation.

### Checkpoint C status and conclusion

Checkpoint C adds `SurfaceRainHeightPlanner`/
`SurfaceRainHeightPlan` and `SurfaceRainHeightScanner`/
`SurfaceRainHeightScanResult`. Planning stores one primitive candidate array
per tile, compact promotion bits, and primitive cell ordinals grouped by
requested server `ChunkPosition`; scanning consumes one `ParsedChunk` and
retains no decoded-chunk reference after the callback. Promotion clears prior
fast resolutions for the complete mapchunk.

The characterization test confirms that the legacy `SurfaceScanner` keeps the
first equal-Y value when duplicate rows with the same coordinate are supplied.
The valid PF-1.2 reader topology requests/delivers each server chunk position
once, and the C scanner rejects duplicate delivery, so conflicting equal-Y
observations are unreachable for valid fast-path input. The compact
tie-breaker remains deterministic and is not claimed equivalent for malformed
duplicate-row input. C runtime evidence is **NOT RUN**.

### Checkpoint D status

Checkpoint D adds `SurfaceStreamingSession` and extends the compact
RainHeight scanner with immediate fallback consumption. Fallback scans each
promoted mapchunk column from high to low within each decoded chunk, records
the highest qualifying observation through the primitive accumulator, and is
order-independent across vertical chunk completion. Promotion clears any
earlier fast result, so fallback is authoritative even when its Y is lower
than the fast observation. Decoded fallback chunks are not retained, and the
legacy `SurfaceFastPathMerger` is not called by this new session; it remains
an independent oracle until G. D runtime evidence is **NOT RUN**.

### Checkpoint E status

Checkpoint E wires the C+D session into both production Surface render
pipelines: `RenderSurfaceResourceMapUseCase` and
`RenderActualOreMapUseCase`. Their reader callbacks consume decoded chunks
directly, and the renderer, soil overlay, material matcher, CLI report, and
workstation diagnostics consume `SurfaceMapScanResult`/`SurfaceMap` primitive
iteration. The old `SurfaceScanResult` field remains only as an empty,
counter-bearing compatibility view for APIs still transitioning in F/G; the
new render paths do not call `blocks()`, `SurfaceFastPathMerger`, or retain
fallback decoded chunks.

`SurfaceResourceAnalyzer` uses primitive coordinate arrays, an open-addressed
primitive occupancy index, and an integer flood-fill queue for material
clustering. Final public deposit/point objects remain proportional to actual
matches for compatibility with existing result and overlay APIs; they are not
the working graph. Registry metadata remains outside the cells and is looked
up only at the consumer boundary. E is **IMPLEMENTED — STATIC REVIEW
PENDING**. E runtime evidence is **NOT RUN**.

### Checkpoint F status

Checkpoint F adds `SurfaceObjectCompactPlanner`, which consumes each mapchunk
into one mapchunk-sized primitive tile state and stores only a terrain anchor,
a rain anchor, and two presence bits per active cell. Candidate Y values are
the clipped union of the two fixed ranges and server-chunk membership is
derived while planning/scanning; expanded per-candidate Y and chunk-index
arrays are not retained. The plan payload is 9 bytes per tile cell as an
arithmetic representation estimate (two `int` anchors and one flag byte),
excluding tile and chunk metadata; this is not a heap measurement.
`SurfaceObjectStreamingScanner`
consumes each `SelectiveChunkVisit` directly: decoded chunks are inspected and
released within the callback, palette rejection is available-but-not-observed,
and missing/failed visits contribute unavailable evidence. An expected position
with no visit is also unavailable for normal non-empty wanted-ID scans; the
empty-wanted-ID path explicitly marks planned positions available because the
legacy reader intentionally skips the selective read. Primitive observation
arrays are heap-sorted deterministically by Z/X/Y/block ID at finalization,
with O(m log m) time and no boxed observation objects.

`DiscoverObservedSurfaceResourcesUseCase` and `InspectSurfaceObjectsUseCase`
now return scalar plan statistics plus `SurfaceObjectCompactScanResult`.
`ObservedSurfaceResourceCatalogBuilder` consumes that result without creating
SurfaceBlock adapters. The legacy planner, target, plan, batch scanner, and
bulk scan result remain independent differential oracles with no migrated
production callers; final legacy cleanup is G. F is **IMPLEMENTED — STATIC
REVIEW PENDING**. F runtime evidence is **NOT RUN**.

### Checkpoint G status

G adds the reusable `ReadSurfaceMapUseCase` callback boundary for non-render
Surface consumers and migrates `scan surface`, surface resource search/render,
and `geology surface` to compact streaming reads. Render result records now
carry only `SurfaceMapScanResult`; the compatibility conversion and unused
SurfaceScanner wiring are removed. Terrain-only renderer calls bypass the
legacy SurfaceBlock overload. `SurfaceRegistryLookup` precomputes sorted
primitive registry metadata for compact Surface, soil, and classification
hot loops; compact material matching uses primitive ID membership and the
existing primitive clustering graph. Remaining legacy list/scanner classes
are definitions retained as independent H or differential oracles, with no
production callers. G is **IMPLEMENTED — STATIC REVIEW PENDING**. Runtime
validation remains **NOT RUN**.

The G repair closes the remaining integration gates: the compact reader
records deduplicated liquid decode failures at the Surface boundary, fallback
diagnostics resolve their chunk-scale primitive bucket once per delivered
chunk, and registry-derived ID-zero/unknown semantics remain identical to the
legacy classifier. Compact geology and unknown-code diagnostics aggregate with
primitive counters before constructing bounded report output. Focused tests
cover the compact reader, registry parity, primitive diagnostics, and
compact/legacy geology parity.

### Controller-review repair status

The post-C/D/E controller review identified three integration defects: fallback
promotion discovered during fast scanning was not guaranteed to be visible
before finalization, `RenderActualOreMapUseCase` retained stale RainHeight type
names/accessors, and E derived legacy diagnostics from clipped active cells.
The repair keeps the lifecycle as `finishPlanning`, fast consumption, current
fallback promotion discovery, fallback consumption, then `finish`. Missing
server chunks are promoted before fallback planning as well.

Fallback diagnostics now use bounded primitive byte flags per delivered
horizontal server-chunk column domain. Considered, resolved, and
liquid-unavailable flags are deduplicated across vertical chunks and include
columns outside the clipped render circle, while `SurfaceMap` remains clipped.
Final production counters are taken from this diagnostic state plus healthy
fast-path targets; a fast missing-liquid event is not retained as final
diagnostic evidence after fallback promotion. The repair is **STATIC REVIEW
PENDING** and runtime evidence remains **NOT RUN**.

A subsequent compile/static repair also restores the compact CLI diagnostic
type boundary and makes promotion reset every transient fast cell bit and
payload while preserving `ACTIVE`. Fallback then repopulates authoritative
considered, resolved, and liquid-unavailable state. This remains a repair
under review; it does not advance PF-1.4 to validation or start checkpoint F.

## 3. Current retention inventory

The following is the characterization of the current `master` implementation
at the PF-1.4 baseline. Method names are exact unless marked as a future
method.

### 3.1 Rain-height fast path

`RainHeightSurfacePlanner.StreamingSession.accept(MapChunk)` in
`scanner/RainHeightSurfacePlanner.java` creates one
`RainHeightSurfaceTarget` per accepted in-circle world column and appends it
to a session list. `finish()` sorts all targets, derives a distinct sorted
`List<ChunkPosition>`, and returns them in `RainHeightSurfacePlan`.

`RainHeightSurfaceScanner.StreamingSession` in
`scanner/RainHeightSurfaceScanner.java` rebuilds a
`Map<ChunkPosition,List<RainHeightSurfaceTarget>>` in its constructor. It
retains delivered positions, resolved and unresolved target sets, creates one
`SurfaceBlock` per resolved column, and globally sorts the bulk output in
`finish()`.

`RainHeightSurfacePlan` and `RainHeightSurfaceScanResult` make those lists
part of the current intermediate API. They are valid characterization/oracle
inputs during migration but must not remain the production hot-path result.

### 3.2 Fallback path and merger

`RenderSurfaceResourceMapUseCase.execute(...)` reads fallback positions with
`VcdbsReader.forEachChunkByPositionAdaptive(...)` into
`List<ParsedChunk> fallbackChunks` and only calls `SurfaceScanner.scan(...)`
after the reader completes. The same topology is present in
`RenderActualOreMapUseCase.readSurface(...)`.

`SurfaceFastPathMerger.merge(...)` creates a `WorldColumn` key per candidate,
retains `HashMap<WorldColumn,SurfaceBlock> selected`, retains a fallback-column
`HashSet`, and globally sorts the final `List<SurfaceBlock>`. It is a useful
legacy differential oracle only; it must have zero production callers after
Checkpoint D/G as specified below.

`SurfaceFallbackMapChunks.collect(...)` and
`SurfaceFallbackChunkPlanner.plan(...)` currently materialize fallback
coordinate sets/lists. Coordinate planning metadata may remain bounded and
primitive, but decoded fallback chunks must not be retained for a later scan.
The current rule that a mapchunk is promoted as a whole fallback unit is a
semantic contract; PF-1.4 must not silently turn it into per-column fallback.

### 3.3 General surface scanner/result

`SurfaceScanner.scan(List<ParsedChunk>,...)` sorts the decoded chunks,
maintains a per-chunk-column `SurfaceBlock[]`, and appends a bulk
`List<SurfaceBlock>`. The `SurfaceScanResult.blocks()` list is currently the
canonical result consumed by rendering, CLI, resource, geology, and UI paths.
`SurfaceBlock` itself retains `BlockInfo` and liquid `BlockInfo` references,
which is useful at compatibility boundaries but not once per cell in the
canonical map.

`SurfaceClassifier.classify(...)` performs repeated null/unknown checks,
water checks, lower-casing, and substring classification. The behavior is
frozen for migration; an implementation may later use precomputed primitive
classification data only after parity tests prove equivalence.

### 3.4 Surface object discovery

`SurfaceObjectPlanner.StreamingSession` retains all mapchunks in
`Map<MapChunkCoordinate,MapChunk>`. Its `finish()` allocates a
`HashSet<Integer>` for every world column, boxes candidate Y values, sorts
them into a `List<Integer>`, creates `SurfaceObjectTarget`, then builds a
sorted chunk-position set.

`DiscoverObservedSurfaceResourcesUseCase.execute(...)` retains every decoded
chunk in `List<ParsedChunk> decoded` and available positions in a set, then
calls `SurfaceObjectScanner.scan(...)` only after the selective reader ends.
`InspectSurfaceObjectsUseCase.execute(...)` has the same decoded-list and
available-set topology.

`SurfaceObjectScanner.scan(...)` builds
`Map<ChunkPosition,ParsedChunk> chunksByPosition`, uses string keys of the
form `x:y:z` for observation de-duplication, creates object-heavy
`SurfaceBlock` results, and sorts the result list. The overload accepting a
`RainHeightSurfacePlan` also rebuilds candidate objects and boxed ranges.

`DiscoverObservedSurfaceResourcesResult.plan` retains the entire
`SurfaceObjectPlan`. `SurfaceDiscoveryCache` caches that result, so each
successful cached entry can pin a large per-column planning graph. The
parallel inspection result also retains its plan through
`InspectSurfaceObjectsResult`.

### 3.5 Consumers currently requiring bulk surface blocks

The complete static migration inventory at this checkpoint is:

* `RenderSurfaceResourceMapUseCase` and `RenderSurfaceResourceMapResult`;
  `RenderActualOreMapUseCase` and `RenderActualOreMapResult`.
* `MapRenderer`, including its surface drawing methods; semantic surface
  rendering and `SurfaceOverlayLegend`/legend generation paths.
* `SoilFertilityOverlayRenderer`.
* `SurfaceMaterialMatch`, `SurfaceMaterialAnalyzer`, and the current
  `SurfaceResourceAnalyzer`/`SurfaceResourceAnalysis` clustering path.
* `ObservedSurfaceResourceCatalogBuilder` and surface-object result/catalog
  construction.
* `SurfaceScanResult`, `RainHeightSurfaceScanResult`, and
  `SurfaceObjectScanResult` compatibility APIs.
* `MapCommand`, `ScanCommand`, `ResourceCommand`, and `GeologyCommand`, plus
  `GeologyAnalyzer` where its surface list is used.
* UI/result inspection through `ResultInspectorPane` and the application
  wiring in `CartographerDesktopApp`.
* Tests covering these paths, especially
  `RainHeightSurfacePlannerTest`, `RainHeightSurfaceScannerTest`,
  `SurfaceFallbackMapChunksTest`, `SurfaceFallbackChunkPlannerTest`,
  `SurfaceFastPathMergerTest`, `SurfaceScannerTest`,
  `SurfaceObjectPlannerTest`, `SurfaceObjectScannerTest`,
  `RenderSurfaceResourceMapUseCaseTest`, discovery/cache tests, renderer
  tests, material tests, and resource/catalog tests.

The inventory is deliberately broader than direct `SurfaceBlock` imports:
CLI and UI consumers that request counts, legends, analysis, or rendering
must migrate to primitive/tile iteration rather than receiving a temporary
whole-map list.

## 4. Coordinate and tile domains

World coordinates remain absolute world coordinates. Display-coordinate
conversion remains the responsibility of existing metadata/rendering
boundaries; PF-1.4 must not mix the two spaces.

The normal horizontal tile is mapchunk-sized. `MapChunk.SIZE` is the size of
the mapchunk height-map domain. `ChunkCoordinate.SIZE_BLOCKS` is the size of
the server-chunk block-coordinate domain. They are currently both `32`, but
they are different contracts and must not be replaced by a duplicated numeric
literal or casually conflated.

`SurfaceTileLayout` must name its domain and use `MapChunk.SIZE` for tile/local
mapchunk addressing. Server-chunk lookup must use
`ChunkCoordinate.SIZE_BLOCKS`. If an implementation relies on equality, it
must validate the equality explicitly at a controlled boundary and retain the
domain-specific constants in the calculations. Negative coordinates must use
checked `floorDiv`/`floorMod` semantics where applicable.

The existing surface center behavior uses `Math.round(center.x())` and
`Math.round(center.z())` in the relevant render/use-case path. That behavior
is frozen. PF-1.4 must not substitute ROCK's floor behavior.

## 5. Target pipeline and threading contract

The canonical conceptual pipeline is:

```text
mapchunk stream
    -> compact Surface tile planning state

chunk stream / PF-1.2 bounded decode
    -> immediate Surface tile accumulation
    -> decoded chunk released

compact immutable SurfaceMap
    -> primitive analyzers
    -> primitive renderers
```

Mapchunk and decoded-chunk callbacks may arrive in any valid order permitted
by the reader. The final map, counters, diagnostics, and ordered fingerprints
must be independent of completion order. Surface session state is mutable
only on the existing serialized/control-thread consumer path. Decode workers
must never mutate domain state.

PF-1.4 must not introduce `parallelStream()`, the common ForkJoinPool,
virtual-thread-per-chunk CPU decoding, or an unbounded queue. It must preserve
PF-1.2 bounded in-flight work, callback completion, lifecycle validation, and
backpressure. A PF-1.2 redesign requires separate escalation.

## 6. Canonical compact model

Names may change during implementation only when the change and reason are
recorded in the checkpoint review. The expected conceptual types are:

* `SurfaceTileLayout`: immutable geometry, bounds, tile dimensions, checked
  tile/cell indexing, active-circle membership, and coordinate derivation.
* `SurfaceTileAccumulator`: session-owned mutable state for one tile or a
  bounded tile collection. One tile object plus primitive arrays/bitplanes is
  acceptable; one object per world column is not.
* `SurfaceTile`: immutable compact tile snapshot/finalized view.
* `SurfaceMap`: immutable compact map plus counters/diagnostics and primitive
  tile iteration.
* `SurfaceCellState` or an equivalent primitive encoding: cell state bits and
  primitive payloads.

The canonical map must encode enough information to preserve current
behavior, including, as applicable:

* active/in-circle and world-bounds status;
* considered/resolved/unresolved state;
* absolute surface Y;
* block ID and liquid block ID;
* `SurfaceClass` (or a stable compact ordinal);
* mapchunk/fallback promotion state;
* liquid-unavailable diagnostics;
* empty/unresolved semantics and status needed by current counters.

Coordinates must be derived from tile index plus local index and layout, not
stored as coordinate records or objects per cell. Registry-derived block IDs
remain the source of identity; vanilla IDs must not be hard-coded.

`BlockInfo` references must not be stored once per cell. The compact map owns
IDs and compact classification state. Registry metadata is resolved at outer
compatibility, analysis, or presentation layers as needed.

Do not prematurely require signed `short` storage for absolute Y. If a
narrower representation is chosen, it must have a checked supported-range
contract, reject or switch representation for larger/modded worlds, and never
silently truncate. Impossible dimensions, products, indexes, and array sizes
must fail clearly before allocation.

## 7. Estimated compact byte model

The following is an arithmetic design estimate, not a heap measurement. A
possible R1024/R2048 payload using one `int[]` for absolute Y, one `int[]` for
block IDs, one `int[]` for liquid IDs, and compact bitplanes for status/class
is approximately:

| payload | R1024 (`3,294,097` cells) | R2048 (`13,176,729` cells) |
|---|---:|---:|
| absolute Y `int[]` | 12.57 MiB | 50.27 MiB |
| block ID `int[]` | 12.57 MiB | 50.27 MiB |
| liquid ID `int[]` | 12.57 MiB | 50.27 MiB |
| 8 one-bit planes | 3.14 MiB | 12.57 MiB |
| illustrative payload total | 40.85 MiB | 163.38 MiB |

These figures exclude array headers, tile metadata, geometry, registry,
decoder working set, diagnostics, analysis scratch state, and output image.
They are not measured savings and do not prescribe the final bit allocation.
If IDs or Y are packed differently, the implementation must publish its
checked arithmetic estimate and compare the same fields. It must not claim a
runtime percentage.

The required asymptotic model is:

```text
M = O(compactSurfaceOutput)
  + O(bounded PF-1.2 working set)
  + O(sparse discovery matches)
  + small O(tileCount / registry) metadata
```

The following are forbidden in the final production Surface path:

```text
O(decodedChunkCount * decodedChunkSize)
O(worldColumnCount * JavaObjectGraph)
```

## 8. Semantic compatibility contract

PF-1.4 is a data-layout and consumption refactor, not permission for a
surface semantic redesign. Characterization must freeze the following before
legacy paths are removed:

* `Math.round` center semantics and absolute world-coordinate behavior;
* negative-coordinate behavior, world-boundary clipping, and inclusive
  circle membership;
* foliage handling and `WATER` classification;
* missing-liquid-layer diagnostics and unknown block behavior;
* existing fallback selection semantics;
* mapchunk-missing and invalid-rain-height behavior;
* counters, diagnostic statuses, and deterministic ordering/fingerprints.

The current implementation promotes an entire mapchunk to fallback when its
planning/scanning decision requires it. The tile engine must preserve that
selection rule; it must not silently optimize to per-column fallback.

If characterization exposes a correctness bug or ambiguous legacy behavior,
record it as a named risk/question and stop that migration at the review gate.
Do not fix it opportunistically inside the performance refactor.

## 9. Fast-path contract (Checkpoint C)

The fast-path session must consume mapchunks incrementally and write candidate
rain heights directly into tile-local primitive state. It must derive a
compact unique set/list of requested server-chunk positions without retaining
per-column `RainHeightSurfaceTarget` objects.

Decoded chunks are accepted immediately. For one decoded server chunk, the
implementation should inspect only the relevant fixed-size tile cells or a
precomputed compact tile/chunk intersection, rather than doing a hash lookup
for every world column. It must record fallback requirements compactly and
write fast and fallback candidates into the same tile-backed state.

No canonical `List<SurfaceBlock>` is emitted. Finalization may sort compact
ordinals or iterate deterministic tile/local order, but the result must not
depend on decoded arrival order.

## 10. Fallback contract (Checkpoint D)

Checkpoint D eliminates `List<ParsedChunk> fallbackChunks` and the legacy
whole-operation scan requirement. Each fallback decoded chunk is consumed
into the same `SurfaceTileAccumulator` and becomes eligible for release as
soon as the serialized callback returns.

The initial algorithm must remain order-independent and preserve the
characterized legacy result by selecting the correct/highest qualifying
surface observation. Top-down vertical wave scheduling and early termination
are explicitly not mandatory PF-1.4 optimizations. The PF-1.2 reader is
completion-driven; forcing waves can add SQLite passes and I/O. Vertical
pruning is a future profiling-driven opportunity only.

## 11. Merger elimination

The production pipeline must not require `SurfaceFastPathMerger`'s
per-column `WorldColumn`, `HashMap`, fallback-column `HashSet`, or final full
result sort. Fast and fallback stages finalize the same tile-backed cells.

The legacy merger may remain as an independent test oracle with zero
production callers until differential and runtime review permits cleanup. It
must never be selected as a hidden production fallback after a streaming
failure or for large radii.

## 12. Canonical `SurfaceMap` API contract

`SurfaceMap` or an equivalent immutable result must not canonically store a
`List<SurfaceBlock>`, `SurfaceBlock[]` for every world column, boxed
coordinate collections, decoded chunks, or planning targets.

Production access is tile/primitive based: iterate active cells, inspect a
cell by layout coordinate, obtain primitive IDs/classes/Y, and derive world
coordinates. A single-cell compatibility accessor may materialize one
`SurfaceBlock` on demand.

A bulk `blocks()` adapter is allowed only temporarily for differential tests
or package-private compatibility. It must be explicitly non-canonical, absent
from renderer/analyzer production hot paths, and prohibited as the immediate
whole-map adapter before every legacy consumer. A compatibility result API
must expose diagnostics/counters independently of bulk block materialization.

## 13. Surface object discovery contract (Checkpoint F)

The planner must stop retaining mapchunks, per-column `HashSet<Integer>`,
boxed candidate-Y lists, and one `SurfaceObjectTarget` per column. Candidate
ranges around terrain/rain heights must be represented by compact primitive
anchor/range data. A candidate union must be iterated without duplicate
`(x,y,z)` checks and without temporary collections in the hot loop.

The scanner consumes `SelectiveChunkVisit` directly. It retains no whole-
operation decoded list, `Map<ChunkPosition,ParsedChunk>`, or String
coordinate key. Observation storage scales with actual matches using
primitive arrays or an equivalently compact representation. Final ordering is
deterministic and independent of completion order.

The semantics of `DECODED`, `PALETTE_REJECTED`, `MISSING`, and `FAILED` are
characterized and preserved. In particular, palette rejection is not silently
treated as a decoded empty chunk, and unavailable/not-observed/observed
counters retain their current meaning.

`DiscoverObservedSurfaceResourcesResult` should replace the retained
`SurfaceObjectPlan` with compact diagnostics/statistics such as planned
target/column count, requested chunk-position count, unavailable target
count, observed target count, and not-observed target count. The same review
applies to `InspectSurfaceObjectsResult` if it remains a public result.
`SurfaceDiscoveryCache` may retain its current capacity policy; each cached
result must nevertheless be compact. PF-1.7 weighted cache architecture is
out of scope.

## 14. Material analysis contract (Checkpoint E)

Material analysis must not recreate object-heavy memory after the surface map
is compact. The target design precomputes primitive block-ID membership for
the selected material, iterates compact cells directly, and avoids one
`SurfaceResourcePoint` per matching cell where possible.

The connected-deposit implementation must not use
`HashMap<Long,SurfaceResourcePoint>` for a large match set. It should use a
primitive occupancy bit/mask/index structure or another justified compact
flood-fill algorithm. Final `SurfaceResourceDeposit`/material result objects
are acceptable because they scale with deposits and selected output, not with
every world column. Deposit ordering remains deterministic.

## 15. Consumer migration plan

Each consumer migrates to primitive/tile access in the indicated checkpoint:

| consumer | migration contract |
|---|---|
| `RenderSurfaceResourceMapUseCase`, `RenderActualOreMapUseCase` | Own one streaming session; pass `SurfaceMap` to consumers; no fallback decoded list or merger. |
| `RenderSurfaceResourceMapResult`, `RenderActualOreMapResult`, `SurfaceScanResult` | Carry compact map and counters; bulk blocks are test-only/package-private compatibility if temporarily needed. |
| `MapRenderer`, semantic surface rendering, `SurfaceOverlayLegend` | Iterate cells/tiles directly, preserving pixel and legend fingerprints. |
| `SoilFertilityOverlayRenderer` | Iterate compact cells and resolve only the needed primitive classification/ID; never request a whole-map list. |
| `SurfaceMaterialMatch`, `SurfaceMaterialAnalyzer`, `SurfaceResourceAnalyzer` | Use precomputed ID membership and primitive occupancy/cluster state. |
| `ObservedSurfaceResourceCatalogBuilder` | Consume compact discovery observations/primitive IDs; domain catalog objects remain output-scale. |
| `MapCommand`, `ScanCommand`, `ResourceCommand`, `GeologyCommand` and `GeologyAnalyzer` | Query counters, deterministic primitive iteration, or bounded single-cell adapters; do not materialize a full list for reporting. |
| `ResultInspectorPane`, `CartographerDesktopApp` | Read compact result diagnostics/catalogs and preserve UI behavior without pinning plans or blocks. |
| Surface renderer/resource/discovery tests | Compare compact access directly; use a legacy bulk oracle only in differential tests. |

The exact method signatures are implementation work in E and must be
reviewed against every caller found by repository-wide search. No migration
is complete merely because a compatibility `blocks()` method compiles.

## 16. Hot-loop allocation rules

Final production hot loops must avoid allocation per block, candidate Y, or
world column. The implementation must investigate and remove from hot paths:

* eager `BlockInfo.unknown(...)` creation through `Map.getOrDefault(...)`;
* repeated string normalization/classification;
* boxed block IDs and Y values;
* coordinate records used only for lookup;
* String coordinate de-duplication keys.

A small immutable/precomputed primitive classification lookup is allowed only
if needed to remove a demonstrated Surface allocation and only within PF-1.4.
The full PF-1.6 `SaveSession` redesign is not pulled into this work.

## 17. Checkpoint plan and review boundaries

Each checkpoint is independently reviewable; B-G must not be collapsed into a
single implementation commit.

```text
A — this architecture contract and caller/semantic inventory
B — characterization oracle plus compact Surface tile/layout model
C — tile-based RainHeight fast-path planning/scanning
D — streaming fallback session and merger elimination
E — SurfaceMap production integration: renderer, diagnostics, soil/material
    analysis, and result API
F — compact streaming Surface Object Discovery
G — legacy production cleanup plus structural performance gates
H — integrated runtime validation, real-save parity, JFR, and performance
    evidence
```

By the end of G, static review must prove:

* zero production whole-operation `List<ParsedChunk>` retention in Surface
  paths;
* zero production `Map<ChunkPosition,ParsedChunk>` retention for discovery;
* zero canonical whole-surface `List<SurfaceBlock>`;
* zero retained Java object per world column in canonical Surface state;
* zero production per-column merge `HashMap`/`HashSet` representation;
* zero production per-column candidate collection;
* no String coordinate de-duplication hot path;
* no hidden legacy production fallback;
* PF-1.2 bounds/backpressure, completion order, and worker lifecycle
  preserved;
* decoded chunks consumed and released;
* deterministic completion-order-independent accumulation;
* read-only `.vcdbs` behavior unchanged;
* coordinate semantics, counters, and diagnostics characterized and
  preserved.

Legacy implementations may survive only as independent test oracles with
zero production callers until H/reviewer approval.

## 18. Checkpoint-B characterization and differential tests

Tests must be authored before production migration and must use explicit
lifecycle handshakes for concurrency. No sleep-based synchronization,
thread-state polling, or weakened assertions are permitted.

### Geometry and layout

Cover exact circle boundary, world-edge clipping, supported negative
coordinates, fractional centers, current `Math.round` center semantics,
tile/local conversion, checked overflow, impossible allocation sizes, and
domain-correct `MapChunk.SIZE` versus `ChunkCoordinate.SIZE_BLOCKS` usage.

### Rain-height fast path

Cover valid and invalid rain heights, missing rain-height maps, duplicate
mapchunk input, air, foliage, water/liquid, missing liquid layer, unknown
block ID, and missing requested server chunk.

### Fallback

Cover one chunk, multiple vertical chunks, highest qualifying result, empty
column, high-to-low and low-to-high arrivals, deterministic shuffled orders,
absent chunk rows, liquid-unavailable count, foliage-ignore behavior, and
whole-mapchunk fallback promotion.

### Merge equivalence

For generated fixtures compare
`RainHeightSurfacePlanner + RainHeightSurfaceScanner + fallback +
SurfaceFastPathMerger` against the new tile output for coordinates, Y, block
ID/code, liquid ID/code, `SurfaceClass`, counters, diagnostics, and ordering/
fingerprint where applicable.

### Object discovery

Cover terrain-only, rain-only, overlapping ranges, no duplicate candidate Y,
multiple wanted observations in one column, `DECODED`, `PALETTE_REJECTED`,
`MISSING`, `FAILED`, missing chunk table/zero visits where applicable,
arbitrary completion order, deterministic output, and registry/modded
candidate behavior. Preserve the current unavailable/not-observed semantics.

### Renderer/analyzer parity

Before adapters are retired compare semantic surface image pixel fingerprints,
soil-fertility results/pixels, material analysis output, unknown/water counts,
legend class set/order, and discovery catalog semantics. Any mismatch is a
correctness failure, not an acceptable performance difference.

## 19. Runtime validation plan for H — not run in A

H must use the existing Performance Foundation methodology and the same save,
workload, and exact baseline/candidate commit SHAs. It must require:

* full Gradle test suite;
* Surface correctness/result fingerprint parity;
* rendered pixel fingerprint parity where applicable;
* real-save validation;
* save integrity hash/size validation and no suspicious WAL/SHM files;
* canonical R1024 BEFORE/AFTER Surface workload;
* R2048 scalability workload where supported;
* peak heap, allocated bytes, wall time, GC behavior, and useful JFR
  allocation/CPU inspection;
* diagnostics equivalence and explicit workload metadata.

No runtime benchmark may PASS with a semantic fingerprint mismatch. If the
environment is unavailable, evidence is `NOT RUN` / `PENDING MANUAL
VALIDATION`, never fabricated PASS. Checkpoint A records all runtime
validation as NOT RUN.

## 20. Explicit non-goals and forbidden shortcuts

PF-1.4 must not redesign SQLite storage, introduce SaveSession/PF-1.6,
implement PF-1.7 cache architecture, redesign PF-1.2 concurrency without
escalation, use virtual threads as a CPU shortcut, use `parallelStream()` or
the common ForkJoinPool, add JNI/Unsafe/GPU/OpenCL/off-heap machinery, or add
a primitive-collections dependency without a demonstrated need.

It must not tune GC flags instead of fixing data layout, introduce a second
full Surface representation in production, weaken diagnostics, silently
change circle/coordinate/fallback semantics, rewrite tests to accept new
behavior without proving equivalence, write to `.vcdbs`, or claim performance
improvement without measurements.

## 21. Risks and open questions

Implementation must validate the largest supported/modded world dimensions,
absolute-Y range, tile/cell ordinal capacity, and checked array products. The
final bit allocation must be reviewed against registry IDs and diagnostics.
The exact fast-path tile/server-chunk intersection strategy and primitive
material flood fill require differential tests and may require profiling.
The current `MapChunk.SIZE == ChunkCoordinate.SIZE_BLOCKS` equality is an
implementation fact, not permission to merge their domains. The legacy
fallback promotion rule and missing-liquid semantics are particularly
important characterization risks.

No question in this list authorizes a silent semantic correction. Any
ambiguous result blocks the affected checkpoint until the controller and
reviewer decide the intended behavior.

## 22. Definition of PF-1.4 completion

PF-1.4 is not DONE at A, B, C, D, E, F, or G. Completion requires all planned
tests and differential/permutation tests, structural gates, production
consumer migration, legacy-oracle review, R1024 parity, R2048 correctness and
bounded-memory evidence where supported, diagnostics/coordinate parity,
real-save validation, save-safety evidence, and H reviewer sign-off.

The reviewer must inspect the exact branch HEAD and evidence. Static contract
review alone cannot establish runtime behavior, PNG correctness, bounded heap,
performance, or save safety.

**Tests and real-save validation were not run; they are left to the reviewer.**
