package cartographer.save;

import cartographer.testing.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class SqliteSaveConnectionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void productionConnectionPreservesReadOnlySourceContract() throws Exception {
        Path database = temporaryDirectory.resolve("source.vcdbs");
        createFixture(database);
        byte[] beforeHash = sha256(database);
        long beforeSize = Files.size(database);
        Path wal = sidecar(database, "-wal");
        Path shm = sidecar(database, "-shm");

        assertFalse(Files.exists(wal));
        assertFalse(Files.exists(shm));

        try (Connection connection = new SqliteSaveConnection().openReadOnly(database);
             Statement statement = connection.createStatement()) {
            try (ResultSet queryOnly = statement.executeQuery("PRAGMA query_only")) {
                assertTrue(queryOnly.next());
                assertEquals(1, queryOnly.getInt(1));
            }

            try (ResultSet rows = statement.executeQuery("SELECT value FROM sample")) {
                assertTrue(rows.next());
                assertEquals("authoritative", rows.getString(1));
            }

            assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("INSERT INTO sample(value) VALUES ('mutated')")
            );
            assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("UPDATE sample SET value = 'mutated'")
            );
            assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("CREATE TABLE forbidden(id INTEGER)")
            );
        }

        assertEquals(beforeSize, Files.size(database));
        assertArrayEquals(beforeHash, sha256(database));
        assertFalse(Files.exists(wal));
        assertFalse(Files.exists(shm));
    }

    @Test
    void missingSourceIsRejectedBeforeOpeningJdbc() {
        Path missing = temporaryDirectory.resolve("missing.vcdbs");

        SaveException failure = assertThrows(
                SaveException.class,
                () -> new SqliteSaveConnection().openReadOnly(missing).close()
        );

        assertTrue(failure.getMessage().contains("does not exist"));
    }

    private static void createFixture(Path database) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample(value TEXT NOT NULL)");
            statement.execute("INSERT INTO sample(value) VALUES ('authoritative')");
        }
    }

    private static byte[] sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(Files.readAllBytes(path));
    }

    private static Path sidecar(Path database, String suffix) {
        return Path.of(database.toString() + suffix);
    }
}
