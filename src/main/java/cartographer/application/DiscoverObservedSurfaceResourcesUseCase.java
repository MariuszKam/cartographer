package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.resource.ObservedSurfaceResourceCatalogBuilder;
import cartographer.resource.SurfaceObjectCandidateCatalog;
import cartographer.resource.SurfaceObjectCandidateCatalogBuilder;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.VcdbsReader;
import cartographer.scanner.SurfaceObjectCompactPlan;
import cartographer.scanner.SurfaceObjectCompactPlanner;
import cartographer.scanner.SurfaceObjectCompactScanResult;
import cartographer.scanner.SurfaceObjectStreamingScanner;

import java.util.List;
import java.util.Map;

/** Discovers observed loose surface objects with one combined selective scan. */
public final class DiscoverObservedSurfaceResourcesUseCase {
    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final MapChunkPositionPlanner mapChunkPositionPlanner =
            new MapChunkPositionPlanner();
    private final SurfaceObjectCompactPlanner planner = new SurfaceObjectCompactPlanner();
    private final SurfaceObjectStreamingScanner scanner = new SurfaceObjectStreamingScanner();
    private final SurfaceObjectCandidateCatalogBuilder candidateBuilder =
            new SurfaceObjectCandidateCatalogBuilder();
    private final ObservedSurfaceResourceCatalogBuilder observedBuilder =
            new ObservedSurfaceResourceCatalogBuilder();

    public DiscoverObservedSurfaceResourcesUseCase(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory
    ) {
        this.reader = java.util.Objects.requireNonNull(
                reader,
                "reader is required"
        );
        this.sessionFactory = java.util.Objects.requireNonNull(
                sessionFactory,
                "sessionFactory is required"
        );
    }

    public DiscoverObservedSurfaceResourcesResult execute(
            DiscoverObservedSurfaceResourcesRequest request
    ) {
        return execute(request, ProgressReporter.NONE);
    }

    public DiscoverObservedSurfaceResourcesResult execute(
            DiscoverObservedSurfaceResourcesRequest request,
            ProgressReporter progress
    ) {
        java.util.Objects.requireNonNull(request, "request is required");
        java.util.Objects.requireNonNull(progress, "progress is required");
        try (SaveSession session = sessionFactory.open(request.savePath())) {
            return execute(session, request, progress);
        }
    }

    public DiscoverObservedSurfaceResourcesResult execute(
            SaveSession session,
            DiscoverObservedSurfaceResourcesRequest request,
            ProgressReporter progress
    ) {
        java.util.Objects.requireNonNull(session, "session is required");
        java.util.Objects.requireNonNull(request, "request is required");
        java.util.Objects.requireNonNull(progress, "progress is required");
        session.requireSameSave(request.savePath());

        WorldMetadata metadata = session.snapshot().metadata();
        WorldPosition center = request.center().orElseGet(
                () -> reader.readPlayerPosition(session, progress)
        );
        Map<Integer, BlockInfo> registry =
                session.snapshot().blockRegistry();
        SurfaceObjectCandidateCatalog candidates =
                candidateBuilder.build(registry);
        ReadDiagnostics mapDiagnostics = new ReadDiagnostics();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        if (candidates.candidateBlockIds().isEmpty()) {
            SurfaceObjectCompactPlan emptyPlan =
                    SurfaceObjectCompactPlan.empty();
            SurfaceObjectCompactScanResult emptyScan =
                    SurfaceObjectCompactScanResult.empty();
            return result(
                    center,
                    candidates,
                    emptyPlan,
                    emptyStats(),
                    emptyScan,
                    mapDiagnostics,
                    chunkDiagnostics
            );
        }

        int centerX = (int) Math.floor(center.x());
        int centerZ = (int) Math.floor(center.z());
        SurfaceObjectCompactPlanner.StreamingSession planning =
                planner.begin(
                        metadata,
                        centerX,
                        centerZ,
                        request.radius()
                );
        List<MapChunkCoordinate> coordinates =
                mapChunkPositionPlanner.plan(
                        metadata,
                        centerX,
                        centerZ,
                        request.radius()
                );
        reader.forEachMapChunkByCoordinate(
                session,
                coordinates,
                mapDiagnostics,
                planning::accept,
                progress
        );
        SurfaceObjectCompactPlan plan = planning.finish();
        int[] wantedIds = candidates.candidateBlockIds().stream()
                .mapToInt(Integer::intValue)
                .toArray();
        SurfaceObjectStreamingScanner.Session scanSession =
                scanner.begin(plan, wantedIds);
        SelectiveChunkStreamStats stats = emptyStats();
        if (!plan.chunkPositions().isEmpty()) {
            stats = reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    session,
                    plan.chunkPositions(),
                    wantedIds,
                    chunkDiagnostics,
                    scanSession::accept,
                    progress
            );
        }
        SurfaceObjectCompactScanResult scan = scanSession.finish();
        return result(
                center,
                candidates,
                plan,
                stats,
                scan,
                mapDiagnostics,
                chunkDiagnostics
        );
    }

    private DiscoverObservedSurfaceResourcesResult result(
            WorldPosition center,
            SurfaceObjectCandidateCatalog candidates,
            SurfaceObjectCompactPlan plan,
            SelectiveChunkStreamStats stats,
            SurfaceObjectCompactScanResult scan,
            ReadDiagnostics mapDiagnostics,
            ReadDiagnostics chunkDiagnostics
    ) {
        ObservedSurfaceResourceCatalog observed = observedBuilder.build(
                candidates,
                scan
        );
        return new DiscoverObservedSurfaceResourcesResult(
                center,
                candidates,
                plan.plannedTargetCount(),
                plan.chunkPositions().size(),
                stats,
                scan,
                observed,
                mapDiagnostics,
                chunkDiagnostics
        );
    }

    private SelectiveChunkStreamStats emptyStats() {
        return new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
