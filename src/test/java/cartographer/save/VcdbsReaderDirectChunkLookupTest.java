package cartographer.save;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.cli.ProgressReporter;
import cartographer.parser.ChunkParser;
import cartographer.parser.ChunkDecodeProfile;
import cartographer.parser.ChunkDecodeWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VcdbsReaderDirectChunkLookupTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsOnlyRequestedPrimaryKeys() throws Exception {
        ChunkPosition a = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition b = new ChunkPosition(3, 0, 4, 0);
        ChunkPosition c = new ChunkPosition(5, 0, 6, 0);
        Path database = databaseWithRows(a, b, c);
        StubChunkParser parser = new StubChunkParser();

        ChunkStreamStats stats = read(database, parser, List.of(a, c));

        assertEquals(2, stats.uniquePositionsRequested());
        assertEquals(2, stats.rowsFound());
        assertEquals(2, stats.parsedChunks());
        assertEquals(Set.of(a.x(), c.x()), Set.copyOf(parser.xCoordinates()));
    }

    @Test
    void missingPositionIsNotFailure() throws Exception {
        ChunkPosition a = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition b = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRows(a);

        ChunkStreamStats stats = read(database, new StubChunkParser(), List.of(a, b));

        assertEquals(1, stats.rowsFound());
        assertEquals(1, stats.parsedChunks());
        assertEquals(0, stats.failedChunks());
    }

    @Test
    void adaptiveSingleBatchUsesDirectLookupWithoutStrategyProbe() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 0, 0);
        ChunkPosition second = new ChunkPosition(2, 0, 0, 0);
        Path database = databaseWithRows(first, second);
        CountingSqliteSaveConnection connections = new CountingSqliteSaveConnection();

        ChunkStreamStats stats = new VcdbsReader(
                null, null, new StubChunkParser(), null, connections
        ).forEachChunkByPositionAdaptive(
                database,
                List.of(first, second),
                new ReadDiagnostics(),
                ignored -> { }
        );

        assertEquals(1, connections.openCount());
        assertEquals(2, stats.rowsFound());
        assertEquals(2, stats.parsedChunks());
        assertEquals(1, stats.batchesExecuted());
    }

    @Test
    void adaptiveUsesDirectLookupWhenTableContainsMoreRowsThanRequest() throws Exception {
        Path database = databaseWithRowCount(300);
        List<ChunkPosition> requested = positions(257);
        CountingSqliteSaveConnection connections = new CountingSqliteSaveConnection();

        ChunkStreamStats stats = new VcdbsReader(
                null, null, new StubChunkParser(), null, connections
        ).forEachChunkByPositionAdaptive(
                database, requested, new ReadDiagnostics(), ignored -> { }
        );

        assertEquals(257, stats.uniquePositionsRequested());
        assertEquals(257, stats.rowsFound());
        assertEquals(257, stats.parsedChunks());
        assertEquals(2, stats.batchesExecuted());
        assertEquals(2, connections.openCount());
    }

    @Test
    void adaptiveUsesTableStreamWhenRequestExceedsTableCardinality() throws Exception {
        Path database = databaseWithRowCount(300);
        CountingSqliteSaveConnection connections = new CountingSqliteSaveConnection();

        ChunkStreamStats stats = new VcdbsReader(
                null, null, new StubChunkParser(), null, connections
        ).forEachChunkByPositionAdaptive(
                database, positions(320), new ReadDiagnostics(), ignored -> { }
        );

        assertEquals(320, stats.uniquePositionsRequested());
        assertEquals(300, stats.rowsFound());
        assertEquals(300, stats.parsedChunks());
        assertEquals(0, stats.failedChunks());
        assertEquals(1, stats.batchesExecuted());
        assertEquals(2, connections.openCount());
    }

    @Test
    void adaptiveUsesTableStreamWhenRequestEqualsTableCardinality() throws Exception {
        Path database = databaseWithRowCount(300);

        ChunkStreamStats stats = new VcdbsReader(
                null, null, new StubChunkParser(), null, new CountingSqliteSaveConnection()
        ).forEachChunkByPositionAdaptive(
                database, positions(300), new ReadDiagnostics(), ignored -> { }
        );

        assertEquals(1, stats.batchesExecuted());
        assertEquals(300, stats.rowsFound());
    }

    @Test
    void adaptiveStrategyUsesUniquePositionCount() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 0, 0);
        ChunkPosition second = new ChunkPosition(2, 0, 0, 0);
        List<ChunkPosition> requested = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            requested.add(index % 2 == 0 ? first : second);
        }
        CountingSqliteSaveConnection connections = new CountingSqliteSaveConnection();

        ChunkStreamStats stats = new VcdbsReader(
                null, null, new StubChunkParser(), null, connections
        ).forEachChunkByPositionAdaptive(
                databaseWithRows(first, second),
                requested,
                new ReadDiagnostics(),
                ignored -> { }
        );

        assertEquals(1, connections.openCount());
        assertEquals(2, stats.uniquePositionsRequested());
        assertEquals(1, stats.batchesExecuted());
    }

    @Test
    void tableStreamMatchesDirectLookupSemantics() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition second = new ChunkPosition(3, 0, 4, 0);
        ChunkPosition unrequested = new ChunkPosition(5, 0, 6, 0);
        Path database = databaseWithRows(first, second, unrequested);
        StubChunkParser directParser = new StubChunkParser();
        StubChunkParser tableParser = new StubChunkParser();

        ChunkStreamStats direct = read(database, directParser, List.of(first, second));
        ChunkStreamStats table = new VcdbsReader(
                null, null, tableParser, null
        ).forEachChunkByPositionTableStream(
                database,
                List.of(first, second),
                new ReadDiagnostics(),
                tableParser.delivered()::add,
                ProgressReporter.NONE
        );

        assertEquals(direct.uniquePositionsRequested(), table.uniquePositionsRequested());
        assertEquals(direct.rowsFound(), table.rowsFound());
        assertEquals(direct.parsedChunks(), table.parsedChunks());
        assertEquals(direct.failedChunks(), table.failedChunks());
        assertEquals(direct.payloadBytes(), table.payloadBytes());
        assertEquals(Set.of(1, 3), Set.copyOf(tableParser.xCoordinates()));
    }

    @Test
    void tableStreamDoesNotParseUnrequestedPayloads() throws Exception {
        ChunkPosition requested = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition other = new ChunkPosition(3, 0, 4, 0);
        ChunkPosition another = new ChunkPosition(5, 0, 6, 0);
        Path database = databaseWithRows(requested, other, another);
        StubChunkParser parser = new StubChunkParser();

        new VcdbsReader(null, null, parser, null)
                .forEachChunkByPositionTableStream(
                        database,
                        List.of(requested),
                        new ReadDiagnostics(),
                        parser.delivered()::add,
                        ProgressReporter.NONE
                );

        assertEquals(1, parser.xCoordinates().size());
        assertEquals(1, parser.xCoordinates().getFirst());
    }

    @Test
    void tableStreamReportsSingleBatchAndRequestedNullPayloadIsSkipped() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithNullRow(position);
        ReadDiagnostics diagnostics = new ReadDiagnostics();

        ChunkStreamStats stats = new VcdbsReader(null, null, new StubChunkParser(), null)
                .forEachChunkByPositionTableStream(
                        database,
                        List.of(position),
                        diagnostics,
                        ignored -> { },
                        ProgressReporter.NONE
                );

        assertEquals(1, stats.batchesExecuted());
        assertEquals(1, stats.rowsFound());
        assertEquals(0, stats.parsedChunks());
        assertEquals(0, stats.failedChunks());
        assertEquals(0, stats.payloadBytes());
        assertTrue(
                diagnostics.skippedNotes().contains(
                        "skipped: 1 x chunk row has null payload"
                )
        );
    }

    @Test
    void tableStreamMissingRequestedKeyIsNotFailure() throws Exception {
        ChunkPosition existing = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition missing = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRows(existing);

        ChunkStreamStats stats = new VcdbsReader(
                null, null, new StubChunkParser(), null
        ).forEachChunkByPositionTableStream(
                database,
                List.of(existing, missing),
                new ReadDiagnostics(),
                ignored -> { },
                ProgressReporter.NONE
        );

        assertEquals(2, stats.uniquePositionsRequested());
        assertEquals(1, stats.rowsFound());
        assertEquals(0, stats.failedChunks());
    }

    @Test
    void tableStreamDecodeStillRunsConcurrently() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition second = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRows(first, second);
        BlockingChunkParser parser = new BlockingChunkParser();
        VcdbsReader reader = new VcdbsReader(
                null, null, parser, null, new SqliteSaveConnection(), 2, 4
        );
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        AtomicReference<Thread> consumerThread = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread caller = Thread.ofPlatform().start(() -> {
            callerThread.set(Thread.currentThread());
            try {
                reader.forEachChunkByPositionTableStream(
                        database,
                        List.of(first, second),
                        new ReadDiagnostics(),
                        ignored -> consumerThread.set(Thread.currentThread()),
                        ProgressReporter.NONE
                );
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });

        assertTrue(parser.bothStarted.await(1, TimeUnit.SECONDS));
        parser.release.countDown();
        caller.join();

        assertNull(failure.get());
        assertEquals(callerThread.get(), consumerThread.get());
        assertEquals(2, parser.workerThreads.size());
        assertTrue(parser.workerThreads.stream().noneMatch(Thread::isVirtual));
    }

    @Test
    void parallelDecodeOverlapsWhileConsumerRemainsCallerThread() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition second = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRows(first, second);
        BlockingChunkParser parser = new BlockingChunkParser();
        VcdbsReader reader = new VcdbsReader(
                null, null, parser, null, new SqliteSaveConnection(), 2, 4
        );
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        AtomicReference<Thread> consumerThread = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread caller = Thread.ofPlatform().start(() -> {
            callerThread.set(Thread.currentThread());
            try {
                reader.forEachChunkByPosition(
                        database,
                        List.of(first, second),
                        new ReadDiagnostics(),
                        ignored -> consumerThread.set(Thread.currentThread())
                );
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });

        assertTrue(parser.bothStarted.await(1, TimeUnit.SECONDS));
        parser.release.countDown();
        caller.join();

        assertNull(failure.get());
        assertEquals(callerThread.get(), consumerThread.get());
        assertEquals(2, parser.workerThreads.size());
        assertTrue(parser.workerThreads.stream().noneMatch(Thread::isVirtual));
    }

    @Test
    void deduplicatesRequestedPositions() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRows(position);
        StubChunkParser parser = new StubChunkParser();

        ChunkStreamStats stats = read(
                database,
                parser,
                List.of(position, position, position)
        );

        assertEquals(1, stats.uniquePositionsRequested());
        assertEquals(1, stats.rowsFound());
        assertEquals(1, parser.xCoordinates().size());
    }

    @Test
    void supportsNegativeCoordinatesAndDimensions() throws Exception {
        ChunkPosition position = new ChunkPosition(-10, 3, -20, 37);
        Path database = databaseWithRows(position);
        StubChunkParser parser = new StubChunkParser();

        ChunkStreamStats stats = read(database, parser, List.of(position));

        assertEquals(1, stats.parsedChunks());
        assertEquals(
                new ChunkCoordinate(-10, 3, -20),
                parser.coordinates().getFirst()
        );
    }

    @Test
    void verticalAndDimensionKeysDoNotCollide() throws Exception {
        ChunkPosition y1 = new ChunkPosition(10, 1, 20, 0);
        ChunkPosition y2 = new ChunkPosition(10, 2, 20, 0);
        ChunkPosition dimension1 = new ChunkPosition(10, 2, 20, 1);
        Path database = databaseWithRows(y1, y2, dimension1);
        StubChunkParser parser = new StubChunkParser();

        ChunkStreamStats yStats = read(database, parser, List.of(y2));
        assertEquals(1, yStats.rowsFound());
        assertEquals(new ChunkCoordinate(10, 2, 20), parser.coordinates().getFirst());

        parser.coordinates().clear();
        ChunkStreamStats dimensionStats = read(
                database,
                parser,
                List.of(dimension1)
        );
        assertEquals(1, dimensionStats.rowsFound());
        assertEquals(new ChunkCoordinate(10, 2, 20), parser.coordinates().getFirst());
        assertEquals(1, dimension1.dimension());
    }

    @Test
    void handlesNullPayloadWithoutConsumerCall() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithNullRow(position);
        StubChunkParser parser = new StubChunkParser();

        ChunkStreamStats stats = read(database, parser, List.of(position));

        assertEquals(1, stats.rowsFound());
        assertEquals(0, stats.payloadBytes());
        assertEquals(0, stats.parsedChunks());
        assertTrue(parser.coordinates().isEmpty());
    }

    @Test
    void recordsParserFailureWithoutConsumerCall() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRows(position);
        StubChunkParser parser = new StubChunkParser();
        parser.failurePayload = new byte[]{1};

        ChunkStreamStats stats = read(database, parser, List.of(position));

        assertEquals(1, stats.failedChunks());
        assertEquals(0, stats.parsedChunks());
        assertTrue(parser.delivered().isEmpty());
    }

    @Test
    void usesMoreThanOneBatch() throws Exception {
        List<ChunkPosition> positions = new ArrayList<>();
        for (int index = 0; index < 257; index++) {
            positions.add(new ChunkPosition(index, 0, 0, 0));
        }
        Path database = databaseWithRows(positions.toArray(ChunkPosition[]::new));

        ChunkStreamStats stats = read(database, new StubChunkParser(), positions);

        assertEquals(257, stats.uniquePositionsRequested());
        assertEquals(2, stats.batchesExecuted());
        assertEquals(257, stats.rowsFound());
    }

    @Test
    void emptyRequestDoesNotOpenDatabase() {
        ChunkStreamStats stats = read(
                temporaryDirectory.resolve("missing.vcdbs"),
                new StubChunkParser(),
                List.of()
        );

        assertEquals(new ChunkStreamStats(0, 0, 0, 0, 0, 0), stats);
    }

    @Test
    void missingChunkTableReturnsSafely() throws Exception {
        Path database = temporaryDirectory.resolve("empty.vcdbs");
        createDatabase(database, "");
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<ParsedChunk> delivered = new ArrayList<>();
        RecordingProgressReporter progress = new RecordingProgressReporter();

        ChunkStreamStats stats = new VcdbsReader(
                null,
                null,
                new StubChunkParser(),
                null
        ).forEachChunkByPosition(
                database,
                List.of(new ChunkPosition(1, 0, 2, 0)),
                diagnostics,
                delivered::add,
                progress
        );

        assertEquals(new ChunkStreamStats(1, 0, 0, 0, 0, 0), stats);
        assertTrue(diagnostics.notes().contains("missing table: chunk"));
        assertTrue(delivered.isEmpty());
        assertEquals(List.of("start", "done"), progress.events);
        assertEquals(
                "Exact chunk lookup unavailable: chunk table missing",
                progress.doneMessage
        );
    }

    private ChunkStreamStats read(
            Path database,
            StubChunkParser parser,
            List<ChunkPosition> positions
    ) {
        return new VcdbsReader(
                null,
                null,
                parser,
                null
        ).forEachChunkByPosition(
                database,
                positions,
                new ReadDiagnostics(),
                parser.delivered()::add
        );
    }

    private Path databaseWithRows(ChunkPosition... positions) throws Exception {
        Path database = temporaryDirectory.resolve(
                "save-" + System.nanoTime() + ".vcdbs"
        );
        String sql = "CREATE TABLE chunk (position INTEGER PRIMARY KEY, data BLOB)";
        createDatabase(database, sql);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO chunk(position, data) VALUES (?, ?)")) {
            for (ChunkPosition position : positions) {
                statement.setLong(1, ChunkPosEncoder.encode(position));
                statement.setBytes(2, new byte[]{1});
                statement.executeUpdate();
            }
        }
        return database;
    }

    private Path databaseWithRowCount(int count) throws Exception {
        return databaseWithRows(positions(count).toArray(ChunkPosition[]::new));
    }

    private List<ChunkPosition> positions(int count) {
        List<ChunkPosition> positions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            positions.add(new ChunkPosition(index, 0, 0, 0));
        }
        return positions;
    }

    private Path databaseWithNullRow(ChunkPosition position) throws Exception {
        Path database = databaseWithRows(position);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE chunk SET data = NULL WHERE position = ?")) {
            statement.setLong(1, ChunkPosEncoder.encode(position));
            statement.executeUpdate();
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

    private static final class StubChunkParser extends ChunkParser {
        private final List<ChunkCoordinate> coordinates =
                Collections.synchronizedList(new ArrayList<>());
        private final List<ParsedChunk> delivered =
                Collections.synchronizedList(new ArrayList<>());
        private byte[] failurePayload;

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

        private ParseResult<ParsedChunk> parseStub(
                ChunkCoordinate coordinate,
                byte[] payload
        ) {
            coordinates.add(coordinate);
            if (failurePayload != null
                    && java.util.Arrays.equals(failurePayload, payload)) {
                return ParseResult.failure("intentional test failure");
            }
            ParsedChunk parsed = new ParsedChunk(
                    coordinate,
                    coordinate.y(),
                    1,
                    1,
                    1,
                    new int[]{1}
            );
            return ParseResult.success(parsed);
        }

        private List<ChunkCoordinate> coordinates() {
            return coordinates;
        }

        private List<ParsedChunk> delivered() {
            return delivered;
        }

        private List<Integer> xCoordinates() {
            return coordinates.stream().map(ChunkCoordinate::x).toList();
        }
    }

    private static final class CountingSqliteSaveConnection
            extends SqliteSaveConnection {
        private final AtomicInteger opens = new AtomicInteger();

        @Override
        public Connection openReadOnly(Path savePath) {
            opens.incrementAndGet();
            return super.openReadOnly(savePath);
        }

        private int openCount() {
            return opens.get();
        }
    }

    private static final class BlockingChunkParser extends ChunkParser {
        private final CountDownLatch bothStarted = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Thread> workerThreads =
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

        private ParseResult<ParsedChunk> parseBlocking(
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
            return ParseResult.success(new ParsedChunk(
                    coordinate, coordinate.y(), 1, 1, 1, new int[]{1}
            ));
        }
    }

    private static final class RecordingProgressReporter extends ProgressReporter {
        private final List<String> events = new ArrayList<>();
        private String doneMessage;

        private RecordingProgressReporter() {
            super(null);
        }

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
