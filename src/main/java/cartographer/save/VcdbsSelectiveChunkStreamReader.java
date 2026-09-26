package cartographer.save;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.parser.ChunkDecodeWorkspace;
import cartographer.parser.ChunkParser;
import cartographer.parser.SelectiveChunkParseResult;
import cartographer.progress.ProgressReporter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Streams CHUNK rows through the selective palette-aware decoder.
 *
 * <p>The source connection is always borrowed from {@link SaveSession}; this
 * reader never owns or mutates the authoritative save.</p>
 */
final class VcdbsSelectiveChunkStreamReader {

    private static final int DIRECT_CHUNK_BATCH_SIZE = 256;

    private final ChunkParser chunkParser;
    private final int chunkDecodeWorkerCount;
    private final int chunkDecodeMaxInFlight;

    VcdbsSelectiveChunkStreamReader(
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

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(wantedBlockIds, "wantedBlockIds is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");
        int[] uniqueWantedBlockIds = uniqueWantedBlockIds(wantedBlockIds);
        if (uniqueWantedBlockIds.length == 0) {
            throw new IllegalArgumentException("wantedBlockIds cannot be empty");
        }
        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
        try {
            return forEachChunkByPositionMatchingBlockIdsAdaptive(
                    session.connection(), packedPositions, uniqueWantedBlockIds,
                    diagnostics, consumer, progress
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot read adaptive selective chunk traversal: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer
    ) {
        return forEachChunkByPositionMatchingBlockIdsAdaptive(
                session, positions, wantedBlockIds, diagnostics, consumer,
                ProgressReporter.NONE
        );
    }

    private SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
            Connection connection,
            Set<Long> packedPositions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) throws SQLException {
        boolean tableStream;
        try {
            tableStream = shouldUseChunkTableStream(
                    connection, packedPositions.size()
            );
        } catch (SQLException exception) {
            tableStream = false;
        }
        if (tableStream) {
            return forEachChunkByPositionMatchingBlockIdsTableStream(
                    connection, packedPositions, wantedBlockIds,
                    diagnostics, consumer, progress
            );
        }
        return forEachChunkByPositionMatchingBlockIds(
                connection, packedPositions, wantedBlockIds,
                diagnostics, consumer, progress
        );
    }

    /**
     * Visits exact chunk positions with the selective decoder and reports
     * availability independently from whether a ParsedChunk was delivered.
     *
     * <p>The session connection is borrowed and never closed by this reader.</p>
     */
    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        return forEachChunkByPositionMatchingBlockIdsWithCoverage(
                session.connection(),
                positions,
                wantedBlockIds,
                diagnostics,
                consumer,
                progress
        );
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer
    ) {
        return forEachChunkByPositionMatchingBlockIdsWithCoverage(
                session,
                positions,
                wantedBlockIds,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    private SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
            Connection connection,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(connection, "connection is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(wantedBlockIds, "wantedBlockIds is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        int[] uniqueWantedBlockIds = uniqueWantedBlockIds(wantedBlockIds);
        if (uniqueWantedBlockIds.length == 0) {
            throw new IllegalArgumentException("wantedBlockIds cannot be empty");
        }

        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0);
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
        return readSelectiveChunkCoverage(
                connection,
                packedPositions,
                uniqueWantedBlockIds,
                diagnostics,
                consumer,
                progress,
                tableStream
        );
    }

    SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIds(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(wantedBlockIds, "wantedBlockIds is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        int[] uniqueWantedBlockIds = uniqueWantedBlockIds(wantedBlockIds);
        if (uniqueWantedBlockIds.length == 0) {
            throw new IllegalArgumentException("wantedBlockIds cannot be empty");
        }
        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0);
        }

        try {
            return forEachChunkByPositionMatchingBlockIds(
                    session.connection(),
                    packedPositions,
                    uniqueWantedBlockIds,
                    diagnostics,
                    consumer,
                    progress
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot read chunk table selectively: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsTableStream(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(wantedBlockIds, "wantedBlockIds is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        int[] uniqueWantedBlockIds = uniqueWantedBlockIds(wantedBlockIds);
        if (uniqueWantedBlockIds.length == 0) {
            throw new IllegalArgumentException("wantedBlockIds cannot be empty");
        }
        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0);
        }

        try {
            return forEachChunkByPositionMatchingBlockIdsTableStream(
                    session.connection(),
                    packedPositions,
                    uniqueWantedBlockIds,
                    diagnostics,
                    consumer,
                    progress
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot scan chunk table selectively for exact positions: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsTableStream(
            Connection connection,
            Set<Long> packedPositions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) throws SQLException {
        progress.start("Scanning chunk table selectively for exact positions");
        SelectiveDecodeCounters counters = new SelectiveDecodeCounters();
        int rowsFound = 0;
        if (SqliteSaveTableInspector.tableMissing(connection, SaveTable.CHUNK.tableName())) {
            diagnostics.missingTable(SaveTable.CHUNK.tableName());
            progress.done("Selective chunk table scan unavailable: chunk table missing");
            return new SelectiveChunkStreamStats(
                    packedPositions.size(), 0, 0, 0, 0, 0, 0, 0
            );
        }
        try (ChunkDecodeWorkspacePool workspaces = new ChunkDecodeWorkspacePool(chunkDecodeWorkerCount);
             BoundedStreamingDecodePipeline<SelectiveDecodeOutcome> pipeline =
                     new BoundedStreamingDecodePipeline<>(
                             chunkDecodeWorkerCount,
                             chunkDecodeMaxInFlight,
                             outcome -> applySelectiveOutcome(
                                     outcome, diagnostics, consumer, counters
                             ));
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT position, data FROM \"" + SaveTable.CHUNK.tableName() + "\""
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
                        position.x(), position.y(), position.z()
                );
                pipeline.submit(() -> decodeSelectiveChunk(
                        coordinate, payload, wantedBlockIds, workspaces
                ));
            }
            pipeline.finish();
        }
        progress.done("Selective chunk table scan complete");
        return new SelectiveChunkStreamStats(
                packedPositions.size(), 1, rowsFound,
                counters.payloadsParsed, counters.paletteRejectedChunks,
                counters.fullyDecodedChunks, counters.failedChunks, counters.payloadBytes
        );
    }

    private SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIds(
            Connection connection,
            Set<Long> packedPositions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) throws SQLException {
        progress.start("Reading selective chunks by exact position");
        int batchesExecuted = 0;
        int rowsFound = 0;
        SelectiveDecodeCounters counters = new SelectiveDecodeCounters();

        if (SqliteSaveTableInspector.tableMissing(connection, SaveTable.CHUNK.tableName())) {
            diagnostics.missingTable(SaveTable.CHUNK.tableName());
            progress.done("Selective chunk lookup unavailable: chunk table missing");
            return new SelectiveChunkStreamStats(
                    packedPositions.size(), 0, 0, 0, 0, 0, 0, 0
            );
        }

        List<Long> requested = new ArrayList<>(packedPositions);
        try (ChunkDecodeWorkspacePool workspaces = new ChunkDecodeWorkspacePool(chunkDecodeWorkerCount);
             BoundedStreamingDecodePipeline<SelectiveDecodeOutcome> pipeline =
                     new BoundedStreamingDecodePipeline<>(
                             chunkDecodeWorkerCount,
                             chunkDecodeMaxInFlight,
                             outcome -> applySelectiveOutcome(
                                     outcome, diagnostics, consumer, counters
                             ))) {
            for (int start = 0; start < requested.size(); start += DIRECT_CHUNK_BATCH_SIZE) {
                int end = Math.min(start + DIRECT_CHUNK_BATCH_SIZE, requested.size());
                SelectiveBatchStats batch = readSelectiveChunkBatch(
                        connection, requested.subList(start, end), wantedBlockIds,
                        diagnostics, pipeline, workspaces
                );
                batchesExecuted++;
                rowsFound += batch.rowsFound();
                counters.payloadBytes += batch.payloadBytes();
                progress.progress("Reading selective chunks by exact position", end, requested.size());
            }
            pipeline.finish();
        }
        progress.done("Selective chunk lookup complete");
        return new SelectiveChunkStreamStats(
                packedPositions.size(), batchesExecuted, rowsFound,
                counters.payloadsParsed, counters.paletteRejectedChunks,
                counters.fullyDecodedChunks, counters.failedChunks, counters.payloadBytes
        );
    }

    private int[] uniqueWantedBlockIds(
            int[] wantedBlockIds
    ) {
        int[] unique =
                new int[wantedBlockIds.length];
        int size =
                0;

        for (int wantedBlockId : wantedBlockIds) {
            boolean alreadyPresent =
                    false;

            for (int index = 0;
                 index < size;
                 index++) {
                if (unique[index] == wantedBlockId) {
                    alreadyPresent =
                            true;
                    break;
                }
            }

            if (!alreadyPresent) {
                unique[size++] = wantedBlockId;
            }
        }

        return java.util.Arrays.copyOf(
                unique,
                size
        );
    }

    private SelectiveBatchStats readSelectiveChunkBatch(
            Connection connection,
            List<Long> packedPositions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            BoundedStreamingDecodePipeline<SelectiveDecodeOutcome> pipeline,
            ChunkDecodeWorkspacePool workspaces
    ) throws SQLException {
        String sql =
                "SELECT position, data FROM \""
                        + SaveTable.CHUNK.tableName()
                        + "\" WHERE position IN ("
                        + sqlPlaceholders(packedPositions.size())
                        + ")";

        int rowsFound = 0;
        long payloadBytes = 0;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
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

                    pipeline.submit(() -> decodeSelectiveChunk(
                            coordinate,
                            payload,
                            wantedBlockIds,
                            workspaces
                    ));
                }
            }
        }

        return new SelectiveBatchStats(
                rowsFound,
                payloadBytes
        );
    }

    private SelectiveChunkStreamStats readSelectiveChunkCoverage(
            Connection connection,
            Set<Long> packedPositions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer,
            ProgressReporter progress,
            boolean tableStream
    ) {
        Objects.requireNonNull(connection, "connection is required");
        progress.start(
                tableStream
                        ? "Scanning chunk table selectively for coverage"
                        : "Reading selective chunks with coverage"
        );
        SelectiveDecodeCounters counters = new SelectiveDecodeCounters();
        Set<Long> foundPositions = new LinkedHashSet<>();
        int[] batchesExecuted = {0};
        int[] rowsFound = {0};

        try {
            if (SqliteSaveTableInspector.tableMissing(connection, SaveTable.CHUNK.tableName())) {
                diagnostics.missingTable(SaveTable.CHUNK.tableName());
                progress.done("Selective chunk coverage unavailable: chunk table missing");
                return new SelectiveChunkStreamStats(
                        packedPositions.size(), 0, 0, 0, 0, 0, 0, 0
                );
            }

            try (ChunkDecodeWorkspacePool workspaces = new ChunkDecodeWorkspacePool(chunkDecodeWorkerCount);
                 BoundedStreamingDecodePipeline<CoverageDecodeOutcome> pipeline =
                         new BoundedStreamingDecodePipeline<>(
                                 chunkDecodeWorkerCount,
                                 chunkDecodeMaxInFlight,
                                 outcome -> applyCoverageOutcome(
                                         outcome,
                                         diagnostics,
                                         consumer,
                                         counters
                                 )
                         )) {
                if (tableStream) {
                    readCoverageRows(
                            connection,
                            "SELECT position, data FROM \""
                                    + SaveTable.CHUNK.tableName()
                                    + "\"",
                            List.of(),
                            packedPositions,
                            foundPositions,
                            wantedBlockIds,
                            diagnostics,
                            pipeline,
                            workspaces,
                            rowsFound,
                            counters
                    );
                    batchesExecuted[0] = 1;
                } else {
                    List<Long> requested = new ArrayList<>(packedPositions);
                    for (int start = 0;
                         start < requested.size();
                         start += DIRECT_CHUNK_BATCH_SIZE) {
                        int end = Math.min(
                                start + DIRECT_CHUNK_BATCH_SIZE,
                                requested.size()
                        );
                        readCoverageRows(
                                connection,
                                "SELECT position, data FROM \""
                                        + SaveTable.CHUNK.tableName()
                                        + "\" WHERE position IN ("
                                        + sqlPlaceholders(end - start)
                                        + ")",
                                requested.subList(start, end),
                                packedPositions,
                                foundPositions,
                                wantedBlockIds,
                                diagnostics,
                                pipeline,
                                workspaces,
                                consumer,
                                rowsFound,
                                counters
                        );
                        batchesExecuted[0]++;
                        progress.progress(
                                "Reading selective chunks with coverage",
                                end,
                                requested.size()
                        );
                    }
                }
                pipeline.finish();
            }

            for (long packedPosition : packedPositions) {
                if (!foundPositions.contains(packedPosition)) {
                    consumer.accept(
                            SelectiveChunkVisit.missing(
                                    ChunkPosDecoder.decode(packedPosition)
                            )
                    );
                }
            }
            progress.done(
                    tableStream
                            ? "Selective chunk table coverage complete"
                            : "Selective chunk coverage complete"
            );
            return new SelectiveChunkStreamStats(
                    packedPositions.size(),
                    batchesExecuted[0],
                    rowsFound[0],
                    counters.payloadsParsed,
                    counters.paletteRejectedChunks,
                    counters.fullyDecodedChunks,
                    counters.failedChunks,
                    counters.payloadBytes
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot read selective chunk coverage: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private void readCoverageRows(
            Connection connection,
            String sql,
            List<Long> parameters,
            Set<Long> requested,
            Set<Long> found,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            BoundedStreamingDecodePipeline<CoverageDecodeOutcome> pipeline,
            ChunkDecodeWorkspacePool workspaces,
            int[] rowsFound,
            SelectiveDecodeCounters counters
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setLong(index + 1, parameters.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long packedPosition = resultSet.getLong("position");
                    if (!requested.contains(packedPosition)) {
                        continue;
                    }
                    found.add(packedPosition);
                    rowsFound[0]++;
                    ChunkPosition position = ChunkPosDecoder.decode(packedPosition);
                    byte[] payload = resultSet.getBytes("data");
                    if (payload == null) {
                        diagnostics.recordSkipped("chunk row has null payload");
                        pipeline.submit(
                                () -> new CoverageDecodeOutcome(
                                        position,
                                        SelectiveDecodeOutcome.failure(
                                                false,
                                                "chunk row has null payload"
                                        ),
                                        true
                                )
                        );
                        continue;
                    }
                    counters.payloadBytes += payload.length;
                    ChunkCoordinate coordinate = new ChunkCoordinate(
                            position.x(), position.y(), position.z()
                    );
                    pipeline.submit(() -> new CoverageDecodeOutcome(
                            position,
                            decodeSelectiveChunk(
                                    coordinate,
                                    payload,
                                    wantedBlockIds,
                                    workspaces
                            ),
                            false
                    ));
                }
            }
        }
    }

    private void applyCoverageOutcome(
            CoverageDecodeOutcome outcome,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer,
            SelectiveDecodeCounters counters
    ) {
        SelectiveDecodeOutcome decoded = outcome.outcome();
        if (outcome.skipped()) {
            consumer.accept(
                    SelectiveChunkVisit.failed(
                            outcome.position(),
                            decoded.error()
                    )
            );
            return;
        }
        if (decoded.payloadParsed()) {
            counters.payloadsParsed++;
        }
        if (decoded.paletteRejected()) {
            counters.paletteRejectedChunks++;
            consumer.accept(
                    SelectiveChunkVisit.paletteRejected(outcome.position())
            );
            return;
        }
        if (decoded.chunk() != null) {
            diagnostics.recordParsed();
            counters.fullyDecodedChunks++;
            consumer.accept(
                    SelectiveChunkVisit.decoded(
                            outcome.position(),
                            decoded.chunk()
                    )
            );
            return;
        }
        diagnostics.recordFailed(decoded.error());
        counters.failedChunks++;
        consumer.accept(
                SelectiveChunkVisit.failed(
                        outcome.position(),
                        decoded.error()
                )
        );
    }

    private SelectiveDecodeOutcome decodeSelectiveChunk(
            ChunkCoordinate coordinate,
            byte[] payload,
            int[] wantedBlockIds,
            ChunkDecodeWorkspacePool workspaces
    ) {
        ChunkDecodeWorkspace workspace = workspaces.borrow();
        try {
            return decodeSelectiveChunk(coordinate, payload, wantedBlockIds, workspace);
        } finally {
            workspaces.release(workspace);
        }
    }

    private SelectiveDecodeOutcome decodeSelectiveChunk(
            ChunkCoordinate coordinate,
            byte[] payload,
            int[] wantedBlockIds,
            ChunkDecodeWorkspace workspace
    ) {
        SelectiveChunkParseResult parsed =
                chunkParser.parseBlocksIfPaletteContains(
                        coordinate,
                        payload,
                        wantedBlockIds,
                        workspace
                );
        if (parsed.paletteRejected()) {
            return SelectiveDecodeOutcome.rejected();
        }
        if (parsed.chunk().isPresent()) {
            return SelectiveDecodeOutcome.success(
                    parsed.chunk().orElseThrow()
            );
        }
        return SelectiveDecodeOutcome.failure(
                parsed.payloadParsed(),
                parsed.error().orElse(
                        "unknown selective chunk decode error"
                )
        );
    }

    private void applySelectiveOutcome(
            SelectiveDecodeOutcome outcome,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            SelectiveDecodeCounters counters
    ) {
        if (outcome.payloadParsed()) {
            counters.payloadsParsed++;
        }
        if (outcome.paletteRejected()) {
            counters.paletteRejectedChunks++;
            return;
        }
        if (outcome.chunk() != null) {
            diagnostics.recordParsed();
            consumer.accept(outcome.chunk());
            counters.fullyDecodedChunks++;
            return;
        }
        diagnostics.recordFailed(outcome.error());
        counters.failedChunks++;
    }

    private record SelectiveBatchStats(
            int rowsFound,
            long payloadBytes
    ) {
    }

    private static final class SelectiveDecodeCounters {
        private int payloadsParsed;
        private int paletteRejectedChunks;
        private int fullyDecodedChunks;
        private int failedChunks;
        private long payloadBytes;
    }

    private record SelectiveDecodeOutcome(
            boolean payloadParsed,
            boolean paletteRejected,
            ParsedChunk chunk,
            String error
    ) {
        private SelectiveDecodeOutcome {
            if (paletteRejected && (chunk != null || error != null)) {
                throw new IllegalArgumentException("palette rejection cannot contain a result");
            }
            if (!paletteRejected && (chunk == null) == (error == null)) {
                throw new IllegalArgumentException(
                        "selective decode outcome must contain exactly one result"
                );
            }
        }

        private static SelectiveDecodeOutcome rejected() {
            return new SelectiveDecodeOutcome(true, true, null, null);
        }

        private static SelectiveDecodeOutcome success(ParsedChunk chunk) {
            return new SelectiveDecodeOutcome(
                    true,
                    false,
                    Objects.requireNonNull(chunk, "chunk is required"),
                    null
            );
        }

        private static SelectiveDecodeOutcome failure(
                boolean payloadParsed,
                String error
        ) {
            return new SelectiveDecodeOutcome(
                    payloadParsed,
                    false,
                    null,
                    Objects.requireNonNull(error, "error is required")
            );
        }
    }

    private record CoverageDecodeOutcome(
            ChunkPosition position,
            SelectiveDecodeOutcome outcome,
            boolean skipped
    ) {
        private CoverageDecodeOutcome {
            Objects.requireNonNull(position, "position is required");
            Objects.requireNonNull(outcome, "outcome is required");
        }
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

    private String sqlPlaceholders(
            int count
    ) {
        return "?, ".repeat(count - 1) + "?";
    }

}
