package cartographer.scanner;

import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceFallbackChunkPlannerTest {

    private final SurfaceFallbackChunkPlanner planner = new SurfaceFallbackChunkPlanner();

    @Test
    void height256PlansEightVerticalChunks() {
        assertEquals(8, plan(256, List.of(new MapChunkCoordinate(1, 2))).size());
    }

    @Test
    void height257PlansNineVerticalChunks() {
        assertEquals(9, plan(257, List.of(new MapChunkCoordinate(1, 2))).size());
    }

    @Test
    void height1PlansOneVerticalChunk() {
        assertEquals(1, plan(1, List.of(new MapChunkCoordinate(1, 2))).size());
    }

    @Test
    void zeroHeightReturnsEmpty() {
        assertTrue(plan(0, List.of(new MapChunkCoordinate(1, 2))).isEmpty());
    }

    @Test
    void multipleMapChunksPlansEveryVerticalLevel() {
        assertEquals(16, plan(
                256,
                List.of(new MapChunkCoordinate(0, 0), new MapChunkCoordinate(1, 0))
        ).size());
    }

    @Test
    void deduplicatesInputMapChunks() {
        assertEquals(8, plan(
                256,
                List.of(new MapChunkCoordinate(0, 0), new MapChunkCoordinate(0, 0))
        ).size());
    }

    @Test
    void allPositionsUseDimensionZero() {
        assertTrue(plan(64, List.of(new MapChunkCoordinate(1, 2))).stream()
                .allMatch(position -> position.dimension() == 0));
    }

    @Test
    void orderingIsYThenZThenX() {
        List<ChunkPosition> positions = plan(
                65,
                List.of(
                        new MapChunkCoordinate(2, 1),
                        new MapChunkCoordinate(1, 0)
                )
        );

        assertEquals(
                List.of(
                        new ChunkPosition(1, 0, 0, 0),
                        new ChunkPosition(2, 0, 1, 0),
                        new ChunkPosition(1, 1, 0, 0),
                        new ChunkPosition(2, 1, 1, 0),
                        new ChunkPosition(1, 2, 0, 0),
                        new ChunkPosition(2, 2, 1, 0)
                ),
                positions
        );
    }

    private List<ChunkPosition> plan(int height, List<MapChunkCoordinate> mapChunks) {
        return planner.plan(new WorldMetadata(128, height, 128), mapChunks);
    }
}
