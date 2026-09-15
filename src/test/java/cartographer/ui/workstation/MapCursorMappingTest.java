package cartographer.ui.workstation;

import cartographer.render.MapViewportGeometry;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCursorMappingTest {
    private static final MapViewportGeometry FULL_IMAGE = MapViewportGeometry.fullImage(
            100, 80, -20, 40, 30, 80
    );

    @Test
    void mapsMidpointAndIsIndependentOfZoom() {
        MapCursorPosition normal = map(FULL_IMAGE, 50, 40, 0, 0, 100, 80).orElseThrow();
        MapCursorPosition zoomed = map(FULL_IMAGE, 100, 80, 0, 0, 200, 160).orElseThrow();
        assertEquals(5.0, normal.absoluteX());
        assertEquals(60.0, normal.absoluteZ());
        assertEquals(normal, zoomed);
    }

    @Test
    void accountsForRenderedBoundsOrigin() {
        MapCursorPosition position = map(FULL_IMAGE, 75, 65, 25, 25, 100, 80).orElseThrow();
        assertEquals(5.0, position.absoluteX());
        assertEquals(60.0, position.absoluteZ());
    }

    @Test
    void rejectsPaddingAndMaximumExclusiveEdges() {
        MapViewportGeometry geometry = new MapViewportGeometry(
                200, 120, 20, 10, 100, 80, -50, -40, 50, 40
        );
        assertTrue(map(geometry, 20, 10, 0, 0, 200, 120).isPresent());
        assertTrue(map(geometry, 19, 20, 0, 0, 200, 120).isEmpty());
        assertTrue(map(geometry, 121, 20, 0, 0, 200, 120).isEmpty());
        assertTrue(map(geometry, 120, 50, 0, 0, 200, 120).isEmpty());
        assertTrue(map(geometry, 70, 90, 0, 0, 200, 120).isEmpty());
        assertTrue(map(geometry, 119.999, 89.999, 0, 0, 200, 120).isPresent());
    }

    @Test
    void rejectsInvalidRenderedBounds() {
        assertTrue(map(FULL_IMAGE, 1, 1, 0, 0, 0, 80).isEmpty());
        assertTrue(map(FULL_IMAGE, 1, 1, 0, 0, -1, 80).isEmpty());
        assertTrue(map(FULL_IMAGE, 1, 1, 0, 0, Double.NaN, 80).isEmpty());
        assertTrue(map(FULL_IMAGE, Double.POSITIVE_INFINITY, 1, 0, 0, 100, 80).isEmpty());
    }

    @Test
    void preservesNegativeAndFractionalWorldCoordinates() {
        MapViewportGeometry geometry = MapViewportGeometry.fullImage(
                100, 100, -100.5, -20.25, -50.5, 29.75
        );
        MapCursorPosition position = map(geometry, 25.25, 50.5, 0, 0, 100, 100).orElseThrow();
        assertEquals(-87.875, position.absoluteX());
        assertEquals(5.0, position.absoluteZ());
    }

    private Optional<MapCursorPosition> map(
            MapViewportGeometry geometry,
            double pointerX,
            double pointerY,
            double minX,
            double minY,
            double width,
            double height
    ) {
        return MapCursorMapping.toAbsoluteWorld(
                geometry, pointerX, pointerY, minX, minY, width, height
        );
    }
}
