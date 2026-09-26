package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.testing.ConcurrencyTest;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.progress.ProgressReporter;
import cartographer.parser.ChunkParser;
import cartographer.parser.ChunkDecodeProfile;
import cartographer.parser.ChunkDecodeWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class VcdbsReaderDirectChunkLookupTestSupport {
    private static final long TEST_DEADLOCK_TIMEOUT_SECONDS = 10;

    @TempDir
    Path temporaryDirectory;

    ChunkStreamStats read(
            Path database,
            StubChunkParser parser,
            List<ChunkPosition> positions
    ) {
        return direct(
                VcdbsReaderFixtures.withChunkParser(parser),
                database,
                positions,
                new ReadDiagnostics(),
                parser.delivered()::add,
                ProgressReporter.NONE
        );
    }

    ChunkStreamStats adaptive(
            VcdbsReader reader,
            SqliteSaveConnection connections,
            Path database,
            List<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            java.util.function.Consumer<ParsedChunk> consumer
    ) {
        try (SaveSession session = new SaveSession(
                database,
                connections.openReadOnly(database),
                snapshot()
        )) {
            return reader.forEachChunkByPositionAdaptive(
                    session,
                    positions,
                    diagnostics,
                    consumer,
                    ProgressReporter.NONE
            );
        }
    }

    ChunkStreamStats direct(
            VcdbsReader reader,
            Path database,
            List<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            java.util.function.Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        try (SaveSession session = positions.isEmpty()
                ? emptySession(database)
                : openSession(database)) {
            return reader.forEachChunkByPosition(
                    session,
                    positions,
                    diagnostics,
                    consumer,
                    progress
            );
        }
    }

    ChunkStreamStats tableStream(
            VcdbsReader reader,
            Path database,
            List<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            java.util.function.Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        try (SaveSession session = openSession(database)) {
            return reader.forEachChunkByPositionTableStream(
                    session,
                    positions,
                    diagnostics,
                    consumer,
                    progress
            );
        }
    }

    SaveSession openSession(Path database) {
        return new SaveSession(
                database,
                new SqliteSaveConnection().openReadOnly(database),
                snapshot()
        );
    }

    SaveSession emptySession(Path database) {
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> null
        );
        return new SaveSession(database, connection, snapshot());
    }

    SaveSnapshot snapshot() {
        return new SaveSnapshot(
                new WorldMetadata(1, 1, 1),
                Map.of()
        );
    }

    Path databaseWithRows(ChunkPosition... positions) throws Exception {
        Path database = temporaryDirectory.resolve(
                "save-" + System.nanoTime() + ".vcdbs"
        );
        String sql = "CREATE TABLE chunk (position INTEGER PRIMARY KEY, data BLOB)";
        createDatabase(database, sql);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO chunk(position, data) VALUES (?, ?)")) {
            connection.setAutoCommit(false);
            try {
                for (ChunkPosition position : positions) {
                    statement.setLong(1, ChunkPosEncoder.encode(position));
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

    Path databaseWithRowCount(int count) throws Exception {
        return databaseWithRows(positions(count).toArray(ChunkPosition[]::new));
    }

    List<ChunkPosition> positions(int count) {
        List<ChunkPosition> positions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            positions.add(new ChunkPosition(index, 0, 0, 0));
        }
        return positions;
    }

    List<ChunkPosition> spacedPositions(int count) {
        List<ChunkPosition> positions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            positions.add(new ChunkPosition(index * 2, 0, 0, 0));
        }
        return positions;
    }

    Path databaseWithNullRow(ChunkPosition position) throws Exception {
        Path database = databaseWithRows(position);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE chunk SET data = NULL WHERE position = ?")) {
            statement.setLong(1, ChunkPosEncoder.encode(position));
            statement.executeUpdate();
        }
        return database;
    }

    void createDatabase(Path database, String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            if (!schema.isBlank()) {
                statement.execute(schema);
            }
        }
    }

    static void awaitLatch(CountDownLatch latch, String description)
            throws InterruptedException {
        assertTrue(
                latch.await(TEST_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                description + " was not signalled"
        );
    }

    static void joinThread(Thread thread, String description)
            throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(TEST_DEADLOCK_TIMEOUT_SECONDS));
        assertTrue(!thread.isAlive(), description + " did not terminate");
    }

    static final class StubChunkParser extends ChunkParser {
        final List<ChunkCoordinate> coordinates =
                Collections.synchronizedList(new ArrayList<>());
        final List<ParsedChunk> delivered =
                Collections.synchronizedList(new ArrayList<>());
        byte[] failurePayload;
        final AtomicInteger surfaceCompactCalls =
                new AtomicInteger();

        @Override
        public ParseResult<ParsedChunk> parse(
                ChunkCoordinate coordinate,
                byte[] payload
        ) {
            return parseStub(coordinate, payload);
        }

        @Override
        public ParseResult<ParsedChunk> parse(
                ChunkCoordinate coordinate,
                byte[] payload,
                ChunkDecodeProfile profile,
                ChunkDecodeWorkspace workspace
        ) {
            return parseStub(coordinate, payload);
        }

        @Override
        public ParseResult<ParsedChunk> parseSurfaceCompact(
                ChunkCoordinate coordinate,
                byte[] payload,
                ChunkDecodeWorkspace workspace
        ) {
            surfaceCompactCalls.incrementAndGet();
            return parseStub(
                    coordinate,
                    payload
            );
        }

        ParseResult<ParsedChunk> parseStub(
                ChunkCoordinate coordinate,
                byte[] payload
        ) {
            coordinates.add(coordinate);
            if (failurePayload != null
                    && java.util.Arrays.equals(failurePayload, payload)) {
                return ParseResult.failure("intentional test failure");
            }
            ParsedChunk parsed = cartographer.model.ParsedChunkFixtures.create(
                    coordinate,
                    coordinate.y(),
                    1,
                    1,
                    1,
                    new int[]{1}
            );
            return ParseResult.success(parsed);
        }

        int surfaceCompactCalls() {
            return surfaceCompactCalls.get();
        }

        List<ChunkCoordinate> coordinates() {
            return coordinates;
        }

        List<ParsedChunk> delivered() {
            return delivered;
        }

        List<Integer> xCoordinates() {
            return coordinates.stream().map(ChunkCoordinate::x).toList();
        }
    }

    static final class CountingSqliteSaveConnection
            extends SqliteSaveConnection {
        final AtomicInteger opens = new AtomicInteger();

        @Override
        public Connection openReadOnly(Path savePath) {
            opens.incrementAndGet();
            return super.openReadOnly(savePath);
        }

        int openCount() {
            return opens.get();
        }
    }

    static final class BlockingChunkParser extends ChunkParser {
        final CountDownLatch bothStarted = new CountDownLatch(2);
        final CountDownLatch release = new CountDownLatch(1);
        final List<Thread> workerThreads =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public ParseResult<ParsedChunk> parse(
                ChunkCoordinate coordinate,
                byte[] payload
        ) {
            return parseBlocking(coordinate);
        }

        @Override
        public ParseResult<ParsedChunk> parse(
                ChunkCoordinate coordinate,
                byte[] payload,
                ChunkDecodeProfile profile,
                ChunkDecodeWorkspace workspace
        ) {
            return parseBlocking(coordinate);
        }

        ParseResult<ParsedChunk> parseBlocking(
                ChunkCoordinate coordinate
        ) {
            workerThreads.add(Thread.currentThread());
            bothStarted.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return ParseResult.failure("interrupted");
            }
            return ParseResult.success(cartographer.model.ParsedChunkFixtures.create(
                    coordinate, coordinate.y(), 1, 1, 1, new int[]{1}
            ));
        }
    }

    static final class RecordingProgressReporter implements ProgressReporter {
        final List<String> events = new ArrayList<>();
        String doneMessage;

        @Override
        public void start(String stage) {
            events.add("start");
        }

        @Override
        public void done(String stage) {
            events.add("done");
            doneMessage = stage;
        }
    }
}
}
