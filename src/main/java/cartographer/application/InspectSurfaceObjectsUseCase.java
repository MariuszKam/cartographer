package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.resource.SurfaceObjectCandidate;
import cartographer.resource.SurfaceObjectCandidateCatalog;
import cartographer.resource.SurfaceObjectCandidateCatalogBuilder;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisitStatus;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceObjectPlan;
import cartographer.scanner.SurfaceObjectPlanner;
import cartographer.scanner.SurfaceObjectScanResult;
import cartographer.scanner.SurfaceObjectScanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class InspectSurfaceObjectsUseCase {
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final MapChunkPositionPlanner mapChunkPositionPlanner =
            new MapChunkPositionPlanner();
    private final SurfaceObjectPlanner planner = new SurfaceObjectPlanner();
    private final SurfaceObjectScanner scanner = new SurfaceObjectScanner();
    private final SurfaceObjectCandidateCatalogBuilder candidateBuilder =
            new SurfaceObjectCandidateCatalogBuilder();

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
        SurfaceObjectPlanner.StreamingSession planning = planner.begin(
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
        SurfaceObjectPlan plan = planning.finish();
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(request.savePath());
        SurfaceObjectCandidateCatalog catalog = candidateBuilder.build(registry);
        List<SurfaceObjectCandidate> selectedCandidates = catalog.candidates().stream()
                .filter(candidate -> candidate.qualifiedResourceKey().equalsIgnoreCase(request.resourceKey())
                        || candidate.resourceKey().equalsIgnoreCase(request.resourceKey()))
                .toList();
        int[] wantedIds = selectedCandidates.stream()
                .flatMapToInt(candidate -> candidate.blockIds().stream().mapToInt(Integer::intValue))
                .distinct().sorted().toArray();
        List<BlockInfo> registryMatches = selectedCandidates.stream()
                .flatMap(candidate -> candidate.blockIds().stream().map(registry::get))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(BlockInfo::id))
                .toList();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        List<ParsedChunk> decoded = new ArrayList<>();
        Set<ChunkPosition> available = new HashSet<>();
        SelectiveChunkStreamStats stats;
        if (wantedIds.length == 0 || plan.chunkPositions().isEmpty()) {
            stats = new SelectiveChunkStreamStats(
                    plan.chunkPositions().size(), 0, 0, 0, 0, 0, 0, 0
            );
            available.addAll(plan.chunkPositions());
        } else {
            stats = reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    request.savePath(),
                    plan.chunkPositions(),
                    wantedIds,
                    chunkDiagnostics,
                    visit -> {
                        if (visit.status() == SelectiveChunkVisitStatus.DECODED) {
                            decoded.add(visit.chunk());
                            available.add(visit.position());
                        } else if (visit.status()
                                == SelectiveChunkVisitStatus.PALETTE_REJECTED) {
                            available.add(visit.position());
                        }
                    }
            );
        }
        SurfaceObjectScanResult scan = scanner.scan(
                plan,
                registry,
                java.util.Arrays.stream(wantedIds).boxed().collect(java.util.stream.Collectors.toSet()),
                decoded,
                available
        );
        return new InspectSurfaceObjectsResult(
                center,
                registryMatches,
                plan,
                stats,
                scan,
                mapDiagnostics,
                chunkDiagnostics
        );
    }
}
