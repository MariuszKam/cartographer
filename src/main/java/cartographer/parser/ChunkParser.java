package cartographer.parser;

import cartographer.model.ChunkCoordinate;
import cartographer.model.DecodedChunkLayer;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.binary.ProtobufWireReader;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public class ChunkParser {
    private static final int BLOCKS_COMPRESSED_FIELD = 1;
    private static final int SAVED_COMPRESSION_VERSION_FIELD = 15;
    private static final int LIQUIDS_COMPRESSED_FIELD = 16;

    private final ChunkDataLayerDecoder layerDecoder;

    public ChunkParser() {
        this(new ChunkDataLayerDecoder());
    }

    public ChunkParser(ChunkDataLayerDecoder layerDecoder) {
        this.layerDecoder = Objects.requireNonNull(
                layerDecoder,
                "layerDecoder is required"
        );
    }

    public ParseResult<ParsedChunk> parse(
            ChunkCoordinate coordinate,
            byte[] payload
    ) {
        return parse(
                coordinate,
                payload,
                ChunkDecodeProfile.BLOCKS_AND_LIQUIDS
        );
    }

    public ParseResult<ParsedChunk> parse(
            ChunkCoordinate coordinate,
            byte[] payload,
            ChunkDecodeProfile profile
    ) {
        Objects.requireNonNull(profile, "profile is required");
        try (ChunkDecodeWorkspace workspace = new ChunkDecodeWorkspace()) {
            return parse(coordinate, payload, profile, workspace);
        }
    }

    /**
     * Hot-path parse from the original ServerChunk protobuf bytes.
     *
     * <p>Length-delimited block/liquid fields are represented as slices of the
     * original source protobuf and passed directly to the layer decoder. The
     * hot path avoids materializing defensive copies of each compressed
     * field.</p>
     */
    public ParseResult<ParsedChunk> parse(
            ChunkCoordinate coordinate,
            byte[] payload,
            ChunkDecodeProfile profile,
            ChunkDecodeWorkspace workspace
    ) {
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(workspace, "workspace is required");

        ParseResult<OwnedServerChunkPayload> parsedPayload =
                parseOwnedPayload(payload);
        if (!parsedPayload.isSuccess()) {
            return ParseResult.failure(
                    parsedPayload.error().orElse(
                            "unable to parse ServerChunk"
                    )
            );
        }
        return parseOwned(
                coordinate,
                parsedPayload.value().orElseThrow(),
                profile,
                workspace
        );
    }

    /**
     * Surface-oriented parse that preserves ParsedChunk semantics while
     * publishing compact palette/bit-plane layers for point lookups.
     */
    public ParseResult<ParsedChunk> parseSurfaceCompact(
            ChunkCoordinate coordinate,
            byte[] payload,
            ChunkDecodeWorkspace workspace
    ) {
        Objects.requireNonNull(
                coordinate,
                "coordinate is required"
        );
        Objects.requireNonNull(
                workspace,
                "workspace is required"
        );

        ParseResult<OwnedServerChunkPayload> parsedPayload =
                parseOwnedPayload(payload);
        if (!parsedPayload.isSuccess()) {
            return ParseResult.failure(
                    parsedPayload.error().orElse(
                            "unable to parse ServerChunk"
                    )
            );
        }

        return parseSurfaceCompactOwned(
                coordinate,
                parsedPayload.value().orElseThrow(),
                workspace
        );
    }

    /**
     * Palette probe directly from source protobuf bytes.
     *
     * Selective hot path: parse the protobuf once, inspect the block palette,
     * and only decode the full block layer when one of the wanted IDs exists.
     *
     * <p>Success with Optional.empty() means an authoritative palette reject,
     * not a parse failure.</p>
     */
    public SelectiveChunkParseResult parseBlocksIfPaletteContains(
            ChunkCoordinate coordinate,
            byte[] payload,
            int[] wantedBlockIds,
            ChunkDecodeWorkspace workspace
    ) {
        Objects.requireNonNull(coordinate, "coordinate is required");
        Objects.requireNonNull(wantedBlockIds, "wantedBlockIds are required");
        Objects.requireNonNull(workspace, "workspace is required");
        if (wantedBlockIds.length == 0) {
            throw new IllegalArgumentException(
                    "wantedBlockIds cannot be empty"
            );
        }

        ParseResult<OwnedServerChunkPayload> parsedPayload =
                parseOwnedPayload(payload);
        if (!parsedPayload.isSuccess()) {
            return SelectiveChunkParseResult.payloadFailure(
                    parsedPayload.error().orElse(
                            "unable to parse ServerChunk"
                    )
            );
        }

        OwnedServerChunkPayload serverChunk =
                parsedPayload.value().orElseThrow();
        final boolean wanted;
        try {
            PayloadSlice blocks =
                    serverChunk.blocksCompressed();
            wanted = layerDecoder.paletteContainsAny(
                    blocks.source(),
                    blocks.offset(),
                    blocks.length(),
                    serverChunk.savedCompressionVersion(),
                    wantedBlockIds,
                    workspace
            );
        } catch (IllegalArgumentException exception) {
            return SelectiveChunkParseResult.decodeFailure(
                    "blocksCompressed palette: "
                            + exception.getMessage()
            );
        }

        if (!wanted) {
            return SelectiveChunkParseResult.rejected();
        }

        ParseResult<ParsedChunk> parsedChunk = parseOwned(
                coordinate,
                serverChunk,
                ChunkDecodeProfile.BLOCKS_ONLY,
                workspace
        );
        if (!parsedChunk.isSuccess()) {
            return SelectiveChunkParseResult.decodeFailure(
                    parsedChunk.error().orElse(
                            "unable to decode block layer"
                    )
            );
        }
        return SelectiveChunkParseResult.decoded(
                parsedChunk.value().orElseThrow()
        );
    }

    private ParseResult<ParsedChunk> parseOwned(
            ChunkCoordinate coordinate,
            OwnedServerChunkPayload serverChunk,
            ChunkDecodeProfile profile,
            ChunkDecodeWorkspace workspace
    ) {
        Objects.requireNonNull(coordinate, "coordinate is required");
        Objects.requireNonNull(serverChunk, "serverChunk is required");
        Objects.requireNonNull(profile, "profile is required");

        try {
            PayloadSlice blocks =
                    serverChunk.blocksCompressed();
            DecodedChunkLayer blockLayer =
                    layerDecoder.decodeOwned(
                            blocks.source(),
                            blocks.offset(),
                            blocks.length(),
                            serverChunk.savedCompressionVersion(),
                            workspace
                    );

            DecodedLiquids liquids =
                    profile == ChunkDecodeProfile.BLOCKS_ONLY
                            ? DecodedLiquids.unavailable(
                                    "liquid layer not decoded"
                            )
                            : decodeLiquidsOrEmpty(
                                    serverChunk,
                                    workspace
                            );

            return ParseResult.success(
                    ParsedChunk.fromDecodedLayers(
                            coordinate,
                            coordinate.y() * ChunkDataLayerDecoder.SIZE,
                            ChunkDataLayerDecoder.SIZE,
                            ChunkDataLayerDecoder.SIZE,
                            ChunkDataLayerDecoder.SIZE,
                            blockLayer,
                            liquids.layer(),
                            serverChunk.savedCompressionVersion(),
                            liquids.available(),
                            liquids.error()
                    )
            );
        } catch (IllegalArgumentException exception) {
            return ParseResult.failure(
                    "blocksCompressed: " + exception.getMessage()
            );
        }
    }

    private ParseResult<ParsedChunk> parseSurfaceCompactOwned(
            ChunkCoordinate coordinate,
            OwnedServerChunkPayload serverChunk,
            ChunkDecodeWorkspace workspace
    ) {
        try {
            PayloadSlice blocks =
                    serverChunk.blocksCompressed();
            DecodedChunkLayer blockLayer =
                    layerDecoder.decodeCompactOwned(
                            blocks.source(),
                            blocks.offset(),
                            blocks.length(),
                            serverChunk.savedCompressionVersion(),
                            workspace
                    );

            DecodedLiquids liquids =
                    decodeCompactLiquidsOrEmpty(
                            serverChunk,
                            workspace
                    );

            return ParseResult.success(
                    ParsedChunk.fromDecodedLayers(
                            coordinate,
                            coordinate.y()
                                    * ChunkDataLayerDecoder.SIZE,
                            ChunkDataLayerDecoder.SIZE,
                            ChunkDataLayerDecoder.SIZE,
                            ChunkDataLayerDecoder.SIZE,
                            blockLayer,
                            liquids.layer(),
                            serverChunk.savedCompressionVersion(),
                            liquids.available(),
                            liquids.error()
                    )
            );
        } catch (IllegalArgumentException exception) {
            return ParseResult.failure(
                    "blocksCompressed: "
                            + exception.getMessage()
            );
        }
    }

    private DecodedLiquids decodeLiquidsOrEmpty(
            OwnedServerChunkPayload serverChunk,
            ChunkDecodeWorkspace workspace
    ) {
        PayloadSlice liquids =
                serverChunk.liquidsCompressed();
        if (liquids.length() == 0) {
            return DecodedLiquids.available(
                    DecodedChunkLayer.empty(
                            ChunkDataLayerDecoder.VALUE_COUNT
                    )
            );
        }

        try {
            return DecodedLiquids.available(
                    layerDecoder.decodeOwned(
                            liquids.source(),
                            liquids.offset(),
                            liquids.length(),
                            serverChunk.savedCompressionVersion(),
                            workspace
                    )
            );
        } catch (IllegalArgumentException exception) {
            return DecodedLiquids.unavailable(
                    "liquidsCompressed: "
                            + exception.getMessage()
            );
        }
    }

    private DecodedLiquids decodeCompactLiquidsOrEmpty(
            OwnedServerChunkPayload serverChunk,
            ChunkDecodeWorkspace workspace
    ) {
        PayloadSlice liquids =
                serverChunk.liquidsCompressed();
        if (liquids.length() == 0) {
            return DecodedLiquids.available(
                    DecodedChunkLayer.empty(
                            ChunkDataLayerDecoder.VALUE_COUNT
                    )
            );
        }

        try {
            return DecodedLiquids.available(
                    layerDecoder.decodeCompactOwned(
                            liquids.source(),
                            liquids.offset(),
                            liquids.length(),
                            serverChunk.savedCompressionVersion(),
                            workspace
                    )
            );
        } catch (IllegalArgumentException exception) {
            return DecodedLiquids.unavailable(
                    "liquidsCompressed: "
                            + exception.getMessage()
            );
        }
    }

    private ParseResult<OwnedServerChunkPayload> parseOwnedPayload(
            byte[] payload
    ) {
        if (payload == null || payload.length == 0) {
            return ParseResult.failure("chunk payload is empty");
        }

        try {
            Optional<ProtobufWireReader.LengthDelimitedFieldRange>
                    blocksCompressed =
                    ProtobufWireReader.findLengthDelimitedFieldRange(
                            payload,
                            BLOCKS_COMPRESSED_FIELD
                    );

            if (blocksCompressed.isEmpty()
                    || blocksCompressed.get().length() == 0) {
                return ParseResult.failure(
                        "ServerChunk has no blocksCompressed field"
                );
            }

            OptionalLong savedCompressionVersion =
                    ProtobufWireReader.readVarIntField(
                            payload,
                            SAVED_COMPRESSION_VERSION_FIELD
                    );

            ProtobufWireReader.LengthDelimitedFieldRange liquidsRange =
                    ProtobufWireReader.findLengthDelimitedFieldRange(
                                    payload,
                                    LIQUIDS_COMPRESSED_FIELD
                            )
                            .orElse(
                                    new ProtobufWireReader.LengthDelimitedFieldRange(
                                            0,
                                            0
                                    )
                            );

            ProtobufWireReader.LengthDelimitedFieldRange blocksRange =
                    blocksCompressed.orElseThrow();

            return ParseResult.success(
                    new OwnedServerChunkPayload(
                            new PayloadSlice(
                                    payload,
                                    blocksRange.offset(),
                                    blocksRange.length()
                            ),
                            new PayloadSlice(
                                    payload,
                                    liquidsRange.offset(),
                                    liquidsRange.length()
                            ),
                            (int) savedCompressionVersion.orElse(0)
                    )
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ParseResult.failure(
                    "invalid ServerChunk protobuf: "
                            + exception.getMessage()
            );
        }
    }

    private record OwnedServerChunkPayload(
            PayloadSlice blocksCompressed,
            PayloadSlice liquidsCompressed,
            int savedCompressionVersion
    ) {
        private OwnedServerChunkPayload {
            Objects.requireNonNull(
                    blocksCompressed,
                    "blocksCompressed is required"
            );
            Objects.requireNonNull(
                    liquidsCompressed,
                    "liquidsCompressed is required"
            );
        }
    }

    private record PayloadSlice(
            byte[] source,
            int offset,
            int length
    ) {
        private PayloadSlice {
            Objects.requireNonNull(
                    source,
                    "slice source is required"
            );
            if (offset < 0
                    || length < 0
                    || offset > source.length - length) {
                throw new IllegalArgumentException(
                        "payload slice is out of bounds"
                );
            }
        }

    }

    private record DecodedLiquids(
            DecodedChunkLayer layer,
            boolean available,
            String error
    ) {
        static DecodedLiquids available(DecodedChunkLayer layer) {
            return new DecodedLiquids(layer, true, "");
        }

        static DecodedLiquids unavailable(String error) {
            return new DecodedLiquids(null, false, error);
        }
    }
}
