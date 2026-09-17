package cartographer.geology.rock;

import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RockCircleGeometryTest {
    @Test
    void radiusOneContainsFiveCellsInRowMajorOrder() {
        RockCircleGeometry geometry = new RockCircleGeometry(0, 0, 1);
        assertEquals(5, geometry.cellCount());
        assertEquals(0, geometry.rowOffset(0));
        assertEquals(1, geometry.rowLength(0));
        assertEquals(1, geometry.rowLength(1));
        assertEquals(3, geometry.rowLength(2));
        assertEquals(4, geometry.rowOffset(2));
        assertEquals(0, geometry.cellIndex(0, -1));
        assertEquals(1, geometry.cellIndex(-1, 0));
        assertEquals(3, geometry.cellIndex(1, 0));
    }

    @Test
    void radiusTwoAndLargeCountsUsePrimitiveRows() {
        assertEquals(13, new RockCircleGeometry(0, 0, 2).cellCount());
        assertEquals(3_294_097L, new RockCircleGeometry(0, 0, 1024).cellCount());
    }

    @Test
    void negativeAndFractionalCentersUseFloorSemantics() {
        RockCircleGeometry negative = RockCircleGeometry.from(new WorldPosition(-0.2, 0, -0.2), 1);
        RockCircleGeometry positive = RockCircleGeometry.from(new WorldPosition(10.8, 0, 10.8), 1);
        assertEquals(-1, negative.centerX());
        assertEquals(-1, negative.centerZ());
        assertEquals(10, positive.centerX());
        assertEquals(10, positive.centerZ());
    }

    @Test
    void outsideLookupAndOverflowAreRejected() {
        RockCircleGeometry geometry = new RockCircleGeometry(0, 0, 2);
        assertThrows(IllegalArgumentException.class, () -> geometry.cellIndex(2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new RockCircleGeometry(Integer.MAX_VALUE, 0, 1));
    }
}
