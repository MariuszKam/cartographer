package cartographer.save;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.WorldMetadata;
import cartographer.parser.MapChunkParser;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsReaderStrongMapChunkResultTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void distinguishesDecodedAbsentAndUnreadableRows() throws Exception {
        MapChunkCoordinate decoded = new MapChunkCoordinate(1, 2);
        MapChunkCoordinate missing = new MapChunkCoordinate(3, 4);
        MapChunkCoordinate nullPayload = new MapChunkCoordinate(5, 6);
        MapChunkCoordinate parserFailure = new MapChunkCoordinate(7, 8);
        Path database = databaseWithRows(decoded, nullPayload, parserFailure);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE mapchunk SET data = NULL WHERE position = ?"
             )) {
            statement.setLong(
                    1,
                    ChunkPosEncoder.encode(
                            nullPayload.x(), 0, nullPayload.z(), 0
                    )
            );
            statement.executeUpdate();
        }

        SelectiveMapChunkParser parser =
                new SelectiveMapChunkParser(parserFailure);
        List<MapChunkReadResult> results = new ArrayList<>();

        try (SaveSession session = openSession(database)) {
            VcdbsReaderFixtures.withMapChunkParser(parser)
                    .forEachMapChunkByCoordinateWithResults(
                            session,
                            List.of(
                                    decoded,
                                    missing,
                                    nullPayload,
                                    parserFailure
                            ),
                            new ReadDiagnostics(),
                            results::add
                    );
        }

        assertEquals(4, results.size());
        assertEquals(
                MapChunkReadStatus.PRESENT_DECODED,
                resultFor(results, decoded).status()
        );
        assertTrue(resultFor(results, decoded).decodedMapChunk().isPresent());
        assertEquals(
                MapChunkReadStatus.ABSENT,
                resultFor(results, missing).status()
        );
        assertEquals(
                MapChunkReadStatus.PRESENT_UNREADABLE,
                resultFor(results, nullPayload).status()
        );
        assertEquals(
                MapChunkReadStatus.PRESENT_UNREADABLE,
                resultFor(results, parserFailure).status()
        );
    }

    @Test
    void reportsMissingMapchunkTableAsNotCompletedNotAbsent() throws Exception {
        Path database = temporaryDirectory.resolve("missing-table.vcdbs");
        try (Connection ignored = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        )) {
            // Empty database intentionally has no mapchunk table.
        }

        MapChunkCoordinate coordinate = new MapChunkCoordinate(1, 2);
        List<MapChunkReadResult> results = new ArrayList<>();
        try (SaveSession session = openSession(database)) {
            VcdbsReaderFixtures.withMapChunkParser(
                    new SelectiveMapChunkParser(null)
            ).forEachMapChunkByCoordinateWithResults(
                    session,
                    List.of(coordinate),
                    new ReadDiagnostics(),
                    results::add
            );
        }

        assertEquals(1, results.size());
        assertEquals(MapChunkReadStatus.NOT_COMPLETED, results.getFirst().status());
    }

    @Test
    void interruptedLookupMarksEveryUnstartedRequestNotCompleted() throws Exception {
        Path database = databaseWithRows(new MapChunkCoordinate(1, 2));
        List<MapChunkReadResult> results = new ArrayList<>();

        try (SaveSession session = openSession(database)) {
            Thread.currentThread().interrupt();
            try {
                VcdbsReaderFixtures.withMapChunkParser(
                        new SelectiveMapChunkParser(null)
                ).forEachMapChunkByCoordinateWithResults(
                        session,
                        List.of(
                                new MapChunkCoordinate(1, 2),
                                new MapChunkCoordinate(3, 4)
                        ),
                        new ReadDiagnostics(),
                        results::add
                );
            } finally {
                Thread.interrupted();
            }
        }

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(
                result -> result.status() == MapChunkReadStatus.NOT_COMPLETED
        ));
    }

    private MapChunkReadResult resultFor(
            List<MapChunkReadResult> results,
            MapChunkCoordinate coordinate
    ) {
        return results.stream()
                .filter(result -> result.coordinate().equals(coordinate))
                .findFirst()
                .orElseThrow();
    }

    private SaveSession openSession(Path database) {
        return new SaveSession(
                database,
                new SqliteSaveConnection().openReadOnly(database),
                new SaveSnapshot(
                        new WorldMetadata(1024, 256, 1024),
                        Map.of()
                )
        );
    }

    private Path databaseWithRows(MapChunkCoordinate... coordinates)
            throws Exception {
        Path database = temporaryDirectory.resolve(
                "strong-result-" + System.nanoTime() + ".vcdbs"
        );
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE mapchunk "
                            + "(position INTEGER PRIMARY KEY, data BLOB)"
            );
        }

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mapchunk(position, data) VALUES (?, ?)"
             )) {
            for (MapChunkCoordinate coordinate : coordinates) {
                statement.setLong(
                        1,
                        ChunkPosEncoder.encode(
                                coordinate.x(), 0, coordinate.z(), 0
                        )
                );
                statement.setBytes(2, new byte[]{1});
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return database;
    }

    private static final class SelectiveMapChunkParser extends MapChunkParser {
        private final MapChunkCoordinate failingCoordinate;

        private SelectiveMapChunkParser(
                MapChunkCoordinate failingCoordinate
        ) {
            this.failingCoordinate = failingCoordinate;
        }

        @Override
        public ParseResult<MapChunk> parse(
                MapChunkCoordinate coordinate,
                byte[] payload
        ) {
            if (coordinate.equals(failingCoordinate)) {
                return ParseResult.failure("intentional test failure");
            }
            return ParseResult.success(
                    new MapChunk(
                            coordinate,
                            new int[MapChunk.HEIGHT_VALUE_COUNT],
                            new int[0]
                    )
            );
        }
    }
}
