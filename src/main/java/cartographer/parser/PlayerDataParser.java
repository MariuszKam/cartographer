package cartographer.parser;

import cartographer.model.ParseResult;
import cartographer.model.WorldPosition;
import cartographer.binary.ProtobufWireReader;

import java.util.Optional;

public final class PlayerDataParser {

    private static final int ENTITY_PLAYER_FIELD = 3;

    private final EntityPlayerParser entityPlayerParser;

    public PlayerDataParser() {
        this(new EntityPlayerParser());
    }

    public PlayerDataParser(
            EntityPlayerParser entityPlayerParser
    ) {
        this.entityPlayerParser = entityPlayerParser;
    }

    public ParseResult<WorldPosition> parse(
            byte[] playerDataPayload
    ) {
        if (playerDataPayload == null
                || playerDataPayload.length == 0) {
            return ParseResult.failure(
                    "playerdata payload is empty"
            );
        }

        try {
            Optional<byte[]> entityPlayerSerialized =
                    ProtobufWireReader.readLengthDelimitedField(
                            playerDataPayload,
                            ENTITY_PLAYER_FIELD
                    );

            if (entityPlayerSerialized.isEmpty()) {
                return ParseResult.failure(
                        "ServerWorldPlayerData does not contain "
                                + "EntityPlayerSerialized protobuf field 3"
                );
            }

            return entityPlayerParser.parse(
                    entityPlayerSerialized.get()
            );

        } catch (RuntimeException exception) {
            return ParseResult.failure(
                    "Cannot decode ServerWorldPlayerData: "
                            + exception.getMessage()
            );
        }
    }
}