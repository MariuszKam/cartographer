# PF-1.3 Streaming ROCK Engine — Checkpoint A Architecture Contract

**Status:** Architecture contract only. PF-1.3 is not implemented or
validated. This checkpoint intentionally changes documentation only.

## 1. Status and scope

PF-1.3 removes the ROCK operation's two input-sized retention points: the
`List<ParsedChunk>` assembled by `RenderRockMapUseCase` and the
`List<RockColumnSample>` retained by `RockMap`. It defines the contracts for a
future implementation; it does not change Java production code, tests, Gradle,
SQLite access, decoding, parser behavior, save access, or PF-1.2 lifecycle
behavior.

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

The current `RenderRockMapUseCase` plans positions, calls
`VcdbsReader.forEachChunkByPositionMatchingBlockIdsWithCoverage`, stores every
decoded visit in `List<ParsedChunk>`, stores available positions separately,
constructs `RockChunkCoverage`, and only then invokes a scanner. `RockColumnScanner`
indexes chunks and coverage in boxed maps and creates one `RockColumnSample` per
cell. `RockAtYScanner` indexes decoded chunks in a boxed map and creates the same
object list. `RockMap` copies and retains that list. `RockMapRenderer` walks the
objects, builds a boxed rock-count map, and writes a square `BufferedImage`.

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
| `RockMap.columns()` | `RockMapRenderer`, `AnalyzeProspectingAreaUseCase`, `RockColumnScannerTest`, `RockAtYScannerTest`, `RockMapRendererTest` | Remove from production hot paths. Tests/UI compatibility may use `sampleAt`; any bulk adapter must be explicitly test-only or bounded and never used by production. |
| `RockColumnSample` | Both scanners, renderer, prospecting geology aggregation, scanner/renderer tests | Retain as a single-cell compatibility/value facade if useful; never as production map storage. |
| `RockColumnScanner` | Only `RenderRockMapUseCase` and its tests | Replace with streaming UPPER session; removable after differential tests and integration migration. |
| `RockAtYScanner` | Only `RenderRockMapUseCase` and its tests | Replace with streaming AT_Y session; removable after differential tests and integration migration. |
| `RockChunkCoverage` | Both scanners and scanner tests | Replace runtime use with primitive coverage state; retain temporarily as a test oracle/facade only if it does not reintroduce the production path. |
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

Duplicate visits are not part of the current reader contract: the reader
deduplicates requested positions and emits one terminal visit per unique
position. The future session should reject a duplicate position explicitly,
unless a separately reviewed idempotent-join contract is added; silently
counting duplicates is prohibited.

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

Malformed use is explicit: `accept` after `finish`, `finish` before the reader
operation has completed, null visits, invalid coordinates, duplicate visits,
and packed-capacity overflow fail fast. A failed reader operation must preserve
the existing failure/diagnostic semantics and must not manufacture `NO_ROCK`.

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
chunk row (`floorDiv(worldY, 16)`) for each horizontal chunk column. A decoded
visit reads exactly the target local Y and updates the cell to observed when
the block resolves to a catalog rock, otherwise to the no-rock candidate.
The operation is deterministic even if duplicate decoded observations are
encountered; the preferred contract is still to reject duplicate positions.

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

The future state is a primitive vertical bitset per horizontal chunk column,
where a set bit means trusted available coverage (`DECODED` or
`PALETTE_REJECTED`). Missing and failed are absent bits, with separate bounded
counters/diagnostics preserving their distinction. A missing chunk table is
not converted into available coverage.

The requested horizontal chunk bounds are:

```text
chunkMinX = floorDiv(centerBlockX - radius, 16)
chunkMaxX = floorDiv(centerBlockX + radius, 16)
chunkMinZ = floorDiv(centerBlockZ - radius, 16)
chunkMaxZ = floorDiv(centerBlockZ + radius, 16)
chunkMinY = floorDiv(minY, 16)
chunkMaxY = floorDiv(maxYExclusive - 1, 16)
```

Use checked `long` intermediates for bounds and differences. Map a horizontal
chunk `(cx,cz)` to `((long)cz - chunkMinZ) * horizontalWidth + cx - chunkMinX`
after checking the product against the primitive-array limit. Map vertical
chunk `cy` to `cy - chunkMinY`. With `verticalChunkCount = maxY-minY`
rounded over chunk boundaries, use
`wordsPerHorizontalColumn = ceil(verticalChunkCount / 64)`, not a single-long
assumption. Partial bottom/top chunks set only the requested span's coverage
meaning; the finalizer clips tests to `[minY,maxYExclusive)`.

Coverage's trusted bitset is compact, negative-coordinate safe, and does not
need `HashSet<ChunkCoordinate>` or `Map<ColumnCoordinate,List<ChunkSpan>>` in
the hot path. Contiguous trusted coverage is derived by checking the required
vertical bit interval, not by assuming adjacent callback visits.

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
area estimates for R2048 and R4096 are approximately 13,176,795 and
52,707,179 respectively (the exact formula remains the authority).

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
During session creation, create a frozen ordinal table in deterministic block
ID order (the current catalog is backed by a `TreeMap`). Ordinal zero is
reserved for non-observed states. The table stores the identities needed for
legend/palette/UI access; the cell store stores only ordinals.

The catalog must validate that every recognized block ID maps to exactly one
ordinal and that the reverse table is stable across runs for the same registry.
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
adapter, if temporarily retained, must be marked compatibility/test-only and
must not be called by production renderer or analysis code; it must not be a
silent fallback to the old unbounded implementation.

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
coverage:           H * W * 8 bytes, plus bounded status counters
catalog/counts:     O(K)
working set:        O(workerCount * reusable workspace + F * bounded outcome)
```

There is no `C * decodedChunkSize` term and no `N` Java-object term. For
illustration, packed-cell payload alone is:

| Workload | Cell estimate | `int[]` payload | `long[]` payload |
|---|---:|---:|---:|
| R1024 | 3,294,097 exact | 12.57 MiB | 25.13 MiB |
| R2048 | ~13,176,795 | ~50.27 MiB | ~100.53 MiB |
| R4096 | ~52,707,179 | ~201.06 MiB | ~402.12 MiB |

These are arithmetic estimates, not measurements and exclude JVM array/object
headers, geometry, coverage, decoder working set, and the output image. With
the current 256-block world height, `V` is up to 16 chunks and `W=1`; for a
horizontal circle the R1024/R2048/R4096 chunk boxes are approximately
129x129, 257x257, and 513x513 columns, so one-long coverage is only about
0.13, 0.50, and 2.00 MiB respectively. Wider or modded vertical ranges use
additional words linearly in `W`.

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
World-coordinate bounds must remain consistent with `WorldMetadata` when
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
duplicate visits according to the chosen contract, null/invalid visits, and
diagnostic preservation.

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
catalog ordinal capacity; Y-range capacity; multiword coverage; partial top
and bottom chunks; negative floor division; duplicate visits; invalid reserved
packed values; and finish/accept lifecycle violations.

## 23. Legacy cleanup plan

After B through F are reviewed, `RockColumnScanner` and `RockAtYScanner` can
be removed from production if the differential oracle and integration tests
cover their semantics. `RockChunkCoverage` can be removed from production
when primitive coverage is integrated; a small test-only constructor/facade
may survive temporarily if it does not retain decoded chunks in runtime code.
`RockColumnSample` may survive as a single-cell compatibility facade, but its
list-based production role must disappear. `RockMap.columns()` must not remain
an unbounded production API. No legacy scanner may be selected as a hidden
fallback for large radii or errors.

## 24. Remaining PF-1.3 checkpoint plan

```text
A — this Architecture Contract (documentation only)
B — semantic characterization/differential oracle
C — compact immutable RockMap and shared circle geometry
D — streaming UPPER session and primitive coverage
E — streaming AT_Y and use-case integration
F — primitive renderer and aggregate legend/count migration
G — legacy cleanup after reviewer gates
H — runtime validation, profiling, and performance evidence
```

No B-or-later work is included in this checkpoint.

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
change surface/prospecting/cache/SaveSession behavior, add a dependency,
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
profiled rather than assumed. The session's duplicate-visit policy must be
made explicit in its public contract. The current reader's missing-table path
and diagnostics integration need differential tests to ensure an unavailable
coverage result cannot be mistaken for all-empty coverage. Finally, the
full-raster R4096 output may remain a separate memory bottleneck even after
ROCK analysis becomes compact.

## 30. Definition of DONE and reviewer gates

PF-1.3 is not DONE at Checkpoint A. Later completion requires: all planned
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
