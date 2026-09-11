package cartographer.parser;

import cartographer.model.ParseResult;
import cartographer.model.WorldMetadata;
import cartographer.save.ProtobufWireReader;

import java.util.OptionalLong;

public final class SaveGameParser {

    private static final int MAP_SIZE_X_FIELD = 1;
    private static final int MAP_SIZE_Y_FIELD = 2;
    private static final int MAP_SIZE_Z_FIELD = 3;

    public ParseResult<WorldMetadata> parse(
            byte[] payload
    ) {
        if (payload == null
                || payload.length == 0) {
            return ParseResult.failure(
                    "SaveGame payload is empty"
            );
        }

        try {
            OptionalLong mapSizeX =
                    ProtobufWireReader.readVarIntField(
                            payload,
                            MAP_SIZE_X_FIELD
                    );

            OptionalLong mapSizeY =
                    ProtobufWireReader.readVarIntField(
                            payload,
                            MAP_SIZE_Y_FIELD
                    );

            OptionalLong mapSizeZ =
                    ProtobufWireReader.readVarIntField(
                            payload,
                            MAP_SIZE_Z_FIELD
                    );

            if (mapSizeX.isEmpty()) {
                return ParseResult.failure(
                        "SaveGame does not contain MapSizeX"
                );
            }

            if (mapSizeY.isEmpty()) {
                return ParseResult.failure(
                        "SaveGame does not contain MapSizeY"
                );
            }

            if (mapSizeZ.isEmpty()) {
                return ParseResult.failure(
                        "SaveGame does not contain MapSizeZ"
                );
            }

            int x =
                    Math.toIntExact(
                            mapSizeX.getAsLong()
                    );

            int y =
                    Math.toIntExact(
                            mapSizeY.getAsLong()
                    );

            int z =
                    Math.toIntExact(
                            mapSizeZ.getAsLong()
                    );

            if (x <= 0 || y <= 0 || z <= 0) {
                return ParseResult.failure(
                        "Invalid world size: "
                                + x
                                + "x"
                                + y
                                + "x"
                                + z
                );
            }

            return ParseResult.success(
                    new WorldMetadata(
                            x,
                            y,
                            z
                    )
            );

        } catch (RuntimeException exception) {
            return ParseResult.failure(
                    "Cannot parse SaveGame metadata: "
                            + exception.getMessage()
            );
        }
    }
}