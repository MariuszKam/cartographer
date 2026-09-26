package cartographer.parser;

import com.github.luben.zstd.Zstd;
import cartographer.model.ChunkCoordinate;
import cartographer.model.DecodedChunkLayer;
import cartographer.model.ParsedChunk;
import cartographer.model.ParseResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkParserFailureTest extends ChunkParserTestSupport {

    @Test
    void surfaceCompactKeepsCorruptOptionalLiquidsUnavailable() {
        ByteBuffer corruptLiquid =
                ByteBuffer.allocate(16)
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );
        corruptLiquid.putInt(-8);
        corruptLiquid.putInt(0);
        corruptLiquid.putInt(1);
        corruptLiquid.putInt(12345);

        byte[] source =
                serverChunk(
                        encodedLayer(
                                new int[]{0, 9},
                                index -> index == 0
                                        ? 1
                                        : 0
                        ),
                        corruptLiquid.array(),
                        2
                );

        ParseResult<ParsedChunk> result;
        try (ChunkDecodeWorkspace workspace =
                     new ChunkDecodeWorkspace()) {
            result =
                    new ChunkParser()
                            .parseSurfaceCompact(
                                    new ChunkCoordinate(
                                            0,
                                            0,
                                            0
                                    ),
                                    source,
                                    workspace
                            );
        }

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse(
                                "unknown error"
                        )
        );
        ParsedChunk chunk =
                result.value()
                        .orElseThrow();
        assertEquals(
                9,
                chunk.blockIdAt(
                        0,
                        0,
                        0
                )
        );
        assertFalse(
                chunk.liquidLayerAvailable()
        );
        assertTrue(
                chunk.liquidDecodeError()
                        .contains(
                                "liquidsCompressed"
                        )
        );
        assertThrows(
                IllegalStateException.class,
                () -> chunk.liquidIdAt(
                        0,
                        0,
                        0
                )
        );
    }

    @Test
    void ignoresCorruptOptionalLiquidLayerWhenBlocksDecode() {
        ByteBuffer corruptLiquid =
                ByteBuffer.allocate(
                                16
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        corruptLiquid.putInt(
                -8
        );

        corruptLiquid.putInt(
                0
        );

        corruptLiquid.putInt(
                1
        );

        corruptLiquid.putInt(
                12345
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
                                        encodedLayer(
                                                new int[]{0, 9},
                                                index ->
                                                        index == 0
                                                                ? 1
                                                                : 0
                                        ),
                                        corruptLiquid.array(),
                                        2
                                )
                        );

        assertTrue(
                result.isSuccess(),
                () ->
                        result.error()
                                .orElse(
                                        "unknown error"
                                )
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

        assertFalse(
                result.value()
                        .orElseThrow()
                        .liquidLayerAvailable()
        );

        assertTrue(
                result.value()
                        .orElseThrow()
                        .liquidDecodeError()
                        .contains(
                                "liquidsCompressed"
                        )
        );

        ParsedChunk chunk = result.value().orElseThrow();
        assertThrows(
                IllegalStateException.class,
                () -> chunk.liquidIdAt(0, 0, 0)
        );
        assertThrows(
                IllegalStateException.class,
                () -> cartographer.model.ParsedChunkFixtures.liquidIds(chunk)
        );
    }

    @Test
    void failsWhenCompressedPaletteMarkerExceedsPayload() {
        ByteBuffer corrupt =
                ByteBuffer.allocate(
                                4
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        corrupt.putInt(
                99
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
                        .contains(
                                "compressed chunk palette exceeds payload length"
                        )
        );
    }

    @Test
    void failsWhenCompressedPaletteIsMalformed() {
        ByteBuffer corrupt =
                ByteBuffer.allocate(
                                8
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        corrupt.putInt(
                4
        );

        corrupt.putInt(
                12345
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
    }

    @Test
    void failsWhenDecompressedPaletteIsNotIntAligned() {
        byte[] malformedPalette =
                Zstd.compress(
                        new byte[]{
                                1,
                                2,
                                3
                        },
                        -3
                );

        ByteBuffer corrupt =
                ByteBuffer.allocate(
                                Integer.BYTES
                                        + malformedPalette.length
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

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
                        .contains(
                                "not int aligned"
                        )
        );
    }

    @Test
    void failsWhenCompressedBitPlanesAreCorrupt() {
        ByteBuffer corrupt =
                ByteBuffer.allocate(
                                16
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        corrupt.putInt(
                -8
        );

        corrupt.putInt(
                0
        );

        corrupt.putInt(
                1
        );

        corrupt.putInt(
                12345
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
                        .contains(
                                "bit-plane"
                        )
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
                        .contains(
                                "blocksCompressed"
                        )
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
                        .contains(
                                "blocksCompressed: unsupported chunk compression version"
                        )
        );
    }

    @Test
    void failsOnCorruptCompressedData() {
        ByteBuffer corrupt =
                ByteBuffer.allocate(
                                16
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        corrupt.putInt(
                -8
        );

        corrupt.putInt(
                0
        );

        corrupt.putInt(
                1
        );

        corrupt.putInt(
                12345
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
    }

}
