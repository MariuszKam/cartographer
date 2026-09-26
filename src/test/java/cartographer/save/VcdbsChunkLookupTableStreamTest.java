package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.model.ChunkPosition;
import cartographer.progress.ProgressReporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsChunkLookupTableStreamTest extends VcdbsReaderDirectChunkLookupTestSupport {

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
    void explicitDirectLookupReusesFullBatchPreparedStatement()
            throws Exception {
        List<ChunkPosition> requested = positions(600);
        Path database = databaseWithRows(
                requested.toArray(ChunkPosition[]::new)
        );
        VcdbsReader reader = VcdbsReaderFixtures.withChunkParser(new StubChunkParser());

        ChunkStreamStats stats = direct(
                reader,
                database,
                requested,
                new ReadDiagnostics(),
                ignored -> { },
                ProgressReporter.NONE
        );

        ChunkReadMetrics metrics = stats.metrics().orElseThrow();
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
                VcdbsReaderFixtures.withChunkParser(tableParser),
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
                VcdbsReaderFixtures.withChunkParser(parser),
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
                VcdbsReaderFixtures.withChunkParser(new StubChunkParser()),
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
                VcdbsReaderFixtures.withChunkParser(new StubChunkParser()),
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
}
