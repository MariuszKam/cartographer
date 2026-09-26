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

class ChunkParserBehaviorTest extends ChunkParserTestSupport {

    @Test
    void existingParseStillDecodesLiquids() {
        byte[] blocks =
                encodedLayer(
                        new int[]{0, 11},
                        index -> index == 0 ? 1 : 0
                );
        byte[] liquids =
                encodedLayer(
                        new int[]{0, 200},
                        index -> index == 0 ? 1 : 0
                );

        RecordingLayerDecoder decoder = new RecordingLayerDecoder();
        ParseResult<ParsedChunk> result =
                new ChunkParser(decoder)
                        .parse(
                                new ChunkCoordinate(0, 0, 0),
                                serverChunk(blocks, liquids, 2)
                        );

        assertTrue(result.isSuccess());
        assertEquals(200, result.value().orElseThrow().liquidIdAt(0, 0, 0));
        assertTrue(result.value().orElseThrow().liquidLayerAvailable());
        assertEquals(2, decoder.ownedDecodeCalls);
    }

    @Test
    void blocksAndLiquidsProfileMatchesLegacyParse() {
        byte[] blocks =
                encodedLayer(
                        new int[]{0, 11},
                        index -> index == 0 ? 1 : 0
                );
        byte[] liquids =
                encodedLayer(
                        new int[]{0, 200},
                        index -> index == 0 ? 1 : 0
                );
        ChunkCoordinate coordinate = new ChunkCoordinate(0, 0, 0);
        ChunkParser parser = new ChunkParser();

        ParsedChunk legacy =
                parser.parse(coordinate, serverChunk(blocks, liquids, 2))
                        .value()
                        .orElseThrow();
        ParsedChunk explicit =
                parser.parse(
                                coordinate,
                                serverChunk(blocks, liquids, 2),
                                ChunkDecodeProfile.BLOCKS_AND_LIQUIDS
                        )
                        .value()
                        .orElseThrow();

        assertEquals(legacy.coordinate(), explicit.coordinate());
        assertEquals(legacy.minY(), explicit.minY());
        assertArrayEquals(legacy.blockIds(), explicit.blockIds());
        assertArrayEquals(cartographer.model.ParsedChunkFixtures.liquidIds(legacy), cartographer.model.ParsedChunkFixtures.liquidIds(explicit));
        assertEquals(legacy.liquidLayerAvailable(), explicit.liquidLayerAvailable());
    }

    @Test
    void blocksOnlyDoesNotDecodeLiquids() {
        byte[] blocks =
                encodedLayer(
                        new int[]{0, 11},
                        index -> index == 0 ? 1 : 0
                );
        byte[] liquids =
                encodedLayer(
                        new int[]{0, 200},
                        index -> index == 0 ? 1 : 0
                );
        RecordingLayerDecoder decoder = new RecordingLayerDecoder();

        ParseResult<ParsedChunk> result =
                new ChunkParser(decoder)
                        .parse(
                                new ChunkCoordinate(0, 0, 0),
                                serverChunk(blocks, liquids, 2),
                                ChunkDecodeProfile.BLOCKS_ONLY
                        );

        assertTrue(result.isSuccess());
        assertEquals(1, decoder.ownedDecodeCalls);
    }

    @Test
    void selectiveProbeAndDecodeReuseSameOwnedCompressedBuffer() {
        byte[] blocks = encodedLayer(
                new int[]{0, 11},
                index -> index == 0 ? 1 : 0
        );
        SelectiveRecordingLayerDecoder decoder =
                new SelectiveRecordingLayerDecoder();
        ChunkParser parser = new ChunkParser(decoder);

        SelectiveChunkParseResult result =
                parser.parseBlocksIfPaletteContains(
                        new ChunkCoordinate(0, 0, 0),
                        serverChunk(blocks, emptyLayer(), 2),
                        new int[]{11},
                        new ChunkDecodeWorkspace()
                );

        assertTrue(result.chunk().isPresent());
        assertEquals(1, decoder.paletteContainsCalls);
        assertEquals(1, decoder.ownedDecodeCalls);
        assertSame(decoder.probedPayload, decoder.decodedPayload);
        assertEquals(decoder.probedOffset, decoder.decodedOffset);
        assertEquals(decoder.probedLength, decoder.decodedLength);
    }

    @Test
    void selectivePaletteRejectDoesNotDecodeFullBlockLayer() {
        byte[] blocks = encodedLayer(
                new int[]{0, 11},
                index -> index == 0 ? 1 : 0
        );
        SelectiveRecordingLayerDecoder decoder =
                new SelectiveRecordingLayerDecoder();
        ChunkParser parser = new ChunkParser(decoder);

        SelectiveChunkParseResult result =
                parser.parseBlocksIfPaletteContains(
                        new ChunkCoordinate(0, 0, 0),
                        serverChunk(blocks, emptyLayer(), 2),
                        new int[]{999},
                        new ChunkDecodeWorkspace()
                );

        assertTrue(result.paletteRejected());
        assertTrue(result.payloadParsed());
        assertTrue(result.chunk().isEmpty());
        assertTrue(result.error().isEmpty());
        assertEquals(1, decoder.paletteContainsCalls);
        assertEquals(0, decoder.ownedDecodeCalls);
    }

    @Test
    void hotPathDoesNotMutateSourcePayload() {
        byte[] blocks = encodedLayer(
                new int[]{0, 11},
                index -> index == 0 ? 1 : 0
        );
        byte[] source = serverChunk(blocks, emptyLayer(), 2);
        byte[] original = source.clone();

        ParseResult<ParsedChunk> result = new ChunkParser().parse(
                new ChunkCoordinate(0, 0, 0),
                source
        );

        assertTrue(result.isSuccess());
        assertArrayEquals(original, source);
    }

    @Test
    void surfaceCompactParseMatchesMaterializedVoxelSemantics() {
        byte[] blocks =
                encodedLayer(
                        new int[]{0, 11, 17},
                        index -> switch (index % 3) {
                            case 0 -> 0;
                            case 1 -> 1;
                            default -> 2;
                        }
                );
        byte[] liquids =
                encodedLayer(
                        new int[]{0, 200},
                        index -> index % 7 == 0 ? 1 : 0
                );
        byte[] source =
                serverChunk(
                        blocks,
                        liquids,
                        2
                );
        ChunkCoordinate coordinate =
                new ChunkCoordinate(3, 2, 4);
        ChunkParser parser =
                new ChunkParser();

        ParsedChunk full =
                parser.parse(
                                coordinate,
                                source,
                                ChunkDecodeProfile.BLOCKS_AND_LIQUIDS
                        )
                        .value()
                        .orElseThrow();

        ParsedChunk compact;
        try (ChunkDecodeWorkspace workspace =
                     new ChunkDecodeWorkspace()) {
            compact =
                    parser.parseSurfaceCompact(
                                    coordinate,
                                    source,
                                    workspace
                            )
                            .value()
                            .orElseThrow();
        }

        assertArrayEquals(
                full.blockIds(),
                compact.blockIds()
        );
        assertArrayEquals(
                cartographer.model.ParsedChunkFixtures.liquidIds(full),
                cartographer.model.ParsedChunkFixtures.liquidIds(compact)
        );
        assertEquals(
                full.liquidLayerAvailable(),
                compact.liquidLayerAvailable()
        );
        assertEquals(
                full.blockIdAt(5, 7, 9),
                compact.blockIdAt(5, 7, 9)
        );
        assertEquals(
                full.liquidIdAt(5, 7, 9),
                compact.liquidIdAt(5, 7, 9)
        );
    }

    @Test
    void blocksOnlyMarksLiquidsUnavailableAndKeepsSolidBlocks() {
        byte[] blocks =
                encodedLayer(
                        new int[]{0, 11},
                        index -> index == 0 ? 1 : 0
                );

        ParsedChunk chunk =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(0, 0, 0),
                                serverChunk(blocks, emptyLayer(), 2),
                                ChunkDecodeProfile.BLOCKS_ONLY
                        )
                        .value()
                        .orElseThrow();

        assertEquals(11, chunk.blockIdAt(0, 0, 0));
        assertFalse(chunk.liquidLayerAvailable());
        assertEquals("liquid layer not decoded", chunk.liquidDecodeError());
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
    void reusingWorkspaceCannotMutatePublishedLayers() {
        ChunkDecodeWorkspace workspace = new ChunkDecodeWorkspace();
        try {
            ParsedChunk first = new ChunkParser().parse(
                    new ChunkCoordinate(0, 0, 0),
                    serverChunk(
                            encodedLayer(new int[]{0, 11}, index -> index == 0 ? 1 : 0),
                            encodedLayer(new int[]{0, 21}, index -> index == 0 ? 1 : 0),
                            2
                    ),
                    ChunkDecodeProfile.BLOCKS_AND_LIQUIDS,
                    workspace
            ).value().orElseThrow();

            ParsedChunk second = new ChunkParser().parse(
                    new ChunkCoordinate(1, 0, 0),
                    serverChunk(
                            encodedLayer(new int[]{0, 31}, index -> index == 0 ? 1 : 0),
                            encodedLayer(new int[]{0, 41}, index -> index == 0 ? 1 : 0),
                            2
                    ),
                    ChunkDecodeProfile.BLOCKS_AND_LIQUIDS,
                    workspace
            ).value().orElseThrow();

            assertEquals(11, first.blockIdAt(0, 0, 0));
            assertEquals(21, first.liquidIdAt(0, 0, 0));
            assertEquals(31, second.blockIdAt(0, 0, 0));
            assertEquals(41, second.liquidIdAt(0, 0, 0));
        } finally {
            workspace.close();
        }
    }

    @Test
    void closedWorkspaceRejectsDecodeAndCloseIsIdempotent() {
        ChunkDecodeWorkspace workspace = new ChunkDecodeWorkspace();
        workspace.close();
        workspace.close();

        assertThrows(
                IllegalStateException.class,
                () -> new ChunkParser().parse(
                        new ChunkCoordinate(0, 0, 0),
                        serverChunk(
                                encodedLayer(new int[]{0, 11}, index -> index == 0 ? 1 : 0),
                                emptyLayer(),
                                2
                        ),
                        ChunkDecodeProfile.BLOCKS_ONLY,
                        workspace
                )
        );
    }

    @Test
    void rejectsInvalidAndOversizedPaletteMarkers() {
        ChunkParser parser = new ChunkParser();
        ChunkCoordinate coordinate = new ChunkCoordinate(0, 0, 0);

        byte[] minimum = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(Integer.MIN_VALUE)
                .array();
        assertFalse(parser.parse(
                coordinate,
                serverChunk(minimum, emptyLayer(), 2),
                ChunkDecodeProfile.BLOCKS_ONLY
        ).isSuccess());

        byte[] oversizedRaw = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(-1_048_580)
                .array();
        assertFalse(parser.parse(
                coordinate,
                serverChunk(oversizedRaw, emptyLayer(), 2),
                ChunkDecodeProfile.BLOCKS_ONLY
        ).isSuccess());

        byte[] oversizedCompressed = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1_048_577)
                .array();
        assertFalse(parser.parse(
                coordinate,
                serverChunk(oversizedCompressed, emptyLayer(), 2),
                ChunkDecodeProfile.BLOCKS_ONLY
        ).isSuccess());
    }

    @Test
    void rejectsCompressedPaletteWithOversizedDecodedFrame() {
        byte[] paletteBytes = new byte[ChunkDecodeWorkspace.MAX_PALETTE_BYTES + Integer.BYTES];
        byte[] compressed = Zstd.compress(paletteBytes);
        byte[] payload = ByteBuffer.allocate(Integer.BYTES + compressed.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(compressed.length)
                .put(compressed)
                .array();

        ParseResult<ParsedChunk> result = new ChunkParser().parse(
                new ChunkCoordinate(0, 0, 0),
                serverChunk(payload, emptyLayer(), 2),
                ChunkDecodeProfile.BLOCKS_ONLY
        );

        assertFalse(result.isSuccess());
        assertTrue(result.error().orElseThrow().contains("invalid decompressed size"));
    }

    @Test
    void nullProfileRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new ChunkParser().parse(
                        new ChunkCoordinate(0, 0, 0),
                        new byte[0],
                        null
                )
        );
    }

    @Test
    void callerOwnedWorkspaceCanBeReusedAcrossHotPathParses() {
        byte[] blocks = encodedLayer(
                new int[]{0, 11},
                index -> index == 0 ? 1 : 0
        );
        byte[] source = serverChunk(blocks, emptyLayer(), 2);
        ChunkParser parser = new ChunkParser();

        try (ChunkDecodeWorkspace workspace = new ChunkDecodeWorkspace()) {
            ParseResult<ParsedChunk> first = parser.parse(
                    new ChunkCoordinate(0, 0, 0),
                    source,
                    ChunkDecodeProfile.BLOCKS_ONLY,
                    workspace
            );
            ParseResult<ParsedChunk> second = parser.parse(
                    new ChunkCoordinate(0, 0, 0),
                    source,
                    ChunkDecodeProfile.BLOCKS_ONLY,
                    workspace
            );

            assertTrue(first.isSuccess());
            assertTrue(second.isSuccess());
            assertEquals(11, first.value().orElseThrow().blockIdAt(0, 0, 0));
            assertEquals(11, second.value().orElseThrow().blockIdAt(0, 0, 0));
        }
    }

    @Test
    void parsesRealServerChunkProtobufFieldsThroughHotPath() {
        byte[] blocks = encodedLayer(
                new int[]{0, 17},
                index -> index == 0 ? 1 : 0
        );
        byte[] liquids = emptyLayer();

        ParseResult<ParsedChunk> result = new ChunkParser().parse(
                new ChunkCoordinate(0, 0, 0),
                serverChunk(blocks, liquids, 2)
        );

        assertTrue(
                result.isSuccess(),
                () -> result.error().orElse("unknown error")
        );
        ParsedChunk chunk = result.value().orElseThrow();
        assertEquals(2, chunk.savedCompressionVersion());
        assertEquals(17, chunk.blockIdAt(0, 0, 0));
        assertTrue(chunk.liquidLayerAvailable());
        assertEquals(0, chunk.liquidIdAt(0, 0, 0));
    }

    @Test
    void decodesRawSmallPaletteLayerWithXyzOrientation() {
        byte[] blocks =
                encodedLayer(
                        new int[]{
                                0,
                                11,
                                22,
                                33
                        },
                        index -> {
                            int x =
                                    index & 31;

                            int z =
                                    (index >>> 5)
                                            & 31;

                            int y =
                                    (index >>> 10)
                                            & 31;

                            if (x == 31
                                    && y == 31
                                    && z == 31) {

                                return 3;
                            }

                            if (x == 3
                                    && y == 2
                                    && z == 1) {

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
                () ->
                        result.error()
                                .orElse(
                                        "unknown error"
                                )
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
    void decodesMultipleBitPlanesAcrossSlicesAndXPositions() {
        int[] palette =
                new int[]{
                        100,
                        101,
                        102,
                        103,
                        104,
                        105,
                        106,
                        107
                };
        byte[] blocks = encodedLayer(
                palette,
                index -> {
                    int x = index & 31;
                    int slice = index >>> 5;
                    if (slice == 0 && x == 0) {
                        return 1;
                    }
                    if (slice == 0 && x == 16) {
                        return 6;
                    }
                    if (slice == 0 && x == 31) {
                        return 7;
                    }
                    if (slice == 1 && x == 0) {
                        return 3;
                    }
                    if (slice == 2 && x == 31) {
                        return 5;
                    }
                    return 0;
                }
        );

        ParsedChunk chunk =
                new ChunkParser()
                        .parse(
                                new ChunkCoordinate(0, 0, 0),
                                serverChunk(blocks, emptyLayer(), 2),
                                ChunkDecodeProfile.BLOCKS_ONLY
                        )
                        .value()
                        .orElseThrow();

        assertEquals(101, chunk.blockIdAt(0, 0, 0));
        assertEquals(106, chunk.blockIdAt(16, 0, 0));
        assertEquals(107, chunk.blockIdAt(31, 0, 0));
        assertEquals(103, chunk.blockIdAt(0, 0, 1));
        assertEquals(105, chunk.blockIdAt(31, 0, 2));
    }

    @Test
    void decodesLiquidsWithSameLayerFormat() {
        byte[] liquids =
                encodedLayer(
                        new int[]{0, 200},
                        index ->
                                index == 0
                                        ? 1
                                        : 0
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
                () ->
                        result.error()
                                .orElse(
                                        "unknown error"
                                )
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
                () ->
                        result.error()
                                .orElse(
                                        "unknown error"
                                )
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

        ParsedChunk chunk = result.value().orElseThrow();
        assertTrue(chunk.liquidLayerAvailable());
        assertEquals(0, chunk.liquidIdAt(7, 0, 0));
        assertEquals(ChunkDataLayerDecoder.VALUE_COUNT, cartographer.model.ParsedChunkFixtures.liquidIds(chunk).length);
    }

    @Test
    void decodesCompressedPaletteLayer() {
        int[] palette =
                new int[19];

        for (int index = 0;
             index < palette.length;
             index++) {

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
                                    (index >>> 5)
                                            & 31;

                            int y =
                                    (index >>> 10)
                                            & 31;

                            if (x == 7
                                    && y == 0
                                    && z == 0) {

                                return 18;
                            }

                            if (x == 3
                                    && y == 4
                                    && z == 5) {

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
                () ->
                        result.error()
                                .orElse(
                                        "unknown error"
                                )
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

}
