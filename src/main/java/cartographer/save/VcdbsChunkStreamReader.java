package cartographer.save;

import cartographer.progress.ProgressReporter;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.parser.ChunkParser;
import cartographer.parser.ChunkDecodeWorkspace;
import cartographer.parser.ChunkDecodeProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

final class VcdbsChunkStreamReader {

    private enum ChunkDecodeMode {
        FULL,
        SURFACE_COMPACT
    }

    private static final int DIRECT_CHUNK_BATCH_SIZE =
            256;

    private static final int RANGE_RUNS_PER_STATEMENT =
            64;

    private final ChunkParser chunkParser;
    private final int chunkDecodeWorkerCount;
    private final int chunkDecodeMaxInFlight;
    private final PackedPositionRunPlanner packedPositionRunPlanner =
            new PackedPositionRunPlanner();

    VcdbsChunkStreamReader(
            ChunkParser chunkParser,
            int chunkDecodeWorkerCount,
            int chunkDecodeMaxInFlight
    ) {
        if (chunkDecodeWorkerCount <= 0) {
            throw new IllegalArgumentException(
                    "chunkDecodeWorkerCount must be positive"
            );
        }
        if (chunkDecodeMaxInFlight <= chunkDecodeWorkerCount) {
            throw new IllegalArgumentException(
                    "chunkDecodeMaxInFlight must be greater than worker count"
            );
        }
        this.chunkParser = chunkParser;
        this.chunkDecodeWorkerCount = chunkDecodeWorkerCount;
        this.chunkDecodeMaxInFlight = chunkDecodeMaxInFlight;
    }

    public ChunkStreamStats forEachChunkByPositionAdaptive(
            SaveSession session,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");
        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        }
        return forEachChunkByPositionAdaptive(
                session.connection(),
                packedPositions,
                diagnostics,
                consumer,
                progress,
                ChunkDecodeMode.FULL
        );
    }

    /**
     * Surface-only adaptive traversal. SQL strategy selection is identical to
     * the normal adaptive path; only the worker-side decoded-layer
     * representation changes to compact palette/bit-plane point lookup.
     */
    public ChunkStreamStats forEachSurfaceChunkByPositionAdaptive(
            SaveSession session,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(
                session,
                "session is required"
        );
        Objects.requireNonNull(
                positions,
                "positions is required"
        );
        Objects.requireNonNull(
                diagnostics,
                "diagnostics is required"
        );
        Objects.requireNonNull(
                consumer,
                "consumer is required"
        );
        Objects.requireNonNull(
                progress,
                "progress is required"
        );

        Set<Long> packedPositions =
                packedUniquePositions(
                        positions
                );
        if (packedPositions.isEmpty()) {
            return new ChunkStreamStats(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        return forEachChunkByPositionAdaptive(
                session.connection(),
                packedPositions,
                diagnostics,
                consumer,
                progress,
                ChunkDecodeMode.SURFACE_COMPACT
        );
    }

    public ChunkStreamStats forEachChunkByPositionAdaptive(
            SaveSession session,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer
    ) {
        return forEachChunkByPositionAdaptive(
                session, positions, diagnostics, consumer, ProgressReporter.NONE
        );
    }

    private ChunkStreamStats forEachChunkByPositionAdaptive(
            Connection connection,
            Set<Long> packedPositions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress,
            ChunkDecodeMode decodeMode
    ) {
        Objects.requireNonNull(
                decodeMode,
                "decode mode is required"
        );
        if (packedPositions.size() <= DIRECT_CHUNK_BATCH_SIZE) {
            try {
                return forEachChunkByPosition(
                        connection,
                        packedPositions,
                        diagnostics,
                        consumer,
                        progress,
                        0L,
                        decodeMode
                );
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Cannot read chunk table by exact position: "
                                + exception.getMessage(),
                        exception
                );
            }
        }

        long strategyProbeStart = System.nanoTime();
        Optional<List<PackedPositionRun>> rangeRuns =
                packedPositionRunPlanner.planIfClearlyBetter(
                        packedPositions,
                        DIRECT_CHUNK_BATCH_SIZE,
                        RANGE_RUNS_PER_STATEMENT
                );
        if (rangeRuns.isPresent()) {
            long strategyProbeNanos = elapsedNanos(strategyProbeStart);
            try {
                return forEachChunkByPackedRuns(
                        connection,
                        packedPositions.size(),
                        rangeRuns.orElseThrow(),
                        diagnostics,
                        consumer,
                        progress,
                        strategyProbeNanos,
                        decodeMode
                );
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Cannot read chunk table by packed position ranges: "
                                + exception.getMessage(),
                        exception
                );
            }
        }

        boolean tableStream;
        try {
            tableStream = shouldUseChunkTableStream(
                    connection,
                    packedPositions.size()
            );
        } catch (SQLException exception) {
            tableStream = false;
        }
        long strategyProbeNanos = elapsedNanos(strategyProbeStart);
        if (tableStream) {
            try {
                return forEachChunkByPositionTableStream(
                        connection,
                        packedPositions,
                        diagnostics,
                        consumer,
                        progress,
                        strategyProbeNanos,
                        decodeMode
                );
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Cannot scan chunk table for exact positions: "
                                + exception.getMessage(),
                        exception
                );
            }
        }
        try {
            return forEachChunkByPosition(
                    connection,
                    packedPositions,
                    diagnostics,
                    consumer,
                    progress,
                    strategyProbeNanos,
                    decodeMode
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot read chunk table by exact position: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    ChunkStreamStats forEachChunkByPositionTableStream(
            SaveSession session,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        }

        try {
            return forEachChunkByPositionTableStream(
                    session.connection(),
                    packedPositions,
                    diagnostics,
                    consumer,
                    progress,
                    0L,
                    ChunkDecodeMode.FULL
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot scan chunk table for exact positions: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Visits chunks found by exact packed primary-key lookup. SQL result order
     * is unspecified and must not be treated as request order.
     */
    ChunkStreamStats forEachChunkByPosition(
            SaveSession session,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        }

        try {
            return forEachChunkByPosition(
                    session.connection(),
                    packedPositions,
                    diagnostics,
                    consumer,
                    progress,
                    0L,
                    ChunkDecodeMode.FULL
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot read chunk table by exact position: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private ChunkStreamStats forEachChunkByPackedRuns(
            Connection connection,
            int uniquePositionsRequested,
            List<PackedPositionRun> runs,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress,
            long strategyProbeNanos,
            ChunkDecodeMode decodeMode
    ) throws SQLException {
        long totalStart = System.nanoTime();
        progress.start("Reading chunks by packed position ranges");
        int batchesExecuted = 0;
        int statementsPrepared = 0;
        int rowsFound = 0;
        long sourceReadNanos = 0L;
        long pipelineWaitNanos = 0L;
        long finalDrainNanos = 0L;
        ChunkDecodeCounters counters = new ChunkDecodeCounters();

        if (SqliteSaveTableInspector.tableMissing(connection, SaveTable.CHUNK.tableName())) {
            diagnostics.missingTable(SaveTable.CHUNK.tableName());
            progress.done("Packed range lookup unavailable: chunk table missing");
            return new ChunkStreamStats(
                    uniquePositionsRequested,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        int tailRunCount = runs.size() % RANGE_RUNS_PER_STATEMENT;
        PreparedStatement fullStatement = null;
        PreparedStatement tailStatement = null;
        long prepareStart = System.nanoTime();
        try {
            if (runs.size() >= RANGE_RUNS_PER_STATEMENT) {
                fullStatement = prepareRangeChunkStatement(
                        connection,
                        RANGE_RUNS_PER_STATEMENT
                );
                statementsPrepared++;
            }
            if (tailRunCount > 0) {
                tailStatement = prepareRangeChunkStatement(
                        connection,
                        tailRunCount
                );
                statementsPrepared++;
            }
            sourceReadNanos += elapsedNanos(prepareStart);

            try (ChunkDecodeWorkspacePool workspaces =
                         new ChunkDecodeWorkspacePool(chunkDecodeWorkerCount);
                 BoundedStreamingDecodePipeline<ChunkDecodeOutcome> pipeline =
                         new BoundedStreamingDecodePipeline<>(
                                 chunkDecodeWorkerCount,
                                 chunkDecodeMaxInFlight,
                                 outcome -> applyChunkOutcome(
                                         outcome,
                                         diagnostics,
                                         consumer,
                                         counters
                                 )
                         )) {
                int processedPositions = 0;
                for (int start = 0;
                     start < runs.size();
                     start += RANGE_RUNS_PER_STATEMENT) {
                    int end = Math.min(
                            start + RANGE_RUNS_PER_STATEMENT,
                            runs.size()
                    );
                    List<PackedPositionRun> batchRuns =
                            runs.subList(start, end);
                    PreparedStatement statement =
                            batchRuns.size() == RANGE_RUNS_PER_STATEMENT
                                    ? fullStatement
                                    : tailStatement;
                    BatchStats batch = readChunkRangeBatch(
                            statement,
                            batchRuns,
                            diagnostics,
                            pipeline,
                            workspaces,
                            decodeMode
                    );
                    batchesExecuted++;
                    rowsFound += batch.rowsFound();
                    counters.payloadBytes += batch.payloadBytes();
                    sourceReadNanos += batch.sourceReadNanos();
                    pipelineWaitNanos += batch.pipelineWaitNanos();
                    for (PackedPositionRun run : batchRuns) {
                        processedPositions += run.positionCount();
                    }
                    progress.progress(
                            "Reading chunks by packed position ranges",
                            processedPositions,
                            uniquePositionsRequested
                    );
                }
                long drainStart = System.nanoTime();
                pipeline.finish();
                finalDrainNanos = elapsedNanos(drainStart);
            }
        } finally {
            if (tailStatement != null) {
                tailStatement.close();
            }
            if (fullStatement != null) {
                fullStatement.close();
            }
        }

        progress.done("Packed range lookup complete");
        return new ChunkStreamStats(
                uniquePositionsRequested,
                batchesExecuted,
                rowsFound,
                counters.parsedChunks,
                counters.failedChunks,
                counters.payloadBytes,
                Optional.of(new ChunkReadMetrics(
                ChunkReadStrategy.RANGE_RUN_BATCHES,
                uniquePositionsRequested,
                batchesExecuted,
                statementsPrepared,
                batchesExecuted,
                rowsFound,
                counters.parsedChunks,
                counters.failedChunks,
                counters.payloadBytes,
                strategyProbeNanos,
                sourceReadNanos,
                pipelineWaitNanos,
                finalDrainNanos,
                elapsedNanos(totalStart)
                ))
        );
    }

    private ChunkStreamStats forEachChunkByPositionTableStream(
            Connection connection,
            Set<Long> packedPositions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress,
            long strategyProbeNanos,
            ChunkDecodeMode decodeMode
    ) throws SQLException {
        long totalStart = System.nanoTime();
        progress.start("Scanning chunk table for exact positions");
        ChunkDecodeCounters counters = new ChunkDecodeCounters();
        int rowsFound = 0;
        long pipelineWaitNanos = 0L;
        long sourceReadNanos = 0L;
        long finalDrainNanos = 0L;
        if (SqliteSaveTableInspector.tableMissing(connection, SaveTable.CHUNK.tableName())) {
            diagnostics.missingTable(SaveTable.CHUNK.tableName());
            progress.done("Exact chunk table scan unavailable: chunk table missing");
            return new ChunkStreamStats(packedPositions.size(), 0, 0, 0, 0, 0);
        }

        long sourceStart = System.nanoTime();
        try (ChunkDecodeWorkspacePool workspaces =
                     new ChunkDecodeWorkspacePool(chunkDecodeWorkerCount);
             BoundedStreamingDecodePipeline<ChunkDecodeOutcome> pipeline =
                     new BoundedStreamingDecodePipeline<>(
                             chunkDecodeWorkerCount,
                             chunkDecodeMaxInFlight,
                             outcome -> applyChunkOutcome(
                                     outcome,
                                     diagnostics,
                                     consumer,
                                     counters
                             )
                     );
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT position, data FROM \""
                             + SaveTable.CHUNK.tableName()
                             + "\""
             );
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long packedPosition = resultSet.getLong("position");
                if (!packedPositions.contains(packedPosition)) {
                    continue;
                }
                rowsFound++;
                ChunkPosition position = ChunkPosDecoder.decode(packedPosition);
                byte[] payload = resultSet.getBytes("data");
                if (payload == null) {
                    diagnostics.recordSkipped("chunk row has null payload");
                    continue;
                }
                counters.payloadBytes += payload.length;
                ChunkCoordinate coordinate = new ChunkCoordinate(
                        position.x(),
                        position.y(),
                        position.z()
                );
                long submitStart = System.nanoTime();
                pipeline.submit(() -> decodeChunk(
                        coordinate,
                        payload,
                        workspaces,
                        decodeMode
                ));
                pipelineWaitNanos += elapsedNanos(submitStart);
            }
            sourceReadNanos = Math.max(
                    0L,
                    elapsedNanos(sourceStart) - pipelineWaitNanos
            );
            long drainStart = System.nanoTime();
            pipeline.finish();
            finalDrainNanos = elapsedNanos(drainStart);
        }

        progress.done("Exact chunk table scan complete");
        return new ChunkStreamStats(
                packedPositions.size(),
                1,
                rowsFound,
                counters.parsedChunks,
                counters.failedChunks,
                counters.payloadBytes,
                Optional.of(new ChunkReadMetrics(
                ChunkReadStrategy.TABLE_STREAM,
                packedPositions.size(),
                1,
                1,
                1,
                rowsFound,
                counters.parsedChunks,
                counters.failedChunks,
                counters.payloadBytes,
                strategyProbeNanos,
                sourceReadNanos,
                pipelineWaitNanos,
                finalDrainNanos,
                elapsedNanos(totalStart)
                ))
        );
    }

    private ChunkStreamStats forEachChunkByPosition(
            Connection connection,
            Set<Long> packedPositions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress,
            long strategyProbeNanos,
            ChunkDecodeMode decodeMode
    ) throws SQLException {
        long totalStart = System.nanoTime();
        progress.start("Reading chunks by exact position");
        int batchesExecuted = 0;
        int statementsPrepared = 0;
        int rowsFound = 0;
        long sourceReadNanos = 0L;
        long pipelineWaitNanos = 0L;
        long finalDrainNanos = 0L;
        ChunkDecodeCounters counters = new ChunkDecodeCounters();
        if (SqliteSaveTableInspector.tableMissing(connection, SaveTable.CHUNK.tableName())) {
            diagnostics.missingTable(SaveTable.CHUNK.tableName());
            progress.done("Exact chunk lookup unavailable: chunk table missing");
            return new ChunkStreamStats(packedPositions.size(), 0, 0, 0, 0, 0);
        }

        List<Long> requested = new ArrayList<>(packedPositions);
        int tailSize = requested.size() % DIRECT_CHUNK_BATCH_SIZE;
        PreparedStatement fullStatement = null;
        PreparedStatement tailStatement = null;
        long prepareStart = System.nanoTime();
        try {
            if (requested.size() >= DIRECT_CHUNK_BATCH_SIZE) {
                fullStatement = prepareExactChunkStatement(
                        connection,
                        DIRECT_CHUNK_BATCH_SIZE
                );
                statementsPrepared++;
            }
            if (tailSize > 0) {
                tailStatement = prepareExactChunkStatement(
                        connection,
                        tailSize
                );
                statementsPrepared++;
            }
            sourceReadNanos += elapsedNanos(prepareStart);

            try (ChunkDecodeWorkspacePool workspaces =
                         new ChunkDecodeWorkspacePool(chunkDecodeWorkerCount);
                 BoundedStreamingDecodePipeline<ChunkDecodeOutcome> pipeline =
                         new BoundedStreamingDecodePipeline<>(
                                 chunkDecodeWorkerCount,
                                 chunkDecodeMaxInFlight,
                                 outcome -> applyChunkOutcome(
                                         outcome,
                                         diagnostics,
                                         consumer,
                                         counters
                                 )
                         )) {
                for (int start = 0;
                     start < requested.size();
                     start += DIRECT_CHUNK_BATCH_SIZE) {
                    int end = Math.min(
                            start + DIRECT_CHUNK_BATCH_SIZE,
                            requested.size()
                    );
                    List<Long> batchPositions = requested.subList(start, end);
                    PreparedStatement statement =
                            batchPositions.size() == DIRECT_CHUNK_BATCH_SIZE
                                    ? fullStatement
                                    : tailStatement;
                    BatchStats batch = readChunkBatch(
                            statement,
                            batchPositions,
                            diagnostics,
                            pipeline,
                            workspaces,
                            decodeMode
                    );
                    batchesExecuted++;
                    rowsFound += batch.rowsFound();
                    counters.payloadBytes += batch.payloadBytes();
                    sourceReadNanos += batch.sourceReadNanos();
                    pipelineWaitNanos += batch.pipelineWaitNanos();
                    progress.progress(
                            "Reading chunks by exact position",
                            end,
                            requested.size()
                    );
                }
                long drainStart = System.nanoTime();
                pipeline.finish();
                finalDrainNanos = elapsedNanos(drainStart);
            }
        } finally {
            if (tailStatement != null) {
                tailStatement.close();
            }
            if (fullStatement != null) {
                fullStatement.close();
            }
        }

        progress.done("Exact chunk lookup complete");
        return new ChunkStreamStats(
                packedPositions.size(),
                batchesExecuted,
                rowsFound,
                counters.parsedChunks,
                counters.failedChunks,
                counters.payloadBytes,
                Optional.of(new ChunkReadMetrics(
                ChunkReadStrategy.EXACT_POSITION_BATCHES,
                packedPositions.size(),
                batchesExecuted,
                statementsPrepared,
                batchesExecuted,
                rowsFound,
                counters.parsedChunks,
                counters.failedChunks,
                counters.payloadBytes,
                strategyProbeNanos,
                sourceReadNanos,
                pipelineWaitNanos,
                finalDrainNanos,
                elapsedNanos(totalStart)
                ))
        );
    }

    private boolean shouldUseChunkTableStream(
            Connection connection,
            int uniqueRequestedPositions
    ) throws SQLException {
        if (uniqueRequestedPositions <= DIRECT_CHUNK_BATCH_SIZE) {
            return false;
        }
        if (SqliteSaveTableInspector.tableMissing(connection, SaveTable.CHUNK.tableName())) {
            return false;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM \"" + SaveTable.CHUNK.tableName()
                        + "\" LIMIT 1 OFFSET ?"
        )) {
            statement.setInt(1, uniqueRequestedPositions);
            try (ResultSet resultSet = statement.executeQuery()) {
                // OFFSET is zero-based: a row here means table cardinality is
                // greater than the requested unique-position count.
                return !resultSet.next();
            }
        }
    }

    private Set<Long> packedUniquePositions(
            Collection<ChunkPosition> positions
    ) {
        Set<Long> packed =
                new LinkedHashSet<>();

        for (ChunkPosition position : positions) {
            packed.add(
                    ChunkPosEncoder.encode(
                            Objects.requireNonNull(
                                    position,
                                    "positions cannot contain null"
                            )
                    )
            );
        }

        return packed;
    }

    private PreparedStatement prepareRangeChunkStatement(
            Connection connection,
            int runCount
    ) throws SQLException {
        if (runCount <= 0) {
            throw new IllegalArgumentException("runCount must be positive");
        }
        StringBuilder sql = new StringBuilder(
                "SELECT position, data FROM \""
                        + SaveTable.CHUNK.tableName()
                        + "\" WHERE "
        );
        for (int index = 0; index < runCount; index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            sql.append("(position BETWEEN ? AND ?)");
        }
        return connection.prepareStatement(sql.toString());
    }

    private BatchStats readChunkRangeBatch(
            PreparedStatement statement,
            List<PackedPositionRun> runs,
            ReadDiagnostics diagnostics,
            BoundedStreamingDecodePipeline<ChunkDecodeOutcome> pipeline,
            ChunkDecodeWorkspacePool workspaces,
            ChunkDecodeMode decodeMode
    ) throws SQLException {
        Objects.requireNonNull(statement, "statement is required");
        long batchStart = System.nanoTime();
        long pipelineWaitNanos = 0L;
        int rowsFound = 0;
        long payloadBytes = 0;

        statement.clearParameters();
        int parameter = 1;
        for (PackedPositionRun run : runs) {
            statement.setLong(parameter++, run.firstInclusive());
            statement.setLong(parameter++, run.lastInclusive());
        }

        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rowsFound++;
                ChunkPosition position = ChunkPosDecoder.decode(
                        resultSet.getLong("position")
                );
                ChunkCoordinate coordinate = new ChunkCoordinate(
                        position.x(),
                        position.y(),
                        position.z()
                );
                byte[] payload = resultSet.getBytes("data");
                if (payload == null) {
                    diagnostics.recordSkipped(
                            "chunk row has null payload"
                    );
                    continue;
                }

                payloadBytes += payload.length;
                long submitStart = System.nanoTime();
                pipeline.submit(() -> decodeChunk(
                        coordinate,
                        payload,
                        workspaces,
                        decodeMode
                ));
                pipelineWaitNanos += elapsedNanos(submitStart);
            }
        }

        long totalBatchNanos = elapsedNanos(batchStart);
        return new BatchStats(
                rowsFound,
                payloadBytes,
                Math.max(0L, totalBatchNanos - pipelineWaitNanos),
                pipelineWaitNanos
        );
    }

    private PreparedStatement prepareExactChunkStatement(
            Connection connection,
            int positionCount
    ) throws SQLException {
        if (positionCount <= 0) {
            throw new IllegalArgumentException("positionCount must be positive");
        }
        return connection.prepareStatement(
                "SELECT position, data FROM \""
                        + SaveTable.CHUNK.tableName()
                        + "\" WHERE position IN ("
                        + sqlPlaceholders(positionCount)
                        + ")"
        );
    }

    private BatchStats readChunkBatch(
            PreparedStatement statement,
            List<Long> packedPositions,
            ReadDiagnostics diagnostics,
            BoundedStreamingDecodePipeline<ChunkDecodeOutcome> pipeline,
            ChunkDecodeWorkspacePool workspaces,
            ChunkDecodeMode decodeMode
    ) throws SQLException {
        Objects.requireNonNull(statement, "statement is required");
        long batchStart = System.nanoTime();
        long pipelineWaitNanos = 0L;
        int rowsFound = 0;
        long payloadBytes = 0;

        statement.clearParameters();
        for (int index = 0;
             index < packedPositions.size();
             index++) {
            statement.setLong(
                    index + 1,
                    packedPositions.get(index)
            );
        }

        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rowsFound++;

                ChunkPosition position =
                        ChunkPosDecoder.decode(
                                resultSet.getLong("position")
                        );

                ChunkCoordinate coordinate =
                        new ChunkCoordinate(
                                position.x(),
                                position.y(),
                                position.z()
                        );

                byte[] payload =
                        resultSet.getBytes("data");

                if (payload == null) {
                    diagnostics.recordSkipped(
                            "chunk row has null payload"
                    );
                    continue;
                }

                payloadBytes += payload.length;

                long submitStart = System.nanoTime();
                pipeline.submit(() -> decodeChunk(
                        coordinate,
                        payload,
                        workspaces,
                        decodeMode
                ));
                pipelineWaitNanos += elapsedNanos(submitStart);
            }
        }

        long totalBatchNanos = elapsedNanos(batchStart);
        return new BatchStats(
                rowsFound,
                payloadBytes,
                Math.max(0L, totalBatchNanos - pipelineWaitNanos),
                pipelineWaitNanos
        );
    }

    private ChunkDecodeOutcome decodeChunk(
            ChunkCoordinate coordinate,
            byte[] payload,
            ChunkDecodeWorkspacePool workspaces,
            ChunkDecodeMode decodeMode
    ) {
        ChunkDecodeWorkspace workspace =
                workspaces.borrow();
        try {
            return decodeChunk(
                    coordinate,
                    payload,
                    workspace,
                    decodeMode
            );
        } finally {
            workspaces.release(
                    workspace
            );
        }
    }

    private ChunkDecodeOutcome decodeChunk(
            ChunkCoordinate coordinate,
            byte[] payload,
            ChunkDecodeWorkspace workspace,
            ChunkDecodeMode decodeMode
    ) {
        ParseResult<ParsedChunk> parsed =
                switch (decodeMode) {
                    case FULL -> chunkParser.parse(
                            coordinate,
                            payload,
                            ChunkDecodeProfile.BLOCKS_AND_LIQUIDS,
                            workspace
                    );
                    case SURFACE_COMPACT ->
                            chunkParser.parseSurfaceCompact(
                                    coordinate,
                                    payload,
                                    workspace
                            );
                };

        if (parsed.isSuccess()) {
            return ChunkDecodeOutcome.success(
                    parsed.value().orElseThrow()
            );
        }
        return ChunkDecodeOutcome.failure(
                parsed.error().orElse(
                        "unknown chunk parse error"
                )
        );
    }

    private void applyChunkOutcome(
            ChunkDecodeOutcome outcome,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ChunkDecodeCounters counters
    ) {
        if (outcome.chunk() == null) {
            diagnostics.recordFailed(outcome.error());
            counters.failedChunks++;
            return;
        }
        diagnostics.recordParsed();
        consumer.accept(outcome.chunk());
        counters.parsedChunks++;
    }

    private long elapsedNanos(long startedAt) {
        return Math.max(0L, System.nanoTime() - startedAt);
    }

    private String sqlPlaceholders(
            int count
    ) {
        return "?, ".repeat(count - 1) + "?";
    }

    private record BatchStats(
            int rowsFound,
            long payloadBytes,
            long sourceReadNanos,
            long pipelineWaitNanos
    ) {
    }

    private static final class ChunkDecodeCounters {
        private int parsedChunks;
        private int failedChunks;
        private long payloadBytes;
    }

    private record ChunkDecodeOutcome(
            ParsedChunk chunk,
            String error
    ) {
        private ChunkDecodeOutcome {
            if ((chunk == null) == (error == null)) {
                throw new IllegalArgumentException(
                        "chunk decode outcome must contain exactly one result"
                );
            }
        }

        private static ChunkDecodeOutcome success(ParsedChunk chunk) {
            return new ChunkDecodeOutcome(
                    Objects.requireNonNull(chunk, "chunk is required"),
                    null
            );
        }

        private static ChunkDecodeOutcome failure(String error) {
            return new ChunkDecodeOutcome(
                    null,
                    Objects.requireNonNull(error, "error is required")
            );
        }
    }

}
