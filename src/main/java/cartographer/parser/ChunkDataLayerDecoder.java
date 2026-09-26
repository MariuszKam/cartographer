package cartographer.parser;

import com.github.luben.zstd.Zstd;
import cartographer.model.DecodedChunkLayer;

import java.util.Arrays;
import java.util.Objects;

public class ChunkDataLayerDecoder {
    public static final int SIZE = 32;
    public static final int VALUE_COUNT = SIZE * SIZE * SIZE;
    private static final int SLICE_COUNT = SIZE * SIZE;
    private static final int SUPPORTED_COMPRESSION_VERSION = 2;

    DecodedChunkLayer decodeOwned(
            byte[] payload,
            int savedCompressionVersion
    ) {
        try (ChunkDecodeWorkspace workspace = new ChunkDecodeWorkspace()) {
            return decodeOwned(
                    payload,
                    0,
                    payloadLength(payload),
                    savedCompressionVersion,
                    workspace
            );
        }
    }

    DecodedChunkLayer decodeOwned(
            byte[] payload,
            ChunkDecodeWorkspace workspace
    ) {
        return decodeOwned(
                payload,
                0,
                payloadLength(payload),
                SUPPORTED_COMPRESSION_VERSION,
                workspace
        );
    }

    DecodedChunkLayer decodeOwned(
            byte[] payload,
            int sourceOffset,
            int sourceLength,
            int savedCompressionVersion,
            ChunkDecodeWorkspace workspace
    ) {
        int sourceLimit = validateSlice(
                payload,
                sourceOffset,
                sourceLength
        );
        DecodedPalette decodedPalette = readPalette(
                payload,
                sourceOffset,
                sourceLength,
                savedCompressionVersion,
                workspace
        );

        if (decodedPalette.length() == 0) {
            return DecodedChunkLayer.empty(VALUE_COUNT);
        }

        int[] palette = workspace.paletteBuffer();
        int roundedPaletteLength =
                roundedPalette(
                        palette,
                        decodedPalette.length(),
                        workspace
                );

        int bitSize =
                bitSize(
                        roundedPaletteLength
                );

        if (bitSize == 0) {
            return DecodedChunkLayer.constant(
                    VALUE_COUNT,
                    palette[0]
            );
        }

        byte[] dataBitsBytes =
                readCompressedDataBits(
                        payload,
                        decodedPalette.nextOffset(),
                        sourceLimit,
                        bitSize,
                        workspace
                );

        return decodePaletteBits(
                palette,
                dataBitsBytes,
                bitSize,
                roundedPaletteLength,
                workspace
        );
    }

    DecodedChunkLayer decodeCompactOwned(
            byte[] payload,
            int sourceOffset,
            int sourceLength,
            int savedCompressionVersion,
            ChunkDecodeWorkspace workspace
    ) {
        int sourceLimit = validateSlice(
                payload,
                sourceOffset,
                sourceLength
        );
        DecodedPalette decodedPalette = readPalette(
                payload,
                sourceOffset,
                sourceLength,
                savedCompressionVersion,
                workspace
        );

        if (decodedPalette.length() == 0) {
            return DecodedChunkLayer.empty(
                    VALUE_COUNT
            );
        }

        int[] palette =
                workspace.paletteBuffer();
        int roundedPaletteLength =
                roundedPalette(
                        palette,
                        decodedPalette.length(),
                        workspace
                );
        int bitSize =
                bitSize(
                        roundedPaletteLength
                );

        if (bitSize == 0) {
            return DecodedChunkLayer.constant(
                    VALUE_COUNT,
                    palette[0]
            );
        }

        byte[] dataBitsBytes =
                readCompressedDataBits(
                        payload,
                        decodedPalette.nextOffset(),
                        sourceLimit,
                        bitSize,
                        workspace
                );
        int bitPlaneLength =
                Math.multiplyExact(
                        Math.multiplyExact(
                                bitSize,
                                SLICE_COUNT
                        ),
                        Integer.BYTES
                );

        return DecodedChunkLayer.compactPaletteBits(
                VALUE_COUNT,
                palette,
                roundedPaletteLength,
                dataBitsBytes,
                bitPlaneLength,
                bitSize,
                SLICE_COUNT,
                SIZE
        );
    }

    boolean paletteContainsAny(
            byte[] payload,
            int savedCompressionVersion,
            int[] wantedBlockIds,
            ChunkDecodeWorkspace workspace
    ) {
        return paletteContainsAny(
                payload,
                0,
                payloadLength(payload),
                savedCompressionVersion,
                wantedBlockIds,
                workspace
        );
    }

    boolean paletteContainsAny(
            byte[] payload,
            int sourceOffset,
            int sourceLength,
            int savedCompressionVersion,
            int[] wantedBlockIds,
            ChunkDecodeWorkspace workspace
    ) {
        Objects.requireNonNull(
                wantedBlockIds,
                "wantedBlockIds are required"
        );
        if (wantedBlockIds.length == 0) {
            return false;
        }
        DecodedPalette decoded =
                readPalette(
                        payload,
                        sourceOffset,
                        sourceLength,
                        savedCompressionVersion,
                        workspace
                );
        int[] palette = workspace.paletteBuffer();
        for (int paletteIndex = 0;
             paletteIndex < decoded.length();
             paletteIndex++) {
            int value = palette[paletteIndex];
            for (int wantedBlockId : wantedBlockIds) {
                if (value == wantedBlockId) {
                    return true;
                }
            }
        }
        return false;
    }

    private DecodedPalette readPalette(
            byte[] payload,
            int sourceOffset,
            int sourceLength,
            int savedCompressionVersion,
            ChunkDecodeWorkspace workspace
    ) {
        if (savedCompressionVersion != SUPPORTED_COMPRESSION_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported chunk compression version: "
                            + savedCompressionVersion
            );
        }

        int sourceLimit =
                validateSlice(
                        payload,
                        sourceOffset,
                        sourceLength
                );

        if (sourceLength < Integer.BYTES) {
            throw new IllegalArgumentException(
                    "chunk data layer payload is truncated"
            );
        }

        int paletteByteLengthMarker =
                readLittleEndianInt(
                        payload,
                        sourceOffset
                );

        int paletteOffset =
                sourceOffset + Integer.BYTES;

        if (paletteByteLengthMarker == 0) {
            return new DecodedPalette(
                    0,
                    paletteOffset
            );
        }

        if (paletteByteLengthMarker == Integer.MIN_VALUE) {
            throw new IllegalArgumentException(
                    "chunk palette length marker is invalid"
            );
        }

        if (paletteByteLengthMarker < 0) {
            int paletteByteLength =
                    -paletteByteLengthMarker;
            validatePaletteByteLength(
                    paletteByteLength
            );
            int paletteLength =
                    paletteByteLength / Integer.BYTES;
            int[] palette =
                    workspace.ensurePaletteCapacity(
                            paletteLength
                    );
            return new DecodedPalette(
                    paletteLength,
                    readRawPalette(
                            payload,
                            paletteOffset,
                            sourceLimit,
                            paletteLength,
                            paletteByteLength,
                            palette
                    )
            );
        }

        validateCompressedPaletteLength(
                paletteByteLengthMarker,
                sourceLimit - paletteOffset
        );
        return readCompressedPalette(
                payload,
                paletteOffset,
                sourceLimit,
                paletteByteLengthMarker,
                workspace
        );
    }

    private int readRawPalette(
            byte[] payload,
            int sourceOffset,
            int sourceLimit,
            int paletteLength,
            int paletteByteLength,
            int[] palette
    ) {
        if (paletteByteLength % Integer.BYTES != 0) {
            throw new IllegalArgumentException(
                    "chunk palette byte length is not int aligned: "
                            + paletteByteLength
            );
        }

        if (paletteByteLength > sourceLimit - sourceOffset) {
            throw new IllegalArgumentException(
                    "chunk palette exceeds payload length"
            );
        }

        for (int index = 0;
             index < paletteLength;
             index++) {
            palette[index] =
                    readLittleEndianInt(
                            payload,
                            sourceOffset
                                    + index * Integer.BYTES
                    );
        }

        return sourceOffset + paletteByteLength;
    }

    private DecodedPalette readCompressedPalette(
            byte[] payload,
            int sourceOffset,
            int sourceLimit,
            int compressedPaletteLength,
            ChunkDecodeWorkspace workspace
    ) {
        if (compressedPaletteLength
                > sourceLimit - sourceOffset) {
            throw new IllegalArgumentException(
                    "compressed chunk palette exceeds payload length"
            );
        }

        long decompressedSize =
                Zstd.getFrameContentSize(
                        payload,
                        sourceOffset,
                        compressedPaletteLength
                );

        if (Zstd.isError(decompressedSize)
                || decompressedSize <= 0
                || decompressedSize
                > ChunkDecodeWorkspace.MAX_PALETTE_BYTES) {
            throw new IllegalArgumentException(
                    "compressed chunk palette has invalid decompressed size: "
                            + decompressedSize
            );
        }

        if (decompressedSize % Integer.BYTES != 0) {
            throw new IllegalArgumentException(
                    "decompressed chunk palette byte length is not int aligned: "
                            + decompressedSize
            );
        }

        int paletteLength =
                (int) decompressedSize
                        / Integer.BYTES;
        int[] palette =
                workspace.ensurePaletteCapacity(
                        paletteLength
                );
        byte[] paletteBytes =
                workspace.ensureDecompressionCapacity(
                        (int) decompressedSize
                );

        long decompressedLength;

        try {
            decompressedLength =
                    workspace.decompress(
                            paletteBytes,
                            (int) decompressedSize,
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
                            + Zstd.getErrorName(
                            decompressedLength
                    )
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

        for (int index = 0;
             index < paletteLength;
             index++) {
            palette[index] =
                    readLittleEndianInt(
                            paletteBytes,
                            index * Integer.BYTES
                    );
        }

        return new DecodedPalette(
                paletteLength,
                sourceOffset
                        + compressedPaletteLength
        );
    }

    private byte[] readCompressedDataBits(
            byte[] payload,
            int offset,
            int sourceLimit,
            int bitSize,
            ChunkDecodeWorkspace workspace
    ) {
        int expectedLength =
                bitSize
                        * SLICE_COUNT
                        * Integer.BYTES;

        if (expectedLength == 0) {
            return workspace.ensureDecompressionCapacity(
                    0
            );
        }

        if (offset < 0
                || offset >= sourceLimit) {
            throw new IllegalArgumentException(
                    "chunk data layer is missing compressed bit planes"
            );
        }

        byte[] decompressed =
                workspace.ensureDecompressionCapacity(
                        expectedLength
                );

        long decompressedLength;

        try {
            decompressedLength =
                    workspace.decompress(
                            decompressed,
                            expectedLength,
                            payload,
                            offset,
                            sourceLimit - offset
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
                            + Zstd.getErrorName(
                            decompressedLength
                    )
            );
        }

        if (decompressedLength
                != expectedLength) {
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
            int bitSize,
            int paletteLength,
            ChunkDecodeWorkspace workspace
    ) {
        if (bitSize == 0) {
            return DecodedChunkLayer.constant(
                    VALUE_COUNT,
                    palette[0]
            );
        }

        DecodedChunkLayer.Builder values =
                DecodedChunkLayer.builder(
                        VALUE_COUNT
                );
        int[] paletteIndexes =
                workspace.paletteIndexes();

        for (int slice = 0;
             slice < SLICE_COUNT;
             slice++) {
            Arrays.fill(
                    paletteIndexes,
                    0
            );

            for (int bit = 0;
                 bit < bitSize;
                 bit++) {
                int dataBits =
                        readLittleEndianInt(
                                dataBitsBytes,
                                (bit
                                        * SLICE_COUNT
                                        + slice)
                                        * Integer.BYTES
                        );
                int value =
                        1 << bit;

                for (int x = 0;
                     x < SIZE;
                     x++) {
                    paletteIndexes[x] +=
                            ((dataBits >>> x)
                                    & 1)
                                    * value;
                }
            }

            for (int x = 0;
                 x < SIZE;
                 x++) {
                int paletteIndex =
                        paletteIndexes[x];

                if (paletteIndex
                        >= paletteLength) {
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

    private int roundedPalette(
            int[] palette,
            int paletteLength,
            ChunkDecodeWorkspace workspace
    ) {
        int roundedSize =
                roundedUpPowerOfTwo(
                        paletteLength
                );
        workspace.ensurePaletteCapacity(
                roundedSize
        );
        if (roundedSize > paletteLength) {
            Arrays.fill(
                    palette,
                    paletteLength,
                    roundedSize,
                    0
            );
        }
        return roundedSize;
    }

    private int validateSlice(
            byte[] payload,
            int sourceOffset,
            int sourceLength
    ) {
        if (payload == null
                || sourceLength == 0) {
            throw new IllegalArgumentException(
                    "chunk data layer payload is empty"
            );
        }
        if (sourceOffset < 0
                || sourceLength < 0
                || sourceOffset
                > payload.length - sourceLength) {
            throw new IllegalArgumentException(
                    "chunk data layer slice is out of bounds"
            );
        }
        return sourceOffset + sourceLength;
    }

    private int payloadLength(byte[] payload) {
        return payload == null
                ? 0
                : payload.length;
    }

    private void validatePaletteByteLength(
            int length
    ) {
        if (length <= 0
                || length
                > ChunkDecodeWorkspace.MAX_PALETTE_BYTES
                || length % Integer.BYTES != 0) {
            throw new IllegalArgumentException(
                    "chunk palette byte length is invalid: "
                            + length
            );
        }
    }

    private void validateCompressedPaletteLength(
            int length,
            int remaining
    ) {
        if (length <= 0
                || length
                > ChunkDecodeWorkspace.MAX_PALETTE_BYTES) {
            throw new IllegalArgumentException(
                    "compressed chunk palette length is invalid: "
                            + length
            );
        }
        if (length > remaining) {
            throw new IllegalArgumentException(
                    "compressed chunk palette exceeds payload length"
            );
        }
    }

    private int readLittleEndianInt(
            byte[] bytes,
            int offset
    ) {
        if (offset < 0
                || offset
                > bytes.length - Integer.BYTES) {
            throw new IllegalArgumentException(
                    "chunk layer integer is truncated"
            );
        }
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
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
            int length,
            int nextOffset
    ) {
    }
}
