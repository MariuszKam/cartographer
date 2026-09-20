package cartographer.application;

import cartographer.model.MapChunkCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIndexBatchPlannerTest {

    @Test
    void groupsCoordinatesIntoDeterministicBoundedSpatialBuckets() {
        WorldIndexBatchPlanner planner = new WorldIndexBatchPlanner(2);

        List<List<MapChunkCoordinate>> batches = planner.plan(List.of(
                new MapChunkCoordinate(2, 0),
                new MapChunkCoordinate(1, 1),
                new MapChunkCoordinate(0, 0),
                new MapChunkCoordinate(3, 1),
                new MapChunkCoordinate(0, 0)
        ));

        assertEquals(2, batches.size());
        assertEquals(
                List.of(
                        new MapChunkCoordinate(0, 0),
                        new MapChunkCoordinate(1, 1)
                ),
                batches.get(0)
        );
        assertEquals(
                List.of(
                        new MapChunkCoordinate(2, 0),
                        new MapChunkCoordinate(3, 1)
                ),
                batches.get(1)
        );
        assertTrue(batches.stream().allMatch(batch -> batch.size() <= 4));
    }
}
