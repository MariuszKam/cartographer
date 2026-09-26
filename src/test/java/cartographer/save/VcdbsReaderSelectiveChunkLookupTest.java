package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.testing.ConcurrencyTest;
import cartographer.progress.ProgressReporter;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.parser.ChunkDecodeWorkspace;
import cartographer.parser.ChunkParser;
import cartographer.parser.SelectiveChunkParseResult;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsReaderSelectiveChunkLookupTest {
    private static final long TEST_DEADLOCK_TIMEOUT_SECONDS = 10;

    @TempDir
    Path temporaryDirectory;

    @Test
    void paletteMissParsesPayloadOnceAndSkipsFullDecode() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(1, 2, 3);

        SelectiveChunkStreamStats stats = read(
                database,
                parser,
                List.of(position),
                new int[]{99}
        );

        assertEquals(1, parser.selectiveCalls.get());
        assertEquals(1, stats.payloadsParsed());
        assertEquals(1, stats.paletteRejectedChunks());
        assertEquals(0, stats.fullyDecodedChunks());
        assertTrue(parser.delivered.isEmpty());
    }

    @Test
    void paletteHitUsesBlocksOnlyAndDeliversChunk() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(99);

        SelectiveChunkStreamStats stats = read(
                database,
                parser,
                List.of(position),
                new int[]{99}
        );

        assertEquals(1, parser.selectiveCalls.get());
        assertEquals(1, stats.fullyDecodedChunks());
        assertEquals(1, parser.delivered.size());
    }

    @Test
    void multipleWantedIdsAreDeduplicatedAndMatched() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(20);

        SelectiveChunkStreamStats stats = read(
                database,
                parser,
                List.of(position),
                new int[]{10, 10, 20, 20}
        );

        assertEquals(1, stats.fullyDecodedChunks());
        assertEquals(1, parser.selectiveCalls.get());
    }

    @Test
    void recordsFullDecodeFailure() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(99);
        parser.decodeFailure = "decode failed";

        SelectiveChunkStreamStats stats = read(
                database,
                parser,
                List.of(position),
                new int[]{99}
        );

        assertEquals(1, stats.payloadsParsed());
        assertEquals(1, stats.failedChunks());
        assertEquals(0, stats.fullyDecodedChunks());
        assertTrue(parser.delivered.isEmpty());
    }

    @Test
    void recordsPayloadParseFailure() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(99);
        parser.payloadFailure = "malformed payload";

        SelectiveChunkStreamStats stats = read(
                database,
                parser,
                List.of(position),
                new int[]{99}
        );

        assertEquals(0, stats.payloadsParsed());
        assertEquals(1, stats.failedChunks());
        assertTrue(parser.delivered.isEmpty());
    }

    @Test
    void nullPayloadIsSkippedWithoutParsing() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithNullRow(position);
        RecordingChunkParser parser = parserWithPalette(99);

        SelectiveChunkStreamStats stats = read(
                database,
                parser,
                List.of(position),
                new int[]{99}
        );

        assertEquals(1, stats.rowsFound());
        assertEquals(0, stats.payloadBytes());
        assertEquals(0, stats.payloadsParsed());
        assertEquals(0, parser.selectiveCalls.get());
    }

    @Test
    void missingTableCompletesProgressLifecycle() throws Exception {
        Path database = temporaryDirectory.resolve("missing-table.vcdbs");
        createDatabase(database, "");
        RecordingProgressReporter progress = new RecordingProgressReporter();
        ReadDiagnostics diagnostics = new ReadDiagnostics();

        SelectiveChunkStreamStats stats = direct(
                VcdbsReaderFixtures.withChunkParser(parserWithPalette(99)),
                database,
                List.of(new ChunkPosition(1, 0, 2, 0)),
                new int[]{99},
                diagnostics,
                chunk -> { },
                progress
        );

        assertEquals(List.of("start", "done"), progress.events);
        assertEquals(1, stats.uniquePositionsRequested());
        assertTrue(diagnostics.notes().contains("missing table: chunk"));
    }

    @Test
    @ConcurrencyTest
    void selectiveDecodeWorkRunsConcurrently() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition second = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRow(first, new byte[]{7});
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO chunk(position, data) VALUES (?, ?)")) {
            statement.setLong(1, ChunkPosEncoder.encode(second));
            statement.setBytes(2, new byte[]{7});
            statement.executeUpdate();
        }

        BlockingSelectiveParser parser = new BlockingSelectiveParser();
        VcdbsReader reader = VcdbsReaderFixtures.withTwoDecodeWorkers(parser);
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
                        new int[]{99},
                        new ReadDiagnostics(),
                        ignored -> consumerThread.set(Thread.currentThread()),
                        ProgressReporter.NONE
                );
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });

        try {
            awaitBothWorkers(parser.bothStarted);
            parser.release.countDown();
            joinCaller(caller);
        } finally {
            parser.release.countDown();
            joinCaller(caller);
        }

        assertNull(failure.get());
        assertEquals(callerThread.get(), consumerThread.get());
        assertEquals(2, parser.workerThreads.size());
    }

    @Test
    void coverageVisitsAreKeyedByPositionRatherThanCallbackOrder() throws Exception {
        ChunkPosition existing = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition missing = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRow(existing, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(99);
        List<SelectiveChunkVisit> visits = new ArrayList<>();

        SelectiveChunkStreamStats stats = coverage(
                VcdbsReaderFixtures.withChunkParser(parser),
                database,
                List.of(missing, existing),
                new int[]{99},
                new ReadDiagnostics(),
                visits::add
        );

        Map<ChunkPosition, SelectiveChunkVisitStatus> statuses = new HashMap<>();
        for (SelectiveChunkVisit visit : visits) {
            statuses.put(visit.position(), visit.status());
        }
        assertEquals(2, stats.uniquePositionsRequested());
        assertEquals(1, stats.rowsFound());
        assertEquals(
                Map.of(
                        existing, SelectiveChunkVisitStatus.DECODED,
                        missing, SelectiveChunkVisitStatus.MISSING
                ),
                statuses
        );
    }

    @Test
    void coverageDeduplicatesRequestedPositionsAndEmitsOneTerminalVisit()
            throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(99);
        List<SelectiveChunkVisit> visits = new ArrayList<>();

        SelectiveChunkStreamStats stats = coverage(
                VcdbsReaderFixtures.withChunkParser(parser),
                database,
                List.of(position, position),
                new int[]{99},
                new ReadDiagnostics(),
                visits::add
        );

        assertEquals(1, stats.uniquePositionsRequested());
        assertEquals(1, visits.size());
        assertEquals(position, visits.get(0).position());
        assertEquals(SelectiveChunkVisitStatus.DECODED, visits.get(0).status());
    }

    @Test
    void coverageNullPayloadProducesTerminalFailedVisit() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithNullRow(position);
        List<SelectiveChunkVisit> visits = new ArrayList<>();
        ReadDiagnostics diagnostics = new ReadDiagnostics();

        coverage(
                VcdbsReaderFixtures.withChunkParser(parserWithPalette(99)),
                database,
                List.of(position),
                new int[]{99},
                diagnostics,
                visits::add
        );

        assertEquals(1, visits.size());
        assertEquals(SelectiveChunkVisitStatus.FAILED, visits.get(0).status());
        assertEquals("chunk row has null payload", visits.get(0).error());
        assertEquals(1, diagnostics.skipped());
        assertEquals(0, diagnostics.failed());
        assertTrue(diagnostics.skippedNotes().contains(
                "skipped: 1 x chunk row has null payload"
        ));
    }

    @Test
    void coveragePaletteRejectionProducesOneTerminalVisit() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(1, 2, 3);
        List<SelectiveChunkVisit> visits = new ArrayList<>();

        coverage(
                VcdbsReaderFixtures.withChunkParser(parser),
                database,
                List.of(position),
                new int[]{99},
                new ReadDiagnostics(),
                visits::add
        );

        assertEquals(1, visits.size());
        assertEquals(SelectiveChunkVisitStatus.PALETTE_REJECTED,
                visits.get(0).status());
    }

    @Test
    void coverageSelectiveDecodeFailureProducesTerminalFailedVisit() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(99);
        parser.decodeFailure = "malformed palette";
        List<SelectiveChunkVisit> visits = new ArrayList<>();

        coverage(
                VcdbsReaderFixtures.withChunkParser(parser),
                database,
                List.of(position),
                new int[]{99},
                new ReadDiagnostics(),
                visits::add
        );

        assertEquals(1, visits.size());
        assertEquals(SelectiveChunkVisitStatus.FAILED, visits.get(0).status());
        assertEquals("malformed palette", visits.get(0).error());
    }

    @Test
    void coverageMultipleFullDecodeFailuresProduceTerminalFailedVisits()
            throws Exception {
        ChunkPosition decodePosition = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition palettePosition = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRow(decodePosition, new byte[]{7});
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO chunk(position, data) VALUES (?, ?)")) {
            statement.setLong(1, ChunkPosEncoder.encode(palettePosition));
            statement.setBytes(2, new byte[]{7});
            statement.executeUpdate();
        }
        RecordingChunkParser parser = parserWithPalette(99);
        parser.decodeFailure = "decode failed";
        List<SelectiveChunkVisit> visits = new ArrayList<>();

        coverage(
                VcdbsReaderFixtures.withChunkParser(parser),
                database,
                List.of(decodePosition, palettePosition),
                new int[]{99},
                new ReadDiagnostics(),
                visits::add
        );

        assertEquals(2, visits.size());
        assertTrue(visits.stream().allMatch(visit ->
                visit.status() == SelectiveChunkVisitStatus.FAILED
        ));
    }

    @Test
    void missingChunkTableCompletesCoverageWithZeroPositionVisits()
            throws Exception {
        Path database = temporaryDirectory.resolve("missing-coverage-table.vcdbs");
        createDatabase(database, "");
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        List<SelectiveChunkVisit> visits = new ArrayList<>();
        ReadDiagnostics diagnostics = new ReadDiagnostics();

        SelectiveChunkStreamStats stats = coverage(
                VcdbsReaderFixtures.withChunkParser(parserWithPalette(99)),
                database,
                List.of(position),
                new int[]{99},
                diagnostics,
                visits::add
        );

        assertTrue(visits.isEmpty());
        assertEquals(1, stats.uniquePositionsRequested());
        assertEquals(0, stats.rowsFound());
        assertEquals(0, stats.failedChunks());
        assertTrue(diagnostics.notes().contains("missing table: chunk"));
    }

    @Test
    void emptyPositionsReturnZeroStats() {
        SelectiveChunkStreamStats stats = direct(
                VcdbsReaderFixtures.withChunkParser(parserWithPalette(99)),
                temporaryDirectory.resolve("does-not-exist.vcdbs"),
                List.of(),
                new int[]{99},
                new ReadDiagnostics(),
                chunk -> { },
                ProgressReporter.NONE
        );

        assertEquals(
                new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0),
                stats
        );
    }

    @Test
    void selectiveAdaptiveCanChooseTableStreamAndPreservesBlocksOnly() throws Exception {
        Path database = databaseWithRows();
        RecordingChunkParser parser = parserWithPalette(99);

        SelectiveChunkStreamStats stats = adaptive(
                VcdbsReaderFixtures.withChunkParser(parser),
                database,
                positions(),
                new int[]{99},
                new ReadDiagnostics(),
                parser.delivered::add);

        assertEquals(320, stats.uniquePositionsRequested());
        assertEquals(300, stats.rowsFound());
        assertEquals(300, stats.payloadsParsed());
        assertEquals(300, stats.fullyDecodedChunks());
        assertEquals(0, stats.failedChunks());
        assertEquals(1, stats.batchesExecuted());
        assertEquals(300, parser.selectiveCalls.get());
    }

    @Test
    void selectiveAdaptiveRejectsEmptyWantedIdsBeforeDatabaseAccess() {
        assertThrows(
                IllegalArgumentException.class,
                () -> adaptive(
                        VcdbsReaderFixtures.withChunkParser(parserWithPalette(99)),
                        temporaryDirectory.resolve("missing.vcdbs"),
                        List.of(new ChunkPosition(1, 0, 2, 0)),
                        new int[0],
                        new ReadDiagnostics(),
                        ignored -> { })
        );
    }

    @Test
    void selectiveTableStreamMatchesDirectLookupSemantics() throws Exception {
        ChunkPosition requested = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition unrequested = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRow(requested, new byte[]{7});
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO chunk(position, data) VALUES (?, ?)")) {
            statement.setLong(1, ChunkPosEncoder.encode(unrequested));
            statement.setBytes(2, new byte[]{7});
            statement.executeUpdate();
        }
        RecordingChunkParser directParser = parserWithPalette(99);
        RecordingChunkParser tableParser = parserWithPalette(99);

        SelectiveChunkStreamStats direct = read(
                database, directParser, List.of(requested), new int[]{99}
        );
        SelectiveChunkStreamStats table = tableStream(
                VcdbsReaderFixtures.withChunkParser(tableParser),
                database,
                List.of(requested),
                new int[]{99},
                new ReadDiagnostics(),
                tableParser.delivered::add);

        assertEquals(direct.uniquePositionsRequested(), table.uniquePositionsRequested());
        assertEquals(direct.rowsFound(), table.rowsFound());
        assertEquals(direct.payloadsParsed(), table.payloadsParsed());
        assertEquals(direct.paletteRejectedChunks(), table.paletteRejectedChunks());
        assertEquals(direct.fullyDecodedChunks(), table.fullyDecodedChunks());
        assertEquals(direct.failedChunks(), table.failedChunks());
        assertEquals(direct.payloadBytes(), table.payloadBytes());
        assertEquals(1, tableParser.selectiveCalls.get());
    }

    @Test
    void selectiveTableStreamDoesNotInspectUnrequestedPayload() throws Exception {
        ChunkPosition requested = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition unrequested = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRow(requested, new byte[]{7});
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO chunk(position, data) VALUES (?, ?)")) {
            statement.setLong(1, ChunkPosEncoder.encode(unrequested));
            statement.setBytes(2, new byte[]{7});
            statement.executeUpdate();
        }
        RecordingChunkParser parser = parserWithPalette(99);

        tableStream(
                VcdbsReaderFixtures.withChunkParser(parser),
                database,
                List.of(requested),
                new int[]{99},
                new ReadDiagnostics(),
                parser.delivered::add);

        assertEquals(1, parser.selectiveCalls.get());
    }

    private SelectiveChunkStreamStats read(
            Path database,
            RecordingChunkParser parser,
            List<ChunkPosition> positions,
            int[] wantedBlockIds
    ) {
        return direct(
                VcdbsReaderFixtures.withChunkParser(parser),
                database,
                positions,
                wantedBlockIds,
                new ReadDiagnostics(),
                parser.delivered::add,
                ProgressReporter.NONE
        );
    }

    private SelectiveChunkStreamStats direct(
            VcdbsReader reader,
            Path database,
            List<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            java.util.function.Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        try (SaveSession session = testSession(database, positions, wantedBlockIds)) {
            return reader.forEachChunkByPositionMatchingBlockIds(
                    session, positions, wantedBlockIds, diagnostics, consumer, progress
            );
        }
    }

    private SelectiveChunkStreamStats adaptive(
            VcdbsReader reader,
            Path database,
            List<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            java.util.function.Consumer<ParsedChunk> consumer
    ) {
        try (SaveSession session = testSession(database, positions, wantedBlockIds)) {
            return reader.forEachChunkByPositionMatchingBlockIdsAdaptive(
                    session,
                    positions,
                    wantedBlockIds,
                    diagnostics,
                    consumer,
                    ProgressReporter.NONE
            );
        }
    }

    private SelectiveChunkStreamStats coverage(
            VcdbsReader reader,
            Path database,
            List<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            java.util.function.Consumer<SelectiveChunkVisit> consumer
    ) {
        try (SaveSession session = openSession(database)) {
            return reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    session, positions, wantedBlockIds, diagnostics, consumer
            );
        }
    }

    private SelectiveChunkStreamStats tableStream(
            VcdbsReader reader,
            Path database,
            List<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            java.util.function.Consumer<ParsedChunk> consumer
    ) {
        try (SaveSession session = openSession(database)) {
            return reader.forEachChunkByPositionMatchingBlockIdsTableStream(
                    session,
                    positions,
                    wantedBlockIds,
                    diagnostics,
                    consumer,
                    ProgressReporter.NONE
            );
        }
    }

    private SaveSession testSession(
            Path database,
            List<ChunkPosition> positions,
            int[] wantedBlockIds
    ) {
        if (positions.isEmpty() || wantedBlockIds.length == 0) {
            return emptySession(database);
        }
        return openSession(database);
    }

    private SaveSession openSession(Path database) {
        return new SaveSession(
                database,
                new SqliteSaveConnection().openReadOnly(database),
                snapshot()
        );
    }

    private SaveSession emptySession(Path database) {
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new AssertionError("database connection must not be used");
                }
        );
        return new SaveSession(database, connection, snapshot());
    }

    private SaveSnapshot snapshot() {
        return new SaveSnapshot(new WorldMetadata(1, 1, 1), Map.of());
    }

    private RecordingChunkParser parserWithPalette(int... blockIds) {
        RecordingChunkParser parser = new RecordingChunkParser();
        parser.paletteBlockIds = blockIds.clone();
        return parser;
    }

    private Path databaseWithRow(
            ChunkPosition position,
            byte[] payload
    ) throws Exception {
        Path database = temporaryDirectory.resolve(
                "save-" + System.nanoTime() + ".vcdbs"
        );
        createDatabase(
                database,
                "CREATE TABLE chunk (position INTEGER PRIMARY KEY, data BLOB)"
        );
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO chunk(position, data) VALUES (?, ?)")) {
            statement.setLong(1, cartographer.save.ChunkPosEncoder.encode(position));
            statement.setBytes(2, payload);
            statement.executeUpdate();
        }
        return database;
    }

    private Path databaseWithRows() throws Exception {
        Path database = temporaryDirectory.resolve(
                "save-" + System.nanoTime() + ".vcdbs"
        );
        createDatabase(
                database,
                "CREATE TABLE chunk (position INTEGER PRIMARY KEY, data BLOB)"
        );
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO chunk(position, data) VALUES (?, ?)")) {
            connection.setAutoCommit(false);
            try {
                for (int index = 0; index < 300; index++) {
                    statement.setLong(
                            1,
                            ChunkPosEncoder.encode(
                                    new ChunkPosition(index, 0, 0, 0)
                            )
                    );
                    statement.setBytes(2, new byte[]{7});
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

    private List<ChunkPosition> positions() {
        List<ChunkPosition> positions = new ArrayList<>();
        for (int index = 0; index < 320; index++) {
            positions.add(new ChunkPosition(index, 0, 0, 0));
        }
        return positions;
    }

    private Path databaseWithNullRow(ChunkPosition position) throws Exception {
        return databaseWithRow(position, null);
    }

    private void createDatabase(Path database, String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            if (!schema.isBlank()) {
                statement.execute(schema);
            }
        }
    }

    private static void awaitBothWorkers(CountDownLatch latch)
            throws InterruptedException {
        assertTrue(
                latch.await(TEST_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "both workers started was not signalled"
        );
    }

    private static void joinCaller(Thread thread)
            throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(TEST_DEADLOCK_TIMEOUT_SECONDS));
        assertTrue(!thread.isAlive(), "caller did not terminate");
    }

    private static final class RecordingChunkParser extends ChunkParser {
        private final AtomicInteger selectiveCalls = new AtomicInteger();
        private int[] paletteBlockIds = new int[0];
        private String payloadFailure;
        private String decodeFailure;
        private final List<ParsedChunk> delivered =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public SelectiveChunkParseResult parseBlocksIfPaletteContains(
                ChunkCoordinate coordinate,
                byte[] payload,
                int[] wantedBlockIds,
                ChunkDecodeWorkspace workspace
        ) {
            selectiveCalls.incrementAndGet();
            if (payloadFailure != null) {
                return SelectiveChunkParseResult.payloadFailure(payloadFailure);
            }
            if (decodeFailure != null) {
                return SelectiveChunkParseResult.decodeFailure(decodeFailure);
            }

            boolean wanted = false;
            for (int wantedBlockId : wantedBlockIds) {
                for (int paletteBlockId : paletteBlockIds) {
                    if (wantedBlockId == paletteBlockId) {
                        wanted = true;
                        break;
                    }
                }
                if (wanted) {
                    break;
                }
            }
            if (!wanted) {
                return SelectiveChunkParseResult.rejected();
            }

            return SelectiveChunkParseResult.decoded(
                    cartographer.model.ParsedChunkFixtures.create(
                            coordinate,
                            coordinate.y(),
                            1,
                            1,
                            1,
                            new int[]{1}
                    )
            );
        }
    }

    private static final class BlockingSelectiveParser extends ChunkParser {
        private final CountDownLatch bothStarted = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Thread> workerThreads =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public SelectiveChunkParseResult parseBlocksIfPaletteContains(
                ChunkCoordinate coordinate,
                byte[] payload,
                int[] wantedBlockIds,
                ChunkDecodeWorkspace workspace
        ) {
            workerThreads.add(Thread.currentThread());
            bothStarted.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return SelectiveChunkParseResult.payloadFailure("interrupted");
            }
            return SelectiveChunkParseResult.decoded(
                    cartographer.model.ParsedChunkFixtures.create(
                            coordinate,
                            coordinate.y(),
                            1,
                            1,
                            1,
                            new int[]{1}
                    )
            );
        }
    }

    private static final class RecordingProgressReporter implements ProgressReporter {
        private final List<String> events = new ArrayList<>();

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
