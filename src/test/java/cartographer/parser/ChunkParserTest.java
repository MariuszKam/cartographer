package cartographer.parser;

import com.github.luben.zstd.Zstd;
import cartographer.model.ChunkCoordinate;
import cartographer.model.DecodedChunkLayer;
import cartographer.model.ParsedChunk;
import cartographer.model.ParseResult;
import cartographer.model.ServerChunkPayload;
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

class ChunkParserTest {

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
        assertArrayEquals(legacy.liquidIds(), explicit.liquidIds());
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
    void publicParsedPayloadRemainsDefensive() {
        byte[] blocks = encodedLayer(
                new int[]{0, 11},
                index -> index == 0 ? 1 : 0
        );
        ServerChunkPayload payload = new ChunkParser()
                .parsePayload(serverChunk(blocks, emptyLayer(), 2))
                .value()
                .orElseThrow();

        byte[] exposed = payload.blocksCompressed();
        exposed[0] ^= 0x7f;

        assertArrayEquals(blocks, payload.blocksCompressed());
    }

    @Test
    void publicDecoderStillReturnsIndependentArrays() {
        byte[] payload = encodedLayer(
                new int[]{0, 11},
                index -> index == 0 ? 1 : 0
        );
        ChunkDataLayerDecoder decoder = new ChunkDataLayerDecoder();

        int[] first = decoder.decode(payload, 2);
        first[0] = 99;
        int[] second = decoder.decode(payload, 2);

        assertEquals(11, second[0]);
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
                full.liquidIds(),
                compact.liquidIds()
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
                chunk::liquidIds
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
                new ServerChunkPayload(minimum, emptyLayer(), 2),
                ChunkDecodeProfile.BLOCKS_ONLY
        ).isSuccess());

        byte[] oversizedRaw = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(-1_048_580)
                .array();
        assertFalse(parser.parse(
                coordinate,
                new ServerChunkPayload(oversizedRaw, emptyLayer(), 2),
                ChunkDecodeProfile.BLOCKS_ONLY
        ).isSuccess());

        byte[] oversizedCompressed = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1_048_577)
                .array();
        assertFalse(parser.parse(
                coordinate,
                new ServerChunkPayload(oversizedCompressed, emptyLayer(), 2),
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
                new ServerChunkPayload(payload, emptyLayer(), 2),
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
    void alreadyParsedServerChunkDoesNotParseProtobufAgain() {
        byte[] blocks =
                encodedLayer(
                        new int[]{0, 11},
                        index -> index == 0 ? 1 : 0
                );
        ChunkParser parser = new ChunkParser() {
            @Override
            public ParseResult<ServerChunkPayload> parsePayload(
                    byte[] payload
            ) {
                throw new AssertionError("parsePayload must not be called");
            }
        };

        ParseResult<ParsedChunk> result =
                parser.parse(
                        new ChunkCoordinate(0, 0, 0),
                        new ServerChunkPayload(blocks, emptyLayer(), 2),
                        ChunkDecodeProfile.BLOCKS_ONLY
                );

        assertTrue(result.isSuccess());
    }

    private static final class SelectiveRecordingLayerDecoder
            extends ChunkDataLayerDecoder {
        private int paletteContainsCalls;
        private int ownedDecodeCalls;
        private byte[] probedPayload;
        private byte[] decodedPayload;
        private int probedOffset;
        private int decodedOffset;
        private int probedLength;
        private int decodedLength;

        @Override
        boolean paletteContainsAny(
                byte[] payload,
                int sourceOffset,
                int sourceLength,
                int savedCompressionVersion,
                int[] wantedBlockIds,
                ChunkDecodeWorkspace workspace
        ) {
            paletteContainsCalls++;
            probedPayload = payload;
            probedOffset = sourceOffset;
            probedLength = sourceLength;
            return super.paletteContainsAny(
                    payload,
                    sourceOffset,
                    sourceLength,
                    savedCompressionVersion,
                    wantedBlockIds,
                    workspace
            );
        }

        @Override
        DecodedChunkLayer decodeOwned(
                byte[] payload,
                int sourceOffset,
                int sourceLength,
                int savedCompressionVersion,
                ChunkDecodeWorkspace workspace
        ) {
            ownedDecodeCalls++;
            decodedPayload = payload;
            decodedOffset = sourceOffset;
            decodedLength = sourceLength;
            return super.decodeOwned(
                    payload,
                    sourceOffset,
                    sourceLength,
                    savedCompressionVersion,
                    workspace
            );
        }
    }

    private static final class RecordingLayerDecoder
            extends ChunkDataLayerDecoder {
        private int ownedDecodeCalls;

        @Override
        public int[] decode(
                byte[] payload,
                int savedCompressionVersion
        ) {
            throw new AssertionError(
                    "ChunkParser must use decodeOwned"
            );
        }

        @Override
        DecodedChunkLayer decodeOwned(
                byte[] payload,
                int savedCompressionVersion
        ) {
            ownedDecodeCalls++;
            return super.decodeOwned(
                    payload,
                    savedCompressionVersion
            );
        }

        @Override
        DecodedChunkLayer decodeOwned(
                byte[] payload,
                int sourceOffset,
                int sourceLength,
                int savedCompressionVersion,
                ChunkDecodeWorkspace workspace
        ) {
            ownedDecodeCalls++;
            return super.decodeOwned(
                    payload,
                    sourceOffset,
                    sourceLength,
                    savedCompressionVersion,
                    workspace
            );
        }
    }

    @Test
    void parsesRealServerChunkProtobufFields() {
        byte[] blocks =
                encodedLayer(
                        new int[]{0, 17},
                        index ->
                                index == 0
                                        ? 1
                                        : 0
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
                () ->
                        result.error()
                                .orElse(
                                        "unknown error"
                                )
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
        assertEquals(ChunkDataLayerDecoder.VALUE_COUNT, chunk.liquidIds().length);
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
                chunk::liquidIds
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

    private byte[] emptyLayer() {
        return ByteBuffer.allocate(
                        4
                )
                .order(
                        ByteOrder.LITTLE_ENDIAN
                )
                .putInt(
                        0
                )
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
                                bitSize
                                        * 1024
                                        * Integer.BYTES
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        int[][] dataBits =
                new int[bitSize][1024];

        for (int y = 0;
             y < 32;
             y++) {

            for (int z = 0;
                 z < 32;
                 z++) {

                int slice =
                        y * 32
                                + z;

                for (int x = 0;
                     x < 32;
                     x++) {

                    int index =
                            (y << 10)
                                    | (z << 5)
                                    | x;

                    int paletteIndex =
                            paletteIndexAt.paletteIndexAt(
                                    index
                            );

                    for (int bit = 0;
                         bit < bitSize;
                         bit++) {

                        if (((paletteIndex >>> bit)
                                & 1) == 1) {

                            dataBits[bit][slice] |=
                                    1 << x;
                        }
                    }
                }
            }
        }

        for (int bit = 0;
             bit < bitSize;
             bit++) {

            for (int slice = 0;
                 slice < 1024;
                 slice++) {

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
                                        + palette.length
                                        * Integer.BYTES
                                        + compressed.length
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

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
                                bitSize
                                        * 1024
                                        * Integer.BYTES
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        int[][] dataBits =
                dataBits(
                        bitSize,
                        paletteIndexAt
                );

        for (int bit = 0;
             bit < bitSize;
             bit++) {

            for (int slice = 0;
                 slice < 1024;
                 slice++) {

                bitPlanes.putInt(
                        dataBits[bit][slice]
                );
            }
        }

        ByteBuffer paletteBytes =
                ByteBuffer.allocate(
                                palette.length
                                        * Integer.BYTES
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

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
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

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

        for (int y = 0;
             y < 32;
             y++) {

            for (int z = 0;
                 z < 32;
                 z++) {

                int slice =
                        y * 32
                                + z;

                for (int x = 0;
                     x < 32;
                     x++) {

                    int index =
                            (y << 10)
                                    | (z << 5)
                                    | x;

                    int paletteIndex =
                            paletteIndexAt.paletteIndexAt(
                                    index
                            );

                    for (int bit = 0;
                         bit < bitSize;
                         bit++) {

                        if (((paletteIndex >>> bit)
                                & 1) == 1) {

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
            value >>>=
                    1;
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
            rounded <<=
                    1;
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

        writeCompressionVersionField(
                out,
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
                (fieldNumber << 3)
                        | 2
        );

        writeVarInt(
                out,
                value.length
        );

        out.writeBytes(
                value
        );
    }

    private void writeCompressionVersionField(
            ByteArrayOutputStream out,
            int value
    ) {
        writeVarInt(
                out,
                15 << 3
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

            remaining >>>=
                    7;
        }

        out.write(
                remaining
        );
    }

    private interface PaletteIndexAt {
        int paletteIndexAt(
                int index
        );
    }
}
