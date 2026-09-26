package cartographer.save;

import cartographer.model.ChunkPosition;
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
import java.util.LinkedHashSet;
import java.util.List;
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
                    // Catalog membership describes source existence, not
                    // parser success. Publish the coordinate before reading
                    // or parsing the row payload so failed derived decoding
                    // can never be misclassified as source absence.
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
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(coordinates, "coordinates is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");
        Set<Long> packedPositions = packedMapChunkPositions(coordinates);
        if (packedPositions.isEmpty()) {
            return new MapChunkStreamStats(0, 0, 0, 0, 0, 0);
        }
        return forEachMapChunkByCoordinate(
                session.connection(),
                packedPositions,
                diagnostics,
                consumer,
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

    private MapChunkStreamStats forEachMapChunkByCoordinate(
            Connection connection,
            Set<Long> packedPositions,
            ReadDiagnostics diagnostics,
            Consumer<MapChunk> consumer,
            ProgressReporter progress
    ) {
        progress.start("Reading mapchunks by exact position");
        int batchesExecuted = 0;
        int rowsFound = 0;
        int parsedMapChunks = 0;
        int failedMapChunks = 0;
        long payloadBytes = 0L;
        try {
            if (SqliteSaveTableInspector.tableMissing(
                    connection,
                    SaveTable.MAPCHUNK.tableName()
            )) {
                diagnostics.missingTable(SaveTable.MAPCHUNK.tableName());
                progress.done(
                        "Exact mapchunk lookup unavailable: mapchunk table missing"
                );
                return new MapChunkStreamStats(
                        packedPositions.size(),
                        0,
                        0,
                        0,
                        0,
                        0
                );
            }
            List<Long> requested = new ArrayList<>(packedPositions);
            for (int start = 0;
                 start < requested.size();
                 start += DIRECT_MAPCHUNK_BATCH_SIZE) {
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
                payloadBytes += batch.payloadBytes();
                progress.progress(
                        "Reading mapchunks by exact position",
                        end,
                        requested.size()
                );
            }
            progress.done("Exact mapchunk lookup complete");
            return new MapChunkStreamStats(
                    packedPositions.size(),
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

    private Set<Long> packedMapChunkPositions(
            Collection<MapChunkCoordinate> coordinates
    ) {
        Set<Long> packedPositions = new LinkedHashSet<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            Objects.requireNonNull(
                    coordinate,
                    "coordinates cannot contain null"
            );
            packedPositions.add(
                    ChunkPosEncoder.encode(
                            coordinate.x(),
                            0,
                            coordinate.z(),
                            0
                    )
            );
        }
        return packedPositions;
    }

    private MapChunkBatchStats readMapChunkBatch(
            Connection connection,
            List<Long> packedPositions,
            ReadDiagnostics diagnostics,
            Consumer<MapChunk> consumer
    ) throws SQLException {
        String sql =
                "SELECT position, data FROM \""
                        + SaveTable.MAPCHUNK.tableName()
                        + "\" WHERE position IN ("
                        + sqlPlaceholders(packedPositions.size())
                        + ")";

        int rowsFound = 0;
        int parsedMapChunks = 0;
        int failedMapChunks = 0;
        long payloadBytes = 0L;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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

                    ChunkPosition position = ChunkPosDecoder.decode(
                            resultSet.getLong("position")
                    );
                    MapChunkCoordinate coordinate = new MapChunkCoordinate(
                            position.x(),
                            position.z()
                    );
                    byte[] payload = resultSet.getBytes("data");

                    if (payload == null) {
                        diagnostics.recordSkipped(
                                "mapchunk row has null payload"
                        );
                        continue;
                    }

                    payloadBytes += payload.length;
                    ParseResult<MapChunk> parsed = mapChunkParser.parse(
                            coordinate,
                            payload
                    );

                    if (parsed.isSuccess()) {
                        MapChunk mapChunk = parsed.value().orElseThrow();
                        diagnostics.recordParsed();
                        consumer.accept(mapChunk);
                        parsedMapChunks++;
                    } else {
                        diagnostics.recordFailed(
                                parsed.error().orElse(
                                        "unknown mapchunk parse error"
                                )
                        );
                        failedMapChunks++;
                    }
                }
            }
        }

        return new MapChunkBatchStats(
                rowsFound,
                parsedMapChunks,
                failedMapChunks,
                payloadBytes
        );
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
