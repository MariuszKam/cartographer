# Progressive / Streaming World Rendering

## Temporary core implementation plan

Status: ACTIVE MIGRATION PLAN

Branch: feature/progressive-streaming-world-rendering

Audit baseline: master at b44c3b2f707b6d8ed4f1d86d8bbf229f55dccb16

Created: 2026-09-26

This document is the temporary source of truth for the progressive world-rendering migration. It intentionally describes current implementation details, migration stages, expected deletions, validation gates, and unresolved tuning decisions. That information does not belong in AGENTS.md or the durable architecture constitution because it is expected to change while this work is in progress.

Keep this document accurate as implementation proceeds. When the migration and cleanup are complete, replace the durable architectural conclusions with focused permanent documentation and delete this temporary plan.

---

## 1. Decision

VS Cartographer will move from request-bounded whole-map rendering to progressive, tile-based world rendering.

The current user flow is centered on selecting a radius, starting a bounded render, waiting for that entire request to complete, and then receiving one finished raster. The target user flow is:

1. The user selects a save.
2. Cartographer loads the minimum world overview needed to identify the player and world metadata.
3. A progressive map session starts automatically.
4. The render tile containing the player receives bootstrap priority.
5. The first usable map fragment is displayed as soon as that tile is ready.
6. Neighboring observed terrain appears incrementally, piece by piece.
7. Background work continues toward all observed main-world mapchunks known for the selected save revision.
8. If the user pans to an unrendered area, visible and near-visible tiles preempt background completion work.
9. Layers enrich the already-visible map instead of requiring replacement of one monolithic whole-map image.
10. Memory use stays bounded by viewport/cache policy rather than scaling with the full discovered world raster.

The product-level goal is not merely to hide a progress bar. The rendering model itself must become incremental. A tile that is already prepared and rendered must not be recomputed merely because the explored area grows.

---

## 2. Product semantics

### 2.1 What “the whole discovered world” means

For this migration, the authoritative discovery unit is an observed main-world MAPCHUNK row from the selected Vintage Story save, constrained by the existing main-world and world-metadata checks.

This is intentionally the same semantic distinction already represented by WorldIndexCatalogStore and TerrainSnapshotPreparer:

- source existence is authoritative;
- parser success is a separate fact;
- cached or rendered output is derived data;
- absence, corruption, unsupported data, and not-yet-checked data are not interchangeable states.

This plan does not redefine “observed” as “every block the player personally looked at”. The current save evidence available to Cartographer supports observed source mapchunks. If a future feature can prove a more precise exploration semantic from authoritative data, that should be a separate domain decision.

### 2.2 Source-save safety remains non-negotiable

The migration must preserve all current save-safety rules:

- the source save is opened read-only;
- Cartographer never writes to, repairs, migrates, or mutates the source save;
- derived indexes, snapshots, tile caches, raster caches, and scheduler state live outside the source save;
- malformed source data is isolated where possible;
- missing, corrupt, unsupported, inferred, and known data remain distinguishable.

### 2.3 “Real-time” in this document

“Real-time” means progressive visual availability and reprioritizable background work. It does not mean that every source operation is instantaneous or that the entire world is maintained in RAM.

The user should see useful terrain quickly and should be able to navigate while additional tiles continue to appear.

---

## 3. Audit scope and evidence

The implementation audit for this plan was performed against the repository state listed above.

Repository inventory at the audit baseline:

- 595 files total in the supplied repository tree;
- 383 production Java files under src/main;
- 176 test Java files under src/test;
- 528 radius-like references across 76 repository files for the audited grep pattern;
- 454 radius-like references across 61 production Java files;
- 13 test Java files with radius-like references.

The radius count is evidence of migration breadth, not a deletion count. The codebase uses radius for several different concepts:

1. global bounded map/render scope — targeted by this migration;
2. bounded analysis scope for tools such as geology or prospecting — requires deliberate redesign, not blind deletion;
3. legitimate geometry such as circles and marker shapes — must remain where semantically correct.

Therefore no global search-and-replace of “radius” is acceptable.

---

## 4. Current architecture: audited findings

### 4.1 The main map contract is explicitly bounded

RenderOptions contains radiusBlocks and pixelsPerBlock.

MapRasterContract turns that radius into a square world diameter and one bounded raster. It currently caps raster output at 4096 pixels and explicitly documents that the mechanism is not LOD and not incremental rendering.

RenderSamplingPlan is also request-shaped. It builds axis sample tables for one square raster centered on one WorldPosition.

This means radius is not merely a UI control. It is part of the current rendering contract.

### 4.2 Current base-map flow

The important path is approximately:

~~~text
save selected
    -> LoadWorldOverviewUseCase
    -> user chooses tool/radius/layers
    -> WorkstationController builds bounded request
    -> WorkstationOperationCoordinator starts one bounded operation
    -> RenderActualOreMapUseCase / related use case
    -> PrepareMapDataUseCase
    -> SnapshotPreparedMapDataReader or LiveMapDataPreparer
    -> PreparedMapData(center + RenderOptions + bounded prepared data)
    -> MapRenderer
    -> one BufferedImage
    -> RenderedMap
    -> MapPanel.show(...)
    -> one JavaFX ImageView
~~~

The map becomes a visible result after a bounded request produces a complete RenderedMap.

### 4.3 The renderer creates one image

MapRenderer allocates one BufferedImage for the requested raster and paints terrain, semantic surface data, markers, and compatible layers into that image.

RenderedMap owns that image plus its map geometry/report data.

This model is incompatible with an unbounded explored world because a single image would either grow with world extent or continuously be rebuilt.

### 4.4 MapPanel is a single-image viewport

MapPanel currently owns:

- one ImageView;
- one ScrollPane;
- one MapViewportGeometry;
- one base width/height;
- zoom implemented by resizing the ImageView;
- cursor mapping through the geometry of the single image.

MapPanel.show and MapPanel.replaceImage replace the complete raster.

This presentation model must be virtualized for progressive rendering. Replacing the BufferedImage with one enormous JavaFX Canvas would not solve the architectural problem; it would only move the unbounded raster allocation.

### 4.5 MapFrame retains bounded-frame state

MapFrame represents the map currently displayed by the Workstation. It retains geometry and compact analysis state for one bounded frame.

It supports MAP, ORE, SURFACE, GEOLOGY, PROSPECTING, and COVERAGE variants with invariants that assume one current frame/tool result.

MapFrame.canReusePreparedMap includes request compatibility checks for bounded render settings. This is useful today, but it is the wrong lifecycle abstraction for a save-scoped streaming world.

### 4.6 Local layer recomposition rebuilds the whole bounded image

MapFrameCompositor recreates RenderOptions from retained PreparedMapData and selected layers, invokes MapRenderer again, and repaints compatible overlays into a complete BufferedImage.

This is a valuable current optimization because it avoids another save read, but it still recomposes the full bounded frame. In the target architecture, layer changes should invalidate or enrich affected visible tiles/layers rather than force one complete world-raster rebuild.

### 4.7 WorkstationOperationCoordinator provides a useful safety pattern, not the final streaming model

WorkstationOperationCoordinator owns bounded JavaFX Tasks in FOREGROUND, LOCAL, and DISCOVERY scopes.

Important reusable ideas:

- replacement cancels the previous operation in the same scope;
- completion callbacks are generation-gated;
- stale results are prevented from updating the UI;
- worker interruption is part of cancellation.

However, the coordinator models operations with one terminal result. Progressive map rendering needs a longer-lived session that emits many tile-state transitions before the session itself terminates.

The generation-gating principle should be preserved in the new session design.

### 4.8 SaveSession is thread-confined

SaveSession explicitly owns one immutable, read-only SQLite connection and is confined to its creating/analysis thread. Decode workers may process detached payloads, but they must not use the session JDBC connection.

This is a hard constraint for the new architecture.

A progressive map session must not share one SaveSession connection across arbitrary render workers.

### 4.9 Existing cache granularity is already spatial

MapChunkCoordinate.SIZE_BLOCKS is 32.

Existing derived cache data is already naturally partitioned by mapchunk:

- TerrainHeightTile is compact terrain data for one mapchunk;
- SurfaceCacheTile is immutable full-mapchunk Surface data;
- TerrainTileStore stores terrain tiles by MapChunkCoordinate;
- SurfaceTileStore stores surface tiles by MapChunkCoordinate;
- RenderDataCacheRevision namespaces derived data by observed save revision.

This is the strongest existing foundation for the migration.

The new renderer should reuse these data tiles. It should not replace them with a second competing source cache.

### 4.10 An observed-world catalog already exists

WorldIndexCatalogStore is revision-scoped and stores observed main-world MapChunkCoordinate values.

Important current behavior:

- partial discovered coordinates can survive cancellation;
- the complete marker is written only after the authoritative scan finishes;
- observedAmong only treats absence as authoritative when the mapchunk scan is complete;
- observedMapChunks can provide the current catalog contents.

TerrainSnapshotPreparer deliberately records an observed coordinate before reading/parsing its payload. That means catalog membership describes source existence, not parser success.

That distinction is correct and must survive the migration.

### 4.11 Current observed discovery reads more than progressive scheduling requires

VcdbsMapChunkStreamReader.forEachObservedMapChunk currently executes a query equivalent to:

~~~text
SELECT position, data
FROM mapchunk
ORDER BY position
~~~

For each usable main-world coordinate it publishes source existence, then reads and parses the payload.

That behavior is appropriate for the current snapshot-preparation path, where discovery and terrain preparation happen together. It is too expensive as the mandatory gate before the first progressive tile.

Progressive rendering needs a coordinate-only discovery capability which can enumerate source coordinates without requiring every payload to be fetched and parsed.

### 4.12 Exact mapchunk lookup already exists

VcdbsReader exposes forEachMapChunkByCoordinate.

The underlying VcdbsMapChunkStreamReader performs exact-position lookup in bounded batches, currently with DIRECT_MAPCHUNK_BATCH_SIZE = 256.

This is the correct starting mechanism for player-first rendering because a small render tile can request only the mapchunks it needs.

One missing semantic is important: the current callback emits successfully parsed MapChunk values, while aggregate stats count found and failed rows. For a progressive tile loader, callers need per-coordinate terminal state so that “row absent” cannot be confused with “row existed but failed parsing”.

The new source-facing tile API must therefore expose explicit per-coordinate outcomes.

### 4.13 Existing “streaming sessions” are evidence, not reusable world-session semantics

SurfaceStreamingSession and RockStreamingSession already demonstrate bounded streaming/accumulation patterns.

They are useful implementation references for:

- consuming data incrementally;
- bounding retained state;
- finalizing deterministic results;
- handling partial/terminal coverage.

They are not the progressive world rendering session. Both are shaped around bounded analysis geometry and their own domain semantics.

### 4.14 WorldIndexBatchPlanner is data batching, not render-tile layout

WorldIndexBatchPlanner groups observed mapchunks into deterministic 16 x 16 mapchunk buckets to bound Surface accumulator/planner state.

DEFAULT_TILE_SPAN = 16 must not be reused automatically as the visual render-tile size. A data-processing batch and a user-visible render tile have different performance responsibilities.

---

## 5. Architectural direction

### 5.1 The map becomes a save-scoped scene, not a request result

The central ownership change is:

~~~text
OLD
render request -> complete RenderedMap -> display image

NEW
selected save/revision -> ProgressiveMapSession -> stream tile/layer state -> WorldMapViewport
~~~

A ProgressiveMapSession lives for the selected save revision rather than for one Render button click.

The session terminates or is superseded when:

- another save is selected;
- the selected save revision becomes invalid;
- the user closes the application;
- a fatal session-level failure makes authoritative source access impossible.

A local tile failure is not automatically a session-level failure.

### 5.2 Separate three spatial units

Do not overload the word “tile”. The implementation should keep three concepts explicit.

#### Source/data tile

One MapChunkCoordinate = up to 32 x 32 world blocks.

This is the existing cache/source unit for TerrainHeightTile and SurfaceCacheTile.

#### Render tile

A render tile is a fixed, aligned rectangle composed of N x N source mapchunks.

Proposed semantic types:

- RenderTileCoordinate;
- RenderTileLayout;
- RenderTileBounds.

The exact initial span is intentionally not frozen by this document. Candidates such as 4 x 4 mapchunks (128 x 128 blocks) and 8 x 8 mapchunks (256 x 256 blocks) must be benchmarked.

The choice must balance:

- time to first tile;
- source/cache batch efficiency;
- image-allocation overhead;
- number of UI updates;
- scheduler granularity;
- viewport preemption responsiveness;
- seams and overlay cost.

#### Viewport raster

The viewport is the bounded set of pixels currently drawn on screen. Its memory size is tied to the window and render cache policy, not to total discovered world extent.

### 5.3 Alignment must be deterministic for negative coordinates

RenderTileCoordinate conversion must use floor-based world/mapchunk division, matching existing coordinate semantics for negative positions.

A world coordinate must always map to the same render tile regardless of traversal direction or current viewport.

Tile bounds must be expressible in both:

- render-tile coordinates;
- source MapChunkCoordinate ranges;
- absolute world-block bounds.

No silent coordinate-space mixing is acceptable.

---

## 6. Target runtime flow

### 6.1 Save selection and bootstrap

Target startup flow:

~~~text
user selects save
    -> cancel/supersede previous progressive session
    -> LoadWorldOverviewUseCase
       - metadata
       - registry/resources
       - player position if available
    -> observe RenderDataCacheRevision
    -> start ProgressiveMapSession
    -> compute player RenderTileCoordinate
    -> enqueue BOOTSTRAP tile immediately
    -> request required source mapchunks directly
    -> render first base tile
    -> publish TILE_READY
    -> WorldMapViewport displays it
    -> continue discovery and background completion
~~~

The first tile must not require full-world discovery.

If a compatible Terrain/Surface cache already contains needed data, the first tile should reuse it before reading source payloads.

### 6.2 Discovery runs independently of first-paint readiness

After bootstrap starts, the session should discover the observed-world coordinate set.

Discovery responsibilities:

- enumerate observed main-world mapchunk coordinates;
- record them in WorldIndexCatalogStore incrementally;
- preserve partial results across cancellation for the same cache revision;
- mark scan complete only after the authoritative coordinate scan completes;
- expose discovered coordinates to the scheduler incrementally;
- avoid requiring payload decode merely to learn world shape.

Discovery should be bounded and cancellation-aware. A single full-table operation that monopolizes the source I/O thread for a large save would undermine viewport preemption.

Implementation should therefore use a bounded/paged coordinate discovery API. Exact SQL pagination strategy should be chosen after validating source-table characteristics and measuring it; the architectural requirement is that discovery yields between bounded batches.

### 6.3 Scheduler policy

The scheduler is a core product component, not an implementation detail.

Required priority classes, in descending priority:

1. tiles intersecting the current viewport;
2. tiles in a small prefetch margin around the viewport;
3. the initial player bootstrap neighborhood;
4. near-player expansion that gives the initial “map growing outward” effect;
5. background completion of remaining observed world tiles.

Within equivalent priority, ordering must be deterministic.

For player-centered background expansion, use distance/ring ordering over render-tile coordinates rather than repeatedly increasing a global radius.

If the user pans far away, viewport work must preempt background player-ring work.

### 6.4 No repeated expanding-radius renders

This implementation is explicitly forbidden:

~~~text
render radius 256
render radius 512
render radius 1024
render radius 2048
...
~~~

That approach reprocesses already-rendered data and preserves the monolithic-raster model under a different animation.

A completed render tile is an independently reusable unit.

### 6.5 Sparse worlds are first-class

The target scene must not assume that observed terrain forms one rectangle.

A save may contain:

- compact exploration around a base;
- long narrow travel corridors;
- disconnected explored regions;
- holes where no source mapchunk exists;
- corrupt or unsupported source rows within otherwise observed areas.

Scheduler, viewport, cache, and bounds logic must operate on sparse spatial membership.

---

## 7. ProgressiveMapSession responsibilities

The exact class names may evolve, but ownership must remain explicit.

ProgressiveMapSession should own or coordinate:

- selected normalized save identity;
- RenderDataCacheRevision;
- player/world overview needed by rendering;
- session generation/token;
- render-tile scheduler;
- authoritative source-read lifecycle;
- tile data loading;
- render worker lifecycle;
- observed-world discovery state;
- tile status registry;
- publication of tile/layer events;
- bounded in-memory rendered-tile cache;
- cancellation and terminal shutdown.

It must not expose SaveSession, JDBC Connection, or mutable worker internals to presentation code.

### 7.1 Suggested event model

A one-result Task is insufficient. The session should publish typed state transitions such as:

- SESSION_STARTED;
- DISCOVERY_BATCH_AVAILABLE;
- TILE_QUEUED;
- TILE_BASE_READY;
- TILE_LAYER_READY;
- TILE_PARTIAL;
- TILE_FAILED;
- TILE_INVALIDATED;
- DISCOVERY_COMPLETE;
- SESSION_FAILED;
- SESSION_CLOSED.

The UI does not need every internal event. Application-level events may be coalesced into a smaller presentation model. The important requirement is that many tile-ready updates can occur during one session.

### 7.2 Session identity and stale-event rejection

Every published tile update must be attributable to the current session/revision.

A useful key concept is:

~~~text
SessionGeneration + RenderDataCacheRevision + RenderTileCoordinate + LOD + RenderVariant
~~~

If save A is superseded by save B, any late event from A must be rejected before it can mutate the viewport.

This generalizes the generation-gating discipline already used by WorkstationOperationCoordinator.

---

## 8. Source I/O and concurrency model

### 8.1 Never share SaveSession across render workers

SaveSession is explicitly thread-confined. The new design must preserve this.

Recommended model:

- one dedicated source-I/O loop/thread owns one SaveSession for the progressive session;
- all JDBC work for that SaveSession is serialized on its owner thread;
- source reads emit detached immutable data/payload-derived objects;
- CPU rendering and non-source computation may run on a bounded worker pool after data leaves the SaveSession boundary;
- cache stores may use their own existing connection lifecycle as designed;
- JavaFX updates occur only on the JavaFX application thread.

An alternative with short operation-scoped SaveSessions per source batch is acceptable only if benchmarks show that repeated session initialization is not a material cost and lifecycle semantics remain correct. The implementation must choose deliberately; sharing one connection between workers is not an option.

### 8.2 Source I/O must remain preemptible at batch boundaries

A low-priority world scan must not permanently block a high-priority viewport tile.

Therefore authoritative source operations should be split into bounded units. Between units, the source-I/O scheduler can service higher-priority exact-coordinate reads.

Cancellation is cooperative at safe boundaries plus thread interruption where current infrastructure supports it.

### 8.3 CPU worker count is a benchmark decision

Do not encode an arbitrary “fast” thread count into the architecture plan.

The render/decode worker pool must be bounded. Candidate worker counts should be benchmarked against:

- time to first tile;
- sustained tiles/second;
- JavaFX responsiveness;
- peak heap;
- GC activity;
- source I/O saturation.

---

## 9. Per-coordinate source result semantics

The progressive loader needs a stronger exact-mapchunk contract than “callback for parsed values plus aggregate stats”.

Introduce a result model that can express terminal state for every requested source coordinate.

Semantically required outcomes:

- PRESENT_DECODED — authoritative row exists and produced usable MapChunk data;
- ABSENT — exact authoritative lookup established that no matching row exists;
- PRESENT_UNREADABLE — row exists but payload is null, corrupt, unsupported, or parsing failed;
- CANCELED / NOT_COMPLETED — request did not reach a terminal source conclusion.

Names can change, but distinctions must not collapse.

This result can then drive:

- WorldIndexCatalogStore recording for known-present coordinates;
- terrain cache publication for decoded coordinates;
- explicit missing/unknown visual semantics;
- retry policy for derived failures;
- diagnostics without inventing terrain.

A parser failure must never be cached as authoritative source absence.

---

## 10. Tile data loading

Introduce a tile-oriented data-loading seam above existing source/cache mechanisms.

Suggested responsibility name: MapTileDataLoader.

Input should describe:

- save/revision identity;
- RenderTileBounds;
- requested data requirements, initially base terrain and optionally surface;
- cancellation/session token.

It should:

1. derive required MapChunkCoordinate values;
2. query TerrainTileStore first;
3. query SurfaceTileStore only when the requested rendering/layer requires it;
4. identify cache misses/corrupt derived entries;
5. request only required authoritative source coordinates;
6. publish rebuilt derived data back to the existing revision-scoped stores;
7. preserve per-coordinate source status;
8. return detached tile input data without retaining SaveSession/JDBC resources.

Do not create a second terrain/surface cache abstraction with overlapping authority.

---

## 11. Base rendering and tile composition

### 11.1 Replace whole-map output with render-tile output

Introduce a renderer whose unit of work is one RenderTileBounds at one resolution/LOD.

Suggested concepts:

- MapTileRenderer;
- RenderedMapTile;
- RenderTileGeometry or equivalent explicit mapping.

RenderedMapTile should be immutable from the scheduler/UI perspective.

It should contain enough information to place the tile in absolute world space without relying on one global MapViewportGeometry.

### 11.2 Seam correctness

Adjacent tiles must be visually equivalent to the corresponding region of the legacy renderer for features whose semantics are intentionally preserved.

Tests must explicitly cover:

- terrain colors at tile boundaries;
- hillshade calculations that need neighboring samples;
- negative coordinates;
- world edges/partial mapchunks;
- surface classification at tile edges;
- overlay clipping;
- no one-pixel gaps or duplicated columns/rows.

Where a renderer needs neighbor context, the data request may include a small halo around the output tile. Halo data is input context and must not change ownership of the output tile.

### 11.3 Base tile versus overlay layers

Do not bake every interactive layer into one long-lived base image if that would require expensive rerendering for simple toggles.

Target conceptual stack:

~~~text
terrain/base raster
surface semantic layer where enabled
ore layer
geology layer
environment layer
user markers
system markers
player marker
selection/highlight layer
~~~

Implementation may merge layers for performance where invalidation semantics remain explicit. The ownership goal is that changing a marker or local highlight does not force authoritative terrain to be read again.

---

## 12. WorldMapViewport

MapPanel’s single-ImageView model should be replaced by a virtualized world viewport.

Suggested responsibilities:

- camera center in absolute world coordinates;
- zoom level / selected LOD;
- viewport-to-world and world-to-viewport transforms;
- determination of visible render tiles;
- request/prefetch notifications to ProgressiveMapSession;
- drawing ready tiles only;
- placeholders/empty space for not-yet-ready areas;
- independent marker/selection overlays;
- cursor coordinate mapping without one global image geometry;
- center-player action;
- fit-known-world action once enough discovery metadata exists.

The drawing surface must be bounded by the UI, not by world size.

### 12.1 JavaFX update pressure

Do not publish one JavaFX mutation per source mapchunk if rendering can complete much faster than the UI can process events.

Render tiles should be coarse enough to reduce event volume, and the presentation adapter should be allowed to coalesce multiple ready-tile notifications into one repaint pulse.

---

## 13. Progressive enrichment

Base terrain should not wait for every expensive optional layer if that damages time to first useful paint.

A render tile may progress through states such as:

~~~text
REQUESTED
    -> SOURCE/CACHE_LOADING
    -> BASE_READY
    -> DISPLAYED
    -> SURFACE_READY
    -> OPTIONAL_LAYERS_READY
~~~

A tile can therefore appear with terrain first and be enriched later.

This is preferable to making the first paint depend on the slowest selected layer.

Layer readiness must be explicit so that a partially enriched tile is not misrepresented as complete.

---

## 14. LOD strategy

LOD is not required to land in the first executable migration stage, but the new tile key and viewport design must not make LOD impossible.

Expected direction:

- LOD 0: highest detail, approximately one world block per base sample/pixel where appropriate;
- higher LOD levels: progressively coarser blocks-per-pixel;
- viewport chooses LOD based on zoom;
- render cache keys include LOD;
- fit-world does not instantiate thousands of full-resolution tiles merely to shrink them on screen.

Do not reinterpret the existing MapRasterContract 4096 cap as LOD. It is a bounded-raster scaling mechanism and should remain legacy behavior until the tile path replaces it.

---

## 15. Rendered-tile caching and memory

### 15.1 Derived data cache versus rendered raster cache

Keep these concepts separate:

- TerrainTileStore / SurfaceTileStore: persistent derived source data;
- rendered-tile memory cache: UI/render optimization;
- optional future persistent raster-tile cache: only if benchmarks justify it.

The first migration should not add a persistent raster cache unless evidence shows raster generation is a meaningful bottleneck after source-derived data is cached.

### 15.2 Bounded in-memory cache

Rendered images must use a bounded eviction policy, for example an LRU or weighted cache.

Eviction priority should favor keeping:

- visible tiles;
- near-visible prefetch tiles;
- recently used tiles.

The entire discovered world must not remain as BufferedImage/JavaFX Image objects merely because background completion has visited it.

Background completion may prepare persistent derived data without retaining every raster in RAM.

---

## 16. Discovery completion versus rendering completion

These are separate dimensions.

The session should distinguish at least:

- observed-world discovery progress;
- base-derived-data availability;
- rendered-tile readiness for current LOD/style;
- optional layer readiness.

The UI must not reintroduce one misleading “47% of map rendered” progress contract as the primary experience.

A lightweight status is acceptable, for example:

- “Building map · 184 tiles ready”;
- “Discovering world · map remains usable”;
- “Surface details loading”.

The visible map itself is the primary progress feedback.

---

## 17. Error and partial-data behavior

### 17.1 Local failures are isolated

A corrupt source row inside one render tile should not destroy an otherwise usable progressive session.

The tile can enter PARTIAL or FAILED state with diagnostics while neighboring tiles continue.

### 17.2 Session-fatal failures

Examples of session-level failures include:

- source save can no longer be opened read-only;
- required table/schema access fails globally;
- save revision changes in a way that invalidates the active session and cannot be safely continued.

### 17.3 No invented fill

Do not fill absent or unreadable source regions with fabricated terrain merely to make the map visually continuous.

Presentation may use an explicit neutral/unknown visual treatment, but its semantics must remain distinguishable from known terrain.

---

## 18. Save revision changes

RenderDataCacheRevision already provides revision-scoped derived namespaces based on save identity/size/modification plus schema/compatibility versions.

The progressive session must be bound to one revision.

If source revision changes while a session is active:

1. stop accepting new results for the old revision;
2. cancel/close old session work safely;
3. establish a new revision;
4. reuse only derived artifacts compatible with that revision policy;
5. restart bootstrap from the current authoritative overview.

Never merge tile results across revisions merely because coordinates match.

---

## 19. Tool and layer migration strategy

The migration must distinguish “world map” from “bounded analysis tool”. Removing the global map radius does not automatically mean every analysis is unbounded.

### 19.1 MAP

MAP is the first target and defines the new progressive session/viewport architecture.

Its base world should auto-start after save selection and no longer require a global radius control or Render button to obtain initial terrain.

### 19.2 MARKERS

Markers should move out of permanent base-raster ownership where practical.

Player/home/user markers are presentation overlays and should be redrawable without terrain rerender/source reads.

### 19.3 SURFACE

Surface should become progressive tile enrichment.

The existing SurfaceCacheTile and SurfaceTileStore should be reused. Existing bounded Surface planning/scanning logic may need a tile-oriented adapter so only data required for requested render tiles is produced.

Surface object/material analysis that is inherently query-driven may still have separate analysis semantics, but its visualization should target world tiles/overlay state rather than replace the entire map.

### 19.4 ORE

Ore rendering currently uses bounded request radius and overlay painting into the whole raster.

Target behavior is viewport/tile-driven ore overlay production. Expensive ore source scanning must be demand-driven and cached by a semantically correct key.

Do not simply scan the entire observed world for all ores when a save is selected.

### 19.5 GEOLOGY

Current RockMap/RockStreamingSession is bounded by center/radius and produces a compact bounded result.

Geology migration should happen after the base tile pipeline is stable. It may require a tile-local rock result model or another spatially indexed derived representation.

Until then, bounded geology may remain as a transitional tool, but it must not dictate base-map architecture.

### 19.6 PROSPECTING

Prospecting is an analysis operation, not just a render layer.

A bounded user-selected analysis area may remain meaningful even after the global map radius disappears. If a bounded scope remains, its control should be named and presented as analysis scope, not as map render radius.

This distinction prevents accidental deletion of legitimate radius semantics.

### 19.7 COVERAGE

Coverage already has different semantics and currently hides RadiusPane. It should be evaluated separately. It may become an overlay or remain a specialized result, but it is not a blocker for the base progressive map migration.

---

## 20. UI migration

### 20.1 RadiusPane

RadiusPane currently offers 128, 256, 512, 1024, 2048, and 4096 and is shown for every WorkstationTool except COVERAGE.

Target:

- remove global map-render radius selection from MAP;
- remove its role from any tool migrated to viewport/tile-driven rendering;
- preserve or replace bounded analysis controls where the analysis itself needs scope;
- delete RadiusOptionsTest only when no production behavior remains to test.

### 20.2 Render button

For MAP, save selection should start rendering automatically. MAP should not require pressing Render to obtain the world.

Other expensive analyses may retain explicit actions such as Analyze until they are deliberately redesigned.

### 20.3 Busy state

The map must remain navigable while background building continues.

A long-lived progressive session therefore cannot map directly to the current “foreground busy disables controls” semantics.

Foreground user actions and background map population need independent state.

### 20.4 Navigation

Center player becomes a natural camera operation and should work regardless of whether the entire discovered world is already rendered.

Fit-world requires known/discovered bounds. Before discovery completes, fit can target currently known bounds; after completion it can target final observed bounds.

---

## 21. Implementation phases

Each phase should land as a coherent commit/PR-sized change with tests. Do not delete the legacy path before parity/migration gates are satisfied.

### Phase 0 — Baseline and measurement harness

Status: IMPLEMENTED

Goals:

- preserve audit baseline in this document;
- identify current representative saves/fixtures used for render validation;
- add benchmark/measurement seams needed for TTFT and tile throughput if absent;
- record legacy output fixtures/checksums or deterministic pixel assertions for parity-sensitive areas.

No product behavior change.

Implemented baseline:

- `LegacyMapRenderBaselineFixture` defines a deterministic, in-memory terrain fixture centered at world `(0, 0)`, with radius `256` blocks, `16 x 16` source mapchunks, TOPOGRAPHIC style, TERRAIN only, and a deterministic height formula spanning negative and positive coordinates;
- `LegacyMapRenderBaselineTest` freezes the current legacy output at `513 x 513` pixels, `256` input mapchunks, `263169` drawn terrain samples, and image fingerprint `0x22a97e92ba808c50`;
- the fingerprint is a compact deterministic regression oracle, not a visual-quality score; future tile-renderer parity tests should additionally keep focused seam/pixel assertions where a failure needs better localization;
- `LegacyMapRendererBenchmark` is an opt-in JMH baseline for radius `128`, `256`, and `512` using the same deterministic terrain shape;
- because the legacy renderer exposes no usable map image before `MapRenderer.render(...)` returns, legacy whole-render completion latency is also the legacy rendering model's time to first visible map for this in-memory benchmark;
- the `progressiveRenderingBaseline` Gradle task runs only that benchmark and writes CSV evidence to `build/reports/progressive-rendering/legacy-map-renderer-jmh.csv`;
- this benchmark deliberately excludes SQLite/source I/O. Existing `MapChunkStreamStats` and `RenderDataCacheReport` remain the current counters for requested rows, batches, payload bytes, source loads, and cache behavior. Progressive TTFT measurement will compose those counters with session timing once Phase 6 introduces an actual first-tile lifecycle;
- no real Vintage Story save is committed as a performance fixture. Current automated baseline evidence is deterministic synthetic rendering plus the repository's existing SQLite integration fixtures. Real-save performance claims require an explicitly identified external/private fixture and must be reported separately.

Exit gate:

- current behavior has enough automated evidence to detect unintended visual/semantic changes during extraction;
- the legacy parity oracle exists before Phase 1 changes spatial contracts;
- an opt-in, machine-readable renderer baseline exists before tile-size and TTFT comparisons are made.

Validation note:

- the frozen image fingerprint was independently reproduced from the audited legacy renderer implementation before this phase was committed;
- benchmark timing values are intentionally not hard-coded into this document because they are environment-dependent;
- the repository quality gate and JMH baseline must only be reported as passed after they actually execute in a Java 25 / Gradle environment with required dependencies available.

### Phase 1 — Spatial render-tile foundation

Status: IMPLEMENTED

Added explicit render-tile spatial types owned by `cartographer.render`:

- `RenderTileCoordinate` is a stable coordinate in the global render-tile grid and is intentionally distinct from world-block and source `MapChunkCoordinate` spaces;
- `RenderTileLayout` defines tile alignment and the number of source mapchunks per render-tile side;
- `RenderTileBounds` represents half-open absolute world-block bounds and can clip them to authoritative `WorldMetadata` bounds.

Spatial decisions frozen by this phase:

- world -> mapchunk conversion continues to use the existing `MapChunkCoordinate.fromWorld(...)` semantics;
- mapchunk -> render-tile conversion uses `Math.floorDiv`, so negative coordinates are aligned correctly instead of truncating toward zero;
- render tiles are globally aligned and their world bounds are half-open;
- source mapchunks inside a tile/bounds are enumerated deterministically in Z-major, then X-major order;
- square-ring traversal uses Chebyshev distance and has deterministic clockwise ordering, providing a stable spatial primitive for the later scheduler without implementing scheduler policy in this phase;
- world-edge clipping never expands a tile and an entirely out-of-world tile produces no intersection;
- render-tile span remains configurable. Phase 1 deliberately does not choose 4 x 4, 8 x 8, or another production span before benchmark evidence exists.

Implemented tests cover:

- world -> mapchunk -> render tile mapping;
- positive and negative coordinate behavior;
- exact 32-block mapchunk boundaries;
- exact render-tile-span boundaries;
- aligned half-open tile bounds;
- deterministic contained-mapchunk enumeration;
- deterministic ring traversal and uniqueness;
- partial world-edge clipping and out-of-world rejection.

No UI, save-reader, cache, renderer, or scheduler behavior changes in this phase.

Validation note:

- the new production spatial types compile successfully in an isolated Java compilation with all direct model dependencies;
- the new tests compile successfully against the required JUnit API surface;
- an explicit smoke harness exercised positive/negative mapping, clipping, source-mapchunk enumeration, and ring ordering successfully;
- `javac -Xlint:all` reported no warnings for the new production types in that selective compilation;
- this environment provides JDK 21 while the repository requires Java 25, so the full Gradle quality gate is not reported as executed or passed here.

### Phase 2 — Strong exact source results

Status: IMPLEMENTED

Added a strong exact-read contract without breaking existing consumers:

- `MapChunkReadStatus` distinguishes `PRESENT_DECODED`, `ABSENT`, `PRESENT_UNREADABLE`, and `NOT_COMPLETED`;
- `MapChunkReadResult` carries one terminal result per requested unique mapchunk coordinate and only permits a `MapChunk` for `PRESENT_DECODED`;
- `VcdbsReader.forEachMapChunkByCoordinateWithResults(...)` exposes the strong contract while the existing `Consumer<MapChunk>` API remains as a temporary migration adapter;
- exact lookup still uses bounded batches of 256 positions and still borrows the thread-confined `SaveSession` connection rather than taking ownership of it;
- a requested position not returned by the authoritative exact query is `ABSENT`;
- a present row with null payload or parser failure is `PRESENT_UNREADABLE`, never `ABSENT`;
- a missing MAPCHUNK table or an interrupted lookup before a batch starts yields `NOT_COMPLETED` for coordinates that never reached an authoritative terminal conclusion;
- existing `MapChunkStreamStats` accounting is preserved for legacy consumers while strong per-coordinate semantics are available to the progressive pipeline.

Tests cover decoded, absent, null-payload, parser-failure, missing-table, deduplication through the existing exact-read path, and interruption semantics.

The legacy callback API intentionally remains until Phase 4+ consumers have migrated. Its adapter only forwards `PRESENT_DECODED` results and is listed for final cleanup once no consumer requires it.

### Phase 3 — Coordinate-only observed-world discovery

Add a bounded/paged source capability that reads authoritative observed coordinates without loading MAPCHUNK payload data.

Requirements:

- main-world filtering remains correct;
- world metadata bounds remain correct;
- coordinates can be recorded incrementally in WorldIndexCatalogStore;
- completion marker written only after full scan;
- cancellation leaves a valid partial catalog;
- discovery yields between bounded batches so high-priority exact reads can run.

### Phase 4 — Tile data loader

Extract a tile-oriented loader using existing TerrainTileStore and SurfaceTileStore.

Requirements:

- cache-first;
- exact source fallback;
- no SaveSession leakage;
- per-coordinate terminal status preserved;
- source reads only for missing/corrupt derived entries that require repair;
- deterministic returned tile data.

### Phase 5 — MapTileRenderer

Create one-render-tile output path while legacy MapRenderer remains available.

Requirements:

- pixel/semantic parity tests for equivalent bounded regions;
- seam tests;
- optional halo support where rendering math requires neighbors;
- fixed, benchmarkable tile size/layout;
- no dependency from lower rendering/data layers on JavaFX.

### Phase 6 — ProgressiveMapSession scheduler

Introduce save/revision-scoped session lifecycle.

Implement:

- bootstrap player tile;
- tile registry and deduplication;
- viewport priority;
- prefetch priority;
- player-ring background priority;
- observed-world completion queue;
- cancellation/session generation;
- stale-event rejection;
- bounded source I/O and render worker queues.

Required concurrency tests must synchronize on explicit lifecycle events, never sleep timing.

### Phase 7 — Virtualized WorldMapViewport

Introduce the new presentation path alongside legacy MapPanel behavior if needed for migration.

Requirements:

- draw independent ready tiles;
- camera in world coordinates;
- pan/zoom without whole-image scaling;
- cursor absolute-world mapping;
- center player;
- viewport tile-demand callback;
- bounded image cache;
- JavaFX repaint/update coalescing.

### Phase 8 — Automatic MAP startup

After LoadWorldOverviewUseCase succeeds:

- start ProgressiveMapSession automatically;
- center initial camera on player when available;
- request/render bootstrap tile;
- show map before full discovery/render completion;
- background build continues without foreground busy lock.

At this point MAP no longer depends on a selected global radius.

### Phase 9 — Base MAP cutover

Make the progressive viewport the default MAP path.

Remove MAP-specific use of:

- radius selection;
- one-shot map Render action;
- bounded whole-map PreparedMapData as presentation state;
- full-raster local recomposition.

Keep legacy code temporarily only where still used by unmigrated tools.

### Phase 10 — Progressive layers

Migrate in low-risk-to-high-risk order unless benchmarks/evidence justify another sequence:

1. player/system/user markers;
2. base Surface enrichment;
3. environment overlays;
4. ore overlays;
5. geology visualization;
6. specialized Surface analyses;
7. prospecting visualization.

Each layer must define:

- its authoritative/derived source;
- tile key/invalidation semantics;
- whether it is viewport-demanded or background-completed;
- memory/cache policy;
- partial/error presentation.

### Phase 11 — LOD

Add multi-resolution render tiles after LOD-independent tile/session contracts are stable.

Requirements:

- deterministic LOD selection by zoom;
- no high-resolution world-wide raster allocation for fit-world;
- LOD-aware cache keys;
- acceptable transition behavior while zooming.

### Phase 12 — Legacy cleanup and final refactor

Only after all consumers have migrated, remove obsolete bounded rendering infrastructure.

See the explicit cleanup inventory below.

---

## 22. Legacy cleanup inventory

This list is intentionally a migration inventory, not a promise that every class is deleted unchanged. A class may be retained if it still has a coherent post-migration responsibility. Every item must be re-grepped before deletion.

### 22.1 Expected direct removals or major replacements

Likely obsolete for the main map after migration:

- RadiusPane as global map render scope;
- SearchPanel.selectedRadius for MAP;
- RenderOptions.radiusBlocks as the base-world extent contract;
- MapRasterContract as the central main-map extent mechanism;
- RenderSamplingPlan as one global square-map sampling plan;
- RenderedMap as the only/main map presentation result;
- MapPanel’s single ImageView whole-map implementation;
- MapFrame as a one-bounded-frame owner for migrated world layers;
- MapFrameCompositor whole-frame recomposition path;
- MAP reuse checks based on exact radius/center whole-frame compatibility;
- whole-map progress reporting as the primary user feedback.

### 22.2 Classes requiring decomposition, not blind deletion

Likely reusable logic exists inside:

- LiveMapDataPreparer;
- SnapshotPreparedMapDataReader;
- MapTerrainPreparation;
- MapRenderer;
- overlay renderers;
- RenderActualOreMapUseCase;
- RenderSurfaceResourceMapUseCase;
- RenderRockMapUseCase;
- WorkstationController;
- WorkstationMapFrameController;
- WorkstationOperationCoordinator.

Extract useful mechanisms into tile/session-oriented responsibilities before removing old orchestration.

### 22.3 Radius references that may remain

Do not delete radius when it means:

- geometric circle radius;
- marker styling radius;
- local bounded analysis scope that remains a real user/domain concept;
- algorithmic neighborhood distance unrelated to whole-map extent.

At final cleanup, classify every remaining radius reference by semantic meaning.

### 22.4 Cleanup completion grep

Final cleanup must include a repository-wide sweep for at least:

- radiusBlocks;
- selectedRadius;
- RadiusPane;
- MapRasterContract;
- RenderSamplingPlan;
- RenderedMap;
- MapFrame;
- MapFrameCompositor;
- whole-image replaceImage/show paths;
- compatibility constructors or adapters created only for migration;
- unused progress stages tied to whole-map rendering;
- obsolete tests that only protect removed bounded behavior.

Every remaining hit must have an explicit post-migration reason.

---

## 23. Testing strategy

### 23.1 Spatial unit tests

Cover:

- render-tile addressing;
- world/mapchunk/render coordinate conversion;
- negative coordinates;
- sparse membership;
- viewport intersection;
- ring/distance ordering.

### 23.2 Source-reader tests

Cover:

- coordinate-only discovery;
- valid exact row;
- absent exact row;
- null payload;
- parse failure;
- main-world filtering;
- metadata-bounds filtering;
- cancellation between batches;
- source save unchanged/read-only behavior.

### 23.3 Cache tests

Cover:

- terrain hit/miss/corrupt repair;
- surface hit/miss/corrupt repair;
- revision isolation;
- no stale revision reuse;
- deterministic tile publication.

### 23.4 Renderer parity tests

For a bounded fixture region, compare legacy and tile-composed output where semantics are intentionally unchanged.

Use focused pixel assertions/checksums plus seam-specific assertions instead of relying only on manual visual inspection.

### 23.5 Scheduler tests

Use deterministic fake source/renderer collaborators and explicit latches/events.

Cover:

- player tile scheduled first at startup;
- visible viewport work preempts background work;
- duplicate tile requests coalesce;
- old session events rejected;
- cancellation stops future publication;
- local tile failure does not kill unrelated tiles;
- discovery batches add background work incrementally;
- queue/memory bounds are enforced.

Do not use sleep-based timing as correctness proof.

### 23.6 UI tests

Cover transform and state logic separately from expensive JavaFX rendering where possible:

- world <-> viewport coordinate mapping;
- camera pan/zoom;
- visible tile calculation;
- player centering;
- fit-known-world;
- layer invalidation;
- stale session event rejection at presentation boundary.

### 23.7 Integration tests

Add at least one end-to-end progressive flow with a controlled save fixture:

1. select/load save;
2. receive player overview;
3. receive first tile before discovery completes;
4. receive additional tiles;
5. pan/request another region;
6. supersede session with another save/revision;
7. verify no old tiles appear afterward.

---

## 24. Performance validation

The migration is successful only if it improves perceived responsiveness without creating hidden memory/I/O regressions.

Track at least:

- time from successful world overview to first visible base tile;
- time to first useful viewport neighborhood;
- sustained render tiles/second;
- source MAPCHUNK rows and payload bytes read before first tile;
- cache hit ratio;
- peak heap while background completion runs;
- rendered image count/weight in memory;
- JavaFX responsiveness while workers are active;
- cancellation/supersession latency at bounded batch boundaries.

Benchmark candidate render-tile spans rather than choosing by intuition.

The current repository quality gate remains authoritative for automated test/architecture checks. Do not claim a gate passed unless it actually ran in an environment able to execute it.

---

## 25. Definition of done

The migration is not complete until all of these are true.

### User experience

- selecting a save starts the MAP experience without choosing a global render radius;
- the first map fragment appears without waiting for full-world discovery;
- rendering starts at the player when player position is available;
- more observed terrain appears progressively;
- panning to an unready area reprioritizes that area;
- the UI remains usable while background work continues;
- a percentage progress bar is no longer the primary map-building experience.

### Architecture

- no single BufferedImage represents the entire discovered world;
- no global MapViewportGeometry is required to describe the entire map;
- source/data tiles and render tiles are explicit separate concepts;
- source I/O respects SaveSession thread confinement;
- stale events are revision/session gated;
- sparse world shape is supported;
- memory is bounded independently of total discovered-world raster area;
- derived cache remains revision-scoped and external to the source save.

### Semantics

- absent, unreadable/corrupt, unknown/not-yet-checked, and decoded source states remain distinguishable;
- parser failure is never converted into source absence;
- partial tile failures do not silently invent terrain;
- authoritative versus derived information remains explicit.

### Cleanup

- MAP no longer depends on global radius selection;
- legacy whole-map rendering classes/contracts have no remaining migrated consumers;
- obsolete compatibility paths are removed;
- every remaining radius reference has a legitimate non-global-render meaning;
- temporary migration-only adapters/tests are removed or promoted into durable architecture intentionally;
- this temporary document is either deleted or replaced by durable focused documentation describing the final architecture.

---

## 26. Decisions intentionally deferred to measurement

The following are not to be guessed in advance:

- initial render-tile span (for example 4 x 4 versus 8 x 8 mapchunks);
- render worker count;
- exact rendered-image LRU/weight budget;
- whether persistent raster-tile caching is worthwhile;
- precise LOD thresholds;
- exact coordinate-discovery pagination strategy;
- exact amount of viewport prefetch margin;
- whether Surface is painted into base tiles or maintained as a separable raster layer at each stage.

Each decision needs a benchmark or a concrete semantic reason.

---

## 27. Implementation guardrails

During the migration:

- do not rewrite unrelated parser/domain code merely because the renderer changes;
- do not bypass existing cache revision semantics;
- do not introduce a generic shared/util dumping ground for tile concepts;
- do not make presentation types dependencies of save/cache/domain layers;
- do not introduce a service locator or global mutable renderer/session registry;
- do not keep compatibility wrappers after their final consumer disappears;
- do not delete tests merely because they make refactoring harder;
- do not preserve obsolete behavior solely to keep old architecture alive after all product consumers have migrated;
- do not turn one giant rewrite into the only integration point.

Prefer small coherent stages with old and new paths coexisting only for the minimum migration period necessary.

---

## 28. Expected final shape

The intended high-level end state is:

~~~text
SelectedSave / Revision
        |
        v
ProgressiveMapSession
        |
        +--> ObservedWorldDiscovery ----> WorldIndexCatalogStore
        |
        +--> TilePriorityScheduler
                  |
                  v
            MapTileDataLoader
              |         |
              |         +--> TerrainTileStore / SurfaceTileStore
              |
              +--> serialized read-only authoritative source I/O
                  on the SaveSession owner thread
                  |
                  v
             detached tile data
                  |
                  v
             MapTileRenderer
                  |
                  v
            RenderedMapTile events
                  |
                  v
            WorldMapViewport
                  |
        +---------+----------+----------------+
        |                    |                |
   base terrain          map layers        markers/UI
~~~

The architectural center is no longer “render this radius”.

It is “maintain a truthful, revision-scoped, progressively materialized view of the observed world, prioritizing what the user needs to see now”.

That is the core design decision for this branch.
