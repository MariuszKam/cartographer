package cartographer.parser;

import cartographer.model.ChunkCoordinate;
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
        this(
                new ChunkDataLayerDecoder()
        );
    }

    public ChunkParser(
            ChunkDataLayerDecoder layerDecoder
    ) {
        this.layerDecoder =
                layerDecoder;
    }

    public ParseResult<ParsedChunk> parse(ChunkCoordinate coordinate, byte[] payload) {
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
        Objects.requireNonNull(
                profile,
                "profile is required"
        );

        ParseResult<ServerChunkPayload> parsedPayload =
                parsePayload(
                        payload
                );

        if (!parsedPayload.isSuccess()) {
            return ParseResult.failure(
                    parsedPayload.error()
                            .orElse("unable to parse ServerChunk")
            );
        }

        return parse(
                coordinate,
                parsedPayload.value().orElseThrow(),
                profile
        );
    }

    public ParseResult<ParsedChunk> parse(
            ChunkCoordinate coordinate,
            ServerChunkPayload serverChunk,
            ChunkDecodeProfile profile
    ) {
        Objects.requireNonNull(
                serverChunk,
                "serverChunk is required"
        );
        Objects.requireNonNull(
                profile,
                "profile is required"
        );

        try {
            int[] blockIds =
                    layerDecoder.decode(
                            serverChunk.blocksCompressed(),
                            serverChunk.savedCompressionVersion()
                    );

            DecodedLiquids liquids =
                    profile == ChunkDecodeProfile.BLOCKS_ONLY
                            ? DecodedLiquids.unavailable(
                            "liquid layer not decoded"
                    )
                            : decodeLiquidsOrEmpty(
                            serverChunk
                    );

            return ParseResult.success(
                    new ParsedChunk(
                            coordinate,
                            coordinate.y()
                                    * ChunkDataLayerDecoder.SIZE,
                            ChunkDataLayerDecoder.SIZE,
                            ChunkDataLayerDecoder.SIZE,
                            ChunkDataLayerDecoder.SIZE,
                            blockIds,
                            liquids.ids(),
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

    public ParseResult<ChunkPaletteProbe> probeBlockPalette(
            ServerChunkPayload serverChunk
    ) {
        Objects.requireNonNull(
                serverChunk,
                "serverChunk is required"
        );

        try {
            return ParseResult.success(
                    layerDecoder.probePalette(
                            serverChunk.blocksCompressed(),
                            serverChunk.savedCompressionVersion()
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
            ServerChunkPayload serverChunk
    ) {
        if (serverChunk.liquidsCompressed().length == 0) {
            return DecodedLiquids.available(
                    new int[ChunkDataLayerDecoder.VALUE_COUNT]
            );
        }

        try {
            return DecodedLiquids.available(
                    layerDecoder.decode(
                            serverChunk.liquidsCompressed(),
                            serverChunk.savedCompressionVersion()
                    )
            );

        } catch (IllegalArgumentException exception) {
            return DecodedLiquids.unavailable(
                    "liquidsCompressed: "
                            + exception.getMessage()
            );
        }
    }

    public ParseResult<ServerChunkPayload> parsePayload(
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
                            .orElse(
                                    new byte[0]
                            );

            return ParseResult.success(
                    new ServerChunkPayload(
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

    private record DecodedLiquids(
            int[] ids,
            boolean available,
            String error
    ) {
        static DecodedLiquids available(
                int[] ids
        ) {
            return new DecodedLiquids(
                    ids,
                    true,
                    ""
            );
        }

        static DecodedLiquids unavailable(
                String error
        ) {
            return new DecodedLiquids(
                    new int[ChunkDataLayerDecoder.VALUE_COUNT],
                    false,
                    error
            );
        }
    }
}
