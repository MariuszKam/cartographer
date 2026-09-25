package cartographer.application;

import cartographer.spatial.MapChunkPositionPlanner;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapChunkPositionPlannerTest {

    private final MapChunkPositionPlanner planner = new MapChunkPositionPlanner();

    @Test
    void centerInsideChunkPlansContainingChunk() {
        List<MapChunkCoordinate> result = planner.plan(
                new WorldMetadata(128, 256, 128), 16, 16, 1
        );

        assertEquals(List.of(new MapChunkCoordinate(0, 0)), result);
    }

    @Test
    void centerNearChunkBoundaryIncludesAdjacentChunk() {
        List<MapChunkCoordinate> result = planner.plan(
                new WorldMetadata(128, 256, 128), 31, 16, 1
        );

        assertTrue(result.contains(new MapChunkCoordinate(0, 0)));
        assertTrue(result.contains(new MapChunkCoordinate(1, 0)));
    }

    @Test
    void includesChunkWhoseFootprintTouchesCircle() {
        List<MapChunkCoordinate> result = planner.plan(
                new WorldMetadata(96, 256, 96), 0, 16, 32
        );

        assertTrue(result.contains(new MapChunkCoordinate(1, 0)));
    }

    @Test
    void excludesChunkWhoseFootprintIsOutsideCircle() {
        List<MapChunkCoordinate> result = planner.plan(
                new WorldMetadata(96, 256, 96), 0, 16, 31
        );

        assertFalse(result.contains(new MapChunkCoordinate(1, 0)));
    }

    @Test
    void clampsToWorldMinimum() {
        List<MapChunkCoordinate> result = planner.plan(
                new WorldMetadata(64, 256, 64), -10, 16, 20
        );

        assertTrue(result.stream().allMatch(coordinate -> coordinate.x() >= 0));
    }

    @Test
    void clampsToWorldMaximum() {
        List<MapChunkCoordinate> result = planner.plan(
                new WorldMetadata(64, 256, 64), 63, 16, 20
        );

        assertTrue(result.stream().allMatch(coordinate -> coordinate.x() <= 1));
    }

    @Test
    void circleOutsideWorldReturnsEmpty() {
        assertTrue(
                planner.plan(new WorldMetadata(64, 256, 64), -100, 16, 20).isEmpty()
        );
    }

    @Test
    void producesNoDuplicates() {
        List<MapChunkCoordinate> result = planner.plan(
                new WorldMetadata(128, 256, 128), 64, 64, 80
        );

        assertEquals(result.size(), result.stream().distinct().count());
    }

    @Test
    void outputOrderIsDeterministic() {
        WorldMetadata metadata = new WorldMetadata(128, 256, 128);
        List<MapChunkCoordinate> first = planner.plan(metadata, 64, 64, 80);
        List<MapChunkCoordinate> second = planner.plan(metadata, 64, 64, 80);

        assertEquals(first, second);
        for (int index = 1; index < first.size(); index++) {
            MapChunkCoordinate previous = first.get(index - 1);
            MapChunkCoordinate current = first.get(index);
            assertTrue(
                    current.z() > previous.z()
                            || (current.z() == previous.z()
                            && current.x() >= previous.x())
            );
        }
    }
}
