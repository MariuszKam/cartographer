package cartographer.save;

import cartographer.cli.CommandException;
import cartographer.cli.ProgressReporter;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SaveIndexReader {
    private static final List<String> INDEXED_TABLES =
            List.of(
                    "chunk",
                    "mapchunk",
                    "mapregion",
                    "playerdata",
                    "gamedata"
            );

    private final SqliteSaveConnection connectionFactory;

    public SaveIndexReader() {
        this(
                new SqliteSaveConnection()
        );
    }

    public SaveIndexReader(
            SqliteSaveConnection connectionFactory
    ) {
        this.connectionFactory =
                connectionFactory;
    }

    public SaveIndex read(
            Path savePath,
            ProgressReporter progress
    ) {
        progress.start(
                "Opening save read-only"
        );

        try (Connection connection =
                     connectionFactory.openReadOnly(
                             savePath
                     )) {

            progress.done(
                    "Save opened read-only"
            );

            List<TableIndex> tables =
                    new ArrayList<>();

            for (int index = 0;
                 index < INDEXED_TABLES.size();
                 index++) {

                String tableName =
                        INDEXED_TABLES.get(
                                index
                        );

                progress.progress(
                        "Indexing tables",
                        index + 1,
                        INDEXED_TABLES.size()
                );

                if (tableExists(
                        connection,
                        tableName
                )) {
                    tables.add(
                            tableIndex(
                                    connection,
                                    tableName
                            )
                    );
                }
            }

            return new SaveIndex(
                    tables
            );

        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot build save index: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private TableIndex tableIndex(
            Connection connection,
            String tableName
    ) throws SQLException {
        String positionSql =
                hasPositionColumn(
                        connection,
                        tableName
                )
                        ? ", MIN(position), MAX(position)"
                        : ", NULL, NULL";

        String sql =
                "SELECT COUNT(*)"
                        + positionSql
                        + " FROM \""
                        + tableName
                        + "\"";

        try (Statement statement =
                     connection.createStatement();

             ResultSet resultSet =
                     statement.executeQuery(
                             sql
                     )) {

            int rows =
                    resultSet.next()
                            ? resultSet.getInt(1)
                            : 0;

            Long min =
                    resultSet.getObject(2) == null
                            ? null
                            : resultSet.getLong(2);

            Long max =
                    resultSet.getObject(3) == null
                            ? null
                            : resultSet.getLong(3);

            return new TableIndex(
                    tableName,
                    rows,
                    min,
                    max
            );
        }
    }

    private boolean tableExists(
            Connection connection,
            String tableName
    ) throws SQLException {
        try (ResultSet resultSet =
                     connection.getMetaData()
                             .getTables(
                                     null,
                                     null,
                                     tableName,
                                     new String[]{"TABLE"}
                             )) {

            return resultSet.next();
        }
    }

    private boolean hasPositionColumn(
            Connection connection,
            String tableName
    ) throws SQLException {
        try (ResultSet resultSet =
                     connection.getMetaData()
                             .getColumns(
                                     null,
                                     null,
                                     tableName,
                                     "position"
                             )) {

            return resultSet.next();
        }
    }
}