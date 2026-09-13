package cartographer.scanner;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceObjectPlannerTest {
    private static final WorldMetadata WORLD = new WorldMetadata(32, 64, 32);

    @Test
    void usesTerrainAndRainAnchors() {
        int[] terrain = heights(10);
        int[] rain = heights(20);
        SurfaceObjectPlanner.StreamingSession session = new SurfaceObjectPlanner()
                .begin(WORLD, 16, 16, 1);
        session.accept(new MapChunk(new MapChunkCoordinate(0, 0), rain, terrain));

        SurfaceObjectPlan plan = session.finish();
        SurfaceObjectTarget target = plan.targets().getFirst();

        assertTrue(target.candidateWorldYs().contains(8));
        assertTrue(target.candidateWorldYs().contains(13));
        assertTrue(target.candidateWorldYs().contains(20));
        assertTrue(target.candidateWorldYs().contains(23));
    }

    @Test
    void plansChunkCrossingAtRainHeightBoundary() {
        SurfaceObjectPlanner.StreamingSession session = new SurfaceObjectPlanner()
                .begin(WORLD, 16, 16, 1);
        session.accept(new MapChunk(
                new MapChunkCoordinate(0, 0),
                heights(31),
                heights(31)
        ));

        SurfaceObjectPlan plan = session.finish();

        assertTrue(plan.chunkPositions().stream().anyMatch(position -> position.y() == 0));
        assertTrue(plan.chunkPositions().stream().anyMatch(position -> position.y() == 1));
    }

    private int[] heights(int value) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(heights, value);
        return heights;
    }
}
