package cartographer.parser;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerChunkPayload;
import cartographer.save.ProtobufWireReader;

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

        ServerChunkPayload serverChunk =
                parsedPayload.value()
                        .orElseThrow();

        try {
            int[] blockIds =
                    layerDecoder.decode(
                            serverChunk.blocksCompressed(),
                            serverChunk.savedCompressionVersion()
                    );

            int[] liquidIds =
                    serverChunk.liquidsCompressed().length == 0
                            ? new int[ChunkDataLayerDecoder.VALUE_COUNT]
                            : layerDecoder.decode(
                            serverChunk.liquidsCompressed(),
                            serverChunk.savedCompressionVersion()
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
                            liquidIds,
                            serverChunk.savedCompressionVersion()
                    )
            );

        } catch (IllegalArgumentException exception) {
            return ParseResult.failure(
                    "invalid ServerChunk block storage: "
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
}
