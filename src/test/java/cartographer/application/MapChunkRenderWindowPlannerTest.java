package cartographer.application;

import cartographer.spatial.MapChunkRenderWindowPlanner;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapChunkRenderWindowPlannerTest {

    private final MapChunkRenderWindowPlanner planner = new MapChunkRenderWindowPlanner();
    private final WorldMetadata world = new WorldMetadata(256, 256, 256);

    @Test
    void radiusBelowChunkSizeKeepsLegacyOneChunkRadius() {
        assertEquals(9, planner.plan(world, new WorldPosition(80, 0, 80), 1).size());
    }

    @Test
    void radius32UsesOneChunkRadius() {
        assertEquals(9, planner.plan(world, new WorldPosition(80, 0, 80), 32).size());
    }

    @Test
    void radius33UsesTwoChunkRadius() {
        assertEquals(25, planner.plan(world, new WorldPosition(80, 0, 80), 33).size());
    }

    @Test
    void usesCenterMapChunkFromWorldPosition() {
        List<MapChunkCoordinate> result = planner.plan(
                world,
                new WorldPosition(31.9, 0, 32.1),
                1
        );

        assertTrue(result.contains(new MapChunkCoordinate(0, 0)));
        assertTrue(result.contains(new MapChunkCoordinate(1, 1)));
    }

    @Test
    void clampsAtWorldMinimum() {
        List<MapChunkCoordinate> result = planner.plan(
                new WorldMetadata(64, 256, 64),
                new WorldPosition(0, 0, 0),
                1
        );

        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(coordinate -> coordinate.x() >= 0 && coordinate.z() >= 0));
    }

    @Test
    void clampsAtWorldMaximum() {
        List<MapChunkCoordinate> result = planner.plan(
                new WorldMetadata(64, 256, 64),
                new WorldPosition(63, 0, 63),
                1
        );

        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(coordinate -> coordinate.x() <= 1 && coordinate.z() <= 1));
    }

    @Test
    void emptyWorldReturnsEmpty() {
        assertTrue(planner.plan(new WorldMetadata(0, 256, 64), new WorldPosition(0, 0, 0), 1).isEmpty());
    }

    @Test
    void outputOrderIsZThenX() {
        List<MapChunkCoordinate> result = planner.plan(
                world,
                new WorldPosition(80, 0, 80),
                32
        );

        assertEquals(
                List.of(
                        new MapChunkCoordinate(1, 1),
                        new MapChunkCoordinate(2, 1),
                        new MapChunkCoordinate(3, 1),
                        new MapChunkCoordinate(1, 2),
                        new MapChunkCoordinate(2, 2),
                        new MapChunkCoordinate(3, 2),
                        new MapChunkCoordinate(1, 3),
                        new MapChunkCoordinate(2, 3),
                        new MapChunkCoordinate(3, 3)
                ),
                result
        );
    }

    @Test
    void noDuplicates() {
        List<MapChunkCoordinate> result = planner.plan(
                world,
                new WorldPosition(80, 0, 80),
                33
        );

        assertEquals(result.size(), result.stream().distinct().count());
    }
}
