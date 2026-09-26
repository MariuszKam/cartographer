package cartographer.save;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

final class SqliteSaveTableInspector {

    private SqliteSaveTableInspector() {
    }

    static int countRows(
            Connection connection,
            SaveTable table
    ) throws SQLException {
        String sql = "SELECT COUNT(*) FROM \"" + table.tableName() + "\"";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    static boolean tableMissing(
            Connection connection,
            String tableName
    ) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(
                null,
                null,
                tableName,
                new String[]{"TABLE"}
        )) {
            if (resultSet.next()) {
                return false;
            }
        }
        try (ResultSet resultSet = metaData.getTables(
                null,
                null,
                tableName.toUpperCase(Locale.ROOT),
                new String[]{"TABLE"}
        )) {
            return !resultSet.next();
        }
    }
}
