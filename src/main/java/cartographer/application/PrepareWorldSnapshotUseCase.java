package cartographer.application;

import cartographer.environment.EnvironmentInterpreter;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.geology.rock.RockCatalog;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.perf.MapRegionSnapshotEntry;
import cartographer.perf.MapRegionSnapshotRead;
import cartographer.perf.MapRegionSnapshotStore;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.ResourceBlockCatalog;
import cartographer.perf.ResourceChunkBatchIndexer;
import cartographer.perf.ResourceChunkIndexEntry;
import cartographer.perf.ResourceChunkIndexLookup;
import cartographer.perf.ResourceIndexStore;
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
import cartographer.perf.WorldSnapshotHeader;
import cartographer.perf.WorldSnapshotPreparationSummary;
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
 * PF-2 world preparation operation. PF-2.3 prepares revision-scoped Terrain
 * and Surface coverage; PF-2.4 extends the same snapshot with interpreted
 * mapregion state and UPPER_ROCK tiles; PF-2.5 adds compact source-derived
 * actual-resource membership/occurrence coverage.
 *
 * <p>The source save remains read-only and is owned by one operation-scoped
 * {@link SaveSession}. Source payloads and decoded chunks are never retained.
 * Existing valid derived artifacts are reused and missing/corrupt coverage is
 * rebuilt in bounded batches.</p>
 */
public final class PrepareWorldSnapshotUseCase {
    private static final int TERRAIN_BATCH_SIZE = 128;
    private static final int MAPREGION_BATCH_SIZE = 64;

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
        progress.start("Preparing world snapshot");

        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cacheStore,
                request.savePath()
        ).orElseThrow(() -> new IllegalStateException(
                "world-data snapshot is unavailable"
        ));
        WorldMetadata metadata = session.snapshot().metadata();
        Map<Integer, BlockInfo> registry = session.snapshot().blockRegistry();
        if (snapshot.headerStore().read().isEmpty()) {
            Optional<cartographer.model.WorldPosition> player;
            try {
                player = Optional.of(
                        reader.readPlayerPosition(session, progress)
                );
            } catch (RuntimeException unavailable) {
                player = Optional.empty();
            }
            snapshot.headerStore().publish(
                    new WorldSnapshotHeader(
                            metadata,
                            registry,
                            player
                    )
            );
        }
        TerrainTileStore terrainStore = snapshot.terrainStore();
        SurfaceTileStore surfaceStore = snapshot.surfaceStore();
        WorldIndexCatalogStore indexStore = snapshot.indexCatalogStore();
        MapRegionSnapshotStore mapRegionStore = snapshot.mapRegionStore();
        UpperRockTileStore upperRockTileStore =
                snapshot.upperRockTileStore();
        ResourceIndexStore resourceIndexStore =
                snapshot.resourceIndexStore();

        ReadDiagnostics mapChunkDiagnostics = new ReadDiagnostics();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        ReadDiagnostics mapRegionDiagnostics = new ReadDiagnostics();
        ReadDiagnostics rockDiagnostics = new ReadDiagnostics();
        ReadDiagnostics resourceDiagnostics = new ReadDiagnostics();
        Counters counters = new Counters();

        ProgressReporter terrainProgress =
                phase(progress, 1, 5, "Terrain");
        boolean catalogWasComplete = indexStore.mapChunkScanComplete();
        if (!catalogWasComplete) {
            discoverObservedMapChunks(
                    session,
                    terrainStore,
                    indexStore,
                    mapChunkDiagnostics,
                    counters,
                    terrainProgress
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
                    terrainProgress
            );
        }

        boolean terrainComplete = terrainCoverageComplete(
                terrainStore,
                observed
        );

        List<List<MapChunkCoordinate>> batches =
                batchPlanner.plan(observed);
        ProgressReporter surfaceProgress =
                phase(progress, 2, 5, "Surface");
        surfaceProgress.start("Indexing snapshot");
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
                    surfaceProgress
            );
            surfaceProgress.progress(
                    "Indexing snapshot",
                    index + 1,
                    batches.size()
            );
        }
        surfaceProgress.done("Snapshot indexing complete");

        boolean surfaceComplete = surfaceCoverageComplete(
                surfaceStore,
                observed,
                metadata
        );

        ProgressReporter mapRegionProgress =
                phase(progress, 3, 5, "Map regions");
        boolean mapRegionComplete = indexMapRegionSnapshot(
                session,
                mapRegionStore,
                mapRegionDiagnostics,
                counters,
                mapRegionProgress
        );

        ProgressReporter rockProgress =
                phase(progress, 4, 5, "Geology");
        RockCatalog rockCatalog = RockCatalog.from(registry);
        boolean upperRockComplete = indexUpperRockSnapshot(
                session,
                metadata,
                observed,
                batches,
                rockCatalog,
                upperRockTileStore,
                rockDiagnostics,
                counters,
                rockProgress
        );

        ProgressReporter resourceProgress =
                phase(progress, 5, 5, "Resources");
        ResourceBlockCatalog resourceCatalog =
                ResourceBlockCatalog.from(registry);
        boolean resourceIndexComplete = indexResourceSnapshot(
                session,
                metadata,
                observed,
                batches,
                resourceCatalog,
                resourceIndexStore,
                resourceDiagnostics,
                counters,
                resourceProgress
        );

        PrepareWorldSnapshotResult result = new PrepareWorldSnapshotResult(
                snapshot.revisionHash(),
                observed.size(),
                counters.terrainHits,
                counters.terrainPublished,
                counters.surfaceHits,
                counters.surfacePublished,
                counters.surfaceSkippedIncomplete,
                counters.mapRegionHits,
                counters.mapRegionPublished,
                counters.upperRockHits,
                counters.upperRockPublished,
                counters.resourceBlocksCatalogued,
                counters.resourceChunkHits,
                counters.resourceChunksPublished,
                counters.resourceOccurrenceColumnsPublished,
                indexStore.mapChunkScanComplete(),
                terrainComplete,
                surfaceComplete,
                mapRegionComplete,
                upperRockComplete,
                resourceIndexComplete,
                mapChunkDiagnostics,
                chunkDiagnostics,
                mapRegionDiagnostics,
                rockDiagnostics,
                resourceDiagnostics
        );
        snapshot.preparationSummaryStore().publish(
                new WorldSnapshotPreparationSummary(
                        result.revisionHash(),
                        result.observedMapChunks(),
                        result.mapChunkCatalogComplete(),
                        result.terrainCoverageComplete(),
                        result.surfaceCoverageComplete(),
                        result.mapRegionCoverageComplete(),
                        result.upperRockCoverageComplete(),
                        result.resourceIndexCoverageComplete()
                )
        );
        progress.done(
                result.complete()
                        ? "World snapshot prepared"
                        : "World snapshot preparation complete with partial coverage"
        );
        return result;
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

    private boolean indexMapRegionSnapshot(
            SaveSession session,
            MapRegionSnapshotStore store,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        MapRegionSnapshotRead existing = store.readAll();
        if (existing.healthyComplete()) {
            counters.mapRegionHits = Math.addExact(
                    counters.mapRegionHits,
                    existing.entries().size()
            );
            return true;
        }

        store.markScanIncomplete();
        if (existing.corruptRows() > 0) {
            store.clearEntries();
        }

        List<MapRegionSnapshotEntry> buffer =
                new ArrayList<>(MAPREGION_BATCH_SIZE);
        MapRegionStreamStats stats = reader.forEachObservedMapRegion(
                session,
                diagnostics,
                region -> {
                    buffer.add(
                            new MapRegionSnapshotEntry(
                                    region.coordinate(),
                                    environmentInterpreter.interpret(region),
                                    geologicProvinceInterpreter.summarize(region),
                                    region.oreMaps()
                            )
                    );
                    if (buffer.size() >= MAPREGION_BATCH_SIZE) {
                        publishMapRegionBatch(
                                store,
                                buffer,
                                counters
                        );
                    }
                },
                progress
        );
        publishMapRegionBatch(store, buffer, counters);

        if (stats.complete()) {
            store.markScanComplete();
        }
        return store.readAll().healthyComplete();
    }

    private void publishMapRegionBatch(
            MapRegionSnapshotStore store,
            List<MapRegionSnapshotEntry> buffer,
            Counters counters
    ) {
        if (buffer.isEmpty()) return;
        List<MapRegionSnapshotEntry> publish = List.copyOf(buffer);
        store.publish(publish);
        counters.mapRegionPublished = Math.addExact(
                counters.mapRegionPublished,
                publish.size()
        );
        buffer.clear();
    }

    private boolean indexUpperRockSnapshot(
            SaveSession session,
            WorldMetadata metadata,
            List<MapChunkCoordinate> observed,
            List<List<MapChunkCoordinate>> batches,
            RockCatalog catalog,
            UpperRockTileStore store,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        if (observed.isEmpty()) {
            return true;
        }
        if (catalog.rocks().isEmpty()) {
            progress.done(
                    "UPPER_ROCK snapshot unavailable: no natural rock registry"
            );
            return false;
        }

        progress.start("Indexing UPPER_ROCK snapshot");
        for (int index = 0; index < batches.size(); index++) {
            indexUpperRockBatch(
                    session,
                    metadata,
                    batches.get(index),
                    catalog,
                    store,
                    diagnostics,
                    counters,
                    progress
            );
            progress.progress(
                    "Indexing UPPER_ROCK snapshot",
                    index + 1,
                    batches.size()
            );
        }
        progress.done("UPPER_ROCK snapshot indexing complete");
        return upperRockCoverageComplete(store, observed, metadata);
    }

    private void indexUpperRockBatch(
            SaveSession session,
            WorldMetadata metadata,
            List<MapChunkCoordinate> batch,
            RockCatalog catalog,
            UpperRockTileStore store,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        Map<MapChunkCoordinate, UpperRockTileLookup> existing =
                store.read(batch);
        List<MapChunkCoordinate> missing = new ArrayList<>();
        for (MapChunkCoordinate coordinate : batch) {
            UpperRockTileLookup lookup = existing.getOrDefault(
                    coordinate,
                    UpperRockTileLookup.miss()
            );
            if (lookup.status() == UpperRockTileLookup.Status.HIT
                    && lookup.tile().matchesWorld(metadata)) {
                counters.upperRockHits++;
            } else {
                missing.add(coordinate);
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        UpperRockTileBatchIndexer indexer =
                new UpperRockTileBatchIndexer(
                        metadata,
                        missing,
                        catalog
                );
        List<cartographer.model.ChunkPosition> positions =
                indexer.positions();
        if (!positions.isEmpty()) {
            reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    session,
                    positions,
                    indexer.wantedBlockIds(),
                    diagnostics,
                    indexer::accept,
                    progress
            );
        }

        List<UpperRockTile> publish = indexer.finish();
        if (!publish.isEmpty()) {
            store.publish(publish);
            counters.upperRockPublished = Math.addExact(
                    counters.upperRockPublished,
                    publish.size()
            );
        }
    }

    private boolean upperRockCoverageComplete(
            UpperRockTileStore store,
            List<MapChunkCoordinate> coordinates,
            WorldMetadata metadata
    ) {
        List<List<MapChunkCoordinate>> batches =
                batchPlanner.plan(coordinates);
        for (List<MapChunkCoordinate> batch : batches) {
            Map<MapChunkCoordinate, UpperRockTileLookup> lookups =
                    store.read(batch);
            for (MapChunkCoordinate coordinate : batch) {
                UpperRockTileLookup lookup = lookups.getOrDefault(
                        coordinate,
                        UpperRockTileLookup.miss()
                );
                if (lookup.status() != UpperRockTileLookup.Status.HIT
                        || !lookup.tile().matchesWorld(metadata)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean indexResourceSnapshot(
            SaveSession session,
            WorldMetadata metadata,
            List<MapChunkCoordinate> observed,
            List<List<MapChunkCoordinate>> batches,
            ResourceBlockCatalog catalog,
            ResourceIndexStore store,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        counters.resourceBlocksCatalogued = catalog.blocks().size();
        store.publishBlockCatalog(catalog.blocks());

        if (catalog.isEmpty()) {
            if (!store.scanComplete()) {
                store.markScanComplete();
            }
            return true;
        }

        boolean markerInvalidated = !store.scanComplete();
        if (markerInvalidated) {
            store.markScanIncomplete();
        }

        progress.start("Indexing resource snapshot");
        for (int batchIndex = 0;
             batchIndex < batches.size();
             batchIndex++) {
            List<ChunkPosition> positions = resourceChunkPositions(
                    metadata,
                    batches.get(batchIndex)
            );
            Map<ChunkPosition, ResourceChunkIndexLookup> existing =
                    store.readCoverage(positions);
            List<ChunkPosition> missing = new ArrayList<>();
            for (ChunkPosition position : positions) {
                ResourceChunkIndexLookup lookup = existing.getOrDefault(
                        position,
                        ResourceChunkIndexLookup.miss()
                );
                if (lookup.status()
                        == ResourceChunkIndexLookup.Status.HIT) {
                    counters.resourceChunkHits++;
                } else {
                    missing.add(position);
                }
            }

            if (!missing.isEmpty()) {
                if (!markerInvalidated) {
                    store.markScanIncomplete();
                    markerInvalidated = true;
                }
                ResourceChunkBatchIndexer indexer =
                        new ResourceChunkBatchIndexer(
                                metadata,
                                missing,
                                catalog
                        );
                reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                        session,
                        indexer.positions(),
                        indexer.wantedBlockIds(),
                        diagnostics,
                        indexer::accept,
                        progress
                );
                List<ResourceChunkIndexEntry> publish =
                        indexer.finish();
                store.publish(publish);
                counters.resourceChunksPublished = Math.addExact(
                        counters.resourceChunksPublished,
                        publish.size()
                );
                for (ResourceChunkIndexEntry entry : publish) {
                    counters.resourceOccurrenceColumnsPublished =
                            Math.addExact(
                                    counters.resourceOccurrenceColumnsPublished,
                                    entry.occurrences().size()
                            );
                }
            }

            progress.progress(
                    "Indexing resource snapshot",
                    batchIndex + 1,
                    batches.size()
            );
        }

        boolean complete = resourceCoverageComplete(
                store,
                metadata,
                observed
        );
        if (complete) {
            if (markerInvalidated) {
                store.markScanComplete();
            }
        } else if (!markerInvalidated) {
            store.markScanIncomplete();
        }
        progress.done("Resource snapshot indexing complete");
        return complete;
    }

    private boolean resourceCoverageComplete(
            ResourceIndexStore store,
            WorldMetadata metadata,
            List<MapChunkCoordinate> observed
    ) {
        List<List<MapChunkCoordinate>> batches =
                batchPlanner.plan(observed);
        for (List<MapChunkCoordinate> batch : batches) {
            List<ChunkPosition> positions =
                    resourceChunkPositions(metadata, batch);
            Map<ChunkPosition, ResourceChunkIndexLookup> lookups =
                    store.readCoverage(positions);
            for (ChunkPosition position : positions) {
                if (lookups.getOrDefault(
                        position,
                        ResourceChunkIndexLookup.miss()
                ).status() != ResourceChunkIndexLookup.Status.HIT) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<ChunkPosition> resourceChunkPositions(
            WorldMetadata metadata,
            List<MapChunkCoordinate> coordinates
    ) {
        int verticalChunkCount = Math.toIntExact(
                Math.floorDiv(
                        Math.addExact(
                                (long) metadata.mapSizeY(),
                                ChunkCoordinate.SIZE_BLOCKS - 1L
                        ),
                        ChunkCoordinate.SIZE_BLOCKS
                )
        );
        if (verticalChunkCount <= 0) {
            return List.of();
        }

        List<ChunkPosition> result = new ArrayList<>(
                Math.multiplyExact(
                        coordinates.size(),
                        verticalChunkCount
                )
        );
        for (MapChunkCoordinate coordinate : coordinates) {
            for (int chunkY = 0;
                 chunkY < verticalChunkCount;
                 chunkY++) {
                result.add(
                        new ChunkPosition(
                                coordinate.x(),
                                chunkY,
                                coordinate.z(),
                                0
                        )
                );
            }
        }
        return List.copyOf(result);
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

    private ProgressReporter phase(
            ProgressReporter delegate,
            int phase,
            int totalPhases,
            String label
    ) {
        Objects.requireNonNull(delegate, "delegate is required");
        String prefix = "[" + phase + "/" + totalPhases + "] " + label;
        return new ProgressReporter() {
            @Override
            public void start(String stage) {
                delegate.start(prefix + " — " + stage);
            }

            @Override
            public void progress(
                    String stage,
                    int current,
                    int total
            ) {
                delegate.progress(
                        prefix + " — " + stage,
                        current,
                        total
                );
            }

            @Override
            public void done(String stage) {
                delegate.done(prefix + " — " + stage);
            }
        };
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
        private int mapRegionHits;
        private int mapRegionPublished;
        private int upperRockHits;
        private int upperRockPublished;
        private int resourceBlocksCatalogued;
        private int resourceChunkHits;
        private int resourceChunksPublished;
        private long resourceOccurrenceColumnsPublished;
    }
}
