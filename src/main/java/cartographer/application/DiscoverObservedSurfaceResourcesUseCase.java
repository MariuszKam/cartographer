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
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceObjectCompactPlan;
import cartographer.scanner.SurfaceObjectCompactPlanner;
import cartographer.scanner.SurfaceObjectCompactScanResult;
import cartographer.scanner.SurfaceObjectStreamingScanner;

import java.util.List;
import java.util.Map;

/** Discovers observed loose surface objects with one combined selective scan. */
public final class DiscoverObservedSurfaceResourcesUseCase {
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
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
            WorldMetadataReader metadataReader
    ) {
        this.reader = reader;
        this.metadataReader = metadataReader;
    }

    public DiscoverObservedSurfaceResourcesResult execute(
            DiscoverObservedSurfaceResourcesRequest request
    ) {
        WorldMetadata metadata = metadataReader.read(request.savePath());
        WorldPosition center = request.center().orElseGet(
                () -> reader.readPlayerPosition(request.savePath())
        );
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(request.savePath());
        SurfaceObjectCandidateCatalog candidates = candidateBuilder.build(registry);
        ReadDiagnostics mapDiagnostics = new ReadDiagnostics();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        if (candidates.candidateBlockIds().isEmpty()) {
            SurfaceObjectCompactPlan emptyPlan = SurfaceObjectCompactPlan.empty();
            SurfaceObjectCompactScanResult emptyScan = SurfaceObjectCompactScanResult.empty();
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
        SurfaceObjectCompactPlanner.StreamingSession planning = planner.begin(
                metadata, centerX, centerZ, request.radius()
        );
        List<MapChunkCoordinate> coordinates = mapChunkPositionPlanner.plan(
                metadata, centerX, centerZ, request.radius()
        );
        reader.forEachMapChunkByCoordinate(
                request.savePath(), coordinates, mapDiagnostics, planning::accept
        );
        SurfaceObjectCompactPlan plan = planning.finish();
        SurfaceObjectStreamingScanner.Session scanSession = scanner.begin(
                plan,
                candidates.candidateBlockIds().stream().mapToInt(Integer::intValue).toArray()
        );
        SelectiveChunkStreamStats stats = emptyStats();
        int[] wantedIds = candidates.candidateBlockIds().stream()
                .mapToInt(Integer::intValue).toArray();
        if (!plan.chunkPositions().isEmpty()) {
            stats = reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    request.savePath(),
                    plan.chunkPositions(),
                    wantedIds,
                    chunkDiagnostics,
                    scanSession::accept
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
