package cartographer.parser;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPaletteFilterTest {

    @Test
    void matchesWantedIdInRawPalette() {
        byte[] payload = rawPalette(11, 22, 33);

        assertTrue(contains(payload, 2, 22));
        assertFalse(contains(payload, 2, 44));
    }

    @Test
    void matchesWantedIdInCompressedPalette() {
        byte[] payload = compressedPalette(11, 22, 33);

        assertTrue(contains(payload, 2, 33));
        assertFalse(contains(payload, 2, 44));
    }

    @Test
    void ignoresRoundedPalettePadding() {
        byte[] payload = rawPalette(11, 22, 33);

        assertTrue(contains(payload, 2, 33));
        assertFalse(contains(payload, 2, 0));
    }

    @Test
    void rejectsUnsupportedCompressionVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> contains(rawPalette(11), 99, 11)
        );
    }

    @Test
    void rejectsTruncatedPalette() {
        byte[] payload = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(-8)
                .array();

        assertThrows(
                IllegalArgumentException.class,
                () -> contains(payload, 2, 11)
        );
    }

    @Test
    void emptyPaletteRejectsEveryWantedId() {
        byte[] payload = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0)
                .array();

        assertFalse(contains(payload, 2, 11));
    }

    @Test
    void paletteFilterDoesNotDecodeBitPlanes() {
        byte[] payload = rawPalette(11, 22, 33);

        assertTrue(contains(payload, 2, 22));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkDataLayerDecoder().decodeOwned(payload, 2)
        );
    }

    private boolean contains(
            byte[] payload,
            int savedCompressionVersion,
            int... wantedBlockIds
    ) {
        try (ChunkDecodeWorkspace workspace = new ChunkDecodeWorkspace()) {
            return new ChunkDataLayerDecoder().paletteContainsAny(
                    payload,
                    savedCompressionVersion,
                    wantedBlockIds,
                    workspace
            );
        }
    }

    private byte[] rawPalette(int... values) {
        ByteBuffer buffer =
                ByteBuffer.allocate(
                                Integer.BYTES
                                        + values.length * Integer.BYTES
                        )
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

        byte[] rawValues =
                new byte[values.length * Integer.BYTES];
        palette.get(rawValues);
        byte[] compressed = Zstd.compress(rawValues, -3);

        ByteBuffer result =
                ByteBuffer.allocate(
                                Integer.BYTES + compressed.length
                        )
                        .order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(compressed.length);
        result.put(compressed);
        return result.array();
    }
}
