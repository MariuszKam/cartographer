package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceRainHeightFastPathTest {
    private static final WorldMetadata WORLD = new WorldMetadata(64, 256, 64);
    private static final Map<Integer, BlockInfo> REGISTRY = Map.of(
            0, new BlockInfo(0, "air"),
            1, new BlockInfo(1, "game:soil-medium"),
            2, new BlockInfo(2, "game:leaves-oak"),
            3, new BlockInfo(3, "game:water-still")
    );

    @Test
    void plansStableUniqueServerChunkPositionsWithoutColumnTargets() {
        SurfaceRainHeightPlanner.StreamingSession planner = planner(16, 16);
        planner.accept(mapChunk(1));

        SurfaceRainHeightPlan plan = planner.finish();

        assertEquals(List.of(new ChunkPosition(0, 0, 0, 0)), plan.chunkPositions());
        assertTrue(plan.fallbackMapChunks().isEmpty());
    }

    @Test
    void missingNegativeAndTooHighRainHeightsPromoteWholeMapchunk() {
        for (int[] heights : List.of(
                new int[0],
                filledHeights(-1),
                filledHeights(256))) {
            SurfaceRainHeightPlanner.StreamingSession planner = planner(1, 1);
            planner.accept(new MapChunk(
                    new MapChunkCoordinate(0, 0), heights, new int[0]));

            SurfaceRainHeightPlan plan = planner.finish();

            assertTrue(plan.chunkPositions().isEmpty());
            assertEquals(List.of(new MapChunkCoordinate(0, 0)), plan.fallbackMapChunks());
        }
    }

    @Test
    void invalidCandidateAfterEarlierCellsStillPromotesWholeMapchunk() {
        int[] heights = filledHeights(1);
        heights[1 + MapChunk.SIZE] = 256;
        SurfaceRainHeightPlanner.StreamingSession planner = planner(1, 1);
        planner.accept(new MapChunk(
                new MapChunkCoordinate(0, 0), heights, new int[0]));

        SurfaceRainHeightPlan plan = planner.finish();

        assertTrue(plan.chunkPositions().isEmpty());
        assertEquals(List.of(new MapChunkCoordinate(0, 0)), plan.fallbackMapChunks());
    }

    @Test
    void waterResolvesAirCandidateAndMissingLiquidPromotes() {
        SurfaceRainHeightPlan waterPlan = planned();
        SurfaceRainHeightScanner.StreamingSession waterScanner = scanner(waterPlan);
        waterScanner.accept(chunkWith(3, true));
        SurfaceRainHeightScanResult water = waterScanner.finish();

        assertTrue(water.surface().isResolved(1, 1));
        assertEquals(SurfaceClass.WATER, water.surface().surfaceClassAt(1, 1));
        assertTrue(water.fallbackMapChunks().isEmpty());

        SurfaceRainHeightScanner.StreamingSession unavailableScanner = scanner(waterPlan);
        unavailableScanner.accept(chunkWith(1, false));
        SurfaceRainHeightScanResult unavailable = unavailableScanner.finish();

        assertTrue(unavailable.fallbackMapChunks().contains(new MapChunkCoordinate(0, 0)));
        assertFalse(unavailable.surface().isResolved(1, 1));
    }

    @Test
    void airAndIgnoredFoliagePromoteWholeMapchunk() {
        SurfaceRainHeightPlan plan = planned();
        for (int blockId : List.of(0, 2)) {
            SurfaceRainHeightScanner.StreamingSession scanner = scanner(plan);
            scanner.accept(chunkWith(blockId, true));
            SurfaceRainHeightScanResult result = scanner.finish();
            assertTrue(result.fallbackMapChunks().contains(new MapChunkCoordinate(0, 0)));
            assertFalse(result.surface().isResolved(1, 1));
        }
    }

    @Test
    void duplicateDeliveryDoesNotDuplicateOrChangeFastResult() {
        SurfaceRainHeightPlan plan = planned();
        ParsedChunk chunk = chunkWith(1, true);
        SurfaceRainHeightScanner.StreamingSession once = scanner(plan);
        once.accept(chunk);
        SurfaceRainHeightScanner.StreamingSession twice = scanner(plan);
        twice.accept(chunk);
        twice.accept(chunk);

        SurfaceRainHeightScanResult onceResult = once.finish();
        SurfaceRainHeightScanResult twiceResult = twice.finish();

        assertEquals(onceResult.diagnostics(), twiceResult.diagnostics());
        assertEquals(onceResult.fallbackMapChunks(), twiceResult.fallbackMapChunks());
        assertEquals(
                SurfaceSemanticOracle.fingerprint(onceResult.surface()),
                SurfaceSemanticOracle.fingerprint(twiceResult.surface()));
        assertTrue(onceResult.surface().isResolved(1, 1));
    }

    @Test
    void decodedArrivalOrderProducesSameCompactFingerprint() {
        int[] heights = filledHeights(31);
        heights[1 + MapChunk.SIZE] = 32;
        SurfaceRainHeightPlanner.StreamingSession planner = planner(1, 1);
        planner.accept(new MapChunk(new MapChunkCoordinate(0, 0), heights, new int[0]));
        SurfaceRainHeightPlan plan = planner.finish();

        ParsedChunk lower = chunkWithSection(0, 1, true);
        ParsedChunk upper = chunkWithSection(1, 1, true);
        SurfaceRainHeightScanner.StreamingSession first = scanner(plan);
        first.accept(lower);
        first.accept(upper);
        SurfaceRainHeightScanner.StreamingSession second = scanner(plan);
        second.accept(upper);
        second.accept(lower);

        assertEquals(
                SurfaceSemanticOracle.fingerprint(first.finish().surface()),
                SurfaceSemanticOracle.fingerprint(second.finish().surface())
        );
    }

    @Test
    void missingMapchunkCanBePromotedBeforePlanFinish() {
        SurfaceRainHeightPlanner.StreamingSession planner = planner(1, 1);
        planner.promoteMissing(new MapChunkCoordinate(0, 0));

        SurfaceRainHeightPlan plan = planner.finish();

        assertEquals(List.of(new MapChunkCoordinate(0, 0)), plan.fallbackMapChunks());
        assertTrue(plan.chunkPositions().isEmpty());
    }

    private SurfaceRainHeightPlan planned() {
        SurfaceRainHeightPlanner.StreamingSession planner = planner(1, 1);
        planner.accept(mapChunk(1));
        return planner.finish();
    }

    private SurfaceRainHeightPlanner.StreamingSession planner(int x, int z) {
        return new SurfaceRainHeightPlanner().begin(WORLD, x, z, 2);
    }

    private SurfaceRainHeightScanner.StreamingSession scanner(
            SurfaceRainHeightPlan plan
    ) {
        return new SurfaceRainHeightScanner().begin(
                plan, REGISTRY, true, true);
    }

    private MapChunk mapChunk(int height) {
        return new MapChunk(new MapChunkCoordinate(0, 0), filledHeights(height), new int[0]);
    }

    private ParsedChunk chunkWith(int blockId, boolean liquidAvailable) {
        return chunkWithSection(0, blockId, liquidAvailable);
    }

    private ParsedChunk chunkWithSection(int sectionY, int blockId, boolean liquidAvailable) {
        int[] blocks = new int[32 * 32 * 32];
        int[] liquids = liquidAvailable ? new int[blocks.length] : null;
        Arrays.fill(blocks, blockId);
        if (liquids != null && blockId == 3) {
            Arrays.fill(liquids, 3);
        }
        return cartographer.model.ParsedChunkFixtures.create(
                new ChunkCoordinate(0, sectionY, 0), sectionY * ChunkCoordinate.SIZE_BLOCKS,
                32, 32, 32,
                blocks, liquids, 0, liquidAvailable,
                liquidAvailable ? "" : "fixture liquid layer unavailable");
    }

    private static int[] filledHeights(int value) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(heights, value);
        return heights;
    }
}
