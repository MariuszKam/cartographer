package cartographer.save;

import cartographer.cli.CommandException;
import cartographer.application.ProgressReporter;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.PlayerPositionCandidate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SaveInspector {
    private static final int MAX_SAMPLE_ROWS = 3;
    private static final int MAX_PREVIEW_BYTES = 96;

    private final SqliteSaveConnection connectionFactory;
    private final PlayerDataParser playerDataParser;

    public SaveInspector() {
        this(new SqliteSaveConnection(), new PlayerDataParser());
    }

    public SaveInspector(SqliteSaveConnection connectionFactory, PlayerDataParser playerDataParser) {
        this.connectionFactory = connectionFactory;
        this.playerDataParser = playerDataParser;
    }

    public SaveInspection inspect(Path savePath, ProgressReporter progress) {
        progress.start("Opening save read-only");
        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            progress.done("Save opened read-only");
            List<String> tableNames = tableNames(connection);
            List<TableInfo> tables = new ArrayList<>();
            for (int index = 0; index < tableNames.size(); index++) {
                String tableName = tableNames.get(index);
                progress.progress("Inspecting tables", index + 1, tableNames.size());
                tables.add(new TableInfo(
                        tableName,
                        countRows(connection, tableName),
                        columns(connection, tableName),
                        samples(connection, tableName)));
            }
            return new SaveInspection(tables);
        } catch (SQLException exception) {
            throw new CommandException("Cannot inspect save: " + exception.getMessage(), exception);
        }
    }

    private List<String> tableNames(Connection connection) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                if (!tableName.startsWith("sqlite_")) {
                    tableNames.add(tableName);
                }
            }
        }
        tableNames.sort(Comparator.naturalOrder());
        return tableNames;
    }

    private List<ColumnInfo> columns(Connection connection, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet resultSet = connection.createStatement().executeQuery("PRAGMA table_info(\"" + tableName + "\")")) {
            while (resultSet.next()) {
                columns.add(new ColumnInfo(
                        resultSet.getString("name"),
                        resultSet.getString("type"),
                        resultSet.getInt("notnull") == 0));
            }
        }
        return columns;
    }

    private List<PayloadSample> samples(Connection connection, String tableName) throws SQLException {
        List<PayloadSample> samples = new ArrayList<>();
        String sql = "SELECT * FROM \"" + tableName + "\" LIMIT " + MAX_SAMPLE_ROWS;
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int rowNumber = 0;
            while (resultSet.next()) {
                rowNumber++;
                for (int column = 1; column <= metaData.getColumnCount(); column++) {
                    byte[] bytes = resultSet.getBytes(column);
                    if (bytes == null || bytes.length == 0 || !isLikelyPayload(metaData.getColumnName(column), resultSet.getObject(column))) {
                        continue;
                    }
                    samples.add(new PayloadSample(
                            rowNumber,
                            metaData.getColumnName(column),
                            bytes.length,
                            hexPreview(bytes),
                            textPreview(bytes),
                            playerPositionCandidates(tableName, bytes)));
                }
            }
        }
        return samples;
    }

    private int countRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM \"" + tableName + "\"")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private boolean isLikelyPayload(String columnName, Object value) {
        String name = columnName.toLowerCase(Locale.ROOT);
        return value instanceof byte[]
                || name.contains("data")
                || name.contains("payload")
                || name.contains("value")
                || name.contains("blob");
    }

    private String hexPreview(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        int length = Math.min(bytes.length, MAX_PREVIEW_BYTES);
        for (int index = 0; index < length; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", bytes[index]));
        }
        if (bytes.length > length) {
            builder.append(" ...");
        }
        return builder.toString();
    }

    private String textPreview(byte[] bytes) {
        String text = new String(bytes, 0, Math.min(bytes.length, MAX_PREVIEW_BYTES), StandardCharsets.UTF_8)
                .replaceAll("\\p{Cntrl}", ".");
        return bytes.length > MAX_PREVIEW_BYTES ? text + " ..." : text;
    }

    private List<PlayerPositionCandidate> playerPositionCandidates(String tableName, byte[] bytes) {
        if (!"playerdata".equalsIgnoreCase(tableName)) {
            return List.of();
        }
        return playerDataParser.findCandidates(bytes, 12);
    }
}
