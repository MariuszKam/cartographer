package cartographer.scanner;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.save.SelectiveChunkVisit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceObjectCompactDiscoveryTest {
    private static final WorldMetadata WORLD = new WorldMetadata(64, 64, 64);

    @Test
    void unionsTerrainAndRainRangesWithoutDuplicateCandidateYs() {
        SurfaceObjectCompactPlan plan = planned(10, 20);
        SurfaceObjectCompactPlan.Tile tile = plan.tileAt(0);
        int cell = 16 * MapChunk.SIZE + 16;

        int[] candidates = new int[tile.candidateCountAt(cell)];
        for (int index = 0; index < candidates.length; index++) {
            candidates[index] = tile.candidateYAt(cell, index);
        }

        assertEquals(List.of(8, 9, 10, 11, 12, 13, 20, 21, 22, 23),
                Arrays.stream(candidates).boxed().toList());
        assertEquals(candidates.length, Arrays.stream(candidates).distinct().count());
    }

    @Test
    void missingHeightMapsProduceNoTargets() {
        SurfaceObjectCompactPlanner.StreamingSession session =
                new SurfaceObjectCompactPlanner().begin(WORLD, 16, 16, 1);
        session.accept(new MapChunk(new MapChunkCoordinate(0, 0), new int[0], new int[0]));

        SurfaceObjectCompactPlan plan = session.finish();

        assertEquals(0, plan.plannedTargetCount());
        assertTrue(plan.chunkPositions().isEmpty());
    }

    @Test
    void duplicateMapchunkInputDoesNotDuplicateTargets() {
        MapChunk mapChunk = new MapChunk(
                new MapChunkCoordinate(0, 0), filled(20), filled(10));
        SurfaceObjectCompactPlanner.StreamingSession session =
                new SurfaceObjectCompactPlanner().begin(WORLD, 16, 16, 1);
        session.accept(mapChunk);
        int once = session.finish().plannedTargetCount();

        SurfaceObjectCompactPlanner.StreamingSession duplicateSession =
                new SurfaceObjectCompactPlanner().begin(WORLD, 16, 16, 1);
        duplicateSession.accept(mapChunk);
        duplicateSession.accept(mapChunk);

        assertEquals(once, duplicateSession.finish().plannedTargetCount());
    }

    @Test
    void decodedObservationIsConsumedAndStatusIsObserved() {
        SurfaceObjectCompactPlan plan = planned(10, 20);
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7});
        ParsedChunk chunk = chunk(0, 10, 16, 16, 7);
        session.accept(SelectiveChunkVisit.decoded(position(chunk), chunk));

        SurfaceObjectCompactScanResult result = session.finish();

        assertEquals(1, result.observedTargets());
        assertEquals(0, result.unavailablePositions());
        assertEquals(1, result.observedObjects());
    }

    @Test
    void unavailableCandidateDoesNotOverrideAnObservedCandidate() {
        SurfaceObjectCompactPlan plan = planned(31, 31);
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7});
        ChunkPosition lower = new ChunkPosition(0, 0, 0, 0);
        ChunkPosition upper = new ChunkPosition(0, 1, 0, 0);
        session.accept(SelectiveChunkVisit.missing(lower));
        ParsedChunk chunk = chunk(1, 32, 16, 16, 7);
        session.accept(SelectiveChunkVisit.decoded(upper, chunk));

        SurfaceObjectCompactScanResult result = session.finish();

        assertEquals(1, result.observedTargets());
        assertEquals(0, result.unavailablePositions());
    }

    @Test
    void paletteRejectedIsAvailableButNotObserved() {
        SurfaceObjectCompactPlan plan = planned(10, 20);
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7});
        for (ChunkPosition position : plan.chunkPositions()) {
            session.accept(SelectiveChunkVisit.paletteRejected(position));
        }

        SurfaceObjectCompactScanResult result = session.finish();

        assertTrue(result.notObservedTargets() > 0);
        assertEquals(0, result.unavailablePositions());
    }

    @Test
    void failedVisitMakesUnobservedTargetsUnavailable() {
        SurfaceObjectCompactPlan plan = planned(10, 20);
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7});
        for (ChunkPosition position : plan.chunkPositions()) {
            session.accept(SelectiveChunkVisit.failed(position, "fixture failure"));
        }

        SurfaceObjectCompactScanResult result = session.finish();

        assertEquals(0, result.observedTargets());
        assertEquals(0, result.notObservedTargets());
        assertTrue(result.unavailablePositions() > 0);
    }

    @Test
    void missingVisitMakesUnobservedTargetsUnavailable() {
        SurfaceObjectCompactPlan plan = planned(10, 20);
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7});
        for (ChunkPosition position : plan.chunkPositions()) {
            session.accept(SelectiveChunkVisit.missing(position));
        }

        SurfaceObjectCompactScanResult result = session.finish();

        assertEquals(0, result.observedTargets());
        assertEquals(0, result.notObservedTargets());
        assertTrue(result.unavailablePositions() > 0);
    }

    private SurfaceObjectCompactPlan planned(int terrain, int rain) {
        int[] terrainHeights = filled(terrain);
        int[] rainHeights = filled(rain);
        SurfaceObjectCompactPlanner.StreamingSession session =
                new SurfaceObjectCompactPlanner().begin(WORLD, 16, 16, 1);
        session.accept(new MapChunk(
                new MapChunkCoordinate(0, 0), rainHeights, terrainHeights));
        return session.finish();
    }

    private ParsedChunk chunk(int chunkY, int worldY, int worldX, int worldZ, int blockId) {
        int[] blocks = new int[ChunkCoordinate.SIZE_BLOCKS
                * ChunkCoordinate.SIZE_BLOCKS * ChunkCoordinate.SIZE_BLOCKS];
        int localY = worldY - chunkY * ChunkCoordinate.SIZE_BLOCKS;
        int index = (localY * ChunkCoordinate.SIZE_BLOCKS + worldZ % ChunkCoordinate.SIZE_BLOCKS)
                * ChunkCoordinate.SIZE_BLOCKS + worldX % ChunkCoordinate.SIZE_BLOCKS;
        blocks[index] = blockId;
        return new ParsedChunk(
                new ChunkCoordinate(0, chunkY, 0),
                chunkY * ChunkCoordinate.SIZE_BLOCKS,
                ChunkCoordinate.SIZE_BLOCKS,
                ChunkCoordinate.SIZE_BLOCKS,
                ChunkCoordinate.SIZE_BLOCKS,
                blocks
        );
    }

    private ChunkPosition position(ParsedChunk chunk) {
        return new ChunkPosition(
                chunk.coordinate().x(), chunk.coordinate().y(), chunk.coordinate().z(), 0);
    }

    private int[] filled(int value) {
        int[] values = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(values, value);
        return values;
    }
}
