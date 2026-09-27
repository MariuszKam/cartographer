package cartographer.render;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderTileCoordinateTest {

    @Test
    void zeroDistanceRingContainsOnlyCenter() {
        RenderTileCoordinate center = new RenderTileCoordinate(3, -7);

        assertEquals(List.of(center), center.squareRing(0));
    }

    @Test
    void firstRingUsesDeterministicClockwiseOrder() {
        RenderTileCoordinate center = new RenderTileCoordinate(10, -5);

        assertEquals(
                List.of(
                        new RenderTileCoordinate(9, -6),
                        new RenderTileCoordinate(10, -6),
                        new RenderTileCoordinate(11, -6),
                        new RenderTileCoordinate(11, -5),
                        new RenderTileCoordinate(11, -4),
                        new RenderTileCoordinate(10, -4),
                        new RenderTileCoordinate(9, -4),
                        new RenderTileCoordinate(9, -5)
                ),
                center.squareRing(1)
        );
    }

    @Test
    void ringContainsEachChebyshevBoundaryCoordinateExactlyOnce() {
        RenderTileCoordinate center = new RenderTileCoordinate(-2, 4);
        List<RenderTileCoordinate> ring = center.squareRing(2);

        assertEquals(16, ring.size());
        assertEquals(16, new HashSet<>(ring).size());
        for (RenderTileCoordinate coordinate : ring) {
            int dx = Math.abs(coordinate.x() - center.x());
            int dz = Math.abs(coordinate.z() - center.z());
            assertEquals(2, Math.max(dx, dz));
        }
    }

    @Test
    void rejectsNegativeRingDistance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderTileCoordinate(0, 0).squareRing(-1)
        );
    }
}
