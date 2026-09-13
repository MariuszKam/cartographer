package cartographer.save;

import cartographer.model.ChunkPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkPosEncoderTest {

    @Test
    void knownPositiveVector() {
        assertEquals(
                2_148_691_623_533L,
                ChunkPosEncoder.encode(15_981, 0, 16_009, 0)
        );
    }

    @Test
    void knownNegativeVector() {
        assertEquals(
                0x0000FFFFF81FFFFFL,
                ChunkPosEncoder.encode(-1, 0, -1, 0)
        );
    }

    @Test
    void knownYVector() {
        assertEquals(
                0x00C00000481FFFEDL,
                ChunkPosEncoder.encode(-19, 3, 9, 0)
        );
    }

    @Test
    void knownDimensionVector() {
        assertEquals(
                0x7FC2F95E79403039L,
                ChunkPosEncoder.encode(12_345, 511, -54_321, 37)
        );
    }

    @Test
    void roundTripsOrigin() {
        assertRoundTrip(0, 0, 0, 0);
    }

    @Test
    void roundTripsMinimumCoordinates() {
        assertRoundTrip(-1_048_576, 0, -1_048_576, 0);
    }

    @Test
    void roundTripsMaximumCoordinates() {
        assertRoundTrip(1_048_575, 0, 1_048_575, 0);
    }

    @Test
    void roundTripsMaximumY() {
        assertRoundTrip(0, 511, 0, 0);
    }

    @Test
    void roundTripsMaximumDimension() {
        assertRoundTrip(0, 0, 0, 1023);
    }

    @Test
    void positionOverloadMatchesCoordinateOverload() {
        ChunkPosition position =
                new ChunkPosition(12_345, 511, -54_321, 37);

        assertEquals(
                ChunkPosEncoder.encode(
                        position.x(),
                        position.y(),
                        position.z(),
                        position.dimension()
                ),
                ChunkPosEncoder.encode(position)
        );
    }

    @Test
    void rejectsCoordinatesOutsideSigned21BitRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkPosEncoder.encode(-1_048_577, 0, 0, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkPosEncoder.encode(1_048_576, 0, 0, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkPosEncoder.encode(0, 0, -1_048_577, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkPosEncoder.encode(0, 0, 1_048_576, 0)
        );
    }

    @Test
    void rejectsYOutsideUnsigned9BitRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkPosEncoder.encode(0, -1, 0, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkPosEncoder.encode(0, 512, 0, 0)
        );
    }

    @Test
    void rejectsDimensionOutsideUnsigned10BitRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkPosEncoder.encode(0, 0, 0, -1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkPosEncoder.encode(0, 0, 0, 1024)
        );
    }

    private void assertRoundTrip(
            int x,
            int y,
            int z,
            int dimension
    ) {
        ChunkPosition expected =
                new ChunkPosition(x, y, z, dimension);

        assertEquals(
                expected,
                ChunkPosDecoder.decode(
                        ChunkPosEncoder.encode(expected)
                )
        );
    }
}
