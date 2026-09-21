# VS Cartographer

## Project purpose

VS Cartographer is a Java/JavaFX desktop application with CLI tooling for offline analysis of Vintage Story `.vcdbs` save files.

The project reads Vintage Story saves in SQLite read-only mode and turns stored world data into useful navigation, mapping, terrain, environment, geology, resource and marker information.

The tool must never modify the Vintage Story save.

Before substantial work, read the relevant project documentation under `docs/`.
When ChatGPT is acting as the controlling/orchestrating agent, it must read
`docs/CHATGPT_CONTROLLER_WORKFLOW.md` before planning implementation.

---

## Core rules

- Open `.vcdbs` read-only.
- Never write to the game save.
- Treat missing data as missing data, not as permission to guess.
- Binary parsers must be defensive.
- Parser failures for individual rows/chunks should be reported without aborting the full analysis where possible.
- Coordinate spaces must be explicit.
- Prefer real save data over heuristics.
- Keep rendering, parsing and analysis separated.
- Every milestone requires reviewer validation on a real save before being marked DONE.

---

## Coordinate contract

Vintage Story save data uses absolute world coordinates.

For the current known world:

```text
World size:
X = 1,024,000
Y = 256
Z = 1,024,000
```

The world center is therefore approximately:

```text
X = 512000
Z = 512000
```

Display coordinates are:

```text
displayX = absoluteX - worldSizeX / 2
displayZ = absoluteZ - worldSizeZ / 2
```

User-facing HOME and USER MARKERS are stored in DISPLAY coordinates.

Renderers convert them back to absolute coordinates through `WorldMetadata`.

Never silently mix DISPLAY and ABSOLUTE coordinate spaces.

---

## Current architecture

```text
src/main/java/cartographer/

├── cli/
│   ├── CommandRouter
│   ├── WhereamiCommand
│   ├── HomeCommand
│   ├── NavCommand
│   ├── MapCommand
│   ├── MapRegionCommand
│   ├── EnvironmentCommand
│   ├── ResourceCommand
│   ├── ScanCommand
│   ├── GeologyCommand
│   ├── MarkerCommand
│   ├── AtlasCommand
│   └── ProgressReporter
│
├── save/
│   ├── SqliteSaveConnection
│   ├── VcdbsReader
│   ├── WorldMetadataReader
│   └── SaveInspector
│
├── parser/
│   ├── PlayerDataParser
│   ├── MapChunkParser
│   ├── ChunkParser
│   ├── RegistryParser
│   └── ServerMapRegionParser
│
├── scanner/
│   ├── SurfaceStreamingSession
│   ├── SurfaceRainHeightPlanner
│   ├── SurfaceRainHeightScanner
│   ├── SurfaceObjectCompactPlanner
│   └── SurfaceObjectStreamingScanner
│
├── render/
│   ├── MapRenderer
│   ├── UserMarkerRenderer
│   ├── ResourceOverlayRenderer
│   ├── SurfaceResourceOverlayRenderer
│   ├── RenderLayer
│   ├── RenderOptions
│   ├── RenderStyle
│   └── PngWriter
│
├── environment/
│   ├── EnvironmentInterpreter
│   ├── ClimateInterpreter
│   ├── ForestInterpreter
│   ├── OceanInterpreter
│   └── LandformInterpreter
│
├── geology/
│   └── geology analysis
│
├── resource/
│   ├── ResourceAnalyzer
│   ├── ResourceHotspot
│   ├── ResourceOverlayCell
│   ├── SurfaceResourceAnalyzer
│   ├── SurfaceResourceAnalysis
│   ├── SurfaceResourceDeposit
│   └── SurfaceResourcePoint
│
├── marker/
│   ├── UserMarker
│   └── MarkerStore
│
├── navigation/
│   └── HomeStore
│
├── atlas/
│   ├── AtlasRenderer
│   └── TilePyramid
│
└── perf/
    ├── RenderDataCacheStore
    ├── WorldDataSnapshot
    ├── TerrainTileStore
    ├── SurfaceTileStore
    ├── UpperRockTileStore
    └── ResourceIndexStore
```

This is a living architecture. Do not create unused abstractions only because they appear in the roadmap.

---

# Verified save format knowledge

## SQLite tables

Known useful tables include:

```text
playerdata
chunk
mapchunk
mapregion
gamedata
```

---

## Player

Player position parsing is implemented and verified on a real save.

---

## World metadata

World size is read from SaveGame metadata.

Known relevant values:

```text
MapSizeX
MapSizeY
MapSizeZ
```

---

## Chunk coordinates

Chunk/mapchunk/mapregion coordinate decoding is implemented.

Mapregion coordinates must be decoded directly from the packed mapregion position.

Do not divide already-decoded mapregion coordinates by 16 again.

---

## Mapchunk

Mapchunk parsing is implemented and used for terrain rendering.

Known useful fields include terrain/rain height data.

---

## Server chunk

Server chunk decoding supports current known compressed chunk payloads.

Known support includes:

```text
compression version 2
raw palettes
zstd compressed palettes
bitplanes
liquid layer
```

---

## Block registry

Block IDs are resolved through the registry stored in save data.

Do not hard-code block IDs.

---

# Completed milestones

## 0.1–0.8 Foundation

Completed:

```text
0.1 whereami
0.2 HOME / navigation
0.3 coordinate math
0.4 mapchunk parsing
0.5 PNG MVP
0.6 terrain rendering
0.7 block registry
0.8 surface scanner
```

---

## 0.9 Semantic terrain

DONE.

Includes:

```text
semantic surface classification
real liquid handling
surface rendering
unknown block diagnostics
```

Known UNKNOWN blocks may include artificial/player-made blocks such as farmland, cob and fences.

A future BUILT / HUMAN_MADE classification may be added.

---

## 0.10 Performance / SQLite range reading

DONE.

Implemented:

```text
streaming SQLite reads
range filtering before loading chunk BLOBs
bounded Surface streaming temporary state
large-radius rendering without previous OOM
progress overflow fix
```

---

## 0.11 Server MapRegion

DONE.

Parsed mapregion data includes:

```text
Climate
Forest
Landform
GeologicProvince
OreMaps
RockStrata
Ocean
```

---

## 0.12 Environment / Biome interpretation

DONE.

Implemented conservative interpretation of:

```text
Climate
Forest
Ocean
Landform
```

Climate values are treated as indices unless their real physical units are proven.

Do not invent official biome names.

---

## 0.13 Geology / Resources

DONE.

Includes:

```text
RockStrata raw parsing
OreMaps raw parsing
geologic province parsing
real ore signal extraction
relative ore signal analysis
world coordinate conversion
hotspot ranking
spatial hotspot separation
resource heatmaps
ore search maps
surface resource search
connected surface deposits
clay search
peat search
```

Important distinction:

OreMaps indicate relative generated ore signal.

They are not proof that a specific ore block physically exists at a coordinate.

Surface resource search is different: it uses actual decoded visible surface blocks.

---

## 0.14 User Markers

DONE.

Implemented:

```text
per-save marker persistence
DISPLAY coordinate storage
add
update
here
list
remove
clear
multi-word names
case-insensitive replacement
per-save isolation
map rendering
labels
```

USER MARKERS are stored outside the Vintage Story save.

---

# Current milestone

## 1.0 Detailed Cartographer

Most of the original 1.0 scope is already implemented.

Completed:

```text
terrain layer
real water/liquid surface
semantic surface layer
PLAYER marker
HOME marker
user markers
marker labels
PNG export
radius configuration
scale configuration
render styles
render layers
missing-data diagnostics
```

Still missing for 1.0 completion:

```text
explored-world spatial coverage explored-region coverage visualization
```

---

## 1.0a Explored World Coverage

Next implementation target.

Goal:

Build a real spatial index of explored mapregions and summarize the explored world footprint.

Desired CLI:

```text
coverage inspect <save.vcdbs>
coverage render <save.vcdbs> --out <coverage.png>
```

Desired analysis:

```text
present mapregions
region bounding box
world-coordinate bounds
display-coordinate bounds
bounding-grid size
missing cells
coverage percentage
```

Desired render:

```text
explored region cells
missing cells inside explored bounding box
PLAYER
HOME
```

This milestone is considered complete only after:

```text
unit tests
real-save CLI validation
PNG inspection
```

Once 1.0a is complete:

```text
1.0 Detailed Cartographer = DONE
```

---

# Future roadmap

## 1.5 Geology Map / Cross-Sections

Goals:

```text
surface geology visualization
rock-strata interpretation
vertical geological cross sections
terrain slope analysis
geological layer export
```

RockStrata semantics must be established before presenting raw values as named rock types or depths.

---

## 2.0 World Analyzer

Goals:

```text
structure detection
interesting block searches
region statistics
CSV/text export
world reports
```

---

## 3.0 Performance Engine

The current performance architecture is implemented across bounded streaming
decode, compact ROCK/Surface processing, fused prospecting, operation-scoped
read-only save access, persistent render-data caching, and reviewer-controlled
macro/resource/JFR evidence. See
[`docs/PERFORMANCE_ARCHITECTURE.md`](docs/PERFORMANCE_ARCHITECTURE.md).

Legacy common-pool decode scanning was removed during the implementation of
the bounded streaming engine. Runtime validation of the current architecture
remains reviewer-controlled and pending.

---

## 3.5 Real Incremental Rendering

Current incremental support is not considered complete incremental rendering.

Target:

```text
detect changed mapchunks/chunks
invalidate only affected tiles
reuse previous terrain output
marker changes must not force terrain reparsing
```

---

## 4.0 LOD / Atlas

Goals:

```text
tile pyramid
real LOD
huge explored-world atlas
metadata
optional lightweight HTML viewer
```

---

# CLI snapshot

Current major commands include:

```text
whereami

home set
home show

nav home

map render

mapregion inspect

environment inspect

resource list
resource inspect
resource search
resource render
resource surface-search
resource surface-render

scan surface
scan blocks

geology surface
geology strata

markers add
markers update
markers here
markers list
markers remove
markers clear



atlas render

inspect
index
```

---

# Marker contract

Examples:

```text
markers add world.vcdbs RED CLAY -834 259
markers update world.vcdbs RED CLAY -800 300
markers here world.vcdbs BASE
markers remove world.vcdbs RED CLAY
```

For `add` and `update`:

```text
last two arguments = DISPLAY X and Z
everything between save path and coordinates = marker name
```

For `here` and `remove`:

```text
everything after save path = marker name
```

---

# Resource analysis rules

## OreMap resources

Example:

```text
nativecopper
cassiterite
hematite
gold
silver
```

Use OreMap analysis.

Do not claim a hotspot guarantees physical ore blocks.

---

## Surface resources

Example:

```text
clay
peat
```

Use decoded surface block search.

Connected surface deposits currently use 8-neighbor connectivity.

---

# Rendering rules

Current main render layers:

```text
TERRAIN
SURFACE
SOIL_FERTILITY
MARKERS
```

`SOIL_FERTILITY` is optional and is not part of the default render-layer set.
It uses conservative nominal soil/farmland block evidence. It does not
represent current farmland N/P/K state and does not infer fertility through
snow or water.

WATER is represented through the real liquid/surface layer and is not a separate top-level render layer.

---

# Development workflow

## Reviewer responsibilities

The user + ChatGPT are responsible for:

```text
architecture
review
tests
real-save validation
benchmarking
PNG inspection
milestone sign-off
```

## Codex responsibilities

Codex is an implementation executor only.

Codex may:

```text
edit requested files
implement requested functionality
inspect its own diff for accidental edits
commit
push
```

Codex must not:

```text
run Gradle
run tests
run builds
run the application
run benchmarks
run Docker
perform real-save validation
inspect generated PNGs
claim tests pass
claim performance numbers
declare milestones DONE
force push
rewrite history
perform broad unrelated refactors
```

Codex final output should report only:

```text
commit SHA
commit message
files changed
short implementation summary
assumptions / reviewer checks
explicit statement that tests and real-save validation were not run
```

Required final statement:

```text
Tests and real-save validation were not run; they are left to the reviewer.
```

For performance work:

```text
Tests, benchmarks, and real-save validation were not run; they are left to the reviewer.
```

---

# Testing / validation rules

Before marking a milestone DONE:

1. Compile/test suite must be green.
2. Relevant CLI command must be run against the real save.
3. Output must be manually inspected.
4. PNG output must be visually inspected when rendering is involved.
5. No unrelated files should be changed in the implementation commit.

## Test authoring rules

- Concurrency tests must synchronize on explicit lifecycle events using mechanisms such as `CountDownLatch`, barriers, semaphores, futures, or callback handshakes. Do not infer correctness from scheduler timing.
- Do not use `Thread.sleep(...)` or `TimeUnit.*.sleep(...)` to wait for another thread to "probably" reach a state. Real passage of time is appropriate only when time itself is the behavior under test.
- Timed waits may prevent CI from hanging indefinitely, but they are deadlock guards only; they must not encode a finish-within-a-duration correctness assumption. Increasing a timeout alone is not a flaky-test fix.
- Before calling `thread.interrupt()`, deterministically establish the intended blocking or lifecycle phase. Do not use `Thread.State` polling as synchronization.
- Treat worker completion and callback/consumer completion as distinct phases unless the production contract explicitly guarantees their equivalence. Synchronize on the phase the assertion concerns.
- Make concurrency-test cleanup failure-safe: release test-controlled blockers on failure paths, close owned resources, verify test-owned threads terminate, and do not leave live non-daemon threads behind.
- Preserve semantic coverage. Do not remove assertions, disable tests, weaken ordering or concurrency contracts, or ignore missing callbacks or leaked threads to make a test pass.

For CI evidence handling, flaky-test diagnosis, repeated validation, and review gates, follow `docs/CHATGPT_CONTROLLER_WORKFLOW.md`.

---

# Current next step

Implement:

```text
1.0a Explored World Coverage
```

Do not start 1.5 geology cross-sections before 1.0a is reviewed and marked complete.
