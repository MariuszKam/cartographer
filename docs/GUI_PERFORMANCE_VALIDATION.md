# GUI Performance Redesign Validation

## Purpose

This is the acceptance gate for GUI-P1 through GUI-P10. It separates
automated correctness evidence from manual/real-save runtime evidence.

A green unit-test suite is required but is not sufficient to claim real-save
4K performance or end-to-end source safety.

## Automated gate

Run:

```powershell
.\gradlew.bat test
```

The suite must cover, at minimum:

- PF-1.7 Terrain/Surface cache MISS, HIT, corrupt/incompatible fallback and heal;
- save revision invalidation;
- one operation-scoped source session where the production path requires one;
- retained Surface rendering with no additional mapchunk/chunk reads;
- retained Ore rendering that skips base-map preparation while still performing
  an authoritative selective ore scan;
- retained Environment/Geology state reuse without a second map-region read;
- full-vs-retained image fingerprint parity for equivalent requests;
- cross-save retained reuse rejection before additional source reads;
- 4096 raster cap / R2048 / R4096 geometry contracts;
- local recomposition eligibility and deterministic marker visibility;
- stale local-recomposition completion rejection;
- cache-root containment outside the protected save.

## Real-save read-only safety gate

Use a disposable copy only when possible, but the tooling is designed to
verify that the source remains unchanged.

```powershell
.\gradlew.bat realSaveValidation -Psave="C:\path\world.vcdbs"
```

For the PF-1.7 render/cache workload:

```powershell
.\gradlew.bat pf18SourceSafety \
  -Psave="C:\path\world.vcdbs" \
  -PcacheRoot="C:\path\cartographer-cache"
```

Required result:

- source safety PASS;
- no prohibited `.vcdbs` mutation;
- no prohibited WAL/SHM/journal sidecar mutation;
- render/cache artifacts remain outside the save directory.

## Workstation real-save matrix

Use one known representative save. Record the exact branch/commit and save
copy used.

### Base Map / cache

1. Render R1024 with Terrain + Surface.
2. Record cache diagnostics.
3. Repeat the identical request.
4. Confirm second run reports Terrain/Surface HITs where artifacts are
   publishable and performs less source work.
5. Corrupt a cache artifact only in a disposable cache root, never in the save.
6. Re-render and confirm fallback + heal; repeat and confirm HIT.

### R2048

- Area should be approximately 4096 x 4096 blocks.
- Raster must remain at or below 4096 x 4096.
- Status must report actual area/raster/blocks-per-pixel.
- Inspect output for gross coordinate, clipping, or overlay misalignment.

### R4096

- Area should be approximately 8192 x 8192 blocks.
- Raster must remain 4096 x 4096 or below.
- Effective scale should be approximately 2 blocks/pixel for the normal
  1 px/block request.
- Geology rendering must not allocate an 8193 x 8193 raster.
- Record runtime and peak-memory observations separately; do not infer them
  from unit tests.

### Local layer recomposition

After a normal Map/Ore/Surface render:

- zoom and pan away from Fit;
- toggle Terrain, Surface, Soil Fertility and Markers;
- where retained, toggle Environment and Geology;
- confirm zoom/pan are preserved;
- confirm status says local/no-save-read;
- confirm Ore or Surface analytical overlays remain aligned;
- confirm enabling a layer whose data was never prepared asks for Render
  instead of performing hidden source IO.

### Retained operation reuse

- Render a compatible Map with Surface data, then render a Surface material.
  Confirm retained path status and zero new base source-read diagnostics.
- Render an Ore request from a compatible retained Map/Surface frame.
  Confirm base-map preparation is skipped while the ore selective scan still
  occurs.
- Enable Environment or Geology after a compatible base frame and run Render.
  Confirm only missing map-region work is sourced; previously interpreted
  overlay state remains reusable.
- Change radius, center, save, or incompatible style and confirm fallback to
  the full authoritative path.
- Surface Object discovery is intentionally still a distinct selective source
  operation; do not count it as retained Surface-render IO.

## Responsive operation and cancellation acceptance

### Scoped responsiveness

Start a source-heavy R2048 or R4096 render while a valid previous frame is
visible.

Confirm that:

- pan, zoom, Fit/Reset and cursor inspection remain responsive;
- the Result Inspector remains readable;
- retained layer toggles can recompose the previous Map/Ore/Surface frame;
- retained ROCK highlight can rerender the previous Geology/Prospecting frame;
- save/radius/new source Render controls that would conflict with the active
  source request are disabled;
- background Surface discovery does not disable the viewport or inspector.

### Foreground cancellation

1. Start a source-authoritative R2048 or R4096 Map/Ore/Geology/Prospecting
   operation.
2. Press Cancel while source/decode work is active.
3. Confirm the UI reports cancellation rather than an ERROR analysis result.
4. Confirm the previously valid frame remains visible.
5. Confirm the cancelled result is never displayed after cancellation.
6. Confirm the application becomes ready for another operation.
7. Immediately run a smaller operation and confirm it succeeds.

For deterministic automated evidence, cancellation tests must prove that an
interrupted Surface discovery and ROCK source workflow close their
operation-scoped `SaveSession` exactly once. The bounded decode pipeline tests
must continue to prove interruption, outstanding-future cancellation,
`shutdownNow()`, worker quiescence and restored interrupt state.

### Stale completion protection

Rapidly request a second local layer recomposition or ROCK highlight before the
first completes. Only the latest generation may replace the viewport raster.

Changing save invalidates/cancels old FOREGROUND, DISCOVERY and LOCAL work.
No completion belonging to the previous save may update the current
Workstation.

### Source safety after cancellation

After a cancelled real-save operation, rerun the read-only safety gate and
confirm the source `.vcdbs` and prohibited sidecars remain unchanged. A
cancelled operation does not weaken the source-safety contract.

## Visual acceptance

For at least R1024, R2048 and R4096 inspect:

- player/HOME/user-marker alignment;
- Ore overlay alignment;
- Surface object/material overlay alignment;
- Environment and Geology region alignment;
- Terrain/Surface/Soil layer toggling;
- map edges and clipping;
- cursor/display coordinate sanity.

## Evidence status

Do not mark GUI-P10 complete until:

1. automated tests pass on the accepted P10 HEAD;
2. Workstation smoke passes on a real save;
3. at least one R2048 render is visually inspected;
4. one R4096 render is attempted and its actual outcome recorded;
5. read-only safety validation passes.

If R4096 is impractically slow or memory-heavy, record that result. The 4K
raster contract is a correctness/memory bound, not a performance guarantee.
