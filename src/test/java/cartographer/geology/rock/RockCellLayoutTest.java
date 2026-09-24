package cartographer.geology.rock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RockCellLayoutTest {
    @Test
    void selectsIntOrLongAtEveryPackingBoundary() {
        org.junit.jupiter.api.Assertions.assertTrue(
                RockCellLayout.forCatalog((1 << 29) - 1, 1).intBacked()
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                RockCellLayout.forCatalog((1 << 30) - 1, 1).intBacked()
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                RockCellLayout.forCatalog(1 << 30, 1).intBacked()
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                RockCellLayout.forCatalog((1 << 30) - 1, 1L << 31).intBacked()
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                RockCellLayout.forCatalog((1 << 30) - 1, 1L << 32).intBacked()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RockCellLayout.forCatalog((1 << 30) - 1, 1L << 33)
        );
        assertEquals(true, RockCellLayout.forCatalog((1 << 30) - 1, 1).intBacked());
        assertEquals(false, RockCellLayout.forCatalog(1 << 30, 1).intBacked());
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
