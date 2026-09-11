package cartographer.parser;

import com.github.luben.zstd.Zstd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ChunkDataLayerDecoder {
    public static final int SIZE = 32;
    public static final int VALUE_COUNT = SIZE * SIZE * SIZE;
    private static final int SLICE_COUNT = SIZE * SIZE;
    private static final int SUPPORTED_COMPRESSION_VERSION = 2;

    public int[] decode(
            byte[] payload,
            int savedCompressionVersion
    ) {
        if (savedCompressionVersion != SUPPORTED_COMPRESSION_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported chunk compression version: "
                            + savedCompressionVersion
            );
        }

        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException(
                    "chunk data layer payload is empty"
            );
        }

        if (payload.length < Integer.BYTES) {
            throw new IllegalArgumentException(
                    "chunk data layer payload is truncated"
            );
        }

        ByteBuffer buffer =
                ByteBuffer.wrap(payload)
                        .order(ByteOrder.LITTLE_ENDIAN);

        int paletteByteLengthMarker =
                buffer.getInt();

        if (paletteByteLengthMarker == 0) {
            return new int[VALUE_COUNT];
        }

        if (paletteByteLengthMarker > 0) {
            throw new IllegalArgumentException(
                    "unsupported uncompressed chunk data layer marker: "
                            + paletteByteLengthMarker
            );
        }

        int paletteByteLength =
                -paletteByteLengthMarker;

        if (paletteByteLength % Integer.BYTES != 0) {
            throw new IllegalArgumentException(
                    "chunk palette byte length is not int aligned: "
                            + paletteByteLength
            );
        }

        if (paletteByteLength > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "chunk palette exceeds payload length"
            );
        }

        int[] palette =
                new int[paletteByteLength / Integer.BYTES];

        for (int index = 0; index < palette.length; index++) {
            palette[index] =
                    buffer.getInt();
        }

        if (palette.length == 0) {
            return new int[VALUE_COUNT];
        }

        int bitSize =
                bitSize(
                        palette.length
                );

        byte[] dataBitsBytes =
                readDataBits(
                        payload,
                        buffer.position(),
                        bitSize
                );

        return decodePaletteBits(
                palette,
                dataBitsBytes,
                bitSize
        );
    }

    private byte[] readDataBits(
            byte[] payload,
            int offset,
            int bitSize
    ) {
        int expectedLength =
                bitSize * SLICE_COUNT * Integer.BYTES;

        if (expectedLength == 0) {
            return new byte[0];
        }

        if (offset >= payload.length) {
            throw new IllegalArgumentException(
                    "chunk data layer is missing compressed bit planes"
            );
        }

        byte[] compressed =
                new byte[payload.length - offset];

        System.arraycopy(
                payload,
                offset,
                compressed,
                0,
                compressed.length
        );

        byte[] decompressed;

        try {
            decompressed =
                    Zstd.decompress(
                            compressed,
                            expectedLength
                    );

        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "zstd decompression failed: "
                            + exception.getMessage(),
                    exception
            );
        }

        if (decompressed.length != expectedLength) {
            throw new IllegalArgumentException(
                    "decoded bit planes length mismatch: expected "
                            + expectedLength
                            + ", got "
                            + decompressed.length
            );
        }

        return decompressed;
    }

    private int[] decodePaletteBits(
            int[] palette,
            byte[] dataBitsBytes,
            int bitSize
    ) {
        int[] values =
                new int[VALUE_COUNT];

        if (bitSize == 0) {
            for (int index = 0; index < values.length; index++) {
                values[index] =
                        palette[0];
            }

            return values;
        }

        ByteBuffer bits =
                ByteBuffer.wrap(dataBitsBytes)
                        .order(ByteOrder.LITTLE_ENDIAN);

        int[][] dataBits =
                new int[bitSize][SLICE_COUNT];

        for (int bit = 0; bit < bitSize; bit++) {
            for (int slice = 0; slice < SLICE_COUNT; slice++) {
                dataBits[bit][slice] =
                        bits.getInt();
            }
        }

        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                int slice =
                        y * SIZE
                                + z;

                for (int x = 0; x < SIZE; x++) {
                    int paletteIndex =
                            0;

                    int value =
                            1;

                    for (int bit = 0; bit < bitSize; bit++) {
                        paletteIndex +=
                                ((dataBits[bit][slice] >>> x) & 1)
                                        * value;

                        value <<= 1;
                    }

                    if (paletteIndex >= palette.length) {
                        throw new IllegalArgumentException(
                                "palette index out of range: "
                                        + paletteIndex
                        );
                    }

                    values[(y * SIZE + z) * SIZE + x] =
                            palette[paletteIndex];
                }
            }
        }

        return values;
    }

    private int bitSize(
            int paletteLength
    ) {
        int bitSize =
                0;

        int values =
                Math.max(
                        0,
                        paletteLength - 1
                );

        while (values > 0) {
            bitSize++;
            values >>>= 1;
        }

        return bitSize;
    }
}
