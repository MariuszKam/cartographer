package cartographer.save;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.WorldMetadata;
import cartographer.parser.MapChunkParser;
import cartographer.progress.ProgressReporter;
import cartographer.testing.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsReaderObservedMapChunkCoordinateScanTest {

    @TempDir
    Path root;

    @Test
    void scansCoordinatesInBoundedPagesWithoutParsingPayloads() throws Exception {
        Path database = databaseWithMainWorldRows(513);
        CountingParser parser = new CountingParser();
        List<List<MapChunkCoordinate>> batches = new ArrayList<>();

        ObservedMapChunkCoordinateScanStats stats;
        try (SaveSession session = openSession(
                database,
                new WorldMetadata(20_000, 256, 64)
        )) {
            stats = VcdbsReaderFixtures.withMapChunkParser(parser)
                    .scanObservedMapChunkCoordinates(
                            session,
                            new ReadDiagnostics(),
                            batches::add,
                            ProgressReporter.NONE
                    );
        }

        assertTrue(stats.complete());
        assertEquals(513, stats.rowsScanned());
        assertEquals(513, stats.coordinatesAccepted());
        assertEquals(2, stats.batchesExecuted());
        assertEquals(List.of(512, 1), batches.stream().map(List::size).toList());
        assertEquals(0, parser.calls.get());
    }

    @Test
    void filtersNonMainWorldAndOutOfBoundsCoordinates() throws Exception {
        Path database = root.resolve("filtered.vcdbs");
        createTable(database);
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mapchunk(position, data) VALUES (?, ?)"
             )) {
            insert(statement, 1, 0, 1, 0);
            insert(statement, 2, 1, 2, 0);
            insert(statement, 3, 0, 3, 1);
            insert(statement, 40, 0, 1, 0);
        }

        List<MapChunkCoordinate> observed = new ArrayList<>();
        ObservedMapChunkCoordinateScanStats stats;
        try (SaveSession session = openSession(
                database,
                new WorldMetadata(1024, 256, 1024)
        )) {
            stats = VcdbsReaderFixtures.withMapChunkParser(new CountingParser())
                    .scanObservedMapChunkCoordinates(
                            session,
                            new ReadDiagnostics(),
                            observed::addAll,
                            ProgressReporter.NONE
                    );
        }

        assertTrue(stats.complete());
        assertEquals(4, stats.rowsScanned());
        assertEquals(1, stats.coordinatesAccepted());
        assertEquals(List.of(new MapChunkCoordinate(1, 1)), observed);
    }

    @Test
    void interruptionLeavesDiscoveryIncomplete() throws Exception {
        Path database = databaseWithMainWorldRows(1);
        List<MapChunkCoordinate> observed = new ArrayList<>();

        ObservedMapChunkCoordinateScanStats stats;
        try (SaveSession session = openSession(
                database,
                new WorldMetadata(64, 256, 64)
        )) {
            Thread.currentThread().interrupt();
            try {
                stats = VcdbsReaderFixtures.withMapChunkParser(
                        new CountingParser()
                ).scanObservedMapChunkCoordinates(
                        session,
                        new ReadDiagnostics(),
                        observed::addAll,
                        ProgressReporter.NONE
                );
            } finally {
                Thread.interrupted();
            }
        }

        assertFalse(stats.complete());
        assertEquals(0, stats.rowsScanned());
        assertEquals(0, stats.batchesExecuted());
        assertTrue(observed.isEmpty());
    }

    private Path databaseWithMainWorldRows(int count) throws Exception {
        Path database = root.resolve(
                "coordinates-" + count + "-" + System.nanoTime() + ".vcdbs"
        );
        createTable(database);
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mapchunk(position, data) VALUES (?, ?)"
             )) {
            for (int x = 0; x < count; x++) {
                insert(statement, x, 0, 0, 0);
            }
        }
        return database;
    }

    private void createTable(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE mapchunk "
                            + "(position INTEGER PRIMARY KEY, data BLOB)"
            );
        }
    }

    private void insert(
            PreparedStatement statement,
            int x,
            int y,
            int z,
            int dimension
    ) throws Exception {
        statement.setLong(
                1,
                ChunkPosEncoder.encode(x, y, z, dimension)
        );
        statement.setBytes(2, new byte[]{1, 2, 3});
        statement.executeUpdate();
    }

    private SaveSession openSession(
            Path database,
            WorldMetadata metadata
    ) {
        return new SaveSession(
                database,
                new SqliteSaveConnection().openReadOnly(database),
                new SaveSnapshot(metadata, Map.of())
        );
    }

    private static final class CountingParser extends MapChunkParser {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ParseResult<MapChunk> parse(
                MapChunkCoordinate coordinate,
                byte[] payload
        ) {
            calls.incrementAndGet();
            return ParseResult.failure("coordinate-only scan must not parse");
        }
    }
    @Test
    void pageApiAdvancesByPackedPositionWithoutReadingPayload()
            throws Exception {
        Path database = databaseWithMainWorldRows(513);
        CountingParser parser = new CountingParser();

        try (SaveSession session = openSession(
                database,
                new WorldMetadata(20_000, 256, 64)
        )) {
            VcdbsReader reader =
                    VcdbsReaderFixtures.withMapChunkParser(parser);
            ObservedMapChunkCoordinatePage first =
                    reader.readObservedMapChunkCoordinatePage(
                            session,
                            OptionalLong.empty(),
                            new ReadDiagnostics()
                    );
            ObservedMapChunkCoordinatePage second =
                    reader.readObservedMapChunkCoordinatePage(
                            session,
                            first.lastPosition(),
                            new ReadDiagnostics()
                    );

            assertEquals(512, first.rowsScanned());
            assertFalse(first.complete());
            assertEquals(1, second.rowsScanned());
            assertTrue(second.complete());
            assertEquals(0, parser.calls.get());
        }
    }

}
