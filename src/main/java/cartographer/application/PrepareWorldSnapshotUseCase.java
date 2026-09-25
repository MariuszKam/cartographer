package cartographer.application;

import cartographer.cache.RenderDataCacheStore;
import cartographer.geology.rock.RockCatalog;
import cartographer.index.ResourceBlockCatalog;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.progress.ProgressReporter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.snapshot.WorldSnapshotHeader;
import cartographer.snapshot.WorldSnapshotPreparationSummary;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;

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
    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final RenderDataCacheStore cacheStore;
    private final TerrainSnapshotPreparer terrainPreparer;
    private final SurfaceSnapshotPreparer surfacePreparer;
    private final MapRegionSnapshotPreparer mapRegionPreparer;
    private final UpperRockSnapshotPreparer upperRockPreparer;
    private final ResourceSnapshotPreparer resourcePreparer;

    public PrepareWorldSnapshotUseCase(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore cacheStore
    ) {
        this(
                reader,
                sessionFactory,
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
        WorldIndexBatchPlanner planner = Objects.requireNonNull(
                batchPlanner,
                "batchPlanner is required"
        );
        this.terrainPreparer = new TerrainSnapshotPreparer(reader);
        this.surfacePreparer = new SurfaceSnapshotPreparer(reader, planner);
        this.mapRegionPreparer = new MapRegionSnapshotPreparer(reader);
        this.upperRockPreparer = new UpperRockSnapshotPreparer(reader, planner);
        this.resourcePreparer = new ResourceSnapshotPreparer(reader, planner);
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

        ProgressReporter headerProgress = phase(progress, 1, 6, "Header");
        headerProgress.start("Checking snapshot header");
        if (snapshot.headerStore().read().isEmpty()) {
            Optional<cartographer.model.WorldPosition> player;
            try {
                player = Optional.of(
                        reader.readPlayerPosition(session, headerProgress)
                );
            } catch (CancellationException cancellation) {
                throw cancellation;
            } catch (RuntimeException unavailable) {
                player = Optional.empty();
            }
            snapshot.headerStore().publish(
                    new WorldSnapshotHeader(metadata, registry, player)
            );
        }
        headerProgress.done("Header ready");

        WorldSnapshotPreparationSummary previousSummary =
                snapshot.preparationSummaryStore()
                        .read()
                        .orElseGet(() -> new WorldSnapshotPreparationSummary(
                                snapshot.revisionHash(),
                                0,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false
                        ));

        ReadDiagnostics mapChunkDiagnostics = new ReadDiagnostics();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        ReadDiagnostics mapRegionDiagnostics = new ReadDiagnostics();
        ReadDiagnostics rockDiagnostics = new ReadDiagnostics();
        ReadDiagnostics resourceDiagnostics = new ReadDiagnostics();

        TerrainSnapshotPreparer.Result terrain = terrainPreparer.prepare(
                session,
                snapshot.terrainStore(),
                snapshot.indexCatalogStore(),
                mapChunkDiagnostics,
                phase(progress, 2, 6, "Terrain")
        );
        publishPreparationSummary(
                snapshot,
                terrain.observed().size(),
                terrain.catalogComplete(),
                terrain.coverageComplete(),
                previousSummary.surfaceCoverageComplete(),
                previousSummary.mapRegionCoverageComplete(),
                previousSummary.upperRockCoverageComplete(),
                previousSummary.resourceIndexCoverageComplete()
        );

        SurfaceSnapshotPreparer.Result surface = surfacePreparer.prepare(
                session,
                metadata,
                registry,
                snapshot.terrainStore(),
                snapshot.surfaceStore(),
                terrain.observed(),
                chunkDiagnostics,
                phase(progress, 3, 6, "Surface")
        );
        publishPreparationSummary(
                snapshot,
                terrain.observed().size(),
                terrain.catalogComplete(),
                terrain.coverageComplete(),
                surface.coverageComplete(),
                previousSummary.mapRegionCoverageComplete(),
                previousSummary.upperRockCoverageComplete(),
                previousSummary.resourceIndexCoverageComplete()
        );

        MapRegionSnapshotPreparer.Result mapRegion = mapRegionPreparer.prepare(
                session,
                snapshot.mapRegionStore(),
                mapRegionDiagnostics,
                phase(progress, 4, 6, "Map regions")
        );
        publishPreparationSummary(
                snapshot,
                terrain.observed().size(),
                terrain.catalogComplete(),
                terrain.coverageComplete(),
                surface.coverageComplete(),
                mapRegion.coverageComplete(),
                previousSummary.upperRockCoverageComplete(),
                previousSummary.resourceIndexCoverageComplete()
        );

        UpperRockSnapshotPreparer.Result upperRock = upperRockPreparer.prepare(
                session,
                metadata,
                terrain.observed(),
                RockCatalog.from(registry),
                snapshot.upperRockTileStore(),
                rockDiagnostics,
                phase(progress, 5, 6, "Geology")
        );
        publishPreparationSummary(
                snapshot,
                terrain.observed().size(),
                terrain.catalogComplete(),
                terrain.coverageComplete(),
                surface.coverageComplete(),
                mapRegion.coverageComplete(),
                upperRock.coverageComplete(),
                previousSummary.resourceIndexCoverageComplete()
        );

        ResourceSnapshotPreparer.Result resources = resourcePreparer.prepare(
                session,
                metadata,
                terrain.observed(),
                ResourceBlockCatalog.from(registry),
                snapshot.resourceIndexStore(),
                resourceDiagnostics,
                phase(progress, 6, 6, "Resources")
        );

        PrepareWorldSnapshotResult result = new PrepareWorldSnapshotResult(
                snapshot.revisionHash(),
                terrain.observed().size(),
                terrain.hits(),
                terrain.published(),
                surface.hits(),
                surface.published(),
                surface.skippedIncomplete(),
                mapRegion.hits(),
                mapRegion.published(),
                upperRock.hits(),
                upperRock.published(),
                resources.blocksCatalogued(),
                resources.hits(),
                resources.published(),
                resources.occurrenceColumnsPublished(),
                terrain.catalogComplete(),
                terrain.coverageComplete(),
                surface.coverageComplete(),
                mapRegion.coverageComplete(),
                upperRock.coverageComplete(),
                resources.coverageComplete(),
                mapChunkDiagnostics,
                chunkDiagnostics,
                mapRegionDiagnostics,
                rockDiagnostics,
                resourceDiagnostics
        );
        publishPreparationSummary(
                snapshot,
                result.observedMapChunks(),
                result.mapChunkCatalogComplete(),
                result.terrainCoverageComplete(),
                result.surfaceCoverageComplete(),
                result.mapRegionCoverageComplete(),
                result.upperRockCoverageComplete(),
                result.resourceIndexCoverageComplete()
        );
        progress.done(
                result.complete()
                        ? "World snapshot prepared"
                        : "World snapshot preparation complete with partial coverage"
        );
        return result;
    }

    private void publishPreparationSummary(
            WorldDataSnapshot snapshot,
            int observedMapChunks,
            boolean mapChunkCatalogComplete,
            boolean terrainCoverageComplete,
            boolean surfaceCoverageComplete,
            boolean mapRegionCoverageComplete,
            boolean upperRockCoverageComplete,
            boolean resourceIndexCoverageComplete
    ) {
        snapshot.preparationSummaryStore().publish(
                new WorldSnapshotPreparationSummary(
                        snapshot.revisionHash(),
                        observedMapChunks,
                        mapChunkCatalogComplete,
                        terrainCoverageComplete,
                        surfaceCoverageComplete,
                        mapRegionCoverageComplete,
                        upperRockCoverageComplete,
                        resourceIndexCoverageComplete
                )
        );
    }

    private ProgressReporter phase(
            ProgressReporter delegate,
            int phase,
            int totalPhases,
            String label
    ) {
        Objects.requireNonNull(delegate, "delegate is required");
        if (phase <= 0 || phase > totalPhases) {
            throw new IllegalArgumentException(
                    "phase must be inside totalPhases"
            );
        }
        String prefix = "[" + phase + "/" + totalPhases + "] " + label;
        final int unitsPerPhase = 1_000;
        final int totalUnits = Math.multiplyExact(
                totalPhases,
                unitsPerPhase
        );
        final int baseUnits = Math.multiplyExact(
                phase - 1,
                unitsPerPhase
        );
        return new ProgressReporter() {
            private int highWaterUnits = baseUnits;

            @Override
            public void start(String stage) {
                publish(stage, baseUnits);
            }

            @Override
            public void progress(
                    String stage,
                    int current,
                    int total
            ) {
                if (total <= 0) {
                    publish(stage, baseUnits);
                    return;
                }
                double fraction = Math.clamp(
                        current / (double) total,
                        0.0,
                        1.0
                );
                int withinPhase = (int) Math.round(
                        fraction * unitsPerPhase
                );
                publish(stage, baseUnits + withinPhase);
            }

            @Override
            public void done(String stage) {
                publish(stage, baseUnits + unitsPerPhase);
            }

            private void publish(String stage, int candidateUnits) {
                highWaterUnits = Math.max(
                        highWaterUnits,
                        candidateUnits
                );
                delegate.progress(
                        prefix + " — " + stage,
                        highWaterUnits,
                        totalUnits
                );
            }
        };
    }

}
