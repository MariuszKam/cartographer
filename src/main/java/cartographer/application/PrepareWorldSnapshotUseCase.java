package cartographer.application;

import cartographer.environment.EnvironmentInterpreter;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.geology.rock.RockCatalog;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.perf.MapRegionSnapshotEntry;
import cartographer.perf.MapRegionSnapshotRead;
import cartographer.perf.MapRegionSnapshotStore;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.SurfaceCacheTile;
import cartographer.perf.SurfaceTileLookup;
import cartographer.perf.SurfaceTileStore;
import cartographer.perf.TerrainHeightTile;
import cartographer.perf.TerrainTileLookup;
import cartographer.perf.TerrainTileStore;
import cartographer.perf.UpperRockTile;
import cartographer.perf.UpperRockTileBatchIndexer;
import cartographer.perf.UpperRockTileLookup;
import cartographer.perf.UpperRockTileStore;
import cartographer.perf.WorldDataSnapshot;
import cartographer.perf.WorldIndexCatalogStore;
import cartographer.save.MapRegionStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceFallbackChunkPlanner;
import cartographer.scanner.SurfaceRainHeightPlan;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceStreamingSession;
import cartographer.scanner.SurfaceTile;
import cartographer.scanner.SurfaceTileDiagnosticSummary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * PF-2.3 operation that prepares revision-scoped Terrain and Surface coverage
 * for every observed main-world mapchunk.
 *
 * <p>The source save remains read-only and is owned by one operation-scoped
 * {@link SaveSession}. Source payloads are never retained. Existing valid
 * derived tiles are reused, missing/corrupt tiles are rebuilt, and Surface
 * indexing runs in bounded spatial batches.</p>
 */
public final class PrepareWorldSnapshotUseCase {
    private static final int TERRAIN_BATCH_SIZE = 128;

    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final RenderDataCacheStore cacheStore;
    private final WorldIndexBatchPlanner batchPlanner;
    private final SurfaceFallbackChunkPlanner fallbackPlanner =
            new SurfaceFallbackChunkPlanner();
    private final EnvironmentInterpreter environmentInterpreter =
            new EnvironmentInterpreter();
    private final GeologicProvinceInterpreter geologicProvinceInterpreter =
            new GeologicProvinceInterpreter();

    public PrepareWorldSnapshotUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            RenderDataCacheStore cacheStore
    ) {
        this(
                reader,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        metadataReader
                ),
                cacheStore,
                new WorldIndexBatchPlanner()
        );
    }

    PrepareWorldSnapshotUseCase(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore cacheStore,
            WorldIndexBatchPlanner batchPlanner
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "sessionFactory is required"
        );
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
        this.batchPlanner = Objects.requireNonNull(
                batchPlanner,
                "batchPlanner is required"
        );
    }

    public PrepareWorldSnapshotResult execute(
            PrepareWorldSnapshotRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");
        try (SaveSession session = sessionFactory.open(request.savePath())) {
            return execute(session, request, progress);
        }
    }

    public PrepareWorldSnapshotResult execute(
            SaveSession session,
            PrepareWorldSnapshotRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");
        session.requireSameSave(request.savePath());

        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cacheStore,
                request.savePath()
        ).orElseThrow(() -> new IllegalStateException(
                "world-data snapshot is unavailable"
        ));
        WorldMetadata metadata = session.snapshot().metadata();
        Map<Integer, BlockInfo> registry = session.snapshot().blockRegistry();
        TerrainTileStore terrainStore = snapshot.terrainStore();
        SurfaceTileStore surfaceStore = snapshot.surfaceStore();
        WorldIndexCatalogStore indexStore = snapshot.indexCatalogStore();
        MapRegionSnapshotStore mapRegionStore = snapshot.mapRegionStore();
        UpperRockTileStore upperRockTileStore =
                snapshot.upperRockTileStore();

        ReadDiagnostics mapChunkDiagnostics = new ReadDiagnostics();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        ReadDiagnostics mapRegionDiagnostics = new ReadDiagnostics();
        ReadDiagnostics rockDiagnostics = new ReadDiagnostics();
        Counters counters = new Counters();

        boolean catalogWasComplete = indexStore.mapChunkScanComplete();
        if (!catalogWasComplete) {
            discoverObservedMapChunks(
                    session,
                    terrainStore,
                    indexStore,
                    mapChunkDiagnostics,
                    counters,
                    progress
            );
            indexStore.markMapChunkScanComplete();
        }

        List<MapChunkCoordinate> observed =
                indexStore.observedMapChunks();

        if (catalogWasComplete) {
            repairTerrainCoverage(
                    session,
                    terrainStore,
                    observed,
                    mapChunkDiagnostics,
                    counters,
                    progress
            );
        }

        boolean terrainComplete = terrainCoverageComplete(
                terrainStore,
                observed
        );

        List<List<MapChunkCoordinate>> batches =
                batchPlanner.plan(observed);
        progress.start("Indexing Surface snapshot");
        for (int index = 0; index < batches.size(); index++) {
            indexSurfaceBatch(
                    session,
                    metadata,
                    registry,
                    terrainStore,
                    surfaceStore,
                    batches.get(index),
                    chunkDiagnostics,
                    counters,
                    progress
            );
            progress.progress(
                    "Indexing Surface snapshot",
                    index + 1,
                    batches.size()
            );
        }
        progress.done("Surface snapshot indexing complete");

        boolean surfaceComplete = surfaceCoverageComplete(
                surfaceStore,
                observed,
                metadata
        );

        return new PrepareWorldSnapshotResult(
                snapshot.revisionHash(),
                observed.size(),
                counters.terrainHits,
                counters.terrainPublished,
                counters.surfaceHits,
                counters.surfacePublished,
                counters.surfaceSkippedIncomplete,
                indexStore.mapChunkScanComplete(),
                terrainComplete,
                surfaceComplete,
                mapChunkDiagnostics,
                chunkDiagnostics
        );
    }

    private void discoverObservedMapChunks(
            SaveSession session,
            TerrainTileStore terrainStore,
            WorldIndexCatalogStore indexStore,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        List<MapChunkCoordinate> observedBuffer =
                new ArrayList<>(TERRAIN_BATCH_SIZE);
        List<MapChunk> terrainBuffer =
                new ArrayList<>(TERRAIN_BATCH_SIZE);
        reader.forEachObservedMapChunk(
                session,
                diagnostics,
                coordinate -> {
                    observedBuffer.add(coordinate);
                    if (observedBuffer.size() >= TERRAIN_BATCH_SIZE) {
                        indexStore.recordObserved(
                                List.copyOf(observedBuffer)
                        );
                        observedBuffer.clear();
                    }
                },
                mapChunk -> {
                    terrainBuffer.add(mapChunk);
                    if (terrainBuffer.size() >= TERRAIN_BATCH_SIZE) {
                        publishDiscoveryTerrainBatch(
                                terrainStore,
                                terrainBuffer,
                                counters
                        );
                    }
                },
                progress
        );
        if (!observedBuffer.isEmpty()) {
            indexStore.recordObserved(List.copyOf(observedBuffer));
            observedBuffer.clear();
        }
        publishDiscoveryTerrainBatch(
                terrainStore,
                terrainBuffer,
                counters
        );
    }

    private void publishDiscoveryTerrainBatch(
            TerrainTileStore terrainStore,
            List<MapChunk> buffer,
            Counters counters
    ) {
        if (buffer.isEmpty()) {
            return;
        }
        List<MapChunkCoordinate> coordinates = buffer.stream()
                .map(MapChunk::coordinate)
                .toList();

        Map<MapChunkCoordinate, TerrainTileLookup> lookups =
                terrainStore.read(coordinates);
        List<TerrainHeightTile> publish = new ArrayList<>();
        for (MapChunk mapChunk : buffer) {
            TerrainTileLookup lookup = lookups.getOrDefault(
                    mapChunk.coordinate(),
                    TerrainTileLookup.miss()
            );
            if (lookup.status() == TerrainTileLookup.Status.HIT) {
                counters.terrainHits++;
            } else {
                publish.add(TerrainHeightTile.from(mapChunk));
            }
        }
        if (!publish.isEmpty()) {
            terrainStore.publish(publish);
            counters.terrainPublished = Math.addExact(
                    counters.terrainPublished,
                    publish.size()
            );
        }
        buffer.clear();
    }

    private void repairTerrainCoverage(
            SaveSession session,
            TerrainTileStore terrainStore,
            List<MapChunkCoordinate> observed,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        for (int start = 0; start < observed.size(); start += TERRAIN_BATCH_SIZE) {
            List<MapChunkCoordinate> batch = observed.subList(
                    start,
                    Math.min(start + TERRAIN_BATCH_SIZE, observed.size())
            );
            Map<MapChunkCoordinate, TerrainTileLookup> lookups =
                    terrainStore.read(batch);
            List<MapChunkCoordinate> missing = new ArrayList<>();
            for (MapChunkCoordinate coordinate : batch) {
                TerrainTileLookup lookup = lookups.getOrDefault(
                        coordinate,
                        TerrainTileLookup.miss()
                );
                if (lookup.status() == TerrainTileLookup.Status.HIT) {
                    counters.terrainHits++;
                } else {
                    missing.add(coordinate);
                }
            }
            if (missing.isEmpty()) {
                continue;
            }

            List<TerrainHeightTile> rebuilt = new ArrayList<>();
            reader.forEachMapChunkByCoordinate(
                    session,
                    missing,
                    diagnostics,
                    mapChunk -> rebuilt.add(TerrainHeightTile.from(mapChunk)),
                    progress
            );
            if (!rebuilt.isEmpty()) {
                terrainStore.publish(rebuilt);
                counters.terrainPublished = Math.addExact(
                        counters.terrainPublished,
                        rebuilt.size()
                );
            }
        }
    }

    private void indexSurfaceBatch(
            SaveSession session,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            TerrainTileStore terrainStore,
            SurfaceTileStore surfaceStore,
            List<MapChunkCoordinate> batch,
            ReadDiagnostics chunkDiagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        Map<MapChunkCoordinate, SurfaceTileLookup> existing =
                surfaceStore.read(batch);
        List<MapChunkCoordinate> missing = new ArrayList<>();
        for (MapChunkCoordinate coordinate : batch) {
            SurfaceTileLookup lookup = existing.getOrDefault(
                    coordinate,
                    SurfaceTileLookup.miss()
            );
            if (lookup.status() == SurfaceTileLookup.Status.HIT
                    && lookup.tile().matchesWorld(metadata)) {
                counters.surfaceHits++;
            } else {
                missing.add(coordinate);
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        Map<MapChunkCoordinate, TerrainTileLookup> terrain =
                terrainStore.read(missing);
        List<MapChunkCoordinate> ready = new ArrayList<>();
        List<TerrainHeightTile> heightTiles = new ArrayList<>();
        for (MapChunkCoordinate coordinate : missing) {
            TerrainTileLookup lookup = terrain.getOrDefault(
                    coordinate,
                    TerrainTileLookup.miss()
            );
            if (lookup.status() == TerrainTileLookup.Status.HIT) {
                ready.add(coordinate);
                heightTiles.add(lookup.tile());
            } else {
                counters.surfaceSkippedIncomplete++;
            }
        }
        if (ready.isEmpty()) {
            return;
        }

        SurfaceStreamingSession surface = SurfaceStreamingSession.beginForMapChunks(
                metadata,
                ready,
                registry,
                true,
                true
        );
        heightTiles.forEach(surface::acceptMapChunk);
        SurfaceRainHeightPlan plan = surface.finishPlanning();

        if (!plan.chunkPositions().isEmpty()) {
            reader.forEachSurfaceChunkByPositionAdaptive(
                    session,
                    plan.chunkPositions(),
                    chunkDiagnostics,
                    surface::acceptFastChunk,
                    progress
            );
        }

        List<MapChunkCoordinate> fallbackMapChunks =
                surface.fallbackMapChunks();
        List<cartographer.model.ChunkPosition> fallbackPositions =
                fallbackPlanner.plan(metadata, fallbackMapChunks);
        if (!fallbackPositions.isEmpty()) {
            reader.forEachSurfaceChunkByPositionAdaptive(
                    session,
                    fallbackPositions,
                    chunkDiagnostics,
                    surface::acceptFallbackChunk,
                    progress
            );
        }

        SurfaceRainHeightScanResult result = surface.finish();
        Set<MapChunkCoordinate> fallback = new HashSet<>(
                result.fallbackMapChunks()
        );
        List<SurfaceCacheTile> publish = new ArrayList<>();

        for (MapChunkCoordinate coordinate : ready) {
            try {
                SurfaceTile tile = result.surface().tileAt(
                        result.surface().layout().tileIndex(
                                coordinate.x(),
                                coordinate.z()
                        )
                );
                boolean fallbackMode = fallback.contains(coordinate);
                SurfaceTileDiagnosticSummary summary = fallbackMode
                        ? result.fallbackDiagnosticsByMapChunk().get(coordinate)
                        : null;
                if (fallbackMode && summary == null) {
                    counters.surfaceSkippedIncomplete++;
                    continue;
                }
                publish.add(SurfaceCacheTile.fromComplete(
                        tile,
                        metadata,
                        fallbackMode
                                ? SurfaceCacheTile.SourceMode.FALLBACK
                                : SurfaceCacheTile.SourceMode.RAIN_HEIGHT_FAST,
                        fallbackMode
                                ? summary.columnsScanned()
                                : countActiveConsidered(tile),
                        fallbackMode ? summary.emptyColumns() : 0,
                        fallbackMode
                                ? summary.liquidUnavailableColumns()
                                : 0
                ));
            } catch (RuntimeException exception) {
                counters.surfaceSkippedIncomplete++;
            }
        }

        if (!publish.isEmpty()) {
            surfaceStore.publish(publish);
            counters.surfacePublished = Math.addExact(
                    counters.surfacePublished,
                    publish.size()
            );
        }
    }

    private boolean terrainCoverageComplete(
            TerrainTileStore store,
            List<MapChunkCoordinate> coordinates
    ) {
        for (int start = 0; start < coordinates.size(); start += TERRAIN_BATCH_SIZE) {
            List<MapChunkCoordinate> batch = coordinates.subList(
                    start,
                    Math.min(start + TERRAIN_BATCH_SIZE, coordinates.size())
            );
            Map<MapChunkCoordinate, TerrainTileLookup> lookups =
                    store.read(batch);
            for (MapChunkCoordinate coordinate : batch) {
                if (lookups.getOrDefault(
                        coordinate,
                        TerrainTileLookup.miss()
                ).status() != TerrainTileLookup.Status.HIT) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean surfaceCoverageComplete(
            SurfaceTileStore store,
            List<MapChunkCoordinate> coordinates,
            WorldMetadata metadata
    ) {
        List<List<MapChunkCoordinate>> batches =
                batchPlanner.plan(coordinates);
        for (List<MapChunkCoordinate> batch : batches) {
            Map<MapChunkCoordinate, SurfaceTileLookup> lookups =
                    store.read(batch);
            for (MapChunkCoordinate coordinate : batch) {
                SurfaceTileLookup lookup = lookups.getOrDefault(
                        coordinate,
                        SurfaceTileLookup.miss()
                );
                if (lookup.status() != SurfaceTileLookup.Status.HIT
                        || !lookup.tile().matchesWorld(metadata)) {
                    return false;
                }
            }
        }
        return true;
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

    private static final class Counters {
        private int terrainHits;
        private int terrainPublished;
        private int surfaceHits;
        private int surfacePublished;
        private int surfaceSkippedIncomplete;
    }
}
