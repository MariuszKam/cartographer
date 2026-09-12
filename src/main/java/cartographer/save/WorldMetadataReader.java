package cartographer.save;

import cartographer.cli.CommandException;
import cartographer.cli.ProgressReporter;
import cartographer.model.ParseResult;
import cartographer.model.WorldMetadata;
import cartographer.parser.SaveGameParser;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class WorldMetadataReader {

    private final SqliteSaveConnection connectionFactory;
    private final SaveGameParser parser;

    public WorldMetadataReader() {
        this(
                new SqliteSaveConnection(),
                new SaveGameParser()
        );
    }

    public WorldMetadataReader(
            SqliteSaveConnection connectionFactory,
            SaveGameParser parser
    ) {
        this.connectionFactory =
                connectionFactory;

        this.parser =
                parser;
    }

    public WorldMetadata read(
            Path savePath
    ) {
        return read(
                savePath,
                ProgressReporter.NONE
        );
    }

    public WorldMetadata read(
            Path savePath,
            ProgressReporter progress
    ) {
        progress.start(
                "Reading world metadata"
        );

        try (Connection connection =
                     connectionFactory.openReadOnly(
                             savePath
                     );

             Statement statement =
                     connection.createStatement();

             ResultSet resultSet =
                     statement.executeQuery(
                             """
                             SELECT data
                             FROM gamedata
                             WHERE savegameid = 1
                             LIMIT 1
                             """
                     )) {

            if (!resultSet.next()) {
                throw new CommandException(
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
                                            new CommandException(
                                                    result.error()
                                                            .orElse(
                                                                    "Unable to read world metadata"
                                                            )
                                            )
                            );

            progress.done(
                    "World metadata read"
            );

            return metadata;

        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read gamedata: "
                            + exception.getMessage(),
                    exception
            );
        }
    }
}
