package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.WorldDataSnapshot;
import cartographer.perf.WorldIndexCatalogStore;
import cartographer.perf.SurfaceCacheTile;
import cartographer.perf.SurfaceTileLookup;
import cartographer.perf.SurfaceTileStore;
import cartographer.perf.TerrainHeightTile;
import cartographer.perf.TerrainTileLookup;
import cartographer.perf.TerrainTileStore;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.RenderOptions;
import cartographer.save.ChunkReadMetrics;
import cartographer.save.ChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceFallbackChunkPlanner;
import cartographer.scanner.SurfaceMap;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceRainHeightDiagnosticCounters;
import cartographer.scanner.SurfaceRainHeightPlan;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceStreamingSession;
import cartographer.scanner.SurfaceTile;
import cartographer.scanner.SurfaceTileDiagnosticSummary;
import cartographer.snapshot.SnapshotPreparedMapDataReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared operation-scoped terrain/Surface preparation pipeline.
 *
 * <p>The source save remains authoritative. Cache artifacts are optional
 * derived data; MISS/CORRUPT/incompatible data falls back to source reads.</p>
 */
public final class PrepareMapDataUseCase {
    private static final int CACHE_WRITE_BATCH_SIZE = 128;

    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final Optional<RenderDataCacheStore> renderDataCacheStore;
    private final Optional<SnapshotPreparedMapDataReader> snapshotReader;
    private final MapChunkRenderWindowPlanner mapChunkRenderWindowPlanner =
            new MapChunkRenderWindowPlanner();
    private final MapChunkPositionPlanner mapChunkPositionPlanner =
            new MapChunkPositionPlanner();
    private final SurfaceFallbackChunkPlanner surfaceFallbackChunkPlanner =
            new SurfaceFallbackChunkPlanner();

    public PrepareMapDataUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        this(
                reader,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        metadataReader
                ),
                Optional.empty()
        );
    }

    public PrepareMapDataUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            RenderDataCacheStore renderDataCacheStore
    ) {
        this(
                reader,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        metadataReader
                ),
                Optional.of(Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache store is required"
                ))
        );
    }

    PrepareMapDataUseCase(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory is required");
        this.renderDataCacheStore = Objects.requireNonNull(
                renderDataCacheStore,
                "render data cache option is required"
        );
        this.snapshotReader = this.renderDataCacheStore.map(
                SnapshotPreparedMapDataReader::new
        );
    }

    public PreparedMapData execute(
            PrepareMapDataRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");

        Optional<PreparedMapData> snapshot =
                executeSnapshot(request, progress);
        if (snapshot.isPresent()) {
            return snapshot.orElseThrow();
        }

        try (SaveSession session = sessionFactory.open(request.savePath())) {
            return execute(session, request, progress);
        }
    }

    public Optional<PreparedMapData> executeSnapshot(
            PrepareMapDataRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");
        if (snapshotReader.isEmpty()) {
            return Optional.empty();
        }
        return snapshotReader.orElseThrow().read(request, progress);
    }

    public PreparedMapData execute(
            SaveSession session,
            PrepareMapDataRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");
        session.requireSameSave(request.savePath());

        WorldMetadata metadata = session.snapshot().metadata();
        Map<Integer, BlockInfo> registry = session.snapshot().blockRegistry();
        WorldPosition player = reader.readPlayerPosition(session, progress);
        WorldPosition center = request.center().orElse(player);
        RenderOptions options = new RenderOptions(
                request.radius(),
                request.pixelsPerBlock(),
                request.style(),
                request.layers()
        );

        ReadDiagnostics mapChunkDiagnostics = new ReadDiagnostics();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        boolean surfaceDataRequired =
                request.surfaceDataRequirement().requiresSurface();
        int centerWorldX = (int) Math.round(center.x());
        int centerWorldZ = (int) Math.round(center.z());

        List<MapChunkCoordinate> renderMapChunkCoordinates =
                mapChunkRenderWindowPlanner.plan(
                        metadata,
                        center,
                        request.radius()
                );
        List<MapChunkCoordinate> surfaceMapChunkCoordinates = surfaceDataRequired
                ? mapChunkPositionPlanner.plan(
                        metadata,
                        centerWorldX,
                        centerWorldZ,
                        request.radius()
                )
                : List.of();

        Set<MapChunkCoordinate> renderMapChunkSet =
                new HashSet<>(renderMapChunkCoordinates);
        CacheContext cache = request.ignoreFoliage()
                ? prepareCache(request.savePath())
                : CacheContext.disabled(
                        "foliage-inclusive Surface analysis bypasses render-data cache"
                );

        Map<MapChunkCoordinate, SurfaceTileLookup> surfaceLookups = surfaceDataRequired
                ? lookupSurface(cache, surfaceMapChunkCoordinates, metadata)
                : Map.of();
        Set<MapChunkCoordinate> surfaceMissSet = new LinkedHashSet<>();
        Map<MapChunkCoordinate, SurfaceCacheTile> surfaceHits = new LinkedHashMap<>();
        for (Map.Entry<MapChunkCoordinate, SurfaceTileLookup> entry : surfaceLookups.entrySet()) {
            if (entry.getValue().status() == SurfaceTileLookup.Status.HIT) {
                surfaceHits.put(entry.getKey(), entry.getValue().tile());
            } else {
                // A missing mapchunk does not prove the absence of server
                // chunks. Surface therefore retains its authoritative source
                // fallback unless a complete Surface tile is already cached.
                surfaceMissSet.add(entry.getKey());
            }
        }

        List<MapChunkCoordinate> terrainRequiredCoordinates =
                new ArrayList<>(renderMapChunkCoordinates);
        terrainRequiredCoordinates.addAll(surfaceMissSet);
        terrainRequiredCoordinates = terrainRequiredCoordinates.stream()
                .distinct()
                .sorted(Comparator.comparingInt(MapChunkCoordinate::z)
                        .thenComparingInt(MapChunkCoordinate::x))
                .toList();

        Set<MapChunkCoordinate> knownAbsentTerrain =
                cache.knownAbsent(terrainRequiredCoordinates);
        Map<MapChunkCoordinate, TerrainTileLookup> terrainLookups =
                lookupTerrain(cache, terrainRequiredCoordinates);
        Set<MapChunkCoordinate> terrainMissSet = new LinkedHashSet<>();
        Map<MapChunkCoordinate, TerrainHeightTile> terrainHits = new LinkedHashMap<>();
        for (Map.Entry<MapChunkCoordinate, TerrainTileLookup> entry : terrainLookups.entrySet()) {
            if (entry.getValue().status() == TerrainTileLookup.Status.HIT) {
                terrainHits.put(entry.getKey(), entry.getValue().tile());
            } else if (!knownAbsentTerrain.contains(entry.getKey())) {
                terrainMissSet.add(entry.getKey());
            }
        }

        List<MapChunkCoordinate> sourceMapChunkCoordinates = terrainMissSet.stream()
                .sorted(Comparator.comparingInt(MapChunkCoordinate::z)
                        .thenComparingInt(MapChunkCoordinate::x))
                .toList();

        MapTerrainPreparation.Builder terrainBuilder =
                MapTerrainPreparation.builder(
                        center,
                        options,
                        renderMapChunkCoordinates.size(),
                        progress
                );

        SurfaceStreamingSession surfaceSession =
                surfaceDataRequired
                        ? SurfaceStreamingSession.begin(
                                metadata,
                                centerWorldX,
                                centerWorldZ,
                                request.radius(),
                                surfaceMissSet,
                                registry,
                                request.ignoreFoliage(),
                                true
                        )
                        : null;

        List<TerrainHeightTile> terrainWriteBuffer =
                new ArrayList<>(CACHE_WRITE_BATCH_SIZE);
        Set<MapChunkCoordinate> surfacePlanningInputsAvailable =
                new LinkedHashSet<>();

        for (MapChunkCoordinate coordinate : terrainRequiredCoordinates) {
            TerrainHeightTile tile = terrainHits.get(coordinate);
            if (tile == null) continue;
            if (renderMapChunkSet.contains(coordinate)) {
                terrainBuilder.accept(tile);
            }
            if (surfaceDataRequired && surfaceMissSet.contains(coordinate)) {
                surfaceSession.acceptMapChunk(tile);
                surfacePlanningInputsAvailable.add(coordinate);
            }
        }

        if (!sourceMapChunkCoordinates.isEmpty()) {
            reader.forEachMapChunkByCoordinate(
                    session,
                    sourceMapChunkCoordinates,
                    mapChunkDiagnostics,
                    mapChunk -> {
                        MapChunkCoordinate coordinate = mapChunk.coordinate();
                        if (terrainMissSet.contains(coordinate)) {
                            cache.terrain.sourceLoaded++;
                            terrainWriteBuffer.add(TerrainHeightTile.from(mapChunk));
                            if (terrainWriteBuffer.size() >= CACHE_WRITE_BATCH_SIZE) {
                                publishTerrain(cache, terrainWriteBuffer);
                            }
                        }
                        if (renderMapChunkSet.contains(coordinate)) {
                            terrainBuilder.accept(mapChunk);
                        }
                        if (surfaceDataRequired && surfaceMissSet.contains(coordinate)) {
                            surfaceSession.acceptMapChunk(mapChunk);
                            surfacePlanningInputsAvailable.add(coordinate);
                        }
                    },
                    progress
            );
        }
        publishTerrain(cache, terrainWriteBuffer);

        MapTerrainPreparation terrain = terrainBuilder.finish();
        PreparedSurfaceData preparedSurface;
        if (surfaceDataRequired) {
            SurfaceMapScanResult exactSurface = readCompactSurface(
                    session,
                    metadata,
                    surfaceSession,
                    registry,
                    surfaceMissSet,
                    surfaceHits,
                    surfacePlanningInputsAvailable,
                    cache,
                    chunkDiagnostics,
                    progress
            );
            preparedSurface = PreparedSurfaceData.fromExact(
                    exactSurface,
                    center,
                    options,
                    request.surfaceDataRequirement()
            );
        } else {
            preparedSurface = PreparedSurfaceData.none(
                    center,
                    options
            );
        }

        return new PreparedMapData(
                metadata,
                player,
                center,
                options,
                terrain,
                preparedSurface,
                registry,
                mapChunkDiagnostics,
                chunkDiagnostics,
                cache.report()
        );
    }

    private SurfaceMapScanResult readCompactSurface(
            SaveSession session,
            WorldMetadata metadata,
            SurfaceStreamingSession surfaceSession,
            Map<Integer, BlockInfo> registry,
            Set<MapChunkCoordinate> surfaceMisses,
            Map<MapChunkCoordinate, SurfaceCacheTile> surfaceHits,
            Set<MapChunkCoordinate> surfacePlanningInputsAvailable,
            CacheContext cache,
            ReadDiagnostics chunkDiagnostics,
            ProgressReporter progress
    ) {
        SurfaceRainHeightPlan rainPlan = surfaceSession.finishPlanning();
        Set<ChunkPosition> liquidFailureChunks = new HashSet<>();
        for (SurfaceCacheTile tile : surfaceHits.values()) {
            surfaceSession.acceptCachedTile(tile);
        }

        ChunkStreamStats fastChunkStats =
                new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!rainPlan.chunkPositions().isEmpty()) {
            fastChunkStats = reader.forEachSurfaceChunkByPositionAdaptive(
                    session,
                    rainPlan.chunkPositions(),
                    chunkDiagnostics,
                    chunk -> consumeSurfaceChunk(
                            chunk,
                            chunkDiagnostics,
                            liquidFailureChunks,
                            surfaceSession::acceptFastChunk
                    ),
                    progress
            );
            recordChunkReadDiagnostics(cache, "Surface fast");
        }

        List<MapChunkCoordinate> fallbackMapChunks =
                surfaceSession.fallbackMapChunks();
        List<ChunkPosition> fallbackPositions =
                surfaceFallbackChunkPlanner.plan(metadata, fallbackMapChunks);
        ChunkStreamStats fallbackChunkStats =
                new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!fallbackPositions.isEmpty()) {
            fallbackChunkStats = reader.forEachSurfaceChunkByPositionAdaptive(
                    session,
                    fallbackPositions,
                    chunkDiagnostics,
                    chunk -> consumeSurfaceChunk(
                            chunk,
                            chunkDiagnostics,
                            liquidFailureChunks,
                            surfaceSession::acceptFallbackChunk
                    ),
                    progress
            );
            recordChunkReadDiagnostics(cache, "Surface fallback");
        }

        SurfaceRainHeightScanResult result = surfaceSession.finish();
        cache.surface.sourceLoaded = Math.addExact(
                cache.surface.sourceLoaded,
                result.sourceMapChunksLoaded()
        );
        int chunksScanned = Math.addExact(
                fastChunkStats.parsedChunks(),
                fallbackChunkStats.parsedChunks()
        );
        SurfaceMap map = result.surface();
        SurfaceRainHeightDiagnosticCounters diagnostics = result.diagnostics();
        publishSurfaceTiles(
                cache,
                metadata,
                map,
                result,
                surfaceMisses,
                surfacePlanningInputsAvailable
        );

        return new SurfaceMapScanResult(
                map,
                registry,
                chunksScanned,
                diagnostics.columnsScanned(),
                diagnostics.emptyColumns(),
                diagnostics.liquidUnavailableColumns()
        );
    }

    private void consumeSurfaceChunk(
            cartographer.model.ParsedChunk chunk,
            ReadDiagnostics diagnostics,
            Set<ChunkPosition> liquidFailureChunks,
            Consumer<cartographer.model.ParsedChunk> consumer
    ) {
        if (!chunk.liquidLayerAvailable()) {
            ChunkPosition position = new ChunkPosition(
                    chunk.coordinate().x(),
                    chunk.coordinate().y(),
                    chunk.coordinate().z(),
                    0
            );
            if (liquidFailureChunks.add(position)) {
                diagnostics.recordLiquidDecodeFailure(
                        chunk.liquidDecodeError()
                );
            }
        }
        consumer.accept(chunk);
    }

    private void recordChunkReadDiagnostics(
            CacheContext cache,
            String label
    ) {
        reader.lastChunkReadMetrics().ifPresent(metrics ->
                cache.notes.add(formatChunkReadMetrics(label, metrics))
        );
    }

    private String formatChunkReadMetrics(
            String label,
            ChunkReadMetrics metrics
    ) {
        double mib = metrics.payloadBytes() / (1024.0 * 1024.0);
        return String.format(
                java.util.Locale.ROOT,
                "%s read: %s, positions %d, batches %d, prepared %d, executed %d, "
                        + "rows %d, payload %.1f MiB, strategy %.1f ms, source %.1f ms, "
                        + "pipeline-wait %.1f ms, drain %.1f ms, total %.1f ms",
                label,
                metrics.strategy(),
                metrics.uniquePositionsRequested(),
                metrics.batchesExecuted(),
                metrics.statementsPrepared(),
                metrics.statementsExecuted(),
                metrics.rowsFound(),
                mib,
                metrics.strategyProbeNanos() / 1_000_000.0,
                metrics.sourceReadNanos() / 1_000_000.0,
                metrics.decodePipelineWaitNanos() / 1_000_000.0,
                metrics.finalDrainNanos() / 1_000_000.0,
                metrics.totalNanos() / 1_000_000.0
        );
    }

    private Map<MapChunkCoordinate, TerrainTileLookup> lookupTerrain(
            CacheContext cache,
            List<MapChunkCoordinate> coordinates
    ) {
        cache.terrain.requested = coordinates.size();
        if (!cache.enabled) {
            return misses(coordinates);
        }
        Map<MapChunkCoordinate, TerrainTileLookup> lookups;
        try {
            lookups = cache.terrainStore.orElseThrow().read(coordinates);
        } catch (RuntimeException exception) {
            cache.disableWrites(
                    "terrain cache read disabled: " + exception.getMessage()
            );
            lookups = new LinkedHashMap<>();
            for (MapChunkCoordinate coordinate : coordinates) {
                lookups.put(coordinate, TerrainTileLookup.corrupt());
            }
        }
        for (TerrainTileLookup lookup : lookups.values()) {
            switch (lookup.status()) {
                case HIT -> cache.terrain.hits++;
                case MISS -> cache.terrain.misses++;
                case CORRUPT -> cache.terrain.corruptOrIncompatible++;
            }
        }
        return lookups;
    }

    private Map<MapChunkCoordinate, SurfaceTileLookup> lookupSurface(
            CacheContext cache,
            List<MapChunkCoordinate> coordinates,
            WorldMetadata metadata
    ) {
        cache.surface.requested = coordinates.size();
        if (!cache.enabled) {
            return surfaceMisses(coordinates);
        }
        Map<MapChunkCoordinate, SurfaceTileLookup> raw;
        try {
            raw = cache.surfaceStore.orElseThrow().read(coordinates);
        } catch (RuntimeException exception) {
            cache.disableWrites(
                    "Surface cache read disabled: " + exception.getMessage()
            );
            raw = new LinkedHashMap<>();
            for (MapChunkCoordinate coordinate : coordinates) {
                raw.put(coordinate, SurfaceTileLookup.corrupt());
            }
        }

        Map<MapChunkCoordinate, SurfaceTileLookup> result = new LinkedHashMap<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            SurfaceTileLookup lookup =
                    raw.getOrDefault(coordinate, SurfaceTileLookup.miss());
            if (lookup.status() == SurfaceTileLookup.Status.HIT
                    && !lookup.tile().matchesWorld(metadata)) {
                cache.surface.corruptOrIncompatible++;
                cache.surface.worldMismatches++;
                result.put(coordinate, SurfaceTileLookup.corrupt());
            } else {
                result.put(coordinate, lookup);
                switch (lookup.status()) {
                    case HIT -> cache.surface.hits++;
                    case MISS -> cache.surface.misses++;
                    case CORRUPT -> cache.surface.corruptOrIncompatible++;
                }
            }
        }
        return result;
    }

    private void publishTerrain(
            CacheContext cache,
            List<TerrainHeightTile> buffer
    ) {
        if (buffer.isEmpty()) return;
        if (cache.enabled && cache.writesEnabled) {
            try {
                cache.terrainStore.orElseThrow().publish(List.copyOf(buffer));
                cache.terrain.published =
                        Math.addExact(cache.terrain.published, buffer.size());
            } catch (RuntimeException exception) {
                cache.disableWrites(
                        "terrain cache write disabled: " + exception.getMessage()
                );
            }
        }
        buffer.clear();
    }

    private void publishSurfaceTiles(
            CacheContext cache,
            WorldMetadata metadata,
            SurfaceMap map,
            SurfaceRainHeightScanResult result,
            Set<MapChunkCoordinate> surfaceMisses,
            Set<MapChunkCoordinate> surfacePlanningInputsAvailable
    ) {
        if (!cache.enabled || !cache.writesEnabled || surfaceMisses.isEmpty()) {
            return;
        }
        Set<MapChunkCoordinate> fallback =
                new HashSet<>(result.fallbackMapChunks());
        List<SurfaceCacheTile> buffer =
                new ArrayList<>(CACHE_WRITE_BATCH_SIZE);

        for (MapChunkCoordinate coordinate : surfaceMisses) {
            boolean fallbackMode = fallback.contains(coordinate);
            SurfaceTileDiagnosticSummary fallbackSummary = fallbackMode
                    ? result.fallbackDiagnosticsByMapChunk().get(coordinate)
                    : null;

            // RainHeight-fast tiles require authoritative mapchunk planning
            // input. A fallback tile is different: the established full
            // fallback scan is itself the authoritative source for that
            // mapchunk coordinate, even when the complete world-index catalog
            // proves the mapchunk row is absent. In that case the terminal
            // fallback summary is the completeness proof needed for cache
            // publication.
            boolean publishableWithoutPlanningInput =
                    fallbackMode && fallbackSummary != null;
            if (!surfacePlanningInputsAvailable.contains(coordinate)
                    && !publishableWithoutPlanningInput) {
                cache.surface.skippedIncompleteForPublish++;
                continue;
            }
            try {
                SurfaceTile tile = map.tileAt(
                        map.layout().tileIndex(coordinate.x(), coordinate.z())
                );
                if (fallbackMode && fallbackSummary == null) {
                    cache.surface.skippedIncompleteForPublish++;
                    continue;
                }
                SurfaceCacheTile cached = SurfaceCacheTile.fromComplete(
                        tile,
                        metadata,
                        fallbackMode
                                ? SurfaceCacheTile.SourceMode.FALLBACK
                                : SurfaceCacheTile.SourceMode.RAIN_HEIGHT_FAST,
                        fallbackMode
                                ? fallbackSummary.columnsScanned()
                                : countActiveConsidered(tile),
                        fallbackMode ? fallbackSummary.emptyColumns() : 0,
                        fallbackMode
                                ? fallbackSummary.liquidUnavailableColumns()
                                : 0
                );
                buffer.add(cached);
                if (buffer.size() >= CACHE_WRITE_BATCH_SIZE) {
                    publishSurface(cache, buffer);
                }
            } catch (RuntimeException exception) {
                cache.surface.skippedIncompleteForPublish++;
            }
        }
        publishSurface(cache, buffer);
    }

    private void publishSurface(
            CacheContext cache,
            List<SurfaceCacheTile> buffer
    ) {
        if (buffer.isEmpty()) return;
        if (cache.writesEnabled) {
            try {
                cache.surfaceStore.orElseThrow().publish(List.copyOf(buffer));
                cache.surface.published =
                        Math.addExact(cache.surface.published, buffer.size());
            } catch (RuntimeException exception) {
                cache.disableWrites(
                        "Surface cache write disabled: " + exception.getMessage()
                );
            }
        }
        buffer.clear();
    }

    private static int countActiveConsidered(SurfaceTile tile) {
        int count = 0;
        for (int localZ = 0; localZ < tile.height(); localZ++) {
            for (int localX = 0; localX < tile.width(); localX++) {
                if (tile.isActive(localX, localZ)
                        && tile.isConsidered(localX, localZ)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Map<MapChunkCoordinate, TerrainTileLookup> misses(
            List<MapChunkCoordinate> coordinates
    ) {
        Map<MapChunkCoordinate, TerrainTileLookup> result =
                new LinkedHashMap<>();
        coordinates.forEach(
                coordinate -> result.put(
                        coordinate,
                        TerrainTileLookup.miss()
                )
        );
        return result;
    }

    private static Map<MapChunkCoordinate, SurfaceTileLookup> surfaceMisses(
            List<MapChunkCoordinate> coordinates
    ) {
        Map<MapChunkCoordinate, SurfaceTileLookup> result =
                new LinkedHashMap<>();
        coordinates.forEach(
                coordinate -> result.put(
                        coordinate,
                        SurfaceTileLookup.miss()
                )
        );
        return result;
    }

    private CacheContext prepareCache(Path savePath) {
        if (renderDataCacheStore.isEmpty()) {
            return CacheContext.disabled("PF-1.7 render-data cache disabled");
        }
        try {
            RenderDataCacheStore store = renderDataCacheStore.orElseThrow();
            Optional<WorldDataSnapshot> snapshot =
                    WorldDataSnapshot.openOrCreate(store, savePath);
            if (snapshot.isEmpty()) {
                return CacheContext.disabled(
                        "world-data snapshot unavailable or incompatible manifest"
                );
            }
            WorldDataSnapshot world = snapshot.orElseThrow();
            return CacheContext.enabled(
                    world.terrainStore(),
                    world.surfaceStore(),
                    world.indexCatalogStore()
            );
        } catch (RuntimeException exception) {
            return CacheContext.disabled(
                    "world-data snapshot unavailable: " + exception.getMessage()
            );
        }
    }

    private static final class CacheContext {
        private final boolean enabled;
        private final Optional<TerrainTileStore> terrainStore;
        private final Optional<SurfaceTileStore> surfaceStore;
        private final Optional<WorldIndexCatalogStore> indexCatalogStore;
        private final CacheCounters terrain = new CacheCounters();
        private final CacheCounters surface = new CacheCounters();
        private final List<String> notes = new ArrayList<>();
        private boolean writesEnabled;

        private CacheContext(
                boolean enabled,
                Optional<TerrainTileStore> terrainStore,
                Optional<SurfaceTileStore> surfaceStore,
                Optional<WorldIndexCatalogStore> indexCatalogStore,
                String note
        ) {
            this.enabled = enabled;
            this.terrainStore = terrainStore;
            this.surfaceStore = surfaceStore;
            this.indexCatalogStore = indexCatalogStore;
            this.writesEnabled = enabled;
            if (note != null && !note.isBlank()) {
                notes.add(note);
            }
        }

        private static CacheContext enabled(
                TerrainTileStore terrainStore,
                SurfaceTileStore surfaceStore,
                WorldIndexCatalogStore indexCatalogStore
        ) {
            return new CacheContext(
                    true,
                    Optional.of(terrainStore),
                    Optional.of(surfaceStore),
                    Optional.of(indexCatalogStore),
                    null
            );
        }

        private static CacheContext disabled(String note) {
            return new CacheContext(
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    note
            );
        }

        private Set<MapChunkCoordinate> knownAbsent(
                java.util.Collection<MapChunkCoordinate> coordinates
        ) {
            if (!enabled || indexCatalogStore.isEmpty() || coordinates.isEmpty()) {
                return Set.of();
            }
            try {
                WorldIndexCatalogStore catalog = indexCatalogStore.orElseThrow();
                if (!catalog.mapChunkScanComplete()) {
                    return Set.of();
                }
                Set<MapChunkCoordinate> observed = catalog.observedAmong(coordinates);
                LinkedHashSet<MapChunkCoordinate> absent =
                        new LinkedHashSet<>(coordinates);
                absent.removeAll(observed);
                if (!absent.isEmpty()) {
                    notes.add(
                            "world snapshot catalog skipped "
                                    + absent.size()
                                    + " known-unobserved mapchunk source lookups"
                    );
                }
                return Set.copyOf(absent);
            } catch (RuntimeException exception) {
                notes.add(
                        "world snapshot catalog optimization unavailable: "
                                + exception.getMessage()
                );
                return Set.of();
            }
        }

        private void disableWrites(String note) {
            writesEnabled = false;
            if (note != null && !note.isBlank()) {
                notes.add(note);
            }
        }

        private RenderDataCacheReport report() {
            return new RenderDataCacheReport(
                    enabled,
                    terrain.toStats(),
                    surface.toStats(),
                    notes
            );
        }
    }

    private static final class CacheCounters {
        private int requested;
        private int hits;
        private int misses;
        private int corruptOrIncompatible;
        private int sourceLoaded;
        private int published;
        private int skippedIncompleteForPublish;
        private int worldMismatches;

        private RenderDataCacheReport.ArtifactStats toStats() {
            return new RenderDataCacheReport.ArtifactStats(
                    requested,
                    hits,
                    misses,
                    corruptOrIncompatible,
                    sourceLoaded,
                    published,
                    skippedIncompleteForPublish,
                    worldMismatches
            );
        }
    }
}
