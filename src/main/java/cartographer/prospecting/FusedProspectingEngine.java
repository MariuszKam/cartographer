package cartographer.prospecting;

import cartographer.resource.ActualOreObservation;
import cartographer.spatial.OreChunkPositionPlanner;
import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockMapMode;
import cartographer.geology.rock.RockStreamingSession;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSnapshot;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SelectiveChunkVisitStatus;
import cartographer.save.VcdbsReader;
import cartographer.scanner.ActualBlockYFilter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One bounded source traversal feeding the rock session and ore accumulator. */
public final class FusedProspectingEngine {
    private final VcdbsReader reader;
    private final OreChunkPositionPlanner planner = new OreChunkPositionPlanner();

    public FusedProspectingEngine(VcdbsReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
    }

    public FusedProspectingResult analyze(
            SaveSession session,
            WorldPosition center,
            int radius,
            List<String> resources
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(center, "center is required");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources are required"));
        SaveSnapshot snapshot = session.snapshot();
        WorldMetadata metadata = snapshot.metadata();
        Map<Integer, BlockInfo> registry = snapshot.blockRegistry();
        RockCatalog catalog = RockCatalog.from(registry);
        if (catalog.rocks().isEmpty()) throw new IllegalArgumentException("No natural rock blocks were discovered in the save registry");
        CompiledProspectingClassifier classifier = CompiledProspectingClassifier.compile(registry, resources, catalog.rockBlockIds());
        int centerX = floor(center.x());
        int centerZ = floor(center.z());
        List<ChunkPosition> positions = planner.plan(metadata, centerX, centerZ, radius, ActualBlockYFilter.unbounded());
        ProspectingScanPlan plan = new ProspectingScanPlan(resources, positions, metadata, 0, metadata.mapSizeY(), classifier.interestingIds());
        int dimension = positions.isEmpty() ? 0 : positions.getFirst().dimension();
        RockStreamingSession rocks = RockStreamingSession.open(center, radius, plan.minY(), plan.maxYExclusive(), dimension, RockMapMode.UPPER_ROCK, catalog);
        boolean[] matched = new boolean[resources.size()];
        boolean[] unavailable = new boolean[resources.size()];
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(session, plan.positions(), plan.interestingBlockIds(), diagnostics,
                visit -> accept(visit, rocks, classifier, matched, unavailable, centerX, centerZ, radius));
        RockMap rockMap = rocks.finish();
        boolean unavailableAny = diagnostics.failed() > 0 || diagnostics.skipped() > 0;
        Map<String, ActualOreObservation> result = new LinkedHashMap<>();
        for (int i = 0; i < resources.size(); i++) {
            result.put(resources.get(i), matched[i] ? ActualOreObservation.OBSERVED
                    : (unavailable[i] || unavailableAny ? ActualOreObservation.UNAVAILABLE : ActualOreObservation.NOT_OBSERVED));
        }
        return new FusedProspectingResult(rockMap, result);
    }

    private void accept(SelectiveChunkVisit visit, RockStreamingSession rocks, CompiledProspectingClassifier classifier,
                        boolean[] matched, boolean[] unavailable, int centerX, int centerZ, int radius) {
        rocks.accept(visit);
        if (visit.status() != SelectiveChunkVisitStatus.DECODED) {
            if (visit.status() == SelectiveChunkVisitStatus.FAILED || visit.status() == SelectiveChunkVisitStatus.MISSING) {
                Arrays.fill(unavailable, true);
            }
            return;
        }
        ParsedChunk chunk = visit.chunk();
        long radiusSquared = (long) radius * radius;
        int baseX = Math.multiplyExact(chunk.coordinate().x(), ChunkCoordinate.SIZE_BLOCKS);
        int baseZ = Math.multiplyExact(chunk.coordinate().z(), ChunkCoordinate.SIZE_BLOCKS);
        for (int y = 0; y < chunk.sizeY(); y++) {
            for (int z = 0; z < chunk.sizeZ(); z++) {
                int worldZ = baseZ + z;
                long dz = (long) worldZ - centerZ;
                long dz2 = dz * dz;
                if (dz2 > radiusSquared) continue;
                for (int x = 0; x < chunk.sizeX(); x++) {
                    int worldX = baseX + x;
                    long dx = (long) worldX - centerX;
                    if (dx * dx + dz2 > radiusSquared) continue;
                    int blockId = chunk.blockIdAt(x, y, z);
                    int start = classifier.start(blockId);
                    if (start < 0) continue;
                    int end = classifier.end(blockId);
                    for (int i = start; i < end; i++) matched[classifier.membershipAt(i)] = true;
                }
            }
        }
    }

    private int floor(double value) {
        double result = Math.floor(value);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException("world center is outside the supported block range");
        return (int) result;
    }
}
