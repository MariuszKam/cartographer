package cartographer.save;

import cartographer.model.ChunkPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkPosDecoderTest {

    @Test
    void decodesPositiveMainWorldChunkPosition() {
        long packed =
                2_148_691_623_533L;

        ChunkPosition position =
                ChunkPosDecoder.decode(
                        packed
                );

        assertEquals(
                15_981,
                position.x()
        );

        assertEquals(
                0,
                position.y()
        );

        assertEquals(
                16_009,
                position.z()
        );

        assertEquals(
                0,
                position.dimension()
        );
    }

    @Test
    void decodesNegativeCoordinates() {
        long packed =
                0x0000FFFFF81FFFFFL;

        ChunkPosition position =
                ChunkPosDecoder.decode(
                        packed
                );

        assertEquals(
                -1,
                position.x()
        );

        assertEquals(
                0,
                position.y()
        );

        assertEquals(
                -1,
                position.z()
        );

        assertEquals(
                0,
                position.dimension()
        );
    }

    @Test
    void decodesChunkY() {
        long packed =
                0x00C00000481FFFEDL;

        ChunkPosition position =
                ChunkPosDecoder.decode(
                        packed
                );

        assertEquals(
                -19,
                position.x()
        );

        assertEquals(
                3,
                position.y()
        );

        assertEquals(
                9,
                position.z()
        );

        assertEquals(
                0,
                position.dimension()
        );
    }

    @Test
    void decodesDimension() {
        long packed =
                0x7FC2F95E79403039L;

        ChunkPosition position =
                ChunkPosDecoder.decode(
                        packed
                );

        assertEquals(
                12_345,
                position.x()
        );

        assertEquals(
                511,
                position.y()
        );

        assertEquals(
                -54_321,
                position.z()
        );

        assertEquals(
                37,
                position.dimension()
        );
    }

    @Test
    void rejectsSetGuardBit() {
        long packed =
                1L << 21;

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ChunkPosDecoder.decode(
                                packed
                        )
        );
    }
}