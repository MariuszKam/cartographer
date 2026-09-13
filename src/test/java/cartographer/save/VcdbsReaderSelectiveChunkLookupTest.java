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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertEquals(1, parser.parsePayloadCalls);
        assertEquals(1, parser.paletteProbeCalls);
        assertEquals(0, parser.parseServerChunkCalls);
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

        assertEquals(1, parser.parsePayloadCalls);
        assertEquals(1, parser.parseServerChunkCalls);
        assertEquals(ChunkDecodeProfile.BLOCKS_ONLY, parser.lastProfile);
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
        assertEquals(1, parser.parseServerChunkCalls);
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
        assertEquals(0, parser.parseServerChunkCalls);
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
        assertEquals(0, parser.parsePayloadCalls);
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
        private int parsePayloadCalls;
        private int paletteProbeCalls;
        private int parseServerChunkCalls;
        private ChunkDecodeProfile lastProfile;
        private ParseResult<ServerChunkPayload> payloadResult =
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
        private final List<ParsedChunk> delivered = new ArrayList<>();

        @Override
        public ParseResult<ServerChunkPayload> parsePayload(byte[] payload) {
            parsePayloadCalls++;
            return payloadResult;
        }

        @Override
        public ParseResult<ChunkPaletteProbe> probeBlockPalette(
                ServerChunkPayload serverChunk
        ) {
            paletteProbeCalls++;
            return paletteResult;
        }

        @Override
        public ParseResult<ParsedChunk> parse(
                ChunkCoordinate coordinate,
                ServerChunkPayload serverChunk,
                ChunkDecodeProfile profile
        ) {
            parseServerChunkCalls++;
            lastProfile = profile;
            return fullDecodeResult;
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
