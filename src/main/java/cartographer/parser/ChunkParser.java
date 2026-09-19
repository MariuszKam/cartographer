package cartographer.parser;

import cartographer.model.ChunkCoordinate;
import cartographer.model.DecodedChunkLayer;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerChunkPayload;
import cartographer.save.ProtobufWireReader;

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

    public ParseResult<ParsedChunk> parse(
            ChunkCoordinate coordinate,
            ServerChunkPayload serverChunk,
            ChunkDecodeProfile profile
    ) {
        Objects.requireNonNull(serverChunk, "serverChunk is required");
        Objects.requireNonNull(profile, "profile is required");
        try (ChunkDecodeWorkspace workspace = new ChunkDecodeWorkspace()) {
            return parse(coordinate, serverChunk, profile, workspace);
        }
    }

    /**
     * Hot-path parse from the original ServerChunk protobuf bytes.
     *
     * <p>The length-delimited fields copied by ProtobufWireReader are owned by
     * this parse operation and are passed directly to the layer decoder. This
     * avoids constructing a defensive-copy ServerChunkPayload only to clone the
     * same compressed arrays again.</p>
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
     * Compatibility path for already materialized public ServerChunkPayload.
     * Public defensive-copy semantics remain unchanged.
     */
    public ParseResult<ParsedChunk> parse(
            ChunkCoordinate coordinate,
            ServerChunkPayload serverChunk,
            ChunkDecodeProfile profile,
            ChunkDecodeWorkspace workspace
    ) {
        Objects.requireNonNull(serverChunk, "serverChunk is required");
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(workspace, "workspace is required");

        return parseOwned(
                coordinate,
                new OwnedServerChunkPayload(
                        serverChunk.blocksCompressed(),
                        serverChunk.liquidsCompressed(),
                        serverChunk.savedCompressionVersion()
                ),
                profile,
                workspace
        );
    }

    public ParseResult<ChunkPaletteProbe> probeBlockPalette(
            ServerChunkPayload serverChunk
    ) {
        Objects.requireNonNull(serverChunk, "serverChunk is required");
        try (ChunkDecodeWorkspace workspace = new ChunkDecodeWorkspace()) {
            return probeBlockPalette(serverChunk, workspace);
        }
    }

    public ParseResult<ChunkPaletteProbe> probeBlockPalette(
            ServerChunkPayload serverChunk,
            ChunkDecodeWorkspace workspace
    ) {
        Objects.requireNonNull(serverChunk, "serverChunk is required");
        Objects.requireNonNull(workspace, "workspace is required");
        try {
            return ParseResult.success(
                    layerDecoder.probePalette(
                            serverChunk.blocksCompressed(),
                            serverChunk.savedCompressionVersion(),
                            workspace
                    )
            );
        } catch (IllegalArgumentException exception) {
            return ParseResult.failure(
                    "blocksCompressed palette: "
                            + exception.getMessage()
            );
        }
    }

    /**
     * Palette probe directly from source protobuf bytes without materializing
     * the public defensive-copy payload object.
     */
    public ParseResult<ChunkPaletteProbe> probeBlockPalette(
            byte[] payload,
            ChunkDecodeWorkspace workspace
    ) {
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
        return probeBlockPaletteOwned(
                parsedPayload.value().orElseThrow(),
                workspace
        );
    }

    /**
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
            wanted = layerDecoder.paletteContainsAny(
                    serverChunk.blocksCompressed(),
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

    /**
     * Public compatibility parser retains defensive-array ownership.
     */
    public ParseResult<ServerChunkPayload> parsePayload(byte[] payload) {
        ParseResult<OwnedServerChunkPayload> owned =
                parseOwnedPayload(payload);
        if (!owned.isSuccess()) {
            return ParseResult.failure(
                    owned.error().orElse("unable to parse ServerChunk")
            );
        }
        OwnedServerChunkPayload value = owned.value().orElseThrow();
        return ParseResult.success(
                new ServerChunkPayload(
                        value.blocksCompressed(),
                        value.liquidsCompressed(),
                        value.savedCompressionVersion()
                )
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
            DecodedChunkLayer blockLayer =
                    layerDecoder.decodeOwned(
                            serverChunk.blocksCompressed(),
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

    private ParseResult<ChunkPaletteProbe> probeBlockPaletteOwned(
            OwnedServerChunkPayload serverChunk,
            ChunkDecodeWorkspace workspace
    ) {
        try {
            return ParseResult.success(
                    layerDecoder.probePalette(
                            serverChunk.blocksCompressed(),
                            serverChunk.savedCompressionVersion(),
                            workspace
                    )
            );
        } catch (IllegalArgumentException exception) {
            return ParseResult.failure(
                    "blocksCompressed palette: "
                            + exception.getMessage()
            );
        }
    }

    private DecodedLiquids decodeLiquidsOrEmpty(
            OwnedServerChunkPayload serverChunk,
            ChunkDecodeWorkspace workspace
    ) {
        if (serverChunk.liquidsCompressed().length == 0) {
            return DecodedLiquids.available(
                    DecodedChunkLayer.empty(
                            ChunkDataLayerDecoder.VALUE_COUNT
                    )
            );
        }

        try {
            return DecodedLiquids.available(
                    layerDecoder.decodeOwned(
                            serverChunk.liquidsCompressed(),
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
            Optional<byte[]> blocksCompressed =
                    ProtobufWireReader.readLengthDelimitedField(
                            payload,
                            BLOCKS_COMPRESSED_FIELD
                    );

            if (blocksCompressed.isEmpty()
                    || blocksCompressed.get().length == 0) {
                return ParseResult.failure(
                        "ServerChunk has no blocksCompressed field"
                );
            }

            OptionalLong savedCompressionVersion =
                    ProtobufWireReader.readVarIntField(
                            payload,
                            SAVED_COMPRESSION_VERSION_FIELD
                    );

            byte[] liquidsCompressed =
                    ProtobufWireReader.readLengthDelimitedField(
                                    payload,
                                    LIQUIDS_COMPRESSED_FIELD
                            )
                            .orElseGet(() -> new byte[0]);

            return ParseResult.success(
                    new OwnedServerChunkPayload(
                            blocksCompressed.get(),
                            liquidsCompressed,
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
            byte[] blocksCompressed,
            byte[] liquidsCompressed,
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
