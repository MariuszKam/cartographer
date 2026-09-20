package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.cli.ProgressReporter;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.parser.MapChunkParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsReaderDirectMapChunkLookupTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsOnlyRequestedMapChunkPrimaryKeys() throws Exception {
        MapChunkCoordinate wanted = new MapChunkCoordinate(1, 2);
        MapChunkCoordinate unrelated = new MapChunkCoordinate(3, 4);
        Path database = databaseWithRows(wanted, unrelated);
        StubMapChunkParser parser = new StubMapChunkParser();
        List<MapChunk> delivered = new ArrayList<>();

        MapChunkStreamStats stats = read(database, parser, List.of(wanted), delivered);

        assertEquals(1, stats.rowsFound());
        assertEquals(List.of(wanted), parser.coordinates);
        assertEquals(List.of(wanted), delivered.stream().map(MapChunk::coordinate).toList());
    }

    @Test
    void deduplicatesRequestedCoordinates() throws Exception {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(1, 2);
        Path database = databaseWithRows(coordinate);
        StubMapChunkParser parser = new StubMapChunkParser();

        MapChunkStreamStats stats = read(
                database,
                parser,
                List.of(coordinate, coordinate, coordinate),
                new ArrayList<>()
        );

        assertEquals(1, stats.uniquePositionsRequested());
        assertEquals(1, stats.rowsFound());
        assertEquals(1, parser.coordinates.size());
    }

    @Test
    void batches257CoordinatesIntoTwoQueries() throws Exception {
        List<MapChunkCoordinate> coordinates = new ArrayList<>();
        for (int index = 0; index < 257; index++) {
            coordinates.add(new MapChunkCoordinate(index, 0));
        }
        Path database = databaseWithRows(coordinates.toArray(MapChunkCoordinate[]::new));

        MapChunkStreamStats stats = read(
                database,
                new StubMapChunkParser(),
                coordinates,
                new ArrayList<>()
        );

        assertEquals(2, stats.batchesExecuted());
        assertEquals(257, stats.rowsFound());
    }

    @Test
    void supportsNegativeCoordinates() throws Exception {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(-10, -20);
        Path database = databaseWithRows(coordinate);
        StubMapChunkParser parser = new StubMapChunkParser();

        MapChunkStreamStats stats = read(
                database, parser, List.of(coordinate), new ArrayList<>()
        );

        assertEquals(1, stats.parsedMapChunks());
        assertEquals(coordinate, parser.coordinates.getFirst());
    }

    @Test
    void ignoresSameXZWithDifferentYAndDimension() throws Exception {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(10, 20);
        Path database = databaseWithRows(coordinate);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mapchunk(position, data) VALUES (?, ?)")) {
            statement.setLong(1, ChunkPosEncoder.encode(10, 1, 20, 0));
            statement.setBytes(2, new byte[]{2});
            statement.executeUpdate();
            statement.setLong(1, ChunkPosEncoder.encode(10, 0, 20, 1));
            statement.setBytes(2, new byte[]{3});
            statement.executeUpdate();
        }

        StubMapChunkParser parser = new StubMapChunkParser();
        MapChunkStreamStats stats = read(
                database, parser, List.of(coordinate), new ArrayList<>()
        );

        assertEquals(1, stats.rowsFound());
        assertEquals(List.of(coordinate), parser.coordinates);
    }

    @Test
    void nullPayloadIsSkipped() throws Exception {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(1, 2);
        Path database = databaseWithRows(coordinate);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE mapchunk SET data = NULL WHERE position = ?")) {
            statement.setLong(1, ChunkPosEncoder.encode(coordinate.x(), 0, coordinate.z(), 0));
            statement.executeUpdate();
        }

        StubMapChunkParser parser = new StubMapChunkParser();
        MapChunkStreamStats stats = read(
                database, parser, List.of(coordinate), new ArrayList<>()
        );

        assertEquals(1, stats.rowsFound());
        assertEquals(0, stats.parsedMapChunks());
        assertEquals(0, stats.failedMapChunks());
        assertEquals(0, stats.payloadBytes());
        assertTrue(parser.coordinates.isEmpty());
    }

    @Test
    void parserFailureIsCounted() throws Exception {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(1, 2);
        Path database = databaseWithRows(coordinate);
        StubMapChunkParser parser = new StubMapChunkParser();
        parser.fail = true;

        MapChunkStreamStats stats = read(
                database, parser, List.of(coordinate), new ArrayList<>()
        );

        assertEquals(1, stats.failedMapChunks());
        assertEquals(0, stats.parsedMapChunks());
    }

    @Test
    void emptyCoordinatesAvoidDatabaseAccess() {
        MapChunkStreamStats stats = read(
                temporaryDirectory.resolve("missing.vcdbs"),
                new StubMapChunkParser(),
                List.of(),
                new ArrayList<>()
        );

        assertEquals(new MapChunkStreamStats(0, 0, 0, 0, 0, 0), stats);
    }

    @Test
    void missingTableCompletesProgressLifecycle() throws Exception {
        Path database = temporaryDirectory.resolve("empty.vcdbs");
        createDatabase(database, "");
        RecordingProgressReporter progress = new RecordingProgressReporter();

        MapChunkStreamStats stats = new VcdbsReader(
                null, new StubMapChunkParser(), null, null
        ).forEachMapChunkByCoordinate(
                database,
                List.of(new MapChunkCoordinate(1, 2)),
                new ReadDiagnostics(),
                ignored -> { },
                progress
        );

        assertEquals(new MapChunkStreamStats(1, 0, 0, 0, 0, 0), stats);
        assertEquals(List.of("start", "done"), progress.events);
    }

    private MapChunkStreamStats read(
            Path database,
            StubMapChunkParser parser,
            List<MapChunkCoordinate> coordinates,
            List<MapChunk> delivered
    ) {
        return new VcdbsReader(
                null, parser, null, null
        ).forEachMapChunkByCoordinate(
                database,
                coordinates,
                new ReadDiagnostics(),
                delivered::add
        );
    }

    private Path databaseWithRows(MapChunkCoordinate... coordinates) throws Exception {
        Path database = temporaryDirectory.resolve("save-" + System.nanoTime() + ".vcdbs");
        createDatabase(
                database,
                "CREATE TABLE mapchunk (position INTEGER PRIMARY KEY, data BLOB)"
        );
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mapchunk(position, data) VALUES (?, ?)")) {
            connection.setAutoCommit(false);
            try {
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
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
        return database;
    }

    private void createDatabase(Path database, String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            if (!schema.isBlank()) {
                statement.execute(schema);
            }
        }
    }

    private static final class StubMapChunkParser extends MapChunkParser {
        private final List<MapChunkCoordinate> coordinates = new ArrayList<>();
        private boolean fail;

        @Override
        public ParseResult<MapChunk> parse(
                MapChunkCoordinate coordinate,
                byte[] payload
        ) {
            coordinates.add(coordinate);
            if (fail) {
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

    private static final class RecordingProgressReporter extends ProgressReporter {
        private final List<String> events = new ArrayList<>();

        private RecordingProgressReporter() {
            super(new PrintStream(new ByteArrayOutputStream()));
        }

        @Override
        public void start(String stage) {
            events.add("start");
        }

        @Override
        public void done(String stage) {
            events.add("done");
        }
    }
}
