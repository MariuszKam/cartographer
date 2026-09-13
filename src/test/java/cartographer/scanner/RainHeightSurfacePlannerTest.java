package cartographer.scanner;

import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainHeightSurfacePlannerTest {

    private static final WorldMetadata WORLD = new WorldMetadata(64, 256, 64);

    @Test
    void usesRainHeightMapValuesAsWorldY() {
        RainHeightSurfacePlanner.StreamingSession session = planner(16, 16, 1);
        session.accept(mapChunk(new MapChunkCoordinate(0, 0), 45));

        RainHeightSurfacePlan plan = session.finish();

        assertTrue(plan.targets().stream().allMatch(target -> target.worldY() == 45));
    }

    @Test
    void missingRainHeightMapFallsBackWholeMapChunk() {
        RainHeightSurfacePlanner.StreamingSession session = planner(16, 16, 1);
        session.accept(new MapChunk(
                new MapChunkCoordinate(0, 0),
                new int[0],
                filledHeights(99)
        ));

        RainHeightSurfacePlan plan = session.finish();

        assertTrue(plan.targets().isEmpty());
        assertEquals(List.of(new MapChunkCoordinate(0, 0)), plan.fallbackMapChunks());
    }

    @Test
    void doesNotUseWorldGenTerrainHeightAsFallback() {
        RainHeightSurfacePlanner.StreamingSession session = planner(16, 16, 1);
        session.accept(new MapChunk(
                new MapChunkCoordinate(0, 0),
                new int[0],
                filledHeights(99)
        ));

        assertTrue(session.finish().targets().isEmpty());
    }

    @Test
    void invalidHeightFallsBackWholeMapChunkAndEmitsNoPartialTargets() {
        int[] heights = filledHeights(45);
        heights[0] = 256;
        RainHeightSurfacePlanner.StreamingSession session = planner(16, 16, 32);
        session.accept(new MapChunk(
                new MapChunkCoordinate(0, 0), heights, new int[0]
        ));

        RainHeightSurfacePlan plan = session.finish();

        assertTrue(plan.targets().isEmpty());
        assertEquals(1, plan.fallbackMapChunks().size());
    }

    @Test
    void filtersExactCircularRadius() {
        RainHeightSurfacePlanner.StreamingSession session = planner(0, 0, 1);
        session.accept(mapChunk(new MapChunkCoordinate(0, 0), 45));

        assertEquals(1, session.finish().targets().size());
    }

    @Test
    void clipsColumnsToWorldBounds() {
        RainHeightSurfacePlanner.StreamingSession session = new RainHeightSurfacePlanner()
                .begin(new WorldMetadata(20, 256, 20), 0, 0, 20);
        session.accept(mapChunk(new MapChunkCoordinate(0, 0), 45));

        assertTrue(session.finish().targets().stream().allMatch(
                target -> target.worldX() < 20 && target.worldZ() < 20
        ));
    }

    @Test
    void deduplicatesRepeatedMapChunk() {
        RainHeightSurfacePlanner.StreamingSession session = planner(0, 0, 1);
        MapChunk mapChunk = mapChunk(new MapChunkCoordinate(0, 0), 45);
        session.accept(mapChunk);
        session.accept(mapChunk);

        assertEquals(1, session.finish().targets().size());
    }

    @Test
    void deduplicatesRequiredVerticalChunkPositions() {
        RainHeightSurfacePlanner.StreamingSession session = planner(16, 16, 1);
        session.accept(mapChunk(new MapChunkCoordinate(0, 0), 45));

        assertEquals(1, session.finish().chunkPositions().size());
    }

    @Test
    void rainHeight31And32RequireTwoVerticalChunks() {
        int[] heights = filledHeights(31);
        heights[1 + 2 * MapChunk.SIZE] = 32;
        RainHeightSurfacePlanner.StreamingSession session = planner(1, 2, 4);
        session.accept(new MapChunk(new MapChunkCoordinate(0, 0), heights, new int[0]));

        assertEquals(
                List.of(
                        new ChunkPosition(0, 0, 0, 0),
                        new ChunkPosition(0, 1, 0, 0)
                ),
                session.finish().chunkPositions()
        );
    }

    @Test
    void finishIsDeterministicRegardlessOfMapChunkAcceptOrder() {
        MapChunk first = mapChunk(new MapChunkCoordinate(0, 1), 45);
        MapChunk second = mapChunk(new MapChunkCoordinate(1, 0), 45);
        RainHeightSurfacePlanner.StreamingSession a = planner(32, 32, 40);
        RainHeightSurfacePlanner.StreamingSession b = planner(32, 32, 40);
        a.accept(first);
        a.accept(second);
        b.accept(second);
        b.accept(first);

        assertEquals(a.finish(), b.finish());
    }

    @Test
    void allChunkPositionsUseDimensionZero() {
        RainHeightSurfacePlanner.StreamingSession session = planner(16, 16, 1);
        session.accept(mapChunk(new MapChunkCoordinate(0, 0), 45));

        assertTrue(session.finish().chunkPositions().stream()
                .allMatch(position -> position.dimension() == 0));
    }

    private RainHeightSurfacePlanner.StreamingSession planner(int x, int z, int radius) {
        return new RainHeightSurfacePlanner().begin(WORLD, x, z, radius);
    }

    private MapChunk mapChunk(MapChunkCoordinate coordinate, int height) {
        return new MapChunk(coordinate, filledHeights(height), new int[0]);
    }

    private static int[] filledHeights(int value) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        java.util.Arrays.fill(heights, value);
        return heights;
    }
}
