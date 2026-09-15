package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.resource.ObservedSurfaceResourceCatalogBuilder;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Discovers observed loose surface objects with one combined selective scan. */
public final class DiscoverObservedSurfaceResourcesUseCase {
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final MapChunkPositionPlanner mapChunkPositionPlanner =
            new MapChunkPositionPlanner();
    private final SurfaceObjectPlanner planner = new SurfaceObjectPlanner();
    private final SurfaceObjectScanner scanner = new SurfaceObjectScanner();
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
            SurfaceObjectPlan emptyPlan = new SurfaceObjectPlan(List.of(), List.of());
            SurfaceObjectScanResult emptyScan = new SurfaceObjectScanResult(
                    List.of(), 0, 0, 0, 0
            );
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
        SurfaceObjectPlanner.StreamingSession planning = planner.begin(
                metadata, centerX, centerZ, request.radius()
        );
        List<MapChunkCoordinate> coordinates = mapChunkPositionPlanner.plan(
                metadata, centerX, centerZ, request.radius()
        );
        reader.forEachMapChunkByCoordinate(
                request.savePath(), coordinates, mapDiagnostics, planning::accept
        );
        SurfaceObjectPlan plan = planning.finish();
        List<ParsedChunk> decoded = new ArrayList<>();
        Set<ChunkPosition> available = new HashSet<>();
        SelectiveChunkStreamStats stats = emptyStats();
        int[] wantedIds = candidates.candidateBlockIds().stream()
                .mapToInt(Integer::intValue)
                .toArray();
        if (!plan.chunkPositions().isEmpty()) {
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
                new HashSet<>(Arrays.stream(wantedIds).boxed().toList()),
                decoded,
                available
        );
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
            SurfaceObjectPlan plan,
            SelectiveChunkStreamStats stats,
            SurfaceObjectScanResult scan,
            ReadDiagnostics mapDiagnostics,
            ReadDiagnostics chunkDiagnostics
    ) {
        ObservedSurfaceResourceCatalog observed = observedBuilder.build(
                candidates,
                scan.blocks()
        );
        return new DiscoverObservedSurfaceResourcesResult(
                center,
                candidates,
                plan,
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
