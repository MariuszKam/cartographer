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
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.VcdbsReader;
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
    private final SaveSessionFactory sessionFactory;
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
            SaveSessionFactory sessionFactory
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "session factory is required"
        );
    }

    public InspectSurfaceObjectsResult execute(InspectSurfaceObjectsRequest request) {
        Objects.requireNonNull(request, "request is required");
        try (SaveSession session = sessionFactory.open(request.savePath())) {
            WorldMetadata metadata = session.snapshot().metadata();
            WorldPosition center = request.center().orElseGet(
                    () -> reader.readPlayerPosition(session, ProgressReporter.NONE)
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
                    session,
                    coordinates,
                    mapDiagnostics,
                    planning::accept
            );
            SurfaceObjectCompactPlan plan = planning.finish();
            Map<Integer, BlockInfo> registry = session.snapshot().blockRegistry();
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
            if (plan.chunkPositions().isEmpty()) {
                stats = new SelectiveChunkStreamStats(
                        0, 0, 0, 0, 0, 0, 0, 0
                );
            } else {
                stats = reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                        session,
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
}
