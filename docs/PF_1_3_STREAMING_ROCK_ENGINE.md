# PF-1.3 Streaming ROCK Engine — Architecture Contract and Implementation Plan

**Status:** Implementation checkpoints complete through G. PF-1.3 runtime
validation is pending; PF-1.3 is not validated or DONE.

## 1. Status and scope

PF-1.3 removes the ROCK operation's two retention points: input retention in
the `List<ParsedChunk>` assembled by `RenderRockMapUseCase`, and
output/object retention in the `List<RockColumnSample>` retained by `RockMap`.
It defines the contracts for a
implementation checkpoints; it does not change Gradle, SQLite access, decoding,
parser behavior, save access, or PF-1.2 lifecycle behavior.

The target is a streaming domain consumer:

```text
SQLite / VcdbsReader
    -> PF-1.2 bounded completion-driven decode
    -> SelectiveChunkVisit
    -> serialized ROCK session.accept(...)
    -> compact immutable RockMap
    -> primitive renderer
```

A decoded chunk is consumed and then released. The only operation-sized state
is compact ROCK output and compact coverage state. This document does not mark
PF-1.3, PF-1.2, or any workload as validated or DONE.

## 2. Relationship to PF-1.1 and PF-1.2

PF-1.1 owns reusable decoder workspaces, published decoded-layer ownership, and
bounded decoder-local allocation. PF-1.2 owns fixed platform decode workers,
bounded in-flight work, completion-driven consumption, failure/lifecycle
cleanup, and control-thread downstream callbacks. PF-1.2 deliberately does not
redesign ROCK retention.

The PF-1.3 consumer must therefore accept completion order. It must not add a
submission-order queue, mutate analyzer state from decode workers, change the
PF-1.2 worker count/lifecycle, use `parallelStream()` or
`ForkJoinPool.commonPool()`, or use virtual-thread-per-chunk CPU decoding.
PF-1.2's final runtime validation is still pending; PF-1.3 acceptance must not
silently treat PF-1.2's static review as runtime evidence.

## 3. Current architecture and problem statement

Before Checkpoints E/F, `RenderRockMapUseCase` retained decoded visits and
invoked a legacy scanner; `RockMapRenderer` walked object samples and built a
boxed rock-count map. The implemented path now feeds visits directly to
`RockStreamingSession`, finalizes a compact `RockMap`, and renders primitive
indexed cells. The legacy scanners and `RockChunkCoverage` remain only as
independent test-oracle support until post-validation cleanup.

This is not the desired memory model. Decoded input scales with the number and
size of chunks, while the object result scales with approximately `PI*r^2`
cells. The replacement must have memory of `O(outputState + boundedWorkingSet)`.

## 4. Repository caller and consumer inventory

The following inventory was established by searching `src/main` and
`src/test`.

| Symbol/API | Current callers/consumers | Migration requirement |
|---|---|---|
| `RenderRockMapUseCase` | `RockCommand`, `CommandRouter`, `CartographerDesktopApp`, `MacroBaselineRunner` | Create one session before the reader call; feed visits directly; finish before rendering. |
| `RenderRockMapResult` | `RockCommand`, `ResultInspectorPane`, macro runner and application tests | Preserve result-level counts/diagnostics/catalog semantics; map becomes compact. |
| `RockMap` | `RockMapRenderer`, `AnalyzeProspectingAreaUseCase`, scanner tests, renderer tests | Replace list storage with immutable primitive cells and geometry; expose scalar access/iteration without bulk materialization. |
| `RockMap.columns()` | ROCK characterization/oracle tests and compatibility fixtures | Production use was removed in E/F. It is now package-private and retained only as an on-demand test-oracle adapter; it is not canonical storage. |
| `RockColumnSample` | Both scanners, renderer, prospecting geology aggregation, scanner/renderer tests | Retain as a single-cell compatibility/value facade if useful; never as production map storage. |
| `RockColumnScanner` | Differential/characterization tests only | Retain unchanged as the independent UPPER semantic oracle through runtime validation; remove only in a later cleanup after reviewer approval. |
| `RockAtYScanner` | Differential/characterization tests only | Retain unchanged as the independent AT_Y semantic oracle through runtime validation; remove only in a later cleanup after reviewer approval. |
| `RockChunkCoverage` | Legacy scanners and characterization tests only | Retain unchanged for the legacy oracle; primitive streaming coverage remains the production path. |
| `RockMapRenderer` | CLI, desktop UI, prospecting wiring, renderer tests | Decode packed cells directly and use finalization counts/ordinals. |
| `RockCatalog` | Use case, CLI, both scanners, catalog tests | Keep domain catalog; add deterministic ordinal lookup for the packed representation. |
| selective coverage reader APIs | `RenderRockMapUseCase`, reader tests | Keep `SelectiveChunkVisit` status and diagnostics contract; session consumes visits one at a time. |

The cross-section `columns()` usages found in geology CLI/renderer concern a
different cross-section model, not `RockMap`, and are outside PF-1.3.

## 5. Semantic compatibility contract

The result for every geometry cell has exactly one state:

```text
OBSERVED    highest provably valid natural rock in the requested range is known
NO_ROCK     the entire requested range is covered and contains no qualifying rock
UNAVAILABLE coverage is incomplete or failed, so a correct answer is unknown
```

Only registry-resolved natural rock IDs from `RockCatalog` qualify. Modded
`rock-*` blocks remain valid; ore blocks and non-rock blocks remain excluded.
The catalog must continue to derive from the save registry, never hard-coded
block IDs. Absolute world coordinates and the current `floor(center.x/z)`
behavior remain normative, including negative and fractional centers.

`NO_ROCK` is never a default for an absent decoded chunk. It is permitted only
after coverage proves every requested vertical chunk span available and the
decoded/palette evidence proves no qualifying block. `UNAVAILABLE` is the
conservative result for unknown information.

## 6. Thread ownership and completion-order contract

`VcdbsReader` and PF-1.2 invoke the consumer on the producer/control thread.
The ROCK session is owned by that thread. `accept` is not thread-safe by
design; decode workers only create outcomes. The control thread updates
coverage, candidates, diagnostics-facing counters, and progress-visible state.

The session's result is a fold over visits, not an ordered scan. Its output
must be identical for every permutation of the same logical visits. It must
not depend on SQLite row order, submission order, completion order, or vertical
top-to-bottom delivery. The packed geometry is finalized in a deterministic
row-major order (`z` ascending, then `x` ascending), and catalog ordinals are
deterministic.

Duplicate visits are not part of the normal table-present reader contract: the
reader deduplicates requested positions and each unique requested position
reaches one terminal visit. This is not universal: when the chunk table is
missing, `VcdbsReader.readSelectiveChunkCoverage(...)` completes after
recording the missing table and emits no per-position visits. The future
session must therefore track terminal visitation independently of whether the
visit established availability. It must reject a duplicate position
explicitly; silently counting duplicates is prohibited.

## 7. Streaming ROCK session lifecycle

Conceptually the use case becomes:

```java
RockStreamingSession session = RockStreamingSession.open(request, catalog, geometry);
reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(..., session::accept, progress);
RockMap map = session.finish();
```

`open` validates radius, center, Y range, catalog, and checked geometry sizes.
`accept` validates position/status and consumes the chunk immediately. For a
decoded visit it scans only the relevant local columns/Y values and stores no
chunk reference after return. For palette rejection it updates only coverage
and diagnostics-equivalent counters. `finish` closes the session exactly once,
derives final cell states/counts, freezes primitive arrays and the ordinal
table, and returns an immutable `RockMap`.

Malformed use is explicit: `accept` after `finish`, null visits, invalid
coordinates, duplicate visits, and packed-capacity overflow fail fast. The
use case owns sequencing and must invoke `finish()` only after the synchronous
`VcdbsReader` operation has returned successfully. The session does not infer
reader completion from visit count; this matters when a missing chunk table
produces zero visits. A failed reader operation must preserve the existing
failure/diagnostic semantics and must not manufacture `NO_ROCK`.

## 8. UPPER aggregation algorithm

For each output world column `(x,z)`, maintain a candidate `(rockOrdinal, y)`
initially absent. For every decoded chunk covering the requested range, inspect
the relevant local `(x,z)` cells and update the candidate with:

```text
candidate := max-by-Y(candidate, every qualifying natural-rock observation)
```

The update is associative, commutative, and idempotent when equal-Y duplicates
resolve to the same catalog observation. Therefore arbitrary visit order gives
the same candidate. At finish, inspect the primitive coverage bits for all
requested vertical chunks above the candidate's Y. If any such span is
unavailable, the result is `UNAVAILABLE`; otherwise the candidate is
`OBSERVED`. If no candidate exists, the result is `NO_ROCK` only when every
requested vertical span is available; otherwise it is `UNAVAILABLE`.

This invariant handles gaps precisely. A lower rock below an unavailable upper
span cannot prove the uppermost rock. A qualifying upper rock can remain
`OBSERVED` despite an unavailable lower span because lower unknown data cannot
change the uppermost identity. A palette-rejected span is available and
therefore cannot create a gap.

## 9. AT_Y aggregation algorithm

AT_Y has a single requested absolute Y and therefore one required vertical
chunk row (`floorDiv(worldY, S)`) for each horizontal chunk column, where
`S = ChunkCoordinate.SIZE_BLOCKS`. A decoded
visit reads exactly the target local Y and updates the cell to observed when
the block resolves to a catalog rock, otherwise to the no-rock candidate.
For valid unique visits, the operation is deterministic regardless of visit
order; a duplicate terminal visit is rejected by the shared session contract.

At finish, an available palette-rejected chunk proves `NO_ROCK` for its cells.
A missing or failed required chunk produces `UNAVAILABLE`, even when no decoded
chunk was retained. AT_Y must not build a `Map<ChunkCoordinate, ParsedChunk>` or
defer work until all visits have arrived.

## 10. Coverage semantics and representation

`SelectiveChunkVisitStatus` has four meanings verified against the current
reader and tests:

| Status | Coverage meaning | ROCK consequence |
|---|---|---|
| `DECODED` | requested chunk exists and was decoded | available; inspect blocks |
| `PALETTE_REJECTED` | payload/palette was read and contains no wanted rock ID | available; known no qualifying rock |
| `MISSING` | requested position had no row | unavailable |
| `FAILED` | row/palette/full decode failed | unavailable; retain diagnostics |

The future state has two primitive vertical bitsets per horizontal chunk
column:

```text
terminalSeenWords: whether any terminal visit has been accepted
availableWords:    whether the accepted visit was DECODED or PALETTE_REJECTED
```

`accept` computes the primitive position index and checks `terminalSeenWords`
before mutating either candidate or coverage state. If the bit is already set,
it fails explicitly for a duplicate, including `DECODED` -> `DECODED`,
`PALETTE_REJECTED` -> `MISSING`, and every other cross-status pair. For the
first terminal visit, it sets `terminalSeenWords`; it sets the corresponding
`availableWords` bit only for `DECODED` or `PALETTE_REJECTED`. `MISSING` and
`FAILED` therefore remain seen-but-unavailable. Separate bounded counters and
existing diagnostics preserve their status distinction. A missing chunk table
is not converted into available coverage, and its zero-visit result leaves all
requested positions unseen.

The requested horizontal chunk bounds are:

```text
S = ChunkCoordinate.SIZE_BLOCKS

chunkMinX = floorDiv(centerBlockX - radius, S)
chunkMaxX = floorDiv(centerBlockX + radius, S)
chunkMinZ = floorDiv(centerBlockZ - radius, S)
chunkMaxZ = floorDiv(centerBlockZ + radius, S)
chunkMinY = floorDiv(minY, S)
chunkMaxY = floorDiv(maxYExclusive - 1, S)
verticalChunkCount = chunkMaxY - chunkMinY + 1
```

Use checked `long` intermediates for bounds and differences. Map a horizontal
chunk `(cx,cz)` to `((long)cz - chunkMinZ) * horizontalWidth + cx - chunkMinX`
after checking the product against the primitive-array limit. Map vertical
chunk `cy` to `cy - chunkMinY`. The exact
`verticalChunkCount = chunkMaxY - chunkMinY + 1` is derived from the requested
Y endpoints, not from a rounded block-height approximation. Use
`wordsPerHorizontalColumn = ceil(verticalChunkCount / 64)`, not a single-long
assumption. Partial bottom/top chunks set only the requested span's coverage
meaning; the finalizer clips tests to `[minY,maxYExclusive)`.

Every server-chunk calculation, including AT_Y's `chunkY = floorDiv(worldY,S)`
and `localY = floorMod(worldY,S)`, must use `ChunkCoordinate.SIZE_BLOCKS` in
production. The architecture must not duplicate a numeric server-chunk size
literal or conflate server chunks with mapchunks. For the current 256-block
world and `S=32`, the full vertical range spans at most 8 server-chunk rows.

These two primitive planes are compact, negative-coordinate safe, and do not
need `HashSet<ChunkCoordinate>`, `HashSet<Long>`, or
`Map<ColumnCoordinate,List<ChunkSpan>>` in the hot path. Contiguous trusted
coverage is derived by checking the required vertical availability interval
and terminal-seen interval, not by assuming adjacent callback visits.

For a normal table-present successful operation, every requested position is
seen exactly once. For a missing-table completion or any otherwise incomplete
input, an unseen requested position is unknown and finalizes as
`UNAVAILABLE`, never `NO_ROCK`.

## 11. Vertical-gap correctness rules

For UPPER, let `U(c)` be the set of requested Y values above the candidate
rock in column `c`. The candidate is valid exactly when every chunk span
intersecting `U(c)` has the available bit. If no candidate exists, every
requested span must be available. This is the finalization invariant and is
independent of scan direction.

A decoded chunk itself is available even if it contains no qualifying rock.
Palette rejection is equivalent to available/no qualifying rock for the
requested block set. Missing and failed spans are unknown, never empty.

## 12. Compact circle geometry

The shared geometry should enumerate only lattice cells in the inclusive
integer circle centered on `floor(center.x), floor(center.z)`:

```text
for z = centerZ-radius .. centerZ+radius:
    dz = z-centerZ
    halfWidth = floorSqrt(radius*radius - dz*dz)
    rowStartX = centerX-halfWidth
    rowLength = 2*halfWidth+1
    rowOffset[z] = sum(previous rowLength)
```

`halfWidth` must be an exact integer square root (or a checked floating-point
estimate corrected with integer comparisons). `cellIndex` is
`rowOffset[row] + (x-rowStartX[row])`. Rows and cells are stored in ascending
Z/X order. No per-cell coordinate object or hash lookup is permitted in the
analysis/render hot loops.

All radius products, `center +/- radius`, row lengths, offsets, and total cell
count use checked `long` arithmetic before conversion to array indices. A
negative world coordinate is just an integer coordinate; floor behavior is
used for the center and `floorDiv`/`floorMod` for chunk/local mapping.

The exact output-cell count is
`N(r) = sum(dz=-r..r, 2*floorSqrt(r*r-dz*dz)+1)`. The geometry is shared by
analysis and rendering so neither stage reconstructs a different square/circle
mapping. The current workload test records `N(1024)=3,294,097`; the
area estimates for R2048 and R4096 are approximately 13,176,729 and
52,706,921 respectively (the exact formula remains the authority).

## 13. Packed cell representation

Each cell retains only the semantic information needed by current ROCK output:
state, and for `OBSERVED`, rock ordinal and absolute Y. Coordinates come from
shared geometry and are not stored per cell.

The proposed packed logical fields are:

```text
state:       2 bits (0 UNAVAILABLE, 1 NO_ROCK, 2 OBSERVED, 3 reserved/invalid)
rockOrdinal: bRock bits (0 for non-observed; observed ordinals 1..K)
yOffset:     bY bits (0..rangeHeight-1, relative to minY)
```

`bRock = ceilLog2(K+1)` and `bY = ceilLog2(maxYExclusive-minY)`; zero-width
fields are represented as zero bits only where the corresponding value is
provably constant. The implementation must validate catalog size and Y range
before choosing a layout. A deterministic field-layout version belongs in the
immutable map metadata/fingerprint contract.

Use `int[]` when `2+bRock+bY <= 32`, and `long[]` when it is greater than 32
and at most 64. `int` is sufficient for the normal current world only after
the actual catalog cardinality and requested Y height pass the calculation;
there is no “Y is eight bits” assumption. If the layout exceeds 64 bits, the
initial implementation must fail fast with an explicit unsupported-capacity
error (or a separately reviewed multiword layout may be introduced). It must
never silently truncate, wrap, or reinterpret an ordinal/Y value. Boundary
tests must exercise exactly 31/32/33 and 63/64/65-bit totals.

The `UNAVAILABLE`/`NO_ROCK` encodings have no rock/Y payload. `OBSERVED` must
have a nonzero valid ordinal and an offset inside the requested range; invalid
reserved values are rejected on construction and decode.

## 14. Rock catalog and ordinal representation

`RockCatalog` remains the source of truth for block ID to `RockIdentity`.
During session creation, create a frozen ordinal table explicitly by iterating
recognized entries in ascending block-ID order. This is a new packed-map
construction rule; it must not depend on the iteration order of
`Map.copyOf(...)`. `RockCatalog.from(...)` currently builds recognized entries
in a `TreeMap`, then copies that map and its ordered views, but the ordinal
contract is the explicit ascending block-ID construction rather than any
unspecified copied-map order. Ordinal zero is reserved for non-observed states.
The table stores the identities needed for legend/palette/UI access; the cell
store stores only ordinals.

The catalog/ordinal builder must validate that every recognized block ID maps
to exactly one stable ordinal and that the reverse table is stable across runs
for the same registry.
Counts should be accumulated in primitive `long[] countsByOrdinal` during
finalization or one controlled cell pass, then converted to the existing
legend entries in deterministic count-descending/code order. No per-cell
`RockIdentity` reference is retained.

## 15. Target `RockMap` responsibility/API

`RockMap` becomes an immutable analytical result containing center, radius,
requested Y range/mode metadata, shared compact geometry, packed cells, the
immutable ordinal table, and aggregate state/rock counts. It owns no decoded
chunks, boxed column list, optional wrappers, or per-cell coordinates.

The target API should provide primitive-friendly operations such as
`cellAt(worldX,worldZ)`, `stateAt`, `rockOrdinalAt`, `rockYAt`, row-span access,
and aggregate counts. `sampleAt(worldX,worldZ)` may materialize one
`RockColumnSample` on demand for tests/UI compatibility. A bulk `columns()`
adapter, if temporarily retained, is package-private compatibility/test-only and
must not be called by the production renderer or production prospecting
analysis after their assigned migrations. It must not be a silent fallback to
the old unbounded implementation. In particular, Checkpoint E migrates
`AnalyzeProspectingAreaUseCase.geology(RockMap)` to consume compact map
aggregates: observed/no-rock/unavailable counts and the observed rock identity
set or ordinal counts exposed by `RockMap`. It must not materialize or iterate
`RockColumnSample` objects merely to reconstruct those values. `columns()`
was restricted to test-oracle visibility after those production migrations
completed; it remains only until the independent legacy oracle is retired after
runtime review.

`RenderRockMapResult` can continue to carry the map, render result, catalog,
stats, diagnostics, center, and Y bounds while migration is staged. The
use-case and renderer must not retain both old and new representations.

## 16. Renderer migration design

`RockMapRenderer` should iterate shared row spans, decode each packed cell, map
state/ordinal to ARGB, and write the corresponding pixel. It must not create a
`RockColumnSample` for every cell or rebuild rock counts through a boxed map.
The renderer consumes finalized aggregate counts/ordinal data for the legend.

The current output is a square `TYPE_INT_ARGB` image and intentionally renders
circle cells within square dimensions. The renderer migration must preserve
pixel coordinates, state colors, palette identity, unavailable checkerboard,
legend ordering, and fractional/negative-center behavior. Access to the
standard JDK `DataBufferInt` backing array may be evaluated for reducing
`setRGB` overhead, but this is not mandatory until profiling shows value. JNI,
Unsafe, GPU code, and new dependencies are out of scope.

## 17. Memory complexity model

Let `N` be exact circle cells, `H` horizontal chunk columns in the requested
box, `V` requested vertical chunk count, `W=ceil(V/64)`, and `F` PF-1.2
max-in-flight. The target memory is approximately:

```text
packed cells:       N * 4 or N * 8 bytes
row geometry:       O(radius) primitive rows/offsets
coverage:           H * W * 16 bytes for terminal-seen and available planes,
                    plus bounded status counters
catalog/counts:     O(K)
working set:        O(workerCount * reusable workspace + F * bounded outcome)
```

There is no `C * decodedChunkSize` term and no `N` Java-object term. For
illustration, packed-cell payload alone is:

| Workload | Cell estimate | `int[]` payload | `long[]` payload |
|---|---:|---:|---:|
| R1024 | 3,294,097 exact | 12.57 MiB | 25.13 MiB |
| R2048 | 13,176,729 exact | ~50.27 MiB | ~100.53 MiB |
| R4096 | 52,706,921 exact | ~201.06 MiB | ~402.12 MiB |

These are arithmetic estimates, not measurements and exclude JVM array/object
headers, geometry, coverage, decoder working set, and the output image. With
the current 256-block world height and `S=32`, `V` is up to 8 server chunks
and `W=1`; for a horizontal circle the R1024/R2048/R4096 server-chunk boxes
are approximately 65x65, 129x129, and 257x257 columns. The two coverage
planes therefore cost approximately 0.064, 0.254, and 1.006 MiB
respectively (`H * W * 16` bytes). Wider or modded vertical ranges use
additional words linearly in `W`. These are server-chunk
figures; mapchunk geometry is a separate format and is not substituted here.

The full square ARGB raster is a separate cost: approximately 16.02 MiB,
64.03 MiB, and 256.06 MiB for R1024/R2048/R4096 before image overhead. A
R4096 raster failure must not be reported as ROCK analysis failure without
separating the analysis-only probe from rendering allocation.

## 18. Integer and overflow safety

Validate radius positive and all coordinate/range arithmetic in `long` with
`Math.addExact`, `multiplyExact`, and checked conversions. Exact geometry
cell count, row offsets, coverage products, packed-array lengths, image
dimensions, ordinal capacity, and Y offsets must be bounded before allocation.
An unsupported size is a clear `IllegalArgumentException`/domain failure with
diagnostics; partial/truncated maps are forbidden.

`floorDiv` and `floorMod` are mandatory for negative chunk/local coordinates.
All horizontal local coordinates use `floorMod(worldCoordinate, S)` with
`S = ChunkCoordinate.SIZE_BLOCKS`, just as AT_Y uses `localY`; no numeric
server-chunk-size literal is permitted. World-coordinate bounds must remain
consistent with `WorldMetadata` when
available, but the generic primitive layout must not assume positive values.

## 19. Test strategy

Before removing legacy code, add deterministic unit tests for: a single upper
rock; rocks across vertical chunks; no-rock; unavailable; missing upper,
middle, and lower chunks; a qualifying rock above/below missing data;
palette-rejected and failed chunks; partial Y ranges; negative world/chunk
coordinates; fractional centers; modded rocks; ore exclusion; exact geometry
ordering; and AT_Y semantics.

Concurrency tests must use explicit lifecycle handshakes/latches, not sleeps or
thread-state polling. Session tests must also cover malformed lifecycle use,
duplicate `DECODED`, duplicate `PALETTE_REJECTED`, duplicate `MISSING`,
duplicate `FAILED`, cross-status duplicates such as `FAILED -> DECODED`, null/
invalid visits, and diagnostic preservation. Reader/fixture tests must cover a
missing chunk table with zero-visit completion producing `UNAVAILABLE`, and an
unseen requested position at finalization producing `UNAVAILABLE` rather than
`NO_ROCK`. `accept` after `finish` must be rejected.

## 20. Differential/characterization oracle

PF-1.3-B should freeze characterization expectations from
`RockColumnScannerTest`, `RockAtYScannerTest`, `RockMapRendererTest`, reader
coverage tests, and small generated worlds. For the transition period, a
test-only legacy oracle should run the old scanner and compare state, rock
identity, Y, counts, geometry, diagnostics, and rendered pixels with the new
streaming result.

The oracle may be deleted only after all required edge cases, completion-order
permutations, and canonical small-world fingerprints pass on the new compact
representation. It must never be reachable by production fallback.

## 21. Completion-order permutation tests

For each logical world fixture, create the same set of `SelectiveChunkVisit`
values and feed deterministic permutations (including fixed random seeds) to
the session. Compare the complete compact-map fingerprint, aggregate counts,
diagnostic counters, and image fingerprint where rendering applies. Include
permutations where lower chunks arrive first, failures arrive first, and
palette rejections arrive after decoded chunks. A mismatch is a correctness
failure, not a performance variance.

## 22. Overflow and boundary tests

Require tests for radius square/row geometry overflow; center/radius world
bounds; row-offset and cell-index limits; `int`/`long` packing boundaries;
catalog ordinal capacity; Y-range capacity; multiword two-plane coverage;
partial top and bottom chunks; negative floor division; duplicate visits for
all four statuses plus cross-status duplicates; missing-table zero-visit
completion; unseen-position finalization; invalid reserved packed values; and
finish/accept lifecycle violations.

## 23. Legacy cleanup plan

After B through F are reviewed, `RockColumnScanner` and `RockAtYScanner` remain
in production source solely as independent semantic oracles until H runtime
review is complete; removing or moving them earlier would weaken differential
evidence. `RockChunkCoverage` follows the same rule and remains only for the
legacy scanner oracle. All three have zero production callers.
`RockColumnSample` may survive as a single-cell compatibility facade, but its
list-based production role must disappear. `RockMap.columns()` is now a
package-private test-oracle adapter with zero production callers; it is not
permission to retain both representations. No legacy scanner may be selected
as a hidden fallback for large radii or errors.

## 24. Remaining PF-1.3 checkpoint plan

```text
A — this Architecture Contract (documentation only)
B — semantic characterization/differential oracle
C — compact immutable RockMap and shared circle geometry
D — streaming UPPER session and primitive coverage
E — streaming AT_Y, ROCK use-case integration, and production prospecting aggregate migration
F — primitive renderer and aggregate legend/count migration
G — legacy cleanup after reviewer gates
H — runtime validation, profiling, and performance evidence
```

No H runtime validation or performance evidence is included in this checkpoint.

## 25. Benchmark and validation matrix

Reviewer-controlled validation must use the existing workload/fingerprint
infrastructure and a quiescent real save. R256 is sanity/development evidence
only. R1024 is the mandatory canonical BEFORE/AFTER workload and requires
semantic fingerprint, applicable image/pixel fingerprint, wall-clock, peak
heap, allocated bytes, diagnostics equivalence, and save-safety evidence.
R2048 is the primary scalability target and requires successful correctness,
bounded-memory evidence, peak heap/allocation evidence, and no pathological
GC. R4096 remains a stretch workload.

If needed, add a reviewer-only analysis probe that stops after
`read -> decode -> streaming ROCK result`, before `BufferedImage` allocation.
Report ROCK-analysis memory separately from full-raster/output memory. No
benchmark, runtime result, or performance improvement is claimed here.

## 26. JFR and profiling plan

PF-1.3-H should use JFR when evidence requires it to inspect allocation hot
spots, CPU samples, GC pressure, thread behavior, and relevant file I/O. It
must compare the same workload/save and preserve correctness fingerprints.
No custom JFR events are added in Checkpoint A; instrumentation must not enter
per-block hot loops without a later decision.

## 27. Save-safety gate

All `.vcdbs` reads remain strictly read-only. Final validation must apply the
existing save-safety gate: compare required save SHA/size, verify no unintended
save mutation, and check that no suspicious new WAL/SHM sidecars appear. A
performance result cannot pass if save integrity fails. This checkpoint did
not open a save or run that gate.

## 28. Explicit non-goals and prohibitions

PF-1.3-A does not implement ROCK, alter PF-1.2, modify SQLite/decoders/parsers,
redesign the prospecting engine, change surface/cache/SaveSession behavior, add a dependency,
introduce native/Unsafe/GPU code, claim R4096 support, or mark any milestone
validated.

The future implementation explicitly prohibits whole-operation
`List<ParsedChunk>` retention, equivalent hidden decoded-input collections,
`List<RockColumnSample>` production storage, per-column boxed maps/sets without
evidence, order dependence, `parallelStream()`, common-pool scheduling,
worker-thread analyzer mutation, silent overflow, status weakening,
`PALETTE_REJECTED` as missing, `MISSING`/`FAILED` as `NO_ROCK`, weakened
diagnostics/coordinates, save writes, and unsupported performance claims.

## 29. Known risks and open questions

The exact packed layout needs implementation-time validation against the largest
real registry and supported Y ranges. If the 64-bit layout limit is reached, a
multiword representation needs a separate review. The cost of exact integer
square roots, direct `DataBufferInt` access, and any renderer pass should be
profiled rather than assumed. The session must enforce its explicit duplicate
terminal-visit rejection using the terminal-seen plane. The current reader's missing-table path
and diagnostics integration need differential tests to ensure an unavailable
coverage result cannot be mistaken for all-empty coverage. Finally, the
full-raster R4096 output may remain a separate memory bottleneck even after
ROCK analysis becomes compact.

## 30. Definition of DONE and reviewer gates

PF-1.3 is not DONE at Checkpoint G. Later completion requires: all planned
tests and differential/permutation tests green; no production whole-operation
decoded or object-column retention; UPPER and AT_Y streaming integration;
primitive renderer migration; legacy cleanup review; R1024 semantic/image
fingerprint match; R2048 correctness and bounded-memory evidence; honest R4096
stretch or analysis-only evidence; diagnostics equivalence; JFR where useful;
and save-safety PASS.

The reviewer must independently inspect the exact branch HEAD and runtime
evidence. Static documentation and code inspection alone cannot establish
performance, bounded heap measurements, real-save behavior, PNG correctness,
or save safety.

**Tests and real-save validation were not run; they are left to the reviewer.**
