package cartographer.parser;

import com.github.luben.zstd.Zstd;
import cartographer.model.DecodedChunkLayer;

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
        return decodeOwned(
                payload,
                savedCompressionVersion
        ).toArray();
    }

    DecodedChunkLayer decodeOwned(
            byte[] payload,
            int savedCompressionVersion
    ) {
        DecodedPalette decodedPalette =
                readPalette(
                        payload,
                        savedCompressionVersion
                );

        if (decodedPalette.values().length == 0) {
            return DecodedChunkLayer.empty(VALUE_COUNT);
        }

        int[] palette =
                decodedPalette.values();

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
                        decodedPalette.nextOffset(),
                        bitSize
                );

        return decodePaletteBits(
                roundedPalette,
                dataBitsBytes,
                bitSize
        );
    }

    public ChunkPaletteProbe probePalette(
            byte[] payload,
            int savedCompressionVersion
    ) {
        return new ChunkPaletteProbe(
                readPalette(
                        payload,
                        savedCompressionVersion
                ).values()
        );
    }

    private DecodedPalette readPalette(
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
            return new DecodedPalette(
                    new int[0],
                    buffer.position()
            );
        }

        int[] palette =
                paletteByteLengthMarker < 0
                        ? readRawPalette(
                        buffer,
                        -paletteByteLengthMarker
                )
                        : readCompressedPalette(
                        payload,
                        buffer,
                        paletteByteLengthMarker
                );

        return new DecodedPalette(
                palette,
                buffer.position()
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
            byte[] payload,
            ByteBuffer buffer,
            int compressedPaletteLength
    ) {
        if (compressedPaletteLength > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "compressed chunk palette exceeds payload length"
            );
        }

        int sourceOffset = buffer.position();

        long decompressedSize =
                Zstd.getFrameContentSize(
                        payload,
                        sourceOffset,
                        compressedPaletteLength
                );

        if (Zstd.isError(decompressedSize)
                || decompressedSize <= 0
                || decompressedSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "compressed chunk palette has invalid decompressed size: "
                            + decompressedSize
            );
        }

        byte[] paletteBytes =
                new byte[(int) decompressedSize];

        long decompressedLength;

        try {
            decompressedLength =
                    Zstd.decompressByteArray(
                            paletteBytes,
                            0,
                            paletteBytes.length,
                            payload,
                            sourceOffset,
                            compressedPaletteLength
                    );

        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "zstd palette decompression failed: "
                            + exception.getMessage(),
                    exception
            );
        }

        if (Zstd.isError(decompressedLength)) {
            throw new IllegalArgumentException(
                    "zstd palette decompression failed: "
                            + Zstd.getErrorName(decompressedLength)
            );
        }

        if (decompressedLength != decompressedSize) {
            throw new IllegalArgumentException(
                    "decoded palette length mismatch: expected "
                            + decompressedSize
                            + ", got "
                            + decompressedLength
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

        buffer.position(
                sourceOffset
                        + compressedPaletteLength
        );

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

        if (offset < 0 || offset >= payload.length) {
            throw new IllegalArgumentException(
                    "chunk data layer is missing compressed bit planes"
            );
        }

        byte[] decompressed =
                new byte[expectedLength];

        long decompressedLength;

        try {
            decompressedLength =
                    Zstd.decompressByteArray(
                            decompressed,
                            0,
                            expectedLength,
                            payload,
                            offset,
                            payload.length - offset
                    );

        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "zstd bit-plane decompression failed: "
                            + exception.getMessage(),
                    exception
            );
        }

        if (Zstd.isError(decompressedLength)) {
            throw new IllegalArgumentException(
                    "zstd bit-plane decompression failed: "
                            + Zstd.getErrorName(decompressedLength)
            );
        }

        if (decompressedLength != expectedLength) {
            throw new IllegalArgumentException(
                    "decoded bit planes length mismatch: expected "
                            + expectedLength
                            + ", got "
                            + decompressedLength
            );
        }

        return decompressed;
    }

    private DecodedChunkLayer decodePaletteBits(
            int[] palette,
            byte[] dataBitsBytes,
            int bitSize
    ) {
        DecodedChunkLayer.Builder values =
                DecodedChunkLayer.builder(VALUE_COUNT);

        if (bitSize == 0) {
            return values.fill(palette[0]).build();
        }

        ByteBuffer bits =
                ByteBuffer.wrap(dataBitsBytes)
                        .order(ByteOrder.LITTLE_ENDIAN);

        int[] paletteIndexes =
                new int[SIZE];

        for (int slice = 0; slice < SLICE_COUNT; slice++) {
            Arrays.fill(paletteIndexes, 0);

            for (int bit = 0; bit < bitSize; bit++) {
                int dataBits =
                        bits.getInt(
                                (bit * SLICE_COUNT + slice)
                                        * Integer.BYTES
                        );
                int value =
                        1 << bit;

                for (int x = 0; x < SIZE; x++) {
                    paletteIndexes[x] +=
                            ((dataBits >>> x) & 1)
                                    * value;
                }
            }

            for (int x = 0; x < SIZE; x++) {
                int paletteIndex = paletteIndexes[x];

                if (paletteIndex >= palette.length) {
                    throw new IllegalArgumentException(
                            "palette index out of range: "
                                    + paletteIndex
                    );
                }

                values.set(
                        slice * SIZE + x,
                        palette[paletteIndex]
                );
            }
        }

        return values.build();
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

    private record DecodedPalette(
            int[] values,
            int nextOffset
    ) {
    }
}
