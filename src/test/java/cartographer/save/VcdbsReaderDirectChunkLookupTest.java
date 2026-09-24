package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.testing.ConcurrencyTest;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.cli.ProgressReporter;
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

@IntegrationTest
class VcdbsReaderDirectChunkLookupTest {
    private static final long TEST_DEADLOCK_TIMEOUT_SECONDS = 10;

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
    void normalAdaptiveTraversalKeepsFullParser() throws Exception {
        ChunkPosition position =
                new ChunkPosition(1, 0, 2, 0);
        Path database =
                databaseWithRows(position);
        StubChunkParser parser =
                new StubChunkParser();

        VcdbsReader reader = new VcdbsReader(null, null, parser, null);
        ChunkStreamStats stats = adaptive(
                reader,
                new SqliteSaveConnection(),
                database,
                List.of(position),
                new ReadDiagnostics(),
                ignored -> { }
        );

        assertEquals(1, stats.parsedChunks());
        assertEquals(
                0,
                parser.surfaceCompactCalls()
        );
    }

    @Test
    void surfaceAdaptiveTraversalUsesCompactParser() throws Exception {
        ChunkPosition position =
                new ChunkPosition(1, 0, 2, 0);
        Path database =
                databaseWithRows(position);
        StubChunkParser parser =
                new StubChunkParser();
        VcdbsReader reader =
                new VcdbsReader(
                        null,
                        null,
                        parser,
                        null
                );
        SaveSnapshot snapshot =
                new SaveSnapshot(
                        database,
                        new WorldMetadata(
                                64,
                                256,
                                64
                        ),
                        Map.of()
                );
        List<ParsedChunk> delivered =
                new ArrayList<>();

        try (SaveSession session =
                     new SaveSession(
                             database,
                             new SqliteSaveConnection()
                                     .openReadOnly(database),
                             snapshot
                     )) {
            ChunkStreamStats stats =
                    reader.forEachSurfaceChunkByPositionAdaptive(
                            session,
                            List.of(position),
                            new ReadDiagnostics(),
                            delivered::add,
                            cartographer.application.ProgressReporter.NONE
                    );

            assertEquals(1, stats.rowsFound());
            assertEquals(1, stats.parsedChunks());
        }

        assertEquals(
                1,
                parser.surfaceCompactCalls()
        );
        assertEquals(
                1,
                delivered.size()
        );
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

        ChunkStreamStats stats = adaptive(
                new VcdbsReader(null, null, new StubChunkParser(), null),
                connections,
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
    void adaptiveUsesDirectLookupForSparseRequestWhenTableContainsMoreRows()
            throws Exception {
        List<ChunkPosition> allRows = spacedPositions(300);
        Path database = databaseWithRows(allRows.toArray(ChunkPosition[]::new));
        List<ChunkPosition> requested = allRows.subList(0, 257);
        CountingSqliteSaveConnection connections = new CountingSqliteSaveConnection();

        ChunkStreamStats stats = adaptive(
                new VcdbsReader(null, null, new StubChunkParser(), null),
                connections,
                database,
                requested,
                new ReadDiagnostics(),
                ignored -> { }
        );

        assertEquals(257, stats.uniquePositionsRequested());
        assertEquals(257, stats.rowsFound());
        assertEquals(257, stats.parsedChunks());
        assertEquals(2, stats.batchesExecuted());
        assertEquals(1, connections.openCount());
    }

    @Test
    void adaptiveUsesTableStreamForSparseRequestExceedingTableCardinality()
            throws Exception {
        List<ChunkPosition> existing = spacedPositions(300);
        Path database = databaseWithRows(existing.toArray(ChunkPosition[]::new));
        CountingSqliteSaveConnection connections = new CountingSqliteSaveConnection();

        ChunkStreamStats stats = adaptive(
                new VcdbsReader(null, null, new StubChunkParser(), null),
                connections,
                database,
                spacedPositions(320),
                new ReadDiagnostics(),
                ignored -> { }
        );

        assertEquals(320, stats.uniquePositionsRequested());
        assertEquals(300, stats.rowsFound());
        assertEquals(300, stats.parsedChunks());
        assertEquals(0, stats.failedChunks());
        assertEquals(1, stats.batchesExecuted());
        assertEquals(1, connections.openCount());
    }

    @Test
    void adaptiveUsesTableStreamForSparseRequestEqualToTableCardinality()
            throws Exception {
        List<ChunkPosition> requested = spacedPositions(300);
        Path database = databaseWithRows(requested.toArray(ChunkPosition[]::new));

        ChunkStreamStats stats = adaptive(
                new VcdbsReader(null, null, new StubChunkParser(), null),
                new CountingSqliteSaveConnection(),
                database,
                requested,
                new ReadDiagnostics(),
                ignored -> { }
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

        ChunkStreamStats stats = adaptive(
                new VcdbsReader(null, null, new StubChunkParser(), null),
                connections,
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
    void directLookupPublishesCostBreakdownMetrics() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 0, 0);
        ChunkPosition second = new ChunkPosition(2, 0, 0, 0);
        Path database = databaseWithRows(first, second);

        VcdbsReader reader = new VcdbsReader(
                null,
                null,
                new StubChunkParser(),
                null
        );
        ChunkStreamStats stats = adaptive(
                reader,
                new SqliteSaveConnection(),
                                database,
                                List.of(first, second),
                                new ReadDiagnostics(),
                                ignored -> { }
        );

        ChunkReadMetrics metrics = reader.lastChunkReadMetrics().orElseThrow();
        assertEquals(ChunkReadStrategy.EXACT_POSITION_BATCHES, metrics.strategy());
        assertEquals(stats.uniquePositionsRequested(), metrics.uniquePositionsRequested());
        assertEquals(stats.batchesExecuted(), metrics.batchesExecuted());
        assertEquals(stats.rowsFound(), metrics.rowsFound());
        assertEquals(stats.parsedChunks(), metrics.parsedChunks());
        assertEquals(stats.payloadBytes(), metrics.payloadBytes());
        assertEquals(metrics, reader.lastChunkReadMetrics().orElseThrow());
        assertTrue(metrics.totalNanos() >= metrics.finalDrainNanos());
    }

    @Test
    void adaptiveTableStreamPublishesChosenStrategyMetrics() throws Exception {
        List<ChunkPosition> existing = spacedPositions(300);
        Path database = databaseWithRows(existing.toArray(ChunkPosition[]::new));
        VcdbsReader reader = new VcdbsReader(
                null,
                null,
                new StubChunkParser(),
                null
        );

        ChunkStreamStats stats = adaptive(
                reader,
                new SqliteSaveConnection(),
                                database,
                                spacedPositions(320),
                                new ReadDiagnostics(),
                                ignored -> { }
        );

        ChunkReadMetrics metrics = reader.lastChunkReadMetrics().orElseThrow();
        assertEquals(ChunkReadStrategy.TABLE_STREAM, metrics.strategy());
        assertEquals(320, metrics.uniquePositionsRequested());
        assertEquals(1, metrics.batchesExecuted());
        assertEquals(1, metrics.statementsExecuted());
        assertEquals(stats.rowsFound(), metrics.rowsFound());
    }

    @Test
    void adaptiveDenseRequestUsesPackedRangeRuns() throws Exception {
        List<ChunkPosition> requested = positions(1024);
        Path database = databaseWithRows(
                requested.toArray(ChunkPosition[]::new)
        );
        VcdbsReader reader = new VcdbsReader(
                null,
                null,
                new StubChunkParser(),
                null
        );

        ChunkStreamStats stats = adaptive(
                reader,
                new SqliteSaveConnection(),
                                database,
                                requested,
                                new ReadDiagnostics(),
                                ignored -> { }
        );

        assertEquals(1024, stats.rowsFound());
        assertEquals(1024, stats.parsedChunks());
        assertEquals(1, stats.batchesExecuted());
        assertEquals(
                ChunkReadStrategy.RANGE_RUN_BATCHES,
                reader.lastChunkReadMetrics().orElseThrow().strategy()
        );
        assertEquals(1, reader.lastChunkReadMetrics().orElseThrow().statementsPrepared());
        assertEquals(1, reader.lastChunkReadMetrics().orElseThrow().statementsExecuted());
    }

    @Test
    void packedRangeStrategyDoesNotReadGapRows() throws Exception {
        List<ChunkPosition> allRows = positions(512);
        List<ChunkPosition> requested = new ArrayList<>(allRows);
        requested.remove(256);
        Path database = databaseWithRows(
                allRows.toArray(ChunkPosition[]::new)
        );
        StubChunkParser parser = new StubChunkParser();
        VcdbsReader reader = new VcdbsReader(
                null,
                null,
                parser,
                null
        );

        ChunkStreamStats stats = adaptive(
                reader,
                new SqliteSaveConnection(),
                                database,
                                requested,
                                new ReadDiagnostics(),
                                ignored -> { }
        );

        assertEquals(511, stats.rowsFound());
        assertEquals(511, stats.parsedChunks());
        assertEquals(
                ChunkReadStrategy.RANGE_RUN_BATCHES,
                reader.lastChunkReadMetrics().orElseThrow().strategy()
        );
        assertTrue(parser.xCoordinates().stream().noneMatch(x -> x == 256));
    }

    @Test
    void explicitDirectLookupReusesFullBatchPreparedStatement()
            throws Exception {
        List<ChunkPosition> requested = positions(600);
        Path database = databaseWithRows(
                requested.toArray(ChunkPosition[]::new)
        );
        VcdbsReader reader = new VcdbsReader(
                null,
                null,
                new StubChunkParser(),
                null,
                new CountingSqliteSaveConnection()
        );

        ChunkStreamStats stats = direct(
                reader,
                database,
                requested,
                new ReadDiagnostics(),
                ignored -> { },
                ProgressReporter.NONE
        );

        ChunkReadMetrics metrics = reader.lastChunkReadMetrics().orElseThrow();
        assertEquals(3, stats.batchesExecuted());
        assertEquals(2, metrics.statementsPrepared());
        assertEquals(3, metrics.statementsExecuted());
        assertEquals(ChunkReadStrategy.EXACT_POSITION_BATCHES, metrics.strategy());
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
        ChunkStreamStats table = tableStream(
                new VcdbsReader(null, null, tableParser, null),
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

        tableStream(
                new VcdbsReader(null, null, parser, null),
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

        ChunkStreamStats stats = tableStream(
                new VcdbsReader(null, null, new StubChunkParser(), null),
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

        ChunkStreamStats stats = tableStream(
                new VcdbsReader(null, null, new StubChunkParser(), null),
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
    @ConcurrencyTest
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
                tableStream(
                        reader,
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

        try {
            awaitLatch(parser.bothStarted, "both workers started");
            parser.release.countDown();
            joinThread(caller, "caller");
        } finally {
            parser.release.countDown();
            joinThread(caller, "caller");
        }

        assertNull(failure.get());
        assertEquals(callerThread.get(), consumerThread.get());
        assertEquals(2, parser.workerThreads.size());
        assertTrue(parser.workerThreads.stream().noneMatch(Thread::isVirtual));
    }

    @Test
    @ConcurrencyTest
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
                direct(
                        reader,
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

        try {
            awaitLatch(parser.bothStarted, "both workers started");
            parser.release.countDown();
            joinThread(caller, "caller");
        } finally {
            parser.release.countDown();
            joinThread(caller, "caller");
        }

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

        ChunkStreamStats stats = direct(
                new VcdbsReader(null, null, new StubChunkParser(), null),
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
        return direct(
                new VcdbsReader(null, null, parser, null),
                database,
                positions,
                new ReadDiagnostics(),
                parser.delivered()::add,
                ProgressReporter.NONE
        );
    }

    private ChunkStreamStats adaptive(
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
                snapshot(database)
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

    private ChunkStreamStats direct(
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

    private ChunkStreamStats tableStream(
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

    private SaveSession openSession(Path database) {
        return new SaveSession(
                database,
                new SqliteSaveConnection().openReadOnly(database),
                snapshot(database)
        );
    }

    private SaveSession emptySession(Path database) {
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> null
        );
        return new SaveSession(database, connection, snapshot(database));
    }

    private SaveSnapshot snapshot(Path database) {
        return new SaveSnapshot(
                database,
                new WorldMetadata(1, 1, 1),
                Map.of()
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

    private List<ChunkPosition> spacedPositions(int count) {
        List<ChunkPosition> positions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            positions.add(new ChunkPosition(index * 2, 0, 0, 0));
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

    private static void awaitLatch(CountDownLatch latch, String description)
            throws InterruptedException {
        assertTrue(
                latch.await(TEST_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                description + " was not signalled"
        );
    }

    private static void joinThread(Thread thread, String description)
            throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(TEST_DEADLOCK_TIMEOUT_SECONDS));
        assertTrue(!thread.isAlive(), description + " did not terminate");
    }

    private static final class StubChunkParser extends ChunkParser {
        private final List<ChunkCoordinate> coordinates =
                Collections.synchronizedList(new ArrayList<>());
        private final List<ParsedChunk> delivered =
                Collections.synchronizedList(new ArrayList<>());
        private byte[] failurePayload;
        private final AtomicInteger surfaceCompactCalls =
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

        private int surfaceCompactCalls() {
            return surfaceCompactCalls.get();
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
