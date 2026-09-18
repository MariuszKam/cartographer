package cartographer.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapViewportGeometryTest {

    private static final double DELTA = 1.0e-9;

    @Test
    void reportsWorldCoverageAndEffectiveBlocksPerPixel() {
        MapViewportGeometry geometry = fullImage(
                4096, 4096, -4096, -4096, 4096, 4096
        );

        assertEquals(8192.0, geometry.worldWidthBlocks(), DELTA);
        assertEquals(8192.0, geometry.worldHeightBlocks(), DELTA);
        assertEquals(2.0, geometry.blocksPerPixelX(), DELTA);
        assertEquals(2.0, geometry.blocksPerPixelZ(), DELTA);
    }

    @Test
    void mapsWorldCoordinatesToImageCoordinates() {
        MapViewportGeometry geometry = fullImage(100, 80, -20, 40, 30, 80);

        assertEquals(50.0, geometry.absoluteWorldXToImageX(5), DELTA);
        assertEquals(40.0, geometry.absoluteWorldZToImageY(60), DELTA);
    }

    @Test
    void mapsImageCoordinatesToWorldCoordinates() {
        MapViewportGeometry geometry = fullImage(100, 80, -20, 40, 30, 80);

        assertEquals(5.0, geometry.imageXToAbsoluteWorldX(50), DELTA);
        assertEquals(60.0, geometry.imageYToAbsoluteWorldZ(40), DELTA);
    }

    @Test
    void roundTripsFractionalAndNegativeCoordinates() {
        MapViewportGeometry geometry = fullImage(200, 100, -150, -80, 50, 20);
        double worldX = -12.75;
        double worldZ = -33.25;

        assertEquals(
                worldX,
                geometry.imageXToAbsoluteWorldX(
                        geometry.absoluteWorldXToImageX(worldX)
                ),
                DELTA
        );
        assertEquals(
                worldZ,
                geometry.imageYToAbsoluteWorldZ(
                        geometry.absoluteWorldZToImageY(worldZ)
                ),
                DELTA
        );
    }

    @Test
    void supportsPaddedContentRectangle() {
        MapViewportGeometry geometry = new MapViewportGeometry(
                200, 120, 20, 10, 100, 80,
                -50, -40, 50, 40
        );

        assertEquals(20.0, geometry.absoluteWorldXToImageX(-50), DELTA);
        assertEquals(120.0, geometry.absoluteWorldXToImageX(50), DELTA);
        assertEquals(10.0, geometry.absoluteWorldZToImageY(-40), DELTA);
        assertEquals(90.0, geometry.absoluteWorldZToImageY(40), DELTA);
        assertTrue(geometry.containsImagePoint(20, 10));
        assertFalse(geometry.containsImagePoint(120, 90));
    }

    @Test
    void usesInclusiveMinimumAndExclusiveMaximumContainment() {
        MapViewportGeometry geometry = fullImage(100, 80, -20, 40, 30, 80);

        assertTrue(geometry.containsAbsoluteWorldPoint(-20, 40));
        assertTrue(geometry.containsAbsoluteWorldPoint(29.999, 79.999));
        assertFalse(geometry.containsAbsoluteWorldPoint(30, 60));
        assertFalse(geometry.containsAbsoluteWorldPoint(5, 80));
        assertTrue(geometry.containsImagePoint(0, 0));
        assertTrue(geometry.containsImagePoint(99.999, 79.999));
        assertFalse(geometry.containsImagePoint(100, 40));
        assertFalse(geometry.containsImagePoint(50, 80));
    }

    @Test
    void pointsOutsideRepresentedAreasAreNotContained() {
        MapViewportGeometry geometry = new MapViewportGeometry(
                200, 120, 20, 10, 100, 80,
                -50, -40, 50, 40
        );

        assertFalse(geometry.containsImagePoint(19.999, 50));
        assertFalse(geometry.containsImagePoint(50, 90));
        assertFalse(geometry.containsAbsoluteWorldPoint(-50.001, 0));
        assertFalse(geometry.containsAbsoluteWorldPoint(0, 40));
    }

    @Test
    void rejectsInvalidGeometry() {
        assertThrows(IllegalArgumentException.class,
                () -> fullImage(0, 10, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MapViewportGeometry(10, 10, 9, 0, 2, 1, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MapViewportGeometry(10, 10, 0, 0, 1, 1,
                        Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> fullImage(10, 10, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> fullImage(10, 10, 0, 0, Double.POSITIVE_INFINITY, 1));
    }

    private MapViewportGeometry fullImage(
            int width,
            int height,
            double minX,
            double minZ,
            double maxX,
            double maxZ
    ) {
        return MapViewportGeometry.fullImage(
                width, height, minX, minZ, maxX, maxZ
        );
    }
}
