package cartographer.save;

import cartographer.model.ParseResult;
import cartographer.model.WorldMetadata;
import cartographer.parser.SaveGameParser;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class WorldMetadataReader {

    private final SaveGameParser parser;

    public WorldMetadataReader() {
        this.parser = new SaveGameParser();
    }

    /** Reads metadata from an already-open session-owned read-only connection. */
    protected WorldMetadata read(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     """
                     SELECT data
                     FROM gamedata
                     WHERE savegameid = 1
                     LIMIT 1
                     """
             )) {

            if (!resultSet.next()) {
                throw new IllegalStateException(
                        "Save contains no gamedata row"
                );
            }

            byte[] payload =
                    resultSet.getBytes(
                            "data"
                    );

            ParseResult<WorldMetadata> result =
                    parser.parse(
                            payload
                    );

            WorldMetadata metadata =
                    result.value()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    result.error()
                                                            .orElse(
                                                                    "Unable to read world metadata"
                                                            )
                                            )
                            );


            return metadata;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot read gamedata: "
                            + exception.getMessage(),
                    exception
            );
        }
    }
}
