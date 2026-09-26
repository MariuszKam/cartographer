package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsChunkLookupEdgeCaseTest extends VcdbsReaderDirectChunkLookupTestSupport {

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
                VcdbsReaderFixtures.withChunkParser(new StubChunkParser()),
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
}
