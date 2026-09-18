# GUI P1–P14 Integrated Validation

## Purpose

This is the final acceptance contract for GUI-P1 through GUI-P14. The validation tooling is implemented, but the redesign remains **VALIDATION PENDING** until the reviewer executes the workflow on the exact accepted candidate SHA and `guiReleaseGate` returns PASS.

A green unit-test suite alone is not sufficient. R4096 is stretch/headroom evidence: a real attempt and its factual outcome are required, but no invented timing or memory threshold is imposed.

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

The historical P10 checklist below has been superseded by the executable P14
gate in this document. R2048 remains the primary scalability target and R4096
remains stretch/headroom observation. If R4096 is impractically slow,
controlled-failure, or memory-limited, record that factual result; the 4K
raster contract is a correctness/memory bound, not a fabricated performance
guarantee.

## GUI-P14 executable evidence workflow

Use a cache root and evidence root outside the protected Vintage Story save directory. The declared `-PgitSha` is evidence metadata; independently verify that the local checkout being executed is that exact SHA.

### 1. Initialize evidence

```powershell
.\gradlew.bat guiValidationInit -PgitSha=<sha> -PevidenceRoot=<evidence>
```

This creates `manual-validation.properties` with every manual check set to `PENDING`.

### 2. Automated preflight

```powershell
.\gradlew.bat guiValidationPreflight -PgitSha=<sha> -PevidenceRoot=<evidence>
```

The task depends on the full JUnit suite and writes `preflight.properties` only after tests pass.

### 3. Real-save source safety

```powershell
.\gradlew.bat guiSourceSafetyEvidence -Psave=<save> -PcacheRoot=<cache> -PgitSha=<sha> -PevidenceRoot=<evidence>
```

The final gate requires the narrow real-save safety smoke and PF-1.8 production render/cache safety workload to PASS, with a qualifying external cache manifest and contained artifacts.

### 4. Integrated macro evidence

```powershell
.\gradlew.bat guiMacroEvidence -Psave=<save> -PcacheRoot=<cache> -PgitSha=<sha> -PevidenceRoot=<evidence>
```

Mandatory factual R2048 evidence:

- `MAP_R2048 / JVM_WARM`;
- `MAP_R2048 / CACHE_WARM`, including verified cache HITs;
- `ROCK_UPPER_R2048 / JVM_WARM`.

Stretch R4096 evidence:

- `MAP_R4096 / JVM_WARM`;
- `ROCK_UPPER_R4096 / JVM_WARM`.

R4096 may complete factually, fail, or run out of memory. P14 records a terminal attempt marker when a full macro report cannot be produced. That marker proves an attempt occurred; it is never relabeled as factual performance evidence.

### 5. Manual Workstation checks

Set every required key in `manual-validation.properties` to `PASS` only after performing the check:

```text
check.WORKSTATION_REAL_SAVE_SMOKE=PASS
check.R2048_VISUAL=PASS
check.R4096_ATTEMPT_RECORDED=PASS
check.CACHE_COLD_WARM_CORRUPT_HEAL=PASS
check.LOCAL_RECOMPOSITION=PASS
check.FUSED_PROSPECTING=PASS
check.RESPONSIVE_CANCELLATION=PASS
check.SOURCE_SAFETY_AFTER_CANCEL=PASS
check.P13_DOCK_AND_VIEWPORT_UX=PASS
```

Also fill `r4096Outcome` with the actual observed outcome.

The P13 UX check requires that Tool Rail, Context Controls and Inspector work at the supported minimum window size; the two docks collapse independently; dock changes do not open the save, rerender the raster, or reset zoom/pan; Inspector tabs separate Inspect / Results / Layers / Diagnostics; the floating map toolbar and operation/telemetry bar remain usable.

The P12 cancellation check requires that a heavy R2048/R4096 operation can be cancelled without surfacing a false analysis ERROR, the previous valid frame remains visible, no stale result arrives later, and a subsequent smaller operation succeeds. Re-run source-safety after cancellation.

### 6. Final release gate

```powershell
.\gradlew.bat guiReleaseGate -PgitSha=<sha> -PevidenceRoot=<evidence>
```

The gate writes `release-gate-report.txt` and returns PASS only when automated preflight, source safety, mandatory R2048 factual evidence, R4096 terminal attempts, cache-warm HIT evidence, and all manual checks belong to the same candidate SHA and are complete.

Missing, malformed, stale or SHA-mismatched evidence is a failure, not an implicit pass.

## Final completion rule

GUI-P14 and the P1–P14 redesign may be called **DONE / VALIDATED** only after `guiReleaseGate` returns PASS on the exact accepted candidate SHA. Until then the factual status is:

```text
IMPLEMENTATION COMPLETE
VALIDATION PENDING
```
