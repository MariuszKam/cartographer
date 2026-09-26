package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsChunkLookupAdaptiveStrategyTest extends VcdbsReaderDirectChunkLookupTestSupport {

    @Test
    void normalAdaptiveTraversalKeepsFullParser() throws Exception {
        ChunkPosition position =
                new ChunkPosition(1, 0, 2, 0);
        Path database =
                databaseWithRows(position);
        StubChunkParser parser =
                new StubChunkParser();

        VcdbsReader reader = VcdbsReaderFixtures.withChunkParser(parser);
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
                VcdbsReaderFixtures.withChunkParser(parser);
        SaveSnapshot snapshot =
                new SaveSnapshot(
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
                            cartographer.progress.ProgressReporter.NONE
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
    void adaptiveSingleBatchUsesDirectLookupWithoutStrategyProbe() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 0, 0);
        ChunkPosition second = new ChunkPosition(2, 0, 0, 0);
        Path database = databaseWithRows(first, second);
        CountingSqliteSaveConnection connections = new CountingSqliteSaveConnection();

        ChunkStreamStats stats = adaptive(
                VcdbsReaderFixtures.withChunkParser(new StubChunkParser()),
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
                VcdbsReaderFixtures.withChunkParser(new StubChunkParser()),
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
                VcdbsReaderFixtures.withChunkParser(new StubChunkParser()),
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
                VcdbsReaderFixtures.withChunkParser(new StubChunkParser()),
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
                VcdbsReaderFixtures.withChunkParser(new StubChunkParser()),
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

        VcdbsReader reader = VcdbsReaderFixtures.withChunkParser(new StubChunkParser());
        ChunkStreamStats stats = adaptive(
                reader,
                new SqliteSaveConnection(),
                                database,
                                List.of(first, second),
                                new ReadDiagnostics(),
                                ignored -> { }
        );

        ChunkReadMetrics metrics = stats.metrics().orElseThrow();
        assertEquals(ChunkReadStrategy.EXACT_POSITION_BATCHES, metrics.strategy());
        assertEquals(stats.uniquePositionsRequested(), metrics.uniquePositionsRequested());
        assertEquals(stats.batchesExecuted(), metrics.batchesExecuted());
        assertEquals(stats.rowsFound(), metrics.rowsFound());
        assertEquals(stats.parsedChunks(), metrics.parsedChunks());
        assertEquals(stats.payloadBytes(), metrics.payloadBytes());
        assertTrue(metrics.totalNanos() >= metrics.finalDrainNanos());
    }

    @Test
    void metricsRemainAttachedToTheirProducingTraversal() throws Exception {
        List<ChunkPosition> existing = spacedPositions(300);
        Path database = databaseWithRows(existing.toArray(ChunkPosition[]::new));
        VcdbsReader reader = VcdbsReaderFixtures.withChunkParser(new StubChunkParser());

        ChunkStreamStats directStats = adaptive(
                reader,
                new SqliteSaveConnection(),
                database,
                existing.subList(0, 2),
                new ReadDiagnostics(),
                ignored -> { }
        );
        ChunkStreamStats tableStats = adaptive(
                reader,
                new SqliteSaveConnection(),
                database,
                spacedPositions(320),
                new ReadDiagnostics(),
                ignored -> { }
        );

        assertEquals(
                ChunkReadStrategy.EXACT_POSITION_BATCHES,
                directStats.metrics().orElseThrow().strategy()
        );
        assertEquals(
                ChunkReadStrategy.TABLE_STREAM,
                tableStats.metrics().orElseThrow().strategy()
        );
        assertEquals(
                ChunkReadStrategy.EXACT_POSITION_BATCHES,
                directStats.metrics().orElseThrow().strategy()
        );
    }

    @Test
    void adaptiveTableStreamPublishesChosenStrategyMetrics() throws Exception {
        List<ChunkPosition> existing = spacedPositions(300);
        Path database = databaseWithRows(existing.toArray(ChunkPosition[]::new));
        VcdbsReader reader = VcdbsReaderFixtures.withChunkParser(new StubChunkParser());

        ChunkStreamStats stats = adaptive(
                reader,
                new SqliteSaveConnection(),
                                database,
                                spacedPositions(320),
                                new ReadDiagnostics(),
                                ignored -> { }
        );

        ChunkReadMetrics metrics = stats.metrics().orElseThrow();
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
        VcdbsReader reader = VcdbsReaderFixtures.withChunkParser(new StubChunkParser());

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
                stats.metrics().orElseThrow().strategy()
        );
        assertEquals(1, stats.metrics().orElseThrow().statementsPrepared());
        assertEquals(1, stats.metrics().orElseThrow().statementsExecuted());
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
        VcdbsReader reader = VcdbsReaderFixtures.withChunkParser(parser);

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
                stats.metrics().orElseThrow().strategy()
        );
        assertTrue(parser.xCoordinates().stream().noneMatch(x -> x == 256));
    }
}
