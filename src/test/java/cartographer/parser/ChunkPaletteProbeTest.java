package cartographer.parser;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkPaletteProbeTest {

    @Test
    void probesRawPalette() {
        assertArrayEquals(
                new int[]{11, 22, 33},
                new ChunkDataLayerDecoder()
                        .probePalette(rawPalette(11, 22, 33), 2)
                        .blockIds()
        );
    }

    @Test
    void probesCompressedPalette() {
        assertArrayEquals(
                new int[]{11, 22, 33},
                new ChunkDataLayerDecoder()
                        .probePalette(compressedPalette(11, 22, 33), 2)
                        .blockIds()
        );
    }

    @Test
    void probeDoesNotExposeRoundedPadding() {
        ChunkPaletteProbe probe =
                new ChunkDataLayerDecoder()
                        .probePalette(rawPalette(11, 22, 33), 2);

        assertEquals(3, probe.blockIds().length);
        assertArrayEquals(new int[]{11, 22, 33}, probe.blockIds());
    }

    @Test
    void probeRejectsUnsupportedCompressionVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkDataLayerDecoder().probePalette(
                        rawPalette(11),
                        99
                )
        );
    }

    @Test
    void probeRejectsTruncatedPalette() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkDataLayerDecoder().probePalette(
                        ByteBuffer.allocate(4)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(-8)
                                .array(),
                        2
                )
        );
    }

    @Test
    void emptyPaletteReturnsEmptyProbe() {
        assertEquals(
                0,
                new ChunkDataLayerDecoder()
                        .probePalette(
                                ByteBuffer.allocate(4)
                                        .order(ByteOrder.LITTLE_ENDIAN)
                                        .putInt(0)
                                        .array(),
                                2
                        )
                        .blockIds()
                        .length
        );
    }

    @Test
    void probeDoesNotDecodeBitPlanes() {
        byte[] payload = rawPalette(11, 22, 33);

        assertArrayEquals(
                new int[]{11, 22, 33},
                new ChunkDataLayerDecoder().probePalette(payload, 2).blockIds()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkDataLayerDecoder().decode(payload, 2)
        );
    }

    private byte[] rawPalette(int... values) {
        ByteBuffer buffer =
                ByteBuffer.allocate(Integer.BYTES + values.length * Integer.BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(-values.length * Integer.BYTES);
        for (int value : values) {
            buffer.putInt(value);
        }
        return buffer.array();
    }

    private byte[] compressedPalette(int... values) {
        byte[] paletteBytes = rawPalette(values);
        ByteBuffer palette =
                ByteBuffer.wrap(paletteBytes)
                        .order(ByteOrder.LITTLE_ENDIAN);
        palette.getInt();
        byte[] rawValues = new byte[values.length * Integer.BYTES];
        palette.get(rawValues);
        byte[] compressed = Zstd.compress(rawValues, -3);

        ByteBuffer result =
                ByteBuffer.allocate(Integer.BYTES + compressed.length)
                        .order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(compressed.length);
        result.put(compressed);
        return result.array();
    }
}
