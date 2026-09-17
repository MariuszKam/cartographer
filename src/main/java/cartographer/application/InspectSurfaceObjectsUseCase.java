package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.resource.SurfaceObjectCandidate;
import cartographer.resource.SurfaceObjectCandidateCatalog;
import cartographer.resource.SurfaceObjectCandidateCatalogBuilder;
import cartographer.resource.SurfaceObjectCandidateResolver;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceObjectCompactPlan;
import cartographer.scanner.SurfaceObjectCompactPlanner;
import cartographer.scanner.SurfaceObjectCompactScanResult;
import cartographer.scanner.SurfaceObjectStreamingScanner;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InspectSurfaceObjectsUseCase {
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final MapChunkPositionPlanner mapChunkPositionPlanner =
            new MapChunkPositionPlanner();
    private final SurfaceObjectCompactPlanner planner = new SurfaceObjectCompactPlanner();
    private final SurfaceObjectStreamingScanner scanner = new SurfaceObjectStreamingScanner();
    private final SurfaceObjectCandidateCatalogBuilder candidateBuilder =
            new SurfaceObjectCandidateCatalogBuilder();
    private final SurfaceObjectCandidateResolver candidateResolver =
            new SurfaceObjectCandidateResolver();

    public InspectSurfaceObjectsUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        this.reader = reader;
        this.metadataReader = metadataReader;
    }

    public InspectSurfaceObjectsResult execute(InspectSurfaceObjectsRequest request) {
        WorldMetadata metadata = metadataReader.read(request.savePath());
        WorldPosition center = request.center().orElseGet(
                () -> reader.readPlayerPosition(request.savePath())
        );
        int centerX = (int) Math.floor(center.x());
        int centerZ = (int) Math.floor(center.z());
        SurfaceObjectCompactPlanner.StreamingSession planning = planner.begin(
                metadata, centerX, centerZ, request.radius()
        );
        ReadDiagnostics mapDiagnostics = new ReadDiagnostics();
        List<MapChunkCoordinate> coordinates = mapChunkPositionPlanner.plan(
                metadata, centerX, centerZ, request.radius()
        );
        reader.forEachMapChunkByCoordinate(
                request.savePath(),
                coordinates,
                mapDiagnostics,
                planning::accept
        );
        SurfaceObjectCompactPlan plan = planning.finish();
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(request.savePath());
        SurfaceObjectCandidateCatalog catalog = candidateBuilder.build(registry);
        SurfaceObjectCandidate selectedCandidate = candidateResolver.resolve(
                catalog, request.resourceKey());
        List<SurfaceObjectCandidate> selectedCandidates = List.of(selectedCandidate);
        int[] wantedIds = selectedCandidates.stream()
                .flatMapToInt(candidate -> candidate.blockIds().stream().mapToInt(Integer::intValue))
                .distinct().sorted().toArray();
        List<BlockInfo> registryMatches = selectedCandidates.stream()
                .flatMap(candidate -> candidate.blockIds().stream().map(registry::get))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(BlockInfo::id))
                .toList();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        SurfaceObjectStreamingScanner.Session scanSession = scanner.begin(plan, wantedIds);
        SelectiveChunkStreamStats stats;
        if (wantedIds.length == 0 || plan.chunkPositions().isEmpty()) {
            if (wantedIds.length == 0) {
                scanSession.markExpectedPositionsAvailableWithoutVisits();
            }
            stats = new SelectiveChunkStreamStats(
                    plan.chunkPositions().size(), 0, 0, 0, 0, 0, 0, 0
            );
        } else {
            stats = reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    request.savePath(),
                    plan.chunkPositions(),
                    wantedIds,
                    chunkDiagnostics,
                    scanSession::accept
            );
        }
        SurfaceObjectCompactScanResult scan = scanSession.finish();
        return new InspectSurfaceObjectsResult(
                center,
                registryMatches,
                plan.plannedTargetCount(),
                plan.chunkPositions().size(),
                stats,
                scan,
                mapDiagnostics,
                chunkDiagnostics
        );
    }
}
