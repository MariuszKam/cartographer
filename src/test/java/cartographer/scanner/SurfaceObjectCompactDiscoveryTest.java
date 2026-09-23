package cartographer.scanner;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.save.SelectiveChunkVisit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void terrainOnlyUsesExactClippedTerrainRange() {
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(10)),
                WORLD, 16, 16, 1);

        assertEquals(List.of(8, 9, 10, 11, 12, 13), candidatesAt(plan, 16, 16));
    }

    @Test
    void rainOnlyUsesExactClippedRainRange() {
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(0, 0), filled(20), new int[0]),
                WORLD, 16, 16, 1);

        assertEquals(List.of(20, 21, 22, 23), candidatesAt(plan, 16, 16));
    }

    @Test
    void clipsCandidateRangeBelowZero() {
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(0)),
                WORLD, 16, 16, 1);

        assertEquals(List.of(0, 1, 2, 3), candidatesAt(plan, 16, 16));
    }

    @Test
    void clipsCandidateRangeAtMapHeight() {
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(63)),
                WORLD, 16, 16, 1);

        assertEquals(List.of(61, 62, 63), candidatesAt(plan, 16, 16));
        assertTrue(candidatesAt(plan, 16, 16).stream().allMatch(y -> y < WORLD.mapSizeY()));
    }

    @Test
    void compactPlannerOwnCircleIncludesBoundaryAndRejectsOutsideCell() {
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(10)),
                WORLD, 16, 16, 1);

        assertTrue(!candidatesAt(plan, 16, 17).isEmpty());
        assertTrue(candidatesAt(plan, 16, 18).isEmpty());
    }

    @Test
    void compactPlannerClipsPartialWorldEdgeTile() {
        WorldMetadata edgeWorld = new WorldMetadata(33, 64, 33);
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(1, 1), new int[0], filled(10)),
                edgeWorld, 32, 32, 1);

        assertTrue(!candidatesAt(plan, 32, 32).isEmpty());
        assertTrue(candidatesAt(plan, 33, 32).isEmpty());
        assertTrue(candidatesAt(plan, 32, 33).isEmpty());
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
    void decodedAvailableChunkWithoutWantedHitIsNotObserved() {
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(10)),
                WORLD, 16, 16, 1);
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7});
        ParsedChunk chunk = chunk(0, 10, 16, 16, 0);
        session.accept(SelectiveChunkVisit.decoded(position(chunk), chunk));

        SurfaceObjectCompactScanResult result = session.finish();

        assertEquals(0, result.unavailablePositions());
        assertEquals(0, result.observedTargets());
        assertEquals(plan.plannedTargetCount(), result.notObservedTargets());
    }

    @Test
    void oneTargetPreservesMultipleWantedObservationsAndYValues() {
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(10)),
                WORLD, 16, 16, 1);
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7});
        int[] blocks = new int[ChunkCoordinate.SIZE_BLOCKS * ChunkCoordinate.SIZE_BLOCKS
                * ChunkCoordinate.SIZE_BLOCKS];
        int x = 16;
        int z = 16;
        blocks[(10 * ChunkCoordinate.SIZE_BLOCKS + z) * ChunkCoordinate.SIZE_BLOCKS + x] = 7;
        blocks[(11 * ChunkCoordinate.SIZE_BLOCKS + z) * ChunkCoordinate.SIZE_BLOCKS + x] = 7;
        ParsedChunk chunk = new ParsedChunk(new ChunkCoordinate(0, 0, 0), 0,
                ChunkCoordinate.SIZE_BLOCKS, ChunkCoordinate.SIZE_BLOCKS,
                ChunkCoordinate.SIZE_BLOCKS, blocks);
        session.accept(SelectiveChunkVisit.decoded(position(chunk), chunk));

        SurfaceObjectCompactScanResult result = session.finish();
        List<Integer> observedY = new ArrayList<>();
        result.forEachObservation((worldX, worldY, worldZ, blockId) -> {
            if (worldX == x && worldZ == z) observedY.add(worldY);
        });

        assertEquals(1, result.observedTargets());
        assertEquals(2, result.observedObjects());
        assertEquals(List.of(10, 11), observedY);
    }

    @Test
    void nonVanillaWantedIdIsPreservedAsPrimitiveObservation() {
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(10)),
                WORLD, 16, 16, 1);
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {4242});
        ParsedChunk chunk = chunk(0, 10, 16, 16, 4242);
        session.accept(SelectiveChunkVisit.decoded(position(chunk), chunk));

        SurfaceObjectCompactScanResult result = session.finish();
        List<Integer> ids = new ArrayList<>();
        result.forEachObservation((x, y, z, id) -> ids.add(id));

        assertEquals(List.of(4242), ids);
    }

    @Test
    void visitArrivalOrderDoesNotChangeResult() {
        SurfaceObjectCompactPlan plan = compactPlan(
                new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(31)),
                WORLD, 16, 16, 1);
        List<ChunkPosition> positions = plan.chunkPositions();
        assertTrue(positions.size() >= 2);

        SurfaceObjectCompactScanResult first = scanWithDecodedOrder(plan, false);
        SurfaceObjectCompactScanResult second = scanWithDecodedOrder(plan, true);

        assertEquals(first.positionsInspected(), second.positionsInspected());
        assertEquals(first.unavailablePositions(), second.unavailablePositions());
        assertEquals(first.observedTargets(), second.observedTargets());
        assertEquals(first.notObservedTargets(), second.notObservedTargets());
        assertEquals(first.observedObjects(), second.observedObjects());
        assertTrue(first.observedObjects() > 0);
        assertEquals(observationFingerprint(first), observationFingerprint(second));
    }

    @Test
    void mapchunkArrivalOrderDoesNotChangePlan() {
        MapChunk firstChunk = new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(10));
        MapChunk secondChunk = new MapChunk(new MapChunkCoordinate(1, 0), new int[0], filled(20));
        SurfaceObjectCompactPlan first = planForChunks(firstChunk, secondChunk);
        SurfaceObjectCompactPlan second = planForChunks(secondChunk, firstChunk);

        assertEquals(first.plannedTargetCount(), second.plannedTargetCount());
        assertEquals(first.chunkPositions(), second.chunkPositions());
        assertEquals(planFingerprint(first), planFingerprint(second));
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
        assertEquals(plan.plannedTargetCount() - 1, result.unavailablePositions());
        assertEquals(0, result.notObservedTargets());
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

    @Test
    void unvisitedExpectedPositionIsUnavailableForNonEmptyWantedIds() {
        SurfaceObjectCompactPlan plan = planned(10, 20);
        SurfaceObjectCompactScanResult result = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7})
                .finish();

        assertTrue(result.unavailablePositions() > 0);
        assertEquals(0, result.notObservedTargets());
    }

    @Test
    void emptyWantedIdsAreRejected() {
        SurfaceObjectCompactPlan plan = planned(10, 20);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SurfaceObjectStreamingScanner()
                        .begin(plan, new int[0])
        );

        assertEquals(
                "wanted block IDs cannot be empty",
                exception.getMessage()
        );
    }

    @Test
    void observationsAreDeterministicallyOrderedByZThenXThenYThenBlockId() {
        SurfaceObjectCompactPlan plan = planned(10, 20);
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7});
        int[] blocks = new int[ChunkCoordinate.SIZE_BLOCKS * ChunkCoordinate.SIZE_BLOCKS
                * ChunkCoordinate.SIZE_BLOCKS];
        Arrays.fill(blocks, 7);
        ParsedChunk chunk = new ParsedChunk(
                new ChunkCoordinate(0, 0, 0), 0,
                ChunkCoordinate.SIZE_BLOCKS, ChunkCoordinate.SIZE_BLOCKS,
                ChunkCoordinate.SIZE_BLOCKS, blocks);
        session.accept(SelectiveChunkVisit.decoded(new ChunkPosition(0, 0, 0, 0), chunk));

        int[] previous = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        SurfaceObjectCompactScanResult result = session.finish();
        result.forEachObservation((x, y, z, blockId) -> {
            assertTrue(z > previous[2]
                    || (z == previous[2] && (x > previous[0]
                    || (x == previous[0] && (y > previous[1]
                    || (y == previous[1] && blockId >= previous[3]))))));
            previous[0] = x;
            previous[1] = y;
            previous[2] = z;
            previous[3] = blockId;
        });
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

    private SurfaceObjectCompactPlan compactPlan(MapChunk mapChunk, WorldMetadata world,
                                                  int centerX, int centerZ, int radius) {
        SurfaceObjectCompactPlanner.StreamingSession session =
                new SurfaceObjectCompactPlanner().begin(world, centerX, centerZ, radius);
        session.accept(mapChunk);
        return session.finish();
    }

    private List<Integer> candidatesAt(SurfaceObjectCompactPlan plan, int worldX, int worldZ) {
        int tileX = Math.floorDiv(worldX, MapChunk.SIZE);
        int tileZ = Math.floorDiv(worldZ, MapChunk.SIZE);
        SurfaceObjectCompactPlan.Tile tile = plan.tileAt(plan.tileIndexAt(
                new MapChunkCoordinate(tileX, tileZ)));
        int cell = Math.floorMod(worldZ, MapChunk.SIZE) * MapChunk.SIZE
                + Math.floorMod(worldX, MapChunk.SIZE);
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < tile.candidateCountAt(cell); index++) {
            result.add(tile.candidateYAt(cell, index));
        }
        return result;
    }

    private SurfaceObjectCompactPlan planForChunks(MapChunk first, MapChunk second) {
        SurfaceObjectCompactPlanner.StreamingSession session =
                new SurfaceObjectCompactPlanner().begin(WORLD, 32, 16, 32);
        session.accept(first);
        session.accept(second);
        return session.finish();
    }

    private List<String> planFingerprint(SurfaceObjectCompactPlan plan) {
        List<String> result = new ArrayList<>();
        for (int tileIndex = 0; tileIndex < plan.tileCount(); tileIndex++) {
                SurfaceObjectCompactPlan.Tile tile = plan.tileAt(tileIndex);
            for (int cell = 0; cell < MapChunk.SIZE * MapChunk.SIZE; cell++) {
                if (tile.candidateCountAt(cell) == 0) continue;
                int worldX = tile.coordinate().x() * MapChunk.SIZE + cell % MapChunk.SIZE;
                int worldZ = tile.coordinate().z() * MapChunk.SIZE + cell / MapChunk.SIZE;
                result.add(worldX + ":" + worldZ + ":" + candidates(tile, cell));
            }
        }
        return result;
    }

    private List<Integer> candidates(SurfaceObjectCompactPlan.Tile tile, int cell) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < tile.candidateCountAt(cell); index++) {
            result.add(tile.candidateYAt(cell, index));
        }
        return result;
    }

    private SurfaceObjectCompactScanResult scanWithDecodedOrder(
            SurfaceObjectCompactPlan plan, boolean reverse) {
        SurfaceObjectStreamingScanner.Session session = new SurfaceObjectStreamingScanner()
                .begin(plan, new int[] {7});
        ParsedChunk lower = chunk(0, 31, 16, 16, 7);
        ParsedChunk upper = chunk(1, 32, 16, 16, 7);
        if (reverse) {
            session.accept(SelectiveChunkVisit.decoded(position(upper), upper));
            session.accept(SelectiveChunkVisit.decoded(position(lower), lower));
        } else {
            session.accept(SelectiveChunkVisit.decoded(position(lower), lower));
            session.accept(SelectiveChunkVisit.decoded(position(upper), upper));
        }
        return session.finish();
    }

    private List<String> observationFingerprint(SurfaceObjectCompactScanResult result) {
        List<String> fingerprint = new ArrayList<>();
        result.forEachObservation((x, y, z, id) -> fingerprint.add(x + ":" + y + ":" + z + ":" + id));
        return fingerprint;
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
