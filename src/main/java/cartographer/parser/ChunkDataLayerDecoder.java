package cartographer.parser;

import com.github.luben.zstd.Zstd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

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

        int[] palette =
                paletteByteLengthMarker < 0
                        ? readRawPalette(
                        buffer,
                        -paletteByteLengthMarker
                )
                        : readCompressedPalette(
                        buffer,
                        paletteByteLengthMarker
                );

        if (palette.length == 0) {
            return new int[VALUE_COUNT];
        }

        int[] roundedPalette =
                roundedPalette(
                        palette
                );

        int bitSize =
                bitSize(
                        roundedPalette.length
                );

        byte[] dataBitsBytes =
                readCompressedDataBits(
                        payload,
                        buffer.position(),
                        bitSize
                );

        return decodePaletteBits(
                roundedPalette,
                dataBitsBytes,
                bitSize
        );
    }

    private int[] readRawPalette(
            ByteBuffer buffer,
            int paletteByteLength
    ) {
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

        return palette;
    }

    private int[] readCompressedPalette(
            ByteBuffer buffer,
            int compressedPaletteLength
    ) {
        if (compressedPaletteLength > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "compressed chunk palette exceeds payload length"
            );
        }

        byte[] compressedPalette =
                new byte[compressedPaletteLength];

        buffer.get(
                compressedPalette
        );

        long decompressedSize =
                Zstd.getFrameContentSize(
                        compressedPalette
                );

        if (decompressedSize <= 0 || decompressedSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "compressed chunk palette has invalid decompressed size: "
                            + decompressedSize
            );
        }

        byte[] paletteBytes;

        try {
            paletteBytes =
                    Zstd.decompress(
                            compressedPalette,
                            (int) decompressedSize
                    );

        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "zstd palette decompression failed: "
                            + exception.getMessage(),
                    exception
            );
        }

        if (paletteBytes.length % Integer.BYTES != 0) {
            throw new IllegalArgumentException(
                    "decompressed chunk palette byte length is not int aligned: "
                            + paletteBytes.length
            );
        }

        ByteBuffer paletteBuffer =
                ByteBuffer.wrap(
                                paletteBytes
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        int[] palette =
                new int[paletteBytes.length / Integer.BYTES];

        for (int index = 0; index < palette.length; index++) {
            palette[index] =
                    paletteBuffer.getInt();
        }

        return palette;
    }

    private byte[] readCompressedDataBits(
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
                    "zstd bit-plane decompression failed: "
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
            Arrays.fill(values, palette[0]);

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

    private int[] roundedPalette(
            int[] palette
    ) {
        int roundedSize =
                roundedUpPowerOfTwo(
                        palette.length
                );

        int[] rounded =
                new int[roundedSize];

        System.arraycopy(
                palette,
                0,
                rounded,
                0,
                palette.length
        );

        return rounded;
    }

    private int roundedUpPowerOfTwo(
            int value
    ) {
        if (value <= 1) {
            return value;
        }

        int rounded =
                1;

        while (rounded < value) {
            rounded <<= 1;
        }

        return rounded;
    }
}
