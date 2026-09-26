package cartographer.save;

import cartographer.model.WorldMetadata;
import cartographer.testing.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class SaveSessionQuiescenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void unchangedSourceClosesNormally() throws Exception {
        Path database = createDatabase("unchanged.vcdbs");
        SaveSession session = monitoredSession(database);

        assertDoesNotThrow(session::close);
    }

    @Test
    void changedDatabaseTimestampIsRejectedOnClose() throws Exception {
        Path database = createDatabase("changed.vcdbs");
        SaveSession session = monitoredSession(database);
        FileTime changed = FileTime.fromMillis(
                Files.getLastModifiedTime(database).toMillis() + 2_000L
        );
        Files.setLastModifiedTime(database, changed);

        SaveException failure = assertThrows(SaveException.class, session::close);

        assertTrue(failure.getMessage().contains("changed during analysis"));
    }

    @Test
    void newlyCreatedWalSidecarIsRejectedOnClose() throws Exception {
        Path database = createDatabase("wal-change.vcdbs");
        SaveSession session = monitoredSession(database);
        Files.writeString(Path.of(database.toString() + "-wal"), "changed");

        SaveException failure = assertThrows(SaveException.class, session::close);

        assertTrue(failure.getMessage().contains("changed during analysis"));
    }

    private Path createDatabase(String name) throws Exception {
        Path database = temporaryDirectory.resolve(name);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample(value INTEGER)");
        }
        return database;
    }

    private static SaveSession monitoredSession(Path database) {
        SaveSourceStamp stamp = SaveSourceStamp.capture(database);
        return new SaveSession(
                database,
                new SqliteSaveConnection().openReadOnly(database),
                new SaveSnapshot(new WorldMetadata(64, 256, 64), Map.of()),
                stamp
        );
    }
}
