package cartographer.geology.rock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RockCellLayoutTest {
    @Test
    void selectsIntOrLongAtEveryPackingBoundary() {
        assertEquals(31, RockCellLayout.forTestFieldWidths(29, 0).totalBits());
        assertEquals(32, RockCellLayout.forTestFieldWidths(30, 0).totalBits());
        assertEquals(33, RockCellLayout.forTestFieldWidths(30, 1).totalBits());
        assertEquals(63, RockCellLayout.forTestFieldWidths(30, 31).totalBits());
        assertEquals(64, RockCellLayout.forTestFieldWidths(30, 32).totalBits());
        assertThrows(IllegalArgumentException.class,
                () -> RockCellLayout.forTestFieldWidths(30, 33));
        assertEquals(32, RockCellLayout.forTestFieldWidths(30, 0).intBacked() ? 32 : 0);
        assertEquals(33, RockCellLayout.forTestFieldWidths(30, 1).intBacked() ? 0 : 33);
    }

    @Test
    void roundTripsStatesIdentityAndYOffset() {
        RockCellLayout layout = RockCellLayout.forCatalog(3, 64);
        long observed = layout.pack(RockColumnState.OBSERVED, 2, 31);
        assertEquals(RockColumnState.OBSERVED, layout.state(observed));
        assertEquals(2, layout.rockOrdinal(observed));
        assertEquals(31, layout.yOffset(observed));
        assertEquals(RockColumnState.NO_ROCK, layout.state(layout.pack(RockColumnState.NO_ROCK, 0, 0)));
        assertEquals(RockColumnState.UNAVAILABLE, layout.state(layout.pack(RockColumnState.UNAVAILABLE, 0, 0)));
    }

    @Test
    void rejectsReservedAndOutOfRangeValues() {
        RockCellLayout layout = RockCellLayout.forCatalog(3, 64);
        assertThrows(IllegalArgumentException.class, () -> layout.state(3));
        assertThrows(IllegalArgumentException.class, () -> layout.pack(RockColumnState.OBSERVED, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> layout.pack(RockColumnState.OBSERVED, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> layout.pack(RockColumnState.OBSERVED, 1, 64));
        assertThrows(IllegalArgumentException.class, () -> layout.pack(RockColumnState.NO_ROCK, 1, 0));
    }
}
