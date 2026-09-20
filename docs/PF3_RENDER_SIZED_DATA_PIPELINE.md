# PF-3 Render-Sized Data Pipeline

## Goal

PF-3 removes the current requirement to materialize block-resolution request
state when the final raster cannot observe every world column.

The target warm path is:

```text
WorldDataSnapshot
        ↓
bounded tile consumption
        ↓
RenderSamplingPlan
        ↓
render-sized state / direct raster consumers
        ↓
bounded raster
```

instead of:

```text
WorldDataSnapshot
        ↓
materialize all requested tiles
        ↓
full-resolution Terrain / Surface / ROCK request model
        ↓
sample down to the raster
```

The authoritative Vintage Story save remains read-only. PF-2 snapshot
correctness, revision isolation and source fallback rules remain unchanged.

## Correctness boundaries

PF-3 must not:

- introduce Surface top-down early-stop behavior;
- change fallback Y ordering or Surface scan semantics;
- treat derived snapshot data as source authority;
- infer absence from missing derived data;
- open a source `SaveSession` on a compatible READY warm-render path;
- silently change viewport geometry or raster image semantics for performance.

Exact logical ARGB parity remains the default render gate until a visual change
is explicitly approved.

## Dead-code contract

PF-3 is a refactor, not a parallel implementation project.

Each checkpoint must perform a dead-code sweep in the subsystem it changes.
Superseded implementations, adapters, helpers and overloads must be removed
once their production/test callers have migrated and no compatibility,
analysis or authoritative source-fallback contract still requires them.

A source-authoritative fallback is not dead code. An exact analysis path is not
dead code merely because a render-sized path replaces it for rendering.

Do not preserve old and new hot paths side-by-side "just in case".

## Checkpoints

### PF-3.0 — sampling contract

Introduce one immutable render sampling contract for the main map raster and
route existing Terrain pixel sampling through it without changing output.

The contract must centralize:

- world bounds;
- raster size;
- effective pixels/blocks per pixel;
- exact image-column → world-X selection;
- exact image-row → world-Z selection;
- viewport geometry.

This checkpoint does not yet shrink `DenseHeightGrid`; it establishes the
sampling authority required to do that safely.

### PF-3.1 — zero-copy snapshot tiles

Remove duplicate Surface/Terrain/ROCK array ownership transfers and trusted
internal defensive clones while retaining immutable public contracts.

### PF-3.2 — streaming snapshot consumption

Add bounded tile-consumer paths so warm rendering does not first materialize a
complete `Map<Coordinate, Tile>` for the requested viewport.

### PF-3.3 — render-sized Terrain

Replace block-resolution Terrain request materialization with state bounded by
the raster sampling plan plus the halo required by hillshade.

### PF-3.4 — render-sized Surface

Separate exact analysis Surface state from render-sized Surface consumption and
remove the warm render copy into a full request-shaped `SurfaceMap`.

### PF-3.5 — render-sized ROCK

Render compatible UPPER_ROCK views directly from bounded snapshot tiles and the
sampling plan rather than constructing a full request-shaped `RockMap`.

### PF-3.6 — lazy Surface discovery

Selecting a save must no longer trigger expensive Surface object discovery as a
hidden side effect. Discovery becomes an explicit/lazy consumer operation.

### PF-3.7 — integrated validation

Run the final PF-3 HEAD against one immutable real save and compare it with the
stored baseline.

Required evidence includes:

- unit/integration test suite;
- R1024/R2048/R4096 warm render timing;
- process CPU where available;
- peak heap and GC evidence;
- zero source connections for compatible READY warm renders;
- viewport geometry parity;
- exact logical ARGB fingerprint parity;
- real-save source-safety evidence.

No timing or heap threshold is invented before measurement.

## PF-3.0 implementation note

The initial checkpoint precomputes at most one X and one Z lookup table bounded
by the 4096-pixel raster cap. This removes repeated floating-point
pixel-to-world division from the Terrain hot loop and provides the exact
sampling authority later checkpoints will use to avoid building unobservable
block-resolution intermediate state.
