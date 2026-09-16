package cartographer.save;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteSaveConnection {

    public Connection openReadOnly(Path savePath) {
        if (!Files.isRegularFile(savePath)) {
            throw new SaveException(
                    "Save file does not exist: " + savePath
            );
        }

        try {
            String uri = savePath
                    .toAbsolutePath()
                    .normalize()
                    .toUri()
                    .toString();

            Connection connection =
                    DriverManager.getConnection(
                            "jdbc:sqlite:" + uri + "?mode=ro&immutable=1"
                    );

            try (Statement statement =
                         connection.createStatement()) {

                statement.execute(
                        "PRAGMA query_only = ON"
                );
            }

            return connection;

        } catch (SQLException exception) {
            throw new SaveException(
                    "Cannot open save read-only: "
                            + savePath
                            + " ("
                            + exception.getMessage()
                            + ")",
                    exception
            );
        }
    }
}
