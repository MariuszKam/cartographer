package cartographer.save;

import cartographer.cli.ProgressReporter;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerChunkPayload;
import cartographer.parser.ChunkDecodeProfile;
import cartographer.parser.ChunkPaletteProbe;
import cartographer.parser.ChunkParser;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VcdbsReaderSelectiveChunkLookupTest {

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

        assertEquals(1, parser.parsePayloadCalls.get());
        assertEquals(1, parser.paletteProbeCalls.get());
        assertEquals(0, parser.parseServerChunkCalls.get());
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

        assertEquals(1, parser.parsePayloadCalls.get());
        assertEquals(1, parser.parseServerChunkCalls.get());
        assertEquals(ChunkDecodeProfile.BLOCKS_ONLY, parser.lastProfile.get());
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
        assertEquals(1, parser.parseServerChunkCalls.get());
    }

    @Test
    void recordsFullDecodeFailure() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(99);
        parser.fullDecodeResult = ParseResult.failure("decode failed");

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
    void recordsPaletteProbeFailure() throws Exception {
        ChunkPosition position = new ChunkPosition(1, 0, 2, 0);
        Path database = databaseWithRow(position, new byte[]{7});
        RecordingChunkParser parser = parserWithPalette(99);
        parser.paletteResult = ParseResult.failure("malformed palette");

        SelectiveChunkStreamStats stats = read(
                database,
                parser,
                List.of(position),
                new int[]{99}
        );

        assertEquals(1, stats.payloadsParsed());
        assertEquals(1, stats.failedChunks());
        assertEquals(0, parser.parseServerChunkCalls.get());
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
        assertEquals(0, parser.parsePayloadCalls.get());
    }

    @Test
    void missingTableCompletesProgressLifecycle() throws Exception {
        Path database = temporaryDirectory.resolve("missing-table.vcdbs");
        createDatabase(database, "");
        RecordingProgressReporter progress = new RecordingProgressReporter();
        ReadDiagnostics diagnostics = new ReadDiagnostics();

        SelectiveChunkStreamStats stats = new VcdbsReader(
                null,
                null,
                parserWithPalette(99),
                null
        ).forEachChunkByPositionMatchingBlockIds(
                database,
                List.of(new ChunkPosition(1, 0, 2, 0)),
                new int[]{99},
                diagnostics,
                chunk -> {
                },
                progress
        );

        assertEquals(List.of("start", "done"), progress.events);
        assertEquals(1, stats.uniquePositionsRequested());
        assertTrue(diagnostics.notes().contains("missing table: chunk"));
    }

    @Test
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
        VcdbsReader reader = new VcdbsReader(
                null, null, parser, null, new SqliteSaveConnection(), 2, 4
        );
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        AtomicReference<Thread> consumerThread = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            callerThread.set(Thread.currentThread());
            try {
                reader.forEachChunkByPositionMatchingBlockIds(
                        database,
                        List.of(first, second),
                        new int[]{99},
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
    }

    @Test
    void emptyPositionsReturnZeroStats() {
        SelectiveChunkStreamStats stats = new VcdbsReader(
                null,
                null,
                parserWithPalette(99),
                null
        ).forEachChunkByPositionMatchingBlockIds(
                temporaryDirectory.resolve("does-not-exist.vcdbs"),
                List.of(),
                new int[]{99},
                new ReadDiagnostics(),
                chunk -> {
                }
        );

        assertEquals(
                new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0),
                stats
        );
    }

    @Test
    void selectiveAdaptiveCanChooseTableStreamAndPreservesBlocksOnly() throws Exception {
        Path database = databaseWithRows(300);
        RecordingChunkParser parser = parserWithPalette(99);

        SelectiveChunkStreamStats stats = new VcdbsReader(
                null, null, parser, null
        ).forEachChunkByPositionMatchingBlockIdsAdaptive(
                database,
                positions(320),
                new int[]{99},
                new ReadDiagnostics(),
                parser.delivered::add
        );

        assertEquals(320, stats.uniquePositionsRequested());
        assertEquals(300, stats.rowsFound());
        assertEquals(300, stats.payloadsParsed());
        assertEquals(300, stats.fullyDecodedChunks());
        assertEquals(0, stats.failedChunks());
        assertEquals(1, stats.batchesExecuted());
        assertEquals(ChunkDecodeProfile.BLOCKS_ONLY, parser.lastProfile.get());
    }

    @Test
    void selectiveAdaptiveRejectsEmptyWantedIdsBeforeDatabaseAccess() {
        CountingSqliteSaveConnection connections = new CountingSqliteSaveConnection();

        assertThrows(
                IllegalArgumentException.class,
                () -> new VcdbsReader(
                        null, null, parserWithPalette(99), null, connections
                ).forEachChunkByPositionMatchingBlockIdsAdaptive(
                        temporaryDirectory.resolve("missing.vcdbs"),
                        List.of(new ChunkPosition(1, 0, 2, 0)),
                        new int[0],
                        new ReadDiagnostics(),
                        ignored -> { }
                )
        );
        assertEquals(0, connections.openCount());
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
        SelectiveChunkStreamStats table = new VcdbsReader(
                null, null, tableParser, null
        ).forEachChunkByPositionMatchingBlockIdsTableStream(
                database,
                List.of(requested),
                new int[]{99},
                new ReadDiagnostics(),
                tableParser.delivered::add,
                new ProgressReporter(null)
        );

        assertEquals(direct.uniquePositionsRequested(), table.uniquePositionsRequested());
        assertEquals(direct.rowsFound(), table.rowsFound());
        assertEquals(direct.payloadsParsed(), table.payloadsParsed());
        assertEquals(direct.paletteRejectedChunks(), table.paletteRejectedChunks());
        assertEquals(direct.fullyDecodedChunks(), table.fullyDecodedChunks());
        assertEquals(direct.failedChunks(), table.failedChunks());
        assertEquals(direct.payloadBytes(), table.payloadBytes());
        assertEquals(1, tableParser.parsePayloadCalls.get());
        assertEquals(ChunkDecodeProfile.BLOCKS_ONLY, tableParser.lastProfile.get());
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

        new VcdbsReader(null, null, parser, null)
                .forEachChunkByPositionMatchingBlockIdsTableStream(
                        database,
                        List.of(requested),
                        new int[]{99},
                        new ReadDiagnostics(),
                        parser.delivered::add,
                        new ProgressReporter(null)
                );

        assertEquals(1, parser.parsePayloadCalls.get());
        assertEquals(1, parser.paletteProbeCalls.get());
        assertEquals(1, parser.parseServerChunkCalls.get());
    }

    private SelectiveChunkStreamStats read(
            Path database,
            RecordingChunkParser parser,
            List<ChunkPosition> positions,
            int[] wantedBlockIds
    ) {
        return new VcdbsReader(
                null,
                null,
                parser,
                null
        ).forEachChunkByPositionMatchingBlockIds(
                database,
                positions,
                wantedBlockIds,
                new ReadDiagnostics(),
                parser.delivered::add
        );
    }

    private RecordingChunkParser parserWithPalette(int... blockIds) {
        RecordingChunkParser parser = new RecordingChunkParser();
        parser.paletteResult = ParseResult.success(new ChunkPaletteProbe(blockIds));
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

    private Path databaseWithRows(int count) throws Exception {
        Path database = databaseWithRow(
                new ChunkPosition(0, 0, 0, 0), new byte[]{7}
        );
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO chunk(position, data) VALUES (?, ?)")) {
            for (int index = 1; index < count; index++) {
                statement.setLong(
                        1,
                        ChunkPosEncoder.encode(new ChunkPosition(index, 0, 0, 0))
                );
                statement.setBytes(2, new byte[]{7});
                statement.executeUpdate();
            }
        }
        return database;
    }

    private List<ChunkPosition> positions(int count) {
        List<ChunkPosition> positions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
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

    private static final class RecordingChunkParser extends ChunkParser {
        private final AtomicInteger parsePayloadCalls = new AtomicInteger();
        private final AtomicInteger paletteProbeCalls = new AtomicInteger();
        private final AtomicInteger parseServerChunkCalls = new AtomicInteger();
        private final AtomicReference<ChunkDecodeProfile> lastProfile =
                new AtomicReference<>();
        private final ParseResult<ServerChunkPayload> payloadResult =
                ParseResult.success(new ServerChunkPayload(new byte[]{1}, new byte[0], 2));
        private ParseResult<ChunkPaletteProbe> paletteResult;
        private ParseResult<ParsedChunk> fullDecodeResult =
                ParseResult.success(new ParsedChunk(
                        new ChunkCoordinate(1, 0, 2),
                        0,
                        1,
                        1,
                        1,
                        new int[]{1}
                ));
        private final List<ParsedChunk> delivered =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public ParseResult<ServerChunkPayload> parsePayload(byte[] payload) {
            parsePayloadCalls.incrementAndGet();
            return payloadResult;
        }

        @Override
        public ParseResult<ChunkPaletteProbe> probeBlockPalette(
                ServerChunkPayload serverChunk
        ) {
            paletteProbeCalls.incrementAndGet();
            return paletteResult;
        }

        @Override
        public ParseResult<ParsedChunk> parse(
                ChunkCoordinate coordinate,
                ServerChunkPayload serverChunk,
                ChunkDecodeProfile profile
        ) {
            parseServerChunkCalls.incrementAndGet();
            lastProfile.set(profile);
            return fullDecodeResult;
        }
    }

    private static final class BlockingSelectiveParser extends ChunkParser {
        private final CountDownLatch bothStarted = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Thread> workerThreads =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public ParseResult<ServerChunkPayload> parsePayload(byte[] payload) {
            workerThreads.add(Thread.currentThread());
            bothStarted.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return ParseResult.failure("interrupted");
            }
            return ParseResult.success(new ServerChunkPayload(
                    new byte[]{1}, new byte[0], 2
            ));
        }

        @Override
        public ParseResult<ChunkPaletteProbe> probeBlockPalette(
                ServerChunkPayload serverChunk
        ) {
            return ParseResult.success(new ChunkPaletteProbe(new int[]{99}));
        }

        @Override
        public ParseResult<ParsedChunk> parse(
                ChunkCoordinate coordinate,
                ServerChunkPayload serverChunk,
                ChunkDecodeProfile profile
        ) {
            return ParseResult.success(new ParsedChunk(
                    coordinate, coordinate.y(), 1, 1, 1, new int[]{1}
            ));
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

    private static final class RecordingProgressReporter extends ProgressReporter {
        private final List<String> events = new ArrayList<>();

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
        }
    }
}
