package cartographer.application;

import cartographer.spatial.MapChunkRenderWindowPlanner;
import cartographer.spatial.MapChunkPositionPlanner;
import cartographer.progress.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.cache.RenderDataCacheStore;
import cartographer.cache.SurfaceCacheTile;
import cartographer.cache.SurfaceTileLookup;
import cartographer.cache.TerrainHeightTile;
import cartographer.cache.TerrainTileLookup;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.RenderOptions;
import cartographer.save.ChunkReadMetrics;
import cartographer.save.ChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.VcdbsReader;
import cartographer.scanner.SurfaceFallbackChunkPlanner;
import cartographer.scanner.SurfaceMap;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceRainHeightDiagnosticCounters;
import cartographer.scanner.SurfaceRainHeightPlan;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceStreamingSession;

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
 * Prepares map data from an already-open authoritative save session.
 *
 * <p>This class owns the live source/cache/Surface mechanism. Snapshot-vs-live
 * routing and SaveSession lifecycle remain in {@link PrepareMapDataUseCase}.</p>
 */
final class LiveMapDataPreparer {
    private static final int CACHE_WRITE_BATCH_SIZE = 128;

    private final VcdbsReader reader;
    private final LiveMapDataCache cacheCoordinator;
    private final MapChunkRenderWindowPlanner mapChunkRenderWindowPlanner =
            new MapChunkRenderWindowPlanner();
    private final MapChunkPositionPlanner mapChunkPositionPlanner =
            new MapChunkPositionPlanner();
    private final SurfaceFallbackChunkPlanner surfaceFallbackChunkPlanner =
            new SurfaceFallbackChunkPlanner();

    LiveMapDataPreparer(
            VcdbsReader reader,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.cacheCoordinator = new LiveMapDataCache(
                Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache option is required"
                )
        );
    }

    PreparedMapData prepare(
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
        LiveMapDataCache.Context cache = cacheCoordinator.open(
                request.savePath(),
                request.ignoreFoliage()
        );

        Map<MapChunkCoordinate, SurfaceTileLookup> surfaceLookups = surfaceDataRequired
                ? cacheCoordinator.lookupSurface(cache, surfaceMapChunkCoordinates, metadata)
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
                cacheCoordinator.lookupTerrain(cache, terrainRequiredCoordinates);
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
                            cache.recordTerrainSourceLoaded();
                            terrainWriteBuffer.add(TerrainHeightTile.from(mapChunk));
                            if (terrainWriteBuffer.size() >= CACHE_WRITE_BATCH_SIZE) {
                                cacheCoordinator.publishTerrain(cache, terrainWriteBuffer);
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
        cacheCoordinator.publishTerrain(cache, terrainWriteBuffer);

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
            LiveMapDataCache.Context cache,
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
            recordChunkReadDiagnostics(cache, "Surface fast", fastChunkStats);
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
            recordChunkReadDiagnostics(cache, "Surface fallback", fallbackChunkStats);
        }

        SurfaceRainHeightScanResult result = surfaceSession.finish();
        cache.recordSurfaceSourceLoaded(result.sourceMapChunksLoaded());
        int chunksScanned = Math.addExact(
                fastChunkStats.parsedChunks(),
                fallbackChunkStats.parsedChunks()
        );
        SurfaceMap map = result.surface();
        SurfaceRainHeightDiagnosticCounters diagnostics = result.diagnostics();
        cacheCoordinator.publishSurfaceTiles(
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
            LiveMapDataCache.Context cache,
            String label,
            ChunkStreamStats stats
    ) {
        stats.metrics().ifPresent(metrics ->
                cache.addNote(formatChunkReadMetrics(label, metrics))
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

}
