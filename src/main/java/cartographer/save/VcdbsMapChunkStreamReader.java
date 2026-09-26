package cartographer.save;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.parser.MapChunkParser;
import cartographer.progress.ProgressReporter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Streams authoritative MAPCHUNK rows without taking ownership of the
 * session-owned SQLite connection.
 */
final class VcdbsMapChunkStreamReader {
    private static final int DIRECT_MAPCHUNK_BATCH_SIZE = 256;

    private final MapChunkParser mapChunkParser;

    VcdbsMapChunkStreamReader(MapChunkParser mapChunkParser) {
        this.mapChunkParser = mapChunkParser;
    }

    MapChunkStreamStats forEachObservedMapChunk(
            SaveSession session,
            ReadDiagnostics diagnostics,
            Consumer<MapChunkCoordinate> observedCoordinateConsumer,
            Consumer<MapChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(
                observedCoordinateConsumer,
                "observedCoordinateConsumer is required"
        );
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        Connection connection = session.connection();
        progress.start("Discovering observed mapchunks");
        try {
            if (SqliteSaveTableInspector.tableMissing(
                    connection,
                    SaveTable.MAPCHUNK.tableName()
            )) {
                diagnostics.missingTable(SaveTable.MAPCHUNK.tableName());
                throw new IllegalStateException(
                        "Save contains no mapchunk table; "
                                + "world snapshot discovery cannot be completed"
                );
            }

            int expectedRows = SqliteSaveTableInspector.countRows(
                    connection,
                    SaveTable.MAPCHUNK
            );
            String sql = "SELECT position, data FROM \""
                    + SaveTable.MAPCHUNK.tableName()
                    + "\" ORDER BY position";
            int rowsFound = 0;
            int parsed = 0;
            int failed = 0;
            long payloadBytes = 0L;

            try (PreparedStatement statement =
                         connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rowsFound++;
                    progress.progress(
                            "Discovering observed mapchunks",
                            rowsFound,
                            expectedRows
                    );

                    Optional<MapChunkCoordinate> coordinate =
                            SavePackedPositionDecoder
                                    .mainWorldMapChunkCoordinate(
                                            resultSet.getObject("position")
                                    );
                    if (coordinate.isEmpty()) {
                        diagnostics.recordSkipped(
                                "mapchunk row is not a readable main-world mapchunk"
                        );
                        continue;
                    }

                    MapChunkCoordinate observed = coordinate.orElseThrow();
                    if (!SavePackedPositionDecoder.mapChunkWithinWorld(
                            observed,
                            session.snapshot().metadata()
                    )) {
                        diagnostics.recordSkipped(
                                "main-world mapchunk is outside world metadata bounds"
                        );
                        continue;
                    }
                    observedCoordinateConsumer.accept(observed);

                    byte[] payload = resultSet.getBytes("data");
                    if (payload == null) {
                        diagnostics.recordSkipped(
                                "mapchunk row has no payload"
                        );
                        continue;
                    }
                    payloadBytes = Math.addExact(
                            payloadBytes,
                            payload.length
                    );

                    ParseResult<MapChunk> parsedMapChunk =
                            mapChunkParser.parse(
                                    observed,
                                    payload
                            );
                    if (parsedMapChunk.isSuccess()) {
                        parsed++;
                        diagnostics.recordParsed();
                        consumer.accept(
                                parsedMapChunk.value().orElseThrow()
                        );
                    } else {
                        failed++;
                        diagnostics.recordFailed(
                                parsedMapChunk.error().orElse(
                                        "unknown mapchunk parse error"
                                )
                        );
                    }
                }
            }

            progress.done("Observed mapchunk discovery complete");
            return new MapChunkStreamStats(
                    expectedRows,
                    expectedRows == 0 ? 0 : 1,
                    rowsFound,
                    parsed,
                    failed,
                    payloadBytes
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot stream observed mapchunks: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    MapChunkStreamStats forEachMapChunkByCoordinate(
            SaveSession session,
            Collection<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            Consumer<MapChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(consumer, "consumer is required");
        return forEachMapChunkByCoordinateWithResults(
                session,
                coordinates,
                diagnostics,
                result -> result.decodedMapChunk().ifPresent(consumer),
                progress
        );
    }

    MapChunkStreamStats forEachMapChunkByCoordinate(
            SaveSession session,
            Collection<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            Consumer<MapChunk> consumer
    ) {
        return forEachMapChunkByCoordinate(
                session,
                coordinates,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    MapChunkStreamStats forEachMapChunkByCoordinateWithResults(
            SaveSession session,
            Collection<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            Consumer<MapChunkReadResult> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(coordinates, "coordinates is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        Map<Long, MapChunkCoordinate> requested = packedMapChunkRequests(
                coordinates
        );
        if (requested.isEmpty()) {
            return new MapChunkStreamStats(0, 0, 0, 0, 0, 0);
        }
        return forEachMapChunkByCoordinateWithResults(
                session.connection(),
                requested,
                diagnostics,
                consumer,
                progress
        );
    }

    MapChunkStreamStats forEachMapChunkByCoordinateWithResults(
            SaveSession session,
            Collection<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            Consumer<MapChunkReadResult> consumer
    ) {
        return forEachMapChunkByCoordinateWithResults(
                session,
                coordinates,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    private MapChunkStreamStats forEachMapChunkByCoordinateWithResults(
            Connection connection,
            Map<Long, MapChunkCoordinate> requestedByPackedPosition,
            ReadDiagnostics diagnostics,
            Consumer<MapChunkReadResult> consumer,
            ProgressReporter progress
    ) {
        progress.start("Reading mapchunks by exact position");
        int batchesExecuted = 0;
        int rowsFound = 0;
        int parsedMapChunks = 0;
        int failedMapChunks = 0;
        long payloadBytes = 0L;
        List<Map.Entry<Long, MapChunkCoordinate>> requested =
                new ArrayList<>(requestedByPackedPosition.entrySet());

        try {
            if (SqliteSaveTableInspector.tableMissing(
                    connection,
                    SaveTable.MAPCHUNK.tableName()
            )) {
                diagnostics.missingTable(SaveTable.MAPCHUNK.tableName());
                for (Map.Entry<Long, MapChunkCoordinate> entry : requested) {
                    consumer.accept(MapChunkReadResult.notCompleted(
                            entry.getValue(),
                            "mapchunk table is missing"
                    ));
                }
                progress.done(
                        "Exact mapchunk lookup unavailable: mapchunk table missing"
                );
                return new MapChunkStreamStats(
                        requested.size(),
                        0,
                        0,
                        0,
                        0,
                        0
                );
            }

            for (int start = 0;
                 start < requested.size();
                 start += DIRECT_MAPCHUNK_BATCH_SIZE) {
                if (Thread.currentThread().isInterrupted()) {
                    publishNotCompleted(
                            requested.subList(start, requested.size()),
                            consumer,
                            "exact mapchunk lookup interrupted"
                    );
                    progress.done("Exact mapchunk lookup interrupted");
                    return new MapChunkStreamStats(
                            requested.size(),
                            batchesExecuted,
                            rowsFound,
                            parsedMapChunks,
                            failedMapChunks,
                            payloadBytes
                    );
                }

                int end = Math.min(
                        start + DIRECT_MAPCHUNK_BATCH_SIZE,
                        requested.size()
                );
                MapChunkBatchStats batch = readMapChunkBatch(
                        connection,
                        requested.subList(start, end),
                        diagnostics,
                        consumer
                );
                batchesExecuted++;
                rowsFound += batch.rowsFound();
                parsedMapChunks += batch.parsedMapChunks();
                failedMapChunks += batch.failedMapChunks();
                payloadBytes = Math.addExact(
                        payloadBytes,
                        batch.payloadBytes()
                );
                progress.progress(
                        "Reading mapchunks by exact position",
                        end,
                        requested.size()
                );
            }

            progress.done("Exact mapchunk lookup complete");
            return new MapChunkStreamStats(
                    requested.size(),
                    batchesExecuted,
                    rowsFound,
                    parsedMapChunks,
                    failedMapChunks,
                    payloadBytes
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot read mapchunk table by exact position: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private Map<Long, MapChunkCoordinate> packedMapChunkRequests(
            Collection<MapChunkCoordinate> coordinates
    ) {
        Map<Long, MapChunkCoordinate> requested = new LinkedHashMap<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            Objects.requireNonNull(
                    coordinate,
                    "coordinates cannot contain null"
            );
            requested.putIfAbsent(
                    ChunkPosEncoder.encode(
                            coordinate.x(),
                            0,
                            coordinate.z(),
                            0
                    ),
                    coordinate
            );
        }
        return requested;
    }

    private MapChunkBatchStats readMapChunkBatch(
            Connection connection,
            List<Map.Entry<Long, MapChunkCoordinate>> requested,
            ReadDiagnostics diagnostics,
            Consumer<MapChunkReadResult> consumer
    ) throws SQLException {
        String sql =
                "SELECT position, data FROM \""
                        + SaveTable.MAPCHUNK.tableName()
                        + "\" WHERE position IN ("
                        + sqlPlaceholders(requested.size())
                        + ")";

        Map<Long, MapChunkCoordinate> requestedByPacked =
                new LinkedHashMap<>();
        for (Map.Entry<Long, MapChunkCoordinate> entry : requested) {
            requestedByPacked.put(entry.getKey(), entry.getValue());
        }

        Set<Long> found = new LinkedHashSet<>();
        int rowsFound = 0;
        int parsedMapChunks = 0;
        int failedMapChunks = 0;
        long payloadBytes = 0L;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            for (Long packedPosition : requestedByPacked.keySet()) {
                statement.setLong(parameterIndex++, packedPosition);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rowsFound++;
                    long packedPosition = resultSet.getLong("position");
                    MapChunkCoordinate coordinate =
                            requestedByPacked.get(packedPosition);
                    if (coordinate == null) {
                        throw new IllegalStateException(
                                "Exact mapchunk query returned an unrequested position"
                        );
                    }
                    found.add(packedPosition);

                    byte[] payload = resultSet.getBytes("data");
                    if (payload == null) {
                        diagnostics.recordSkipped(
                                "mapchunk row has null payload"
                        );
                        consumer.accept(MapChunkReadResult.unreadable(
                                coordinate,
                                "mapchunk row has null payload"
                        ));
                        continue;
                    }

                    payloadBytes = Math.addExact(
                            payloadBytes,
                            payload.length
                    );
                    ParseResult<MapChunk> parsed = mapChunkParser.parse(
                            coordinate,
                            payload
                    );

                    if (parsed.isSuccess()) {
                        MapChunk mapChunk = parsed.value().orElseThrow();
                        diagnostics.recordParsed();
                        consumer.accept(MapChunkReadResult.decoded(mapChunk));
                        parsedMapChunks++;
                    } else {
                        String reason = parsed.error().orElse(
                                "unknown mapchunk parse error"
                        );
                        diagnostics.recordFailed(reason);
                        consumer.accept(MapChunkReadResult.unreadable(
                                coordinate,
                                reason
                        ));
                        failedMapChunks++;
                    }
                }
            }
        }

        for (Map.Entry<Long, MapChunkCoordinate> entry : requested) {
            if (!found.contains(entry.getKey())) {
                consumer.accept(MapChunkReadResult.absent(entry.getValue()));
            }
        }

        return new MapChunkBatchStats(
                rowsFound,
                parsedMapChunks,
                failedMapChunks,
                payloadBytes
        );
    }

    private void publishNotCompleted(
            List<Map.Entry<Long, MapChunkCoordinate>> requested,
            Consumer<MapChunkReadResult> consumer,
            String detail
    ) {
        for (Map.Entry<Long, MapChunkCoordinate> entry : requested) {
            consumer.accept(MapChunkReadResult.notCompleted(
                    entry.getValue(),
                    detail
            ));
        }
    }

    private String sqlPlaceholders(int count) {
        return "?, ".repeat(count - 1) + "?";
    }

    private record MapChunkBatchStats(
            int rowsFound,
            int parsedMapChunks,
            int failedMapChunks,
            long payloadBytes
    ) {
    }
}
