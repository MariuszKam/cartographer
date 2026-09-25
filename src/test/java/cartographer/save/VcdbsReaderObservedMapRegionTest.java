package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.progress.ProgressReporter;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsReaderObservedMapRegionTest {

    @TempDir
    Path root;

    @Test
    void nonMainWorldRowsAreIgnoredWithoutBlockingCompleteness()
            throws Exception {
        Path database = root.resolve("mapregion.vcdbs");
        try (Connection setup = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        ); Statement statement = setup.createStatement()) {
            statement.execute(
                    "CREATE TABLE mapregion "
                            + "(position INTEGER PRIMARY KEY, data BLOB)"
            );
            try (PreparedStatement insert = setup.prepareStatement(
                    "INSERT INTO mapregion(position, data) VALUES (?, ?)"
            )) {
                insert(
                        insert,
                        new ChunkPosition(2, 0, 3, 1),
                        new byte[0]
                );
                insert(
                        insert,
                        new ChunkPosition(4, 1, 5, 0),
                        new byte[0]
                );
            }
        }

        VcdbsReader reader = reader();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        ); SaveSession session = new SaveSession(
                database,
                connection,
                new SaveSnapshot(
                        new WorldMetadata(1024, 256, 1024),
                        Map.of()
                )
        )) {
            MapRegionStreamStats stats =
                    reader.forEachObservedMapRegion(
                            session,
                            new ReadDiagnostics(),
                            ignored -> { },
                            ProgressReporter.NONE
                    );

            assertEquals(2, stats.rowsFound());
            assertEquals(0, stats.parsedMapRegions());
            assertEquals(0, stats.failedMapRegions());
            assertEquals(0, stats.invalidRows());
            assertEquals(2, stats.ignoredNonMainWorldRows());
            assertTrue(stats.complete());
        }
    }

    @Test
    void invalidMainWorldRowPreventsCompleteSnapshot()
            throws Exception {
        Path database = root.resolve("invalid-main-mapregion.vcdbs");
        try (Connection setup = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        ); Statement statement = setup.createStatement()) {
            statement.execute(
                    "CREATE TABLE mapregion "
                            + "(position INTEGER PRIMARY KEY, data BLOB)"
            );
            try (PreparedStatement insert = setup.prepareStatement(
                    "INSERT INTO mapregion(position, data) VALUES (?, ?)"
            )) {
                insert(
                        insert,
                        new ChunkPosition(2, 0, 3, 0),
                        new byte[0]
                );
            }
        }

        VcdbsReader reader = reader();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        ); SaveSession session = new SaveSession(
                database,
                connection,
                new SaveSnapshot(
                        new WorldMetadata(1024, 256, 1024),
                        Map.of()
                )
        )) {
            MapRegionStreamStats stats =
                    reader.forEachObservedMapRegion(
                            session,
                            new ReadDiagnostics(),
                            ignored -> { },
                            ProgressReporter.NONE
                    );

            assertEquals(1, stats.invalidRows());
            assertFalse(stats.complete());
        }
    }

    private static VcdbsReader reader() {
        return new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );
    }

    private static void insert(
            PreparedStatement statement,
            ChunkPosition position,
            byte[] payload
    ) throws Exception {
        statement.setLong(1, ChunkPosEncoder.encode(position));
        statement.setBytes(2, payload);
        statement.executeUpdate();
    }
}
