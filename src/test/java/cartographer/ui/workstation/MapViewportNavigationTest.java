package cartographer.ui.workstation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapViewportNavigationTest {
    private static final double DELTA = 1.0e-9;

    @Test
    void centersExactMiddle() {
        assertEquals(0.5,
                MapViewportNavigation.centeredScrollFraction(0, 1000, 400, 500), DELTA);
    }

    @Test
    void clampsNearMinimum() {
        assertEquals(0.0,
                MapViewportNavigation.centeredScrollFraction(0, 1000, 400, 50), DELTA);
    }

    @Test
    void clampsNearMaximum() {
        assertEquals(1.0,
                MapViewportNavigation.centeredScrollFraction(0, 1000, 400, 950), DELTA);
    }

    @Test
    void centersNonScrollableContent() {
        assertEquals(0.5,
                MapViewportNavigation.centeredScrollFraction(10, 400, 400, 10), DELTA);
        assertEquals(0.5,
                MapViewportNavigation.centeredScrollFraction(10, 300, 400, 10), DELTA);
    }

    @Test
    void handlesAsymmetricDimensionsAndNonZeroContentOrigin() {
        assertEquals(0.2,
                MapViewportNavigation.centeredScrollFraction(100, 1200, 200, 400), DELTA);
        assertEquals(0.5,
                MapViewportNavigation.centeredScrollFraction(-30, 900, 500, 420), DELTA);
        assertEquals(25.0,
                MapViewportNavigation.interpolateScrollValue(10, 70, 0.25), DELTA);
    }
}
