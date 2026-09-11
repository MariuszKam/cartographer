package cartographer.parser;

import com.github.luben.zstd.Zstd;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.ParseResult;
import cartographer.model.ServerChunkPayload;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkParserTest {
    @Test
    void parsesRealServerChunkProtobufFields() {
        byte[] blocks =
                encodedLayer(
                        new int[]{0, 17},
                        index -> index == 0 ? 1 : 0
                );

        byte[] liquids =
                emptyLayer();

        ParseResult<ServerChunkPayload> result =
                new ChunkParser()
                        .parsePayload(
                                serverChunk(
                                        blocks,
                                        liquids,
                                        2
                                )
                        );

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse("unknown error")
        );

        ServerChunkPayload payload =
                result.value()
                        .orElseThrow();

        assertEquals(
                2,
                payload.savedCompressionVersion()
        );

        assertEquals(
                blocks.length,
                payload.blocksCompressed()
                        .length
        );

        assertEquals(
                liquids.length,
                payload.liquidsCompressed()
                        .length
        );
    }

    @Test
    void decodesRawSmallPaletteLayerWithXyzOrientation() {
        byte[] blocks =
                encodedLayer(
                        new int[]{0, 11, 22, 33},
                        index -> {
                            int x =
                                    index & 31;

                            int z =
                                    (index >>> 5) & 31;

                            int y =
                                    (index >>> 10) & 31;

                            if (x == 31 && y == 31 && z == 31) {
                                return 3;
                            }

                            if (x == 3 && y == 2 && z == 1) {
                                return 2;
                            }

                            return 1;
                        }
                );

        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        5,
                                        4,
                                        7
                                ),
                                serverChunk(
                                        blocks,
                                        emptyLayer(),
                                        2
                                )
                        );

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse("unknown error")
        );

        ParsedChunk chunk =
                result.value()
                        .orElseThrow();

        assertEquals(
                128,
                chunk.minY()
        );

        assertEquals(
                11,
                chunk.blockIdAt(
                        0,
                        0,
                        0
                )
        );

        assertEquals(
                22,
                chunk.blockIdAt(
                        3,
                        2,
                        1
                )
        );

        assertEquals(
                33,
                chunk.blockIdAt(
                        31,
                        31,
                        31
                )
        );
    }

    @Test
    void decodesLiquidsWithSameLayerFormat() {
        byte[] liquids =
                encodedLayer(
                        new int[]{0, 200},
                        index -> index == 0 ? 1 : 0
                );

        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        emptyLayer(),
                                        liquids,
                                        2
                                )
                        );

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse("unknown error")
        );

        ParsedChunk chunk =
                result.value()
                        .orElseThrow();

        assertEquals(
                200,
                chunk.liquidIdAt(
                        0,
                        0,
                        0
                )
        );

        assertEquals(
                0,
                chunk.liquidIdAt(
                        1,
                        0,
                        0
                )
        );
    }

    @Test
    void decodesEmptyLayerMarker() {
        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        emptyLayer(),
                                        emptyLayer(),
                                        2
                                )
                        );

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse("unknown error")
        );

        assertEquals(
                0,
                result.value()
                        .orElseThrow()
                        .blockIdAt(
                                7,
                                0,
                                0
                        )
        );
    }

    @Test
    void decodesCompressedPaletteLayer() {
        int[] palette =
                new int[19];

        for (int index = 0; index < palette.length; index++) {
            palette[index] =
                    index * 10;
        }

        byte[] blocks =
                compressedPaletteLayer(
                        palette,
                        index -> {
                            int x =
                                    index & 31;

                            int z =
                                    (index >>> 5) & 31;

                            int y =
                                    (index >>> 10) & 31;

                            if (x == 7 && y == 0 && z == 0) {
                                return 18;
                            }

                            if (x == 3 && y == 4 && z == 5) {
                                return 17;
                            }

                            return 1;
                        }
                );

        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        blocks,
                                        emptyLayer(),
                                        2
                                )
                        );

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse("unknown error")
        );

        assertEquals(
                180,
                result.value()
                        .orElseThrow()
                        .blockIdAt(
                                7,
                                0,
                                0
                        )
        );

        assertEquals(
                170,
                result.value()
                        .orElseThrow()
                        .blockIdAt(
                                3,
                                4,
                                5
                        )
        );
    }

    @Test
    void ignoresCorruptOptionalLiquidLayerWhenBlocksDecode() {
        ByteBuffer corruptLiquid =
                ByteBuffer.allocate(16)
                        .order(ByteOrder.LITTLE_ENDIAN);

        corruptLiquid.putInt(-8);
        corruptLiquid.putInt(0);
        corruptLiquid.putInt(1);
        corruptLiquid.putInt(12345);

        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        encodedLayer(
                                                new int[]{0, 9},
                                                index -> index == 0 ? 1 : 0
                                        ),
                                        corruptLiquid.array(),
                                        2
                                )
                        );

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse("unknown error")
        );

        assertEquals(
                9,
                result.value()
                        .orElseThrow()
                        .blockIdAt(
                                0,
                                0,
                                0
                        )
        );

        assertEquals(
                0,
                result.value()
                        .orElseThrow()
                        .liquidIdAt(
                                0,
                                0,
                                0
                        )
        );

        assertFalse(
                result.value()
                        .orElseThrow()
                        .liquidLayerAvailable()
        );

        assertTrue(
                result.value()
                        .orElseThrow()
                        .liquidDecodeError()
                        .contains("liquidsCompressed")
        );
    }

    @Test
    void failsWhenCompressedPaletteMarkerExceedsPayload() {
        ByteBuffer corrupt =
                ByteBuffer.allocate(4)
                        .order(ByteOrder.LITTLE_ENDIAN);

        corrupt.putInt(99);

        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        corrupt.array(),
                                        emptyLayer(),
                                        2
                                )
                        );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.error()
                        .orElse("")
                        .contains("compressed chunk palette exceeds payload length")
        );
    }

    @Test
    void failsWhenCompressedPaletteIsMalformed() {
        ByteBuffer corrupt =
                ByteBuffer.allocate(8)
                        .order(ByteOrder.LITTLE_ENDIAN);

        corrupt.putInt(4);
        corrupt.putInt(12345);

        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        corrupt.array(),
                                        emptyLayer(),
                                        2
                                )
                        );

        assertFalse(
                result.isSuccess()
        );
    }

    @Test
    void failsWhenDecompressedPaletteIsNotIntAligned() {
        byte[] malformedPalette =
                Zstd.compress(
                        new byte[]{1, 2, 3},
                        -3
                );

        ByteBuffer corrupt =
                ByteBuffer.allocate(
                                Integer.BYTES
                                        + malformedPalette.length
                        )
                        .order(ByteOrder.LITTLE_ENDIAN);

        corrupt.putInt(
                malformedPalette.length
        );
        corrupt.put(
                malformedPalette
        );

        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        corrupt.array(),
                                        emptyLayer(),
                                        2
                                )
                        );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.error()
                        .orElse("")
                        .contains("not int aligned")
        );
    }

    @Test
    void failsWhenCompressedBitPlanesAreCorrupt() {
        ByteBuffer corrupt =
                ByteBuffer.allocate(16)
                        .order(ByteOrder.LITTLE_ENDIAN);

        corrupt.putInt(-8);
        corrupt.putInt(0);
        corrupt.putInt(1);
        corrupt.putInt(12345);

        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        corrupt.array(),
                                        emptyLayer(),
                                        2
                                )
                        );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.error()
                        .orElse("")
                        .contains("bit-plane")
        );
    }

    @Test
    void failsWhenBlocksCompressedIsMissing() {
        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        new byte[0],
                                        emptyLayer(),
                                        2
                                )
                        );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.error()
                        .orElse("")
                        .contains("blocksCompressed")
        );
    }

    @Test
    void failsOnUnsupportedCompressionVersion() {
        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        emptyLayer(),
                                        emptyLayer(),
                                        99
                                )
                        );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.error()
                        .orElse("")
                        .contains("blocksCompressed: unsupported chunk compression version")
        );
    }

    @Test
    void failsOnCorruptCompressedData() {
        ByteBuffer corrupt =
                ByteBuffer.allocate(16)
                        .order(ByteOrder.LITTLE_ENDIAN);

        corrupt.putInt(-8);
        corrupt.putInt(0);
        corrupt.putInt(1);
        corrupt.putInt(12345);

        ParseResult<ParsedChunk> result =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(
                                        0,
                                        0,
                                        0
                                ),
                                serverChunk(
                                        corrupt.array(),
                                        emptyLayer(),
                                        2
                                )
                        );

        assertFalse(
                result.isSuccess()
        );
    }

    private byte[] emptyLayer() {
        return ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0)
                .array();
    }

    private byte[] encodedLayer(
            int[] palette,
            PaletteIndexAt paletteIndexAt
    ) {
        int bitSize =
                bitSize(
                        palette.length
                );

        ByteBuffer bitPlanes =
                ByteBuffer.allocate(
                                bitSize * 1024 * Integer.BYTES
                        )
                        .order(ByteOrder.LITTLE_ENDIAN);

        int[][] dataBits =
                new int[bitSize][1024];

        for (int y = 0; y < 32; y++) {
            for (int z = 0; z < 32; z++) {
                int slice =
                        y * 32
                                + z;

                for (int x = 0; x < 32; x++) {
                    int index =
                            (y << 10)
                                    | (z << 5)
                                    | x;

                    int paletteIndex =
                            paletteIndexAt.paletteIndexAt(
                                    index
                            );

                    for (int bit = 0; bit < bitSize; bit++) {
                        if (((paletteIndex >>> bit) & 1) == 1) {
                            dataBits[bit][slice] |=
                                    1 << x;
                        }
                    }
                }
            }
        }

        for (int bit = 0; bit < bitSize; bit++) {
            for (int slice = 0; slice < 1024; slice++) {
                bitPlanes.putInt(
                        dataBits[bit][slice]
                );
            }
        }

        byte[] compressed =
                Zstd.compress(
                        bitPlanes.array(),
                        -3
                );

        ByteBuffer out =
                ByteBuffer.allocate(
                                Integer.BYTES
                                        + palette.length * Integer.BYTES
                                        + compressed.length
                        )
                        .order(ByteOrder.LITTLE_ENDIAN);

        out.putInt(
                -palette.length
                        * Integer.BYTES
        );

        for (int id : palette) {
            out.putInt(
                    id
            );
        }

        out.put(
                compressed
        );

        return out.array();
    }

    private byte[] compressedPaletteLayer(
            int[] palette,
            PaletteIndexAt paletteIndexAt
    ) {
        int bitSize =
                bitSize(
                        roundedUpPowerOfTwo(
                                palette.length
                        )
                );

        ByteBuffer bitPlanes =
                ByteBuffer.allocate(
                                bitSize * 1024 * Integer.BYTES
                        )
                        .order(ByteOrder.LITTLE_ENDIAN);

        int[][] dataBits =
                dataBits(
                        bitSize,
                        paletteIndexAt
                );

        for (int bit = 0; bit < bitSize; bit++) {
            for (int slice = 0; slice < 1024; slice++) {
                bitPlanes.putInt(
                        dataBits[bit][slice]
                );
            }
        }

        ByteBuffer paletteBytes =
                ByteBuffer.allocate(
                                palette.length * Integer.BYTES
                        )
                        .order(ByteOrder.LITTLE_ENDIAN);

        for (int id : palette) {
            paletteBytes.putInt(
                    id
            );
        }

        byte[] compressedPalette =
                Zstd.compress(
                        paletteBytes.array(),
                        -3
                );

        byte[] compressedBitPlanes =
                Zstd.compress(
                        bitPlanes.array(),
                        -3
                );

        ByteBuffer out =
                ByteBuffer.allocate(
                                Integer.BYTES
                                        + compressedPalette.length
                                        + compressedBitPlanes.length
                        )
                        .order(ByteOrder.LITTLE_ENDIAN);

        out.putInt(
                compressedPalette.length
        );

        out.put(
                compressedPalette
        );

        out.put(
                compressedBitPlanes
        );

        return out.array();
    }

    private int[][] dataBits(
            int bitSize,
            PaletteIndexAt paletteIndexAt
    ) {
        int[][] dataBits =
                new int[bitSize][1024];

        for (int y = 0; y < 32; y++) {
            for (int z = 0; z < 32; z++) {
                int slice =
                        y * 32
                                + z;

                for (int x = 0; x < 32; x++) {
                    int index =
                            (y << 10)
                                    | (z << 5)
                                    | x;

                    int paletteIndex =
                            paletteIndexAt.paletteIndexAt(
                                    index
                            );

                    for (int bit = 0; bit < bitSize; bit++) {
                        if (((paletteIndex >>> bit) & 1) == 1) {
                            dataBits[bit][slice] |=
                                    1 << x;
                        }
                    }
                }
            }
        }

        return dataBits;
    }

    private int bitSize(
            int paletteLength
    ) {
        int bitSize =
                0;

        int value =
                paletteLength - 1;

        while (value > 0) {
            bitSize++;
            value >>>= 1;
        }

        return bitSize;
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

    private byte[] serverChunk(
            byte[] blocks,
            byte[] liquids,
            int compressionVersion
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        if (blocks.length > 0) {
            writeLengthDelimited(
                    out,
                    1,
                    blocks
            );
        }

        writeVarIntField(
                out,
                15,
                compressionVersion
        );

        if (liquids.length > 0) {
            writeLengthDelimited(
                    out,
                    16,
                    liquids
            );
        }

        return out.toByteArray();
    }

    private void writeLengthDelimited(
            ByteArrayOutputStream out,
            int fieldNumber,
            byte[] value
    ) {
        writeVarInt(
                out,
                (fieldNumber << 3) | 2
        );

        writeVarInt(
                out,
                value.length
        );

        out.writeBytes(
                value
        );
    }

    private void writeVarIntField(
            ByteArrayOutputStream out,
            int fieldNumber,
            int value
    ) {
        writeVarInt(
                out,
                fieldNumber << 3
        );

        writeVarInt(
                out,
                value
        );
    }

    private void writeVarInt(
            ByteArrayOutputStream out,
            int value
    ) {
        int remaining =
                value;

        while (remaining >= 0x80) {
            out.write(
                    (remaining & 0x7F)
                            | 0x80
            );

            remaining >>>= 7;
        }

        out.write(
                remaining
        );
    }

    private interface PaletteIndexAt {
        int paletteIndexAt(int index);
    }
}
