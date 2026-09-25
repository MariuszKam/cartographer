package cartographer.spatial;

import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreChunkPositionPlannerTest {

    private final OreChunkPositionPlanner planner = new OreChunkPositionPlanner();
    private final WorldMetadata world = new WorldMetadata(128, 256, 128);

    @Test
    void unboundedWorldHeight256PlansChunkY0Through7() {
        List<ChunkPosition> positions = plan(32, 32, 1, ActualBlockYFilter.unbounded());

        assertEquals(
                Set.of(0, 1, 2, 3, 4, 5, 6, 7),
                positions.stream().map(ChunkPosition::y).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void filter0To31PlansOnlyY0() {
        assertEquals(Set.of(0), yLevels(plan(32, 32, 1, new ActualBlockYFilter(0, 31))));
    }

    @Test
    void filter30To40PlansY0And1() {
        assertEquals(Set.of(0, 1), yLevels(plan(32, 32, 1, new ActualBlockYFilter(30, 40))));
    }

    @Test
    void filterOutsideWorldReturnsEmpty() {
        assertTrue(plan(32, 32, 1, new ActualBlockYFilter(500, 600)).isEmpty());
    }

    @Test
    void clampsToWorldBounds() {
        List<ChunkPosition> positions = plan(0, 0, 64, ActualBlockYFilter.unbounded());

        assertTrue(positions.stream().allMatch(position -> position.x() >= 0 && position.z() >= 0));
        assertTrue(positions.stream().allMatch(position -> position.x() < 4 && position.z() < 4));
    }

    @Test
    void includesChunkWhoseFootprintTouchesCircle() {
        List<ChunkPosition> positions = plan(0, 16, 32, new ActualBlockYFilter(0, 0));

        assertTrue(positions.contains(new ChunkPosition(1, 0, 0, 0)));
    }

    @Test
    void excludesChunkWhoseFootprintIsOutsideCircle() {
        List<ChunkPosition> positions = plan(0, 16, 32, new ActualBlockYFilter(0, 0));

        assertFalse(positions.contains(new ChunkPosition(2, 0, 0, 0)));
    }

    @Test
    void negativeAbsoluteSearchBoundsAreClamped() {
        List<ChunkPosition> positions = plan(-10, 16, 20, new ActualBlockYFilter(0, 0));

        assertTrue(positions.contains(new ChunkPosition(0, 0, 0, 0)));
        assertTrue(positions.stream().allMatch(position -> position.x() >= 0 && position.z() >= 0));
    }

    @Test
    void plannerProducesNoDuplicateChunkPositions() {
        List<ChunkPosition> positions = plan(32, 32, 32, ActualBlockYFilter.unbounded());

        assertEquals(positions.size(), new HashSet<>(positions).size());
    }

    @Test
    void allPositionsUseDimensionZero() {
        assertTrue(plan(32, 32, 32, ActualBlockYFilter.unbounded())
                .stream()
                .allMatch(position -> position.dimension() == 0));
    }

    @Test
    void centerNearChunkBoundaryIncludesBothAdjacentColumns() {
        List<ChunkPosition> positions = plan(31, 16, 1, new ActualBlockYFilter(0, 0));

        assertTrue(positions.contains(new ChunkPosition(0, 0, 0, 0)));
        assertTrue(positions.contains(new ChunkPosition(1, 0, 0, 0)));
    }

    private List<ChunkPosition> plan(
            int centerX,
            int centerZ,
            int radius,
            ActualBlockYFilter yFilter
    ) {
        return planner.plan(world, centerX, centerZ, radius, yFilter);
    }

    private Set<Integer> yLevels(List<ChunkPosition> positions) {
        return positions.stream()
                .map(ChunkPosition::y)
                .collect(java.util.stream.Collectors.toSet());
    }
}
