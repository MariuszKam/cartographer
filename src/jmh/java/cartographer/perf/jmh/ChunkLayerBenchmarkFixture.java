package cartographer.perf.jmh;

import com.github.luben.zstd.Zstd;
import cartographer.parser.ChunkDataLayerDecoder;
import cartographer.parser.ChunkDataLayerDecoderJmhAccess;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Deterministic in-memory layer fixtures shared by decoder microbenchmarks. */
public final class ChunkLayerBenchmarkFixture {
    public static final String RAW_PALETTE_19 = "RAW_PALETTE_19";
    public static final String COMPRESSED_PALETTE_19 = "COMPRESSED_PALETTE_19";

    private static final int SIZE = 32;
    private static final int SLICE_COUNT = SIZE * SIZE;
    private static final int PALETTE_LENGTH = 19;

    private ChunkLayerBenchmarkFixture() {
    }

    public static FixtureData create(String variant) {
        int[] palette = palette();
        byte[] bitPlanes = compressedBitPlanes();
        byte[] payload;

        if (RAW_PALETTE_19.equals(variant)) {
            payload = rawPalettePayload(palette, bitPlanes);
        } else if (COMPRESSED_PALETTE_19.equals(variant)) {
            payload = compressedPalettePayload(palette, bitPlanes);
        } else {
            throw new IllegalArgumentException("unknown fixture: " + variant);
        }

        return new FixtureData(payload, expectedChecksum(palette));
    }

    public static void verifyPublicDecode(
            ChunkDataLayerDecoder decoder,
            FixtureData fixture
    ) {
        int[] decoded = ChunkDataLayerDecoderJmhAccess
                .decodeOwned(decoder, fixture.payload(), 2)
                .toArray();
        if (decoded.length != ChunkDataLayerDecoder.VALUE_COUNT) {
            throw new IllegalStateException(
                    "fixture decoded length mismatch: " + decoded.length
            );
        }
        if (checksum(decoded) != fixture.expectedChecksum()) {
            throw new IllegalStateException("fixture decoded checksum mismatch");
        }
    }

    public static long checksum(int[] values) {
        long checksum = 0;
        for (int value : values) {
            checksum = checksum * 31 + value;
        }
        return checksum;
    }

    private static int[] palette() {
        int[] palette = new int[PALETTE_LENGTH];
        for (int index = 0; index < palette.length; index++) {
            palette[index] = 1000 + index * 7;
        }
        return palette;
    }

    private static byte[] rawPalettePayload(int[] palette, byte[] bitPlanes) {
        ByteBuffer payload = ByteBuffer.allocate(
                        Integer.BYTES + palette.length * Integer.BYTES + bitPlanes.length
                )
                .order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(-palette.length * Integer.BYTES);
        for (int value : palette) {
            payload.putInt(value);
        }
        payload.put(bitPlanes);
        return payload.array();
    }

    private static byte[] compressedPalettePayload(int[] palette, byte[] bitPlanes) {
        ByteBuffer paletteBytes = ByteBuffer.allocate(palette.length * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int value : palette) {
            paletteBytes.putInt(value);
        }
        byte[] compressedPalette = Zstd.compress(paletteBytes.array(), -3);

        ByteBuffer payload = ByteBuffer.allocate(
                        Integer.BYTES + compressedPalette.length + bitPlanes.length
                )
                .order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(compressedPalette.length);
        payload.put(compressedPalette);
        payload.put(bitPlanes);
        return payload.array();
    }

    private static byte[] compressedBitPlanes() {
        ByteBuffer bitPlanes = ByteBuffer.allocate(
                        5 * SLICE_COUNT * Integer.BYTES
                )
                .order(ByteOrder.LITTLE_ENDIAN);

        for (int bit = 0; bit < 5; bit++) {
            for (int slice = 0; slice < SLICE_COUNT; slice++) {
                int y = slice / SIZE;
                int z = slice % SIZE;
                int word = 0;
                for (int x = 0; x < SIZE; x++) {
                    int paletteIndex = paletteIndex(x, y, z);
                    if (((paletteIndex >>> bit) & 1) != 0) {
                        word |= 1 << x;
                    }
                }
                bitPlanes.putInt(word);
            }
        }

        return Zstd.compress(bitPlanes.array(), -3);
    }

    private static long expectedChecksum(int[] palette) {
        long checksum = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    checksum = checksum * 31 + palette[paletteIndex(x, y, z)];
                }
            }
        }
        return checksum;
    }

    private static int paletteIndex(int x, int y, int z) {
        return (x * 3 + y * 5 + z * 7) % PALETTE_LENGTH;
    }

    public record FixtureData(byte[] payload, long expectedChecksum) {
    }
}
