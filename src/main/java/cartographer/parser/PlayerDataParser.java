package cartographer.parser;

import cartographer.model.ParseResult;
import cartographer.model.WorldPosition;
import cartographer.save.ProtobufWireReader;

import java.util.List;
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

    /*
     * SaveInspector currently calls this method.
     *
     * We keep it for compatibility, but unlike the previous
     * implementation it no longer scans random doubles/floats
     * in the payload.
     */
    public List<PlayerPositionCandidate> findCandidates(
            byte[] payload,
            int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        ParseResult<WorldPosition> result = parse(payload);

        if (!result.isSuccess()) {
            return List.of();
        }

        WorldPosition position =
                result.value().orElseThrow();

        return List.of(
                new PlayerPositionCandidate(
                        -1,
                        "ServerWorldPlayerData.EntityPlayerSerialized",
                        position,
                        Double.POSITIVE_INFINITY
                )
        );
    }
}