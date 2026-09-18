package cartographer.save;

import cartographer.cli.CommandException;
import cartographer.application.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ServerChunkPayload;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.ChunkDecodeWorkspace;
import cartographer.parser.ChunkDecodeProfile;
import cartographer.parser.ChunkPaletteProbe;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.parser.ServerMapRegionParser;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

public class VcdbsReader {

    private static final int DIRECT_CHUNK_BATCH_SIZE =
            256;

    private static final int DIRECT_MAPCHUNK_BATCH_SIZE =
            256;

    private final PlayerDataParser playerDataParser;
    private final MapChunkParser mapChunkParser;
    private final ChunkParser chunkParser;
    private final RegistryParser registryParser;
    private final ServerMapRegionParser serverMapRegionParser;
    private final SqliteSaveConnection connectionFactory;
    private final int chunkDecodeWorkerCount;
    private final int chunkDecodeMaxInFlight;

    public WorldPosition readPlayerPosition(
            Path savePath
    ) {
        return readPlayerPosition(
                savePath,
                ProgressReporter.NONE
        );
    }

    public List<MapChunk> readMapChunksAround(
            Path savePath,
            WorldPosition center,
            int radiusBlocks,
            ReadDiagnostics diagnostics
    ) {
        return readMapChunksAround(
                savePath,
                center,
                radiusBlocks,
                diagnostics,
                ProgressReporter.NONE
        );
    }

    public MapChunkStreamStats forEachMapChunkByCoordinate(
            Path savePath,
            Collection<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            Consumer<MapChunk> consumer
    ) {
        return forEachMapChunkByCoordinate(
                savePath,
                coordinates,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    /**
     * Visits main-world mapchunks by exact INTEGER PRIMARY KEY lookup.
     * Each requested coordinate is packed as x, y=0, z, dimension=0.
     * SQLite result order is unspecified and must not be relied upon.
     */
    public MapChunkStreamStats forEachMapChunkByCoordinate(
            Path savePath,
            Collection<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            Consumer<MapChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(coordinates, "coordinates is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        Set<Long> packedPositions = packedMapChunkPositions(coordinates);

        if (packedPositions.isEmpty()) {
            return new MapChunkStreamStats(0, 0, 0, 0, 0, 0);
        }

        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            return forEachMapChunkByCoordinate(
                    connection, packedPositions, diagnostics, consumer, progress
            );
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read mapchunk table by exact position: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public List<ParsedChunk> readChunksAround(
            Path savePath,
            WorldPosition center,
            int radiusBlocks,
            ReadDiagnostics diagnostics
    ) {
        return readChunksAround(
                savePath,
                center,
                radiusBlocks,
                diagnostics,
                ProgressReporter.NONE
        );
    }

    public ChunkStreamStats forEachChunkByPosition(
            Path savePath,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer
    ) {
        return forEachChunkByPosition(
                savePath,
                positions,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIds(
            Path savePath,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer
    ) {
        return forEachChunkByPositionMatchingBlockIds(
                savePath,
                positions,
                wantedBlockIds,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    public ChunkStreamStats forEachChunkByPositionAdaptive(
            Path savePath,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer
    ) {
        return forEachChunkByPositionAdaptive(
                savePath,
                positions,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    public ChunkStreamStats forEachChunkByPositionAdaptive(
            Path savePath,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        }

        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            return forEachChunkByPositionAdaptive(
                    connection, packedPositions, diagnostics, consumer, progress
            );
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot open save for adaptive chunk traversal: "
                            + exception.getMessage(),
                    exception
            );
        }
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
                session.connection(), packedPositions, diagnostics, consumer, progress
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
            ProgressReporter progress
    ) {
        boolean tableStream;
        try {
            tableStream = shouldUseChunkTableStream(connection, packedPositions.size());
        } catch (SQLException exception) {
            tableStream = false;
        }
        if (tableStream) {
            try {
                return forEachChunkByPositionTableStream(
                        connection, packedPositions, diagnostics, consumer, progress
                );
            } catch (SQLException exception) {
                throw new CommandException(
                        "Cannot scan chunk table for exact positions: "
                                + exception.getMessage(),
                        exception
                );
            }
        }
        try {
            return forEachChunkByPosition(
                    connection, packedPositions, diagnostics, consumer, progress
            );
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read chunk table by exact position: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
            Path savePath,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer
    ) {
        return forEachChunkByPositionMatchingBlockIdsAdaptive(
                savePath,
                positions,
                wantedBlockIds,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
            Path savePath,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(wantedBlockIds, "wantedBlockIds is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        if (uniqueWantedBlockIds(wantedBlockIds).length == 0) {
            throw new IllegalArgumentException("wantedBlockIds cannot be empty");
        }

        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0);
        }

        if (shouldUseChunkTableStream(savePath, packedPositions.size())) {
            return forEachChunkByPositionMatchingBlockIdsTableStream(
                    savePath,
                    positions,
                    wantedBlockIds,
                    diagnostics,
                    consumer,
                    progress
            );
        }
        return forEachChunkByPositionMatchingBlockIds(
                savePath,
                positions,
                wantedBlockIds,
                diagnostics,
                consumer,
                progress
        );
    }

    /**
     * Visits exact chunk positions with the selective decoder and reports
     * availability independently from whether a ParsedChunk was delivered.
     */
    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
            Path savePath,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(wantedBlockIds, "wantedBlockIds is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");
        if (uniqueWantedBlockIds(wantedBlockIds).length == 0) {
            throw new IllegalArgumentException("wantedBlockIds cannot be empty");
        }
        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            return forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    connection,
                    positions,
                    wantedBlockIds,
                    diagnostics,
                    consumer,
                    progress
            );
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot open save for selective chunk coverage: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public MapChunkStreamStats forEachMapChunkByCoordinate(
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
                session.connection(), packedPositions, diagnostics, consumer, progress
        );
    }

    public MapChunkStreamStats forEachMapChunkByCoordinate(
            SaveSession session,
            Collection<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            Consumer<MapChunk> consumer
    ) {
        return forEachMapChunkByCoordinate(
                session, coordinates, diagnostics, consumer, ProgressReporter.NONE
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
        long payloadBytes = 0;
        try {
            if (tableMissing(connection, SaveTable.MAPCHUNK.tableName())) {
                diagnostics.missingTable(SaveTable.MAPCHUNK.tableName());
                progress.done("Exact mapchunk lookup unavailable: mapchunk table missing");
                return new MapChunkStreamStats(packedPositions.size(), 0, 0, 0, 0, 0);
            }
            List<Long> requested = new ArrayList<>(packedPositions);
            for (int start = 0; start < requested.size(); start += DIRECT_MAPCHUNK_BATCH_SIZE) {
                int end = Math.min(start + DIRECT_MAPCHUNK_BATCH_SIZE, requested.size());
                MapChunkBatchStats batch = readMapChunkBatch(
                        connection, requested.subList(start, end), diagnostics, consumer
                );
                batchesExecuted++;
                rowsFound += batch.rowsFound();
                parsedMapChunks += batch.parsedMapChunks();
                failedMapChunks += batch.failedMapChunks();
                payloadBytes += batch.payloadBytes();
                progress.progress("Reading mapchunks by exact position", end, requested.size());
            }
            progress.done("Exact mapchunk lookup complete");
            return new MapChunkStreamStats(
                    packedPositions.size(), batchesExecuted, rowsFound,
                    parsedMapChunks, failedMapChunks, payloadBytes
            );
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read mapchunk table by exact position: " + exception.getMessage(),
                    exception
            );
        }
    }

    private Set<Long> packedMapChunkPositions(Collection<MapChunkCoordinate> coordinates) {
        Set<Long> packedPositions = new LinkedHashSet<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            Objects.requireNonNull(coordinate, "coordinates cannot contain null");
            packedPositions.add(ChunkPosEncoder.encode(coordinate.x(), 0, coordinate.z(), 0));
        }
        return packedPositions;
    }

    /**
     * Session-owned variant. The session connection is borrowed and never
     * closed by this reader.
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

    /**
     * Compatibility overload for callers that still use the CLI progress
     * reporter. The neutral application callback is the primary contract.
     */
    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
            Path savePath,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer,
            cartographer.cli.ProgressReporter progress
    ) {
        return forEachChunkByPositionMatchingBlockIdsWithCoverage(
                savePath,
                positions,
                wantedBlockIds,
                diagnostics,
                consumer,
                (ProgressReporter) progress
        );
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
            Path savePath,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer
    ) {
        return forEachChunkByPositionMatchingBlockIdsWithCoverage(
                savePath,
                positions,
                wantedBlockIds,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIds(
            Path savePath,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(wantedBlockIds, "wantedBlockIds is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        int[] uniqueWantedBlockIds =
                uniqueWantedBlockIds(wantedBlockIds);

        if (uniqueWantedBlockIds.length == 0) {
            throw new IllegalArgumentException(
                    "wantedBlockIds cannot be empty"
            );
        }

        Set<Long> packedPositions =
                packedUniquePositions(positions);

        if (packedPositions.isEmpty()) {
            return new SelectiveChunkStreamStats(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        progress.start(
                "Reading selective chunks by exact position"
        );

        int batchesExecuted = 0;
        int rowsFound = 0;
        SelectiveDecodeCounters counters = new SelectiveDecodeCounters();

        try (Connection connection =
                     connectionFactory.openReadOnly(savePath)) {

            if (tableMissing(
                    connection,
                    SaveTable.CHUNK.tableName()
            )) {
                diagnostics.missingTable(
                        SaveTable.CHUNK.tableName()
                );
                progress.done(
                        "Selective chunk lookup unavailable: chunk table missing"
                );

                return new SelectiveChunkStreamStats(
                        packedPositions.size(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                );
            }

            List<Long> requested = new ArrayList<>(packedPositions);
            try (ChunkDecodeWorkspacePool workspaces = new ChunkDecodeWorkspacePool(chunkDecodeWorkerCount);
                 BoundedStreamingDecodePipeline<SelectiveDecodeOutcome> pipeline =
                         new BoundedStreamingDecodePipeline<>(
                                 chunkDecodeWorkerCount,
                                 chunkDecodeMaxInFlight,
                                 outcome -> applySelectiveOutcome(
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
                    SelectiveBatchStats batch = readSelectiveChunkBatch(
                            connection,
                            requested.subList(start, end),
                            uniqueWantedBlockIds,
                            diagnostics,
                            pipeline,
                            workspaces
                    );
                    batchesExecuted++;
                    rowsFound += batch.rowsFound();
                    counters.payloadBytes += batch.payloadBytes();
                    progress.progress(
                            "Reading selective chunks by exact position",
                            end,
                            requested.size()
                    );
                }
                pipeline.finish();
            }

            progress.done(
                    "Selective chunk lookup complete"
            );

            return new SelectiveChunkStreamStats(
                    packedPositions.size(),
                    batchesExecuted,
                    rowsFound,
                    counters.payloadsParsed,
                    counters.paletteRejectedChunks,
                    counters.fullyDecodedChunks,
                    counters.failedChunks,
                    counters.payloadBytes
            );
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read chunk table selectively: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    ChunkStreamStats forEachChunkByPositionTableStream(
            Path savePath,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        Set<Long> packedPositions = packedUniquePositions(positions);
        if (packedPositions.isEmpty()) {
            return new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        }

        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            return forEachChunkByPositionTableStream(
                    connection, packedPositions, diagnostics, consumer, progress
            );
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot scan chunk table for exact positions: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsTableStream(
            Path savePath,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
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

        progress.start("Scanning chunk table selectively for exact positions");
        SelectiveDecodeCounters counters = new SelectiveDecodeCounters();
        int rowsFound = 0;

        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            if (tableMissing(connection, SaveTable.CHUNK.tableName())) {
                diagnostics.missingTable(SaveTable.CHUNK.tableName());
                progress.done(
                        "Selective chunk table scan unavailable: chunk table missing"
                );
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
                            position.x(), position.y(), position.z()
                    );
                    pipeline.submit(() -> decodeSelectiveChunk(
                            coordinate,
                            payload,
                            uniqueWantedBlockIds,
                            workspaces
                    ));
                }
                pipeline.finish();
            }

            progress.done("Selective chunk table scan complete");
            return new SelectiveChunkStreamStats(
                    packedPositions.size(),
                    1,
                    rowsFound,
                    counters.payloadsParsed,
                    counters.paletteRejectedChunks,
                    counters.fullyDecodedChunks,
                    counters.failedChunks,
                    counters.payloadBytes
            );
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot scan chunk table selectively for exact positions: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Visits chunks found by exact packed primary-key lookup. SQL result order
     * is unspecified and must not be treated as request order.
     */
    public ChunkStreamStats forEachChunkByPosition(
            Path savePath,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(positions, "positions is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        Set<Long> packedPositions =
                packedUniquePositions(positions);

        if (packedPositions.isEmpty()) {
            return new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        }
        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            return forEachChunkByPosition(
                    connection, packedPositions, diagnostics, consumer, progress
            );
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot open save for exact chunk traversal: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private ChunkStreamStats forEachChunkByPositionTableStream(
            Connection connection,
            Set<Long> packedPositions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) throws SQLException {
        progress.start("Scanning chunk table for exact positions");
        ChunkDecodeCounters counters = new ChunkDecodeCounters();
        int rowsFound = 0;
        if (tableMissing(connection, SaveTable.CHUNK.tableName())) {
            diagnostics.missingTable(SaveTable.CHUNK.tableName());
            progress.done("Exact chunk table scan unavailable: chunk table missing");
            return new ChunkStreamStats(packedPositions.size(), 0, 0, 0, 0, 0);
        }
        try (ChunkDecodeWorkspacePool workspaces = new ChunkDecodeWorkspacePool(chunkDecodeWorkerCount);
             BoundedStreamingDecodePipeline<ChunkDecodeOutcome> pipeline =
                     new BoundedStreamingDecodePipeline<>(
                             chunkDecodeWorkerCount,
                             chunkDecodeMaxInFlight,
                             outcome -> applyChunkOutcome(outcome, diagnostics, consumer, counters));
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT position, data FROM \"" + SaveTable.CHUNK.tableName() + "\"");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long packedPosition = resultSet.getLong("position");
                if (!packedPositions.contains(packedPosition)) continue;
                rowsFound++;
                ChunkPosition position = ChunkPosDecoder.decode(packedPosition);
                byte[] payload = resultSet.getBytes("data");
                if (payload == null) {
                    diagnostics.recordSkipped("chunk row has null payload");
                    continue;
                }
                counters.payloadBytes += payload.length;
                ChunkCoordinate coordinate = new ChunkCoordinate(position.x(), position.y(), position.z());
                pipeline.submit(() -> decodeChunk(coordinate, payload, workspaces));
            }
            pipeline.finish();
        }
        progress.done("Exact chunk table scan complete");
        return new ChunkStreamStats(
                packedPositions.size(), 1, rowsFound,
                counters.parsedChunks, counters.failedChunks, counters.payloadBytes
        );
    }

    private ChunkStreamStats forEachChunkByPosition(
            Connection connection,
            Set<Long> packedPositions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) throws SQLException {
        progress.start("Reading chunks by exact position");
        int batchesExecuted = 0;
        int rowsFound = 0;
        ChunkDecodeCounters counters = new ChunkDecodeCounters();
        if (tableMissing(connection, SaveTable.CHUNK.tableName())) {
            diagnostics.missingTable(SaveTable.CHUNK.tableName());
            progress.done("Exact chunk lookup unavailable: chunk table missing");
            return new ChunkStreamStats(packedPositions.size(), 0, 0, 0, 0, 0);
        }
        List<Long> requested = new ArrayList<>(packedPositions);
        try (ChunkDecodeWorkspacePool workspaces = new ChunkDecodeWorkspacePool(chunkDecodeWorkerCount);
             BoundedStreamingDecodePipeline<ChunkDecodeOutcome> pipeline =
                     new BoundedStreamingDecodePipeline<>(
                             chunkDecodeWorkerCount,
                             chunkDecodeMaxInFlight,
                             outcome -> applyChunkOutcome(outcome, diagnostics, consumer, counters))) {
            for (int start = 0; start < requested.size(); start += DIRECT_CHUNK_BATCH_SIZE) {
                int end = Math.min(start + DIRECT_CHUNK_BATCH_SIZE, requested.size());
                BatchStats batch = readChunkBatch(
                        connection, requested.subList(start, end), diagnostics, pipeline, workspaces
                );
                batchesExecuted++;
                rowsFound += batch.rowsFound();
                counters.payloadBytes += batch.payloadBytes();
                progress.progress("Reading chunks by exact position", end, requested.size());
            }
            pipeline.finish();
        }
        progress.done("Exact chunk lookup complete");
        return new ChunkStreamStats(
                packedPositions.size(), batchesExecuted, rowsFound,
                counters.parsedChunks, counters.failedChunks, counters.payloadBytes
        );
    }

    private boolean shouldUseChunkTableStream(
            Path savePath,
            int uniqueRequestedPositions
    ) {
        if (uniqueRequestedPositions <= DIRECT_CHUNK_BATCH_SIZE) {
            return false;
        }

        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            return shouldUseChunkTableStream(connection, uniqueRequestedPositions);
        } catch (SQLException exception) {
            return false;
        }
    }

    private boolean shouldUseChunkTableStream(
            Connection connection,
            int uniqueRequestedPositions
    ) throws SQLException {
        if (uniqueRequestedPositions <= DIRECT_CHUNK_BATCH_SIZE) {
            return false;
        }
        if (tableMissing(connection, SaveTable.CHUNK.tableName())) {
            return false;
        }

        long limit = (long) uniqueRequestedPositions + 1L;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM \"" + SaveTable.CHUNK.tableName()
                        + "\" LIMIT ?"
        )) {
            statement.setLong(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                int rowsSeen = 0;
                while (resultSet.next()) {
                    rowsSeen++;
                    if (rowsSeen > uniqueRequestedPositions) {
                        return false;
                    }
                }
                return true;
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

    private BatchStats readChunkBatch(
            Connection connection,
            List<Long> packedPositions,
            ReadDiagnostics diagnostics,
            BoundedStreamingDecodePipeline<ChunkDecodeOutcome> pipeline,
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

            try (ResultSet resultSet =
                         statement.executeQuery()) {
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

                    pipeline.submit(() -> decodeChunk(coordinate, payload, workspaces));
                }
            }
        }

        return new BatchStats(
                rowsFound,
                payloadBytes
        );
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
        long payloadBytes = 0;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < packedPositions.size(); index++) {
                statement.setLong(index + 1, packedPositions.get(index));
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
            if (tableMissing(connection, SaveTable.CHUNK.tableName())) {
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
                            consumer,
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
            throw new CommandException(
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
            Consumer<SelectiveChunkVisit> consumer,
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

    private boolean containsWantedBlock(
            ChunkPaletteProbe palette,
            int[] wantedBlockIds
    ) {
        for (int wantedBlockId : wantedBlockIds) {
            if (palette.contains(wantedBlockId)) {
                return true;
            }
        }

        return false;
    }

    private ChunkDecodeOutcome decodeChunk(
            ChunkCoordinate coordinate,
            byte[] payload,
            ChunkDecodeWorkspacePool workspaces
    ) {
        ChunkDecodeWorkspace workspace = workspaces.borrow();
        try {
            return decodeChunk(coordinate, payload, workspace);
        } finally {
            workspaces.release(workspace);
        }
    }

    private ChunkDecodeOutcome decodeChunk(
            ChunkCoordinate coordinate,
            byte[] payload,
            ChunkDecodeWorkspace workspace
    ) {
        ParseResult<ParsedChunk> parsed = chunkParser.parse(
                coordinate, payload, ChunkDecodeProfile.BLOCKS_AND_LIQUIDS, workspace
        );
        if (parsed.isSuccess()) {
            return ChunkDecodeOutcome.success(parsed.value().orElseThrow());
        }
        return ChunkDecodeOutcome.failure(
                parsed.error().orElse("unknown chunk parse error")
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
        ParseResult<ServerChunkPayload> parsedPayload = chunkParser.parsePayload(payload);
        if (!parsedPayload.isSuccess()) {
            return SelectiveDecodeOutcome.failure(
                    false,
                    parsedPayload.error().orElse("unknown ServerChunk parse error")
            );
        }

        ServerChunkPayload serverChunk = parsedPayload.value().orElseThrow();
        ParseResult<ChunkPaletteProbe> palette = chunkParser.probeBlockPalette(serverChunk, workspace);
        if (!palette.isSuccess()) {
            return SelectiveDecodeOutcome.failure(
                    true,
                    palette.error().orElse("unknown block palette probe error")
            );
        }

        if (!containsWantedBlock(palette.value().orElseThrow(), wantedBlockIds)) {
            return SelectiveDecodeOutcome.rejected();
        }

        ParseResult<ParsedChunk> parsedChunk = chunkParser.parse(
                coordinate,
                serverChunk,
                ChunkDecodeProfile.BLOCKS_ONLY,
                workspace
        );
        if (parsedChunk.isSuccess()) {
            return SelectiveDecodeOutcome.success(parsedChunk.value().orElseThrow());
        }
        return SelectiveDecodeOutcome.failure(
                true,
                parsedChunk.error().orElse("unknown chunk decode error")
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

    private String sqlPlaceholders(
            int count
    ) {
        return "?, ".repeat(count - 1) + "?";
    }

    private record BatchStats(
            int rowsFound,
            long payloadBytes
    ) {
    }

    private record MapChunkBatchStats(
            int rowsFound,
            int parsedMapChunks,
            int failedMapChunks,
            long payloadBytes
    ) {
    }

    private record SelectiveBatchStats(
            int rowsFound,
            long payloadBytes
    ) {
    }

    private static final class ChunkDecodeCounters {
        private int parsedChunks;
        private int failedChunks;
        private long payloadBytes;
    }

    private static final class SelectiveDecodeCounters {
        private int payloadsParsed;
        private int paletteRejectedChunks;
        private int fullyDecodedChunks;
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

    public Map<Integer, BlockInfo> readBlockRegistry(
            Path savePath
    ) {
        return readBlockRegistry(
                savePath,
                ProgressReporter.NONE
        );
    }

    public List<ServerMapRegion> readMapRegions(
            Path savePath,
            ReadDiagnostics diagnostics
    ) {
        return readMapRegions(
                savePath,
                diagnostics,
                ProgressReporter.NONE
        );
    }

    public VcdbsReader(
            PlayerDataParser playerDataParser,
            MapChunkParser mapChunkParser,
            ChunkParser chunkParser,
            RegistryParser registryParser
    ) {
        this(
                playerDataParser,
                mapChunkParser,
                chunkParser,
                registryParser,
                new SqliteSaveConnection()
        );
    }

    public VcdbsReader(
            PlayerDataParser playerDataParser,
            MapChunkParser mapChunkParser,
            ChunkParser chunkParser,
            RegistryParser registryParser,
            SqliteSaveConnection connectionFactory
    ) {
        this(
                playerDataParser,
                mapChunkParser,
                chunkParser,
                registryParser,
                connectionFactory,
                defaultChunkDecodeWorkerCount(),
                defaultChunkDecodeMaxInFlight(defaultChunkDecodeWorkerCount())
        );
    }

    VcdbsReader(
            PlayerDataParser playerDataParser,
            MapChunkParser mapChunkParser,
            ChunkParser chunkParser,
            RegistryParser registryParser,
            SqliteSaveConnection connectionFactory,
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
        this.playerDataParser =
                playerDataParser;

        this.mapChunkParser =
                mapChunkParser;

        this.chunkParser =
                chunkParser;

        this.registryParser =
                registryParser;

        this.serverMapRegionParser =
                new ServerMapRegionParser();

        this.connectionFactory =
                connectionFactory;

        this.chunkDecodeWorkerCount = chunkDecodeWorkerCount;
        this.chunkDecodeMaxInFlight = chunkDecodeMaxInFlight;
    }

    private static int defaultChunkDecodeWorkerCount() {
        return Math.clamp(
                Runtime.getRuntime().availableProcessors(),
                1,
                4
        );
    }

    private static int defaultChunkDecodeMaxInFlight(int workerCount) {
        return Math.max(workerCount + 1, workerCount * 2);
    }

    public WorldPosition readPlayerPosition(
            Path savePath,
            ProgressReporter progress
    ) {
        List<SaveRecord> records =
                readPlayerRecords(
                        savePath,
                        progress
                );

        SaveRecord selected =
                selectDefaultPlayer(
                        records
                )
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "Table playerdata exists but contains no selectable rows"
                                        )
                        );

        return parsePlayerPosition(
                selected,
                progress
        );
    }

    public WorldPosition readPlayerPosition(
            SaveSession session,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        List<SaveRecord> records;
        try {
            records = readPlayerRecords(session.connection(), progress);
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read playerdata: " + exception.getMessage(),
                    exception
            );
        }
        SaveRecord selected = selectDefaultPlayer(records)
                .orElseThrow(() -> new CommandException(
                        "Table playerdata exists but contains no selectable rows"));
        return parsePlayerPosition(selected, progress);
    }

    public WorldPosition readPlayerPosition(
            Path savePath,
            String playerSelector,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(
                playerSelector,
                "playerSelector is required"
        );

        List<SaveRecord> records =
                readPlayerRecords(
                        savePath,
                        progress
                );

        SaveRecord selected =
                selectPlayer(
                        records,
                        playerSelector
                )
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "No playerdata row matched selector: "
                                                        + playerSelector
                                        )
                        );

        return parsePlayerPosition(
                selected,
                progress
        );
    }

    private List<SaveRecord> readPlayerRecords(
            Path savePath,
            ProgressReporter progress
    ) {
        progress.start(
                "Opening save read-only"
        );

        try (Connection connection =
                     connectionFactory.openReadOnly(
                             savePath
                     )) {

            progress.done(
                    "Save opened read-only"
            );

            return readPlayerRecords(connection, progress);

        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read playerdata: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private List<SaveRecord> readPlayerRecords(
            Connection connection,
            ProgressReporter progress
    ) throws SQLException {
        ensurePlayerDataTable(connection);
        List<SaveRecord> records = readRecords(
                connection,
                SaveTable.PLAYERDATA.tableName(),
                250,
                progress
        );
        if (records.isEmpty()) {
            throw new CommandException("Table playerdata exists but contains no rows");
        }
        return records;
    }

    private WorldPosition parsePlayerPosition(
            SaveRecord selected,
            ProgressReporter progress
    ) {
        progress.start(
                "Parsing player position"
        );

        ParseResult<WorldPosition> result =
                playerDataParser.parse(
                        selected.payload()
                );

        WorldPosition position =
                result.value()
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                result.error()
                                                        .orElse(
                                                                "Unable to parse player position"
                                                        )
                                        )
                        );

        progress.done(
                "Player position parsed"
        );

        return position;
    }

    public List<MapChunk> readMapChunksAround(
            Path savePath,
            WorldPosition center,
            int radiusBlocks,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        progress.start(
                "Opening save read-only"
        );

        try (Connection connection =
                     connectionFactory.openReadOnly(
                             savePath
                     )) {

            progress.done(
                    "Save opened read-only"
            );

            if (tableMissing(
                    connection,
                    SaveTable.MAPCHUNK.tableName()
            )) {
                diagnostics.missingTable(
                        SaveTable.MAPCHUNK.tableName()
                );

                return List.of();
            }

            return readMapChunksAroundFromResultSet(
                    connection,
                    center,
                    radiusBlocks,
                    diagnostics,
                    progress
            );

        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read mapchunk table: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public List<ParsedChunk> readChunksAround(
            Path savePath,
            WorldPosition center,
            int radiusBlocks,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        progress.start(
                "Opening save read-only"
        );

        try (Connection connection =
                     connectionFactory.openReadOnly(
                             savePath
                     )) {

            progress.done(
                    "Save opened read-only"
            );

            if (tableMissing(
                    connection,
                    SaveTable.CHUNK.tableName()
            )) {
                diagnostics.missingTable(
                        SaveTable.CHUNK.tableName()
                );

                return List.of();
            }

            return readChunksAroundFromResultSet(
                    connection,
                    center,
                    radiusBlocks,
                    diagnostics,
                    progress
            );

        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read chunk table: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public Map<Integer, BlockInfo> readBlockRegistry(
            Path savePath,
            ProgressReporter progress
    ) {
        progress.start(
                "Opening save read-only"
        );

        try (Connection connection =
                     connectionFactory.openReadOnly(
                             savePath
                     )) {

            progress.done(
                    "Save opened read-only"
            );

            progress.start(
                    "Reading block registry"
            );

            Map<Integer, BlockInfo> registry =
                    readBlockRegistry(
                            connection
                    );

            progress.done(
                    "Block registry read"
            );

            return registry;

        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read block registry: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public List<ServerMapRegion> readMapRegions(
            Path savePath,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        progress.start(
                "Opening save read-only"
        );

        try (Connection connection =
                     connectionFactory.openReadOnly(
                             savePath
                     )) {

            progress.done(
                    "Save opened read-only"
            );

            if (tableMissing(
                    connection,
                    SaveTable.MAPREGION.tableName()
            )) {
                diagnostics.missingTable(
                        SaveTable.MAPREGION.tableName()
                );

                return List.of();
            }

            return readMapRegionsFromResultSet(
                    connection,
                    diagnostics,
                    progress
            );

        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read mapregion table: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    /** Reads registry data from an already-open session-owned read-only connection. */
    protected Map<Integer, BlockInfo> readBlockRegistry(
            Connection connection
    ) throws SQLException {
        if (tableMissing(
                connection,
                SaveTable.GAMEDATA.tableName()
        )) {
            return Map.of();
        }

        Map<Integer, BlockInfo> blocks =
                new HashMap<>();

        for (SaveRecord record :
                readRecords(
                        connection,
                        SaveTable.GAMEDATA.tableName(),
                        500,
                        ProgressReporter.NONE
                )) {

            blocks.putAll(
                    registryParser.parse(
                            record.payload()
                    )
            );
        }

        return blocks;
    }

    private List<ServerMapRegion> readMapRegionsFromResultSet(
            Connection connection,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) throws SQLException {
        List<ServerMapRegion> regions =
                new ArrayList<>();

        int expectedRows =
                countRows(
                        connection,
                        SaveTable.MAPREGION.tableName()
                );

        String sql =
                "SELECT position, data FROM \""
                        + SaveTable.MAPREGION.tableName()
                        + "\"";

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     );

             ResultSet resultSet =
                     statement.executeQuery()) {

            int row =
                    0;

            while (resultSet.next()) {
                row++;

                progress.progress(
                        "Parsing mapregions",
                        row,
                        expectedRows
                );

                Optional<MapRegionCoordinate> coordinate =
                        mapRegionCoordinateFromPackedPosition(
                                resultSet.getObject(
                                        "position"
                                )
                        );

                if (coordinate.isEmpty()) {
                    diagnostics.recordSkipped(
                            "mapregion row has no readable coordinate"
                    );

                    continue;
                }

                MapRegionCoordinate regionCoordinate =
                        coordinate.orElseThrow();

                byte[] payload =
                        resultSet.getBytes(
                                "data"
                        );

                ParseResult<ServerMapRegion> parsed =
                        serverMapRegionParser.parse(
                                regionCoordinate,
                                payload
                        );

                if (parsed.isSuccess()) {
                    diagnostics.recordParsed();

                    regions.add(
                            parsed.value()
                                    .orElseThrow()
                    );

                } else {
                    diagnostics.recordFailed(
                            parsed.error()
                                    .orElse(
                                            "unknown mapregion parse error"
                                    )
                    );
                }
            }
        }

        return regions;
    }

    private List<MapChunk> readMapChunksAroundFromResultSet(
            Connection connection,
            WorldPosition center,
            int radiusBlocks,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) throws SQLException {
        List<MapChunk> chunks =
                new ArrayList<>();

        int expectedRows =
                countRows(
                        connection,
                        SaveTable.MAPCHUNK.tableName()
                );

        String sql =
                "SELECT position, data FROM \""
                        + SaveTable.MAPCHUNK.tableName()
                        + "\"";

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     );

             ResultSet resultSet =
                     statement.executeQuery()) {

            int row =
                    0;

            while (resultSet.next()) {
                row++;

                progress.progress(
                        "Parsing mapchunks",
                        row,
                        expectedRows
                );

                Optional<MapChunkCoordinate> coordinate =
                        mapChunkCoordinateFromPackedPosition(
                                resultSet.getObject(
                                        "position"
                                )
                        );

                if (coordinate.isEmpty()) {
                    diagnostics.recordSkipped(
                            "mapchunk row has no readable coordinate"
                    );

                    continue;
                }

                MapChunkCoordinate chunkCoordinate =
                        coordinate.orElseThrow();

                if (!withinRadius(
                        chunkCoordinate,
                        center,
                        radiusBlocks
                )) {
                    diagnostics.recordSkipped(
                            "mapchunk outside requested radius"
                    );

                    continue;
                }

                byte[] payload =
                        resultSet.getBytes(
                                "data"
                        );

                if (payload == null) {
                    continue;
                }

                ParseResult<MapChunk> parsed =
                        mapChunkParser.parse(
                                chunkCoordinate,
                                payload
                        );

                if (parsed.isSuccess()) {
                    diagnostics.recordParsed();

                    chunks.add(
                            parsed.value()
                                    .orElseThrow()
                    );

                } else {
                    diagnostics.recordFailed(
                            parsed.error()
                                    .orElse(
                                            "unknown mapchunk parse error"
                                    )
                    );
                }
            }
        }

        return chunks;
    }

    private List<ParsedChunk> readChunksAroundFromResultSet(
            Connection connection,
            WorldPosition center,
            int radiusBlocks,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) throws SQLException {
        try (ChunkDecodeWorkspace workspace = new ChunkDecodeWorkspace()) {
            return readChunksAroundFromResultSet(
                    connection, center, radiusBlocks, diagnostics, progress, workspace
            );
        }
    }

    private List<ParsedChunk> readChunksAroundFromResultSet(
            Connection connection,
            WorldPosition center,
            int radiusBlocks,
            ReadDiagnostics diagnostics,
            ProgressReporter progress,
            ChunkDecodeWorkspace workspace
    ) throws SQLException {
        List<ParsedChunk> chunks =
                new ArrayList<>();

        int expectedRows =
                countRows(
                        connection,
                        SaveTable.CHUNK.tableName()
                );

        String sql =
                "SELECT position, data FROM \""
                        + SaveTable.CHUNK.tableName()
                        + "\"";

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     );

             ResultSet resultSet =
                     statement.executeQuery()) {

            int row =
                    0;

            while (resultSet.next()) {
                row++;

                progress.progress(
                        "Parsing chunks",
                        row,
                        expectedRows
                );

                Optional<ChunkCoordinate> coordinate =
                        chunkCoordinateFromPackedPosition(
                                resultSet.getObject(
                                        "position"
                                )
                        );

                if (coordinate.isEmpty()) {
                    diagnostics.recordSkipped(
                            "chunk row has no readable coordinate"
                    );

                    continue;
                }

                ChunkCoordinate chunkCoordinate =
                        coordinate.orElseThrow();

                if (!withinRadius(
                        chunkCoordinate,
                        center,
                        radiusBlocks
                )) {
                    diagnostics.recordSkipped(
                            "chunk outside requested radius"
                    );

                    continue;
                }

                byte[] payload =
                        resultSet.getBytes(
                                "data"
                        );

                if (payload == null) {
                    continue;
                }

                ParseResult<ParsedChunk> parsed =
                        chunkParser.parse(
                                chunkCoordinate,
                                payload,
                                ChunkDecodeProfile.BLOCKS_AND_LIQUIDS,
                                workspace
                        );

                if (parsed.isSuccess()) {
                    ParsedChunk chunk =
                            parsed.value()
                                    .orElseThrow();

                    diagnostics.recordParsed();

                    if (!chunk.liquidLayerAvailable()) {
                        diagnostics.recordLiquidDecodeFailure(
                                chunk.liquidDecodeError()
                        );
                    }

                    chunks.add(
                            chunk
                    );

                } else {
                    diagnostics.recordFailed(
                            parsed.error()
                                    .orElse(
                                            "unknown chunk parse error"
                                    )
                    );
                }
            }
        }

        return chunks;
    }

    private Optional<MapChunkCoordinate> mapChunkCoordinateFromPackedPosition(
            Object rawValue
    ) {
        return decodePackedPosition(
                rawValue
        )
                .map(
                        position ->
                                new MapChunkCoordinate(
                                        position.x(),
                                        position.z()
                                )
                );
    }

    private Optional<ChunkCoordinate> chunkCoordinateFromPackedPosition(
            Object rawValue
    ) {
        return decodePackedPosition(
                rawValue
        )
                .map(
                        position ->
                                new ChunkCoordinate(
                                        position.x(),
                                        position.y(),
                                        position.z()
                                )
                );
    }

    private Optional<MapRegionCoordinate> mapRegionCoordinateFromPackedPosition(
            Object rawValue
    ) {
        return decodePackedPosition(
                rawValue
        )
                .map(
                        position ->
                                new MapRegionCoordinate(
                                        position.x(),
                                        position.z()
                                )
                );
    }

    private Optional<ChunkPosition> decodePackedPosition(
            Object rawValue
    ) {
        if (!(rawValue instanceof Number number)) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    ChunkPosDecoder.decode(
                            number.longValue()
                    )
            );

        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<SaveRecord> selectDefaultPlayer(
            List<SaveRecord> records
    ) {
        return records.stream()
                .min(
                        Comparator.comparing(
                                record ->
                                        String.valueOf(
                                                record.columns()
                                        )
                        )
                );
    }

    private Optional<SaveRecord> selectPlayer(
            List<SaveRecord> records,
            String selector
    ) {
        String wanted =
                selector.toLowerCase(
                        Locale.ROOT
                );

        return records.stream()
                .filter(
                        record ->
                                record.columns()
                                        .values()
                                        .stream()
                                        .map(
                                                String::valueOf
                                        )
                                        .map(
                                                value ->
                                                        value.toLowerCase(
                                                                Locale.ROOT
                                                        )
                                        )
                                        .anyMatch(
                                                value ->
                                                        value.equals(
                                                                wanted
                                                        )
                                                                || value.contains(
                                                                wanted
                                                        )
                                        )
                )
                .findFirst();
    }

    private List<SaveRecord> readRecords(
            Connection connection,
            String tableName,
            int limit,
            ProgressReporter progress
    ) throws SQLException {
        List<SaveRecord> records =
                new ArrayList<>();

        int expectedRows =
                Math.min(
                        countRows(
                                connection,
                                tableName
                        ),
                        limit
                );

        String sql =
                "SELECT * FROM \""
                        + tableName
                        + "\" LIMIT "
                        + limit;

        try (Statement statement =
                     connection.createStatement();

             ResultSet resultSet =
                     statement.executeQuery(
                             sql
                     )) {

            ResultSetMetaData metaData =
                    resultSet.getMetaData();

            int row =
                    0;

            while (resultSet.next()) {
                row++;

                progress.progress(
                        "Reading "
                                + tableName
                                + " rows",
                        row,
                        expectedRows
                );

                Map<String, Object> columns =
                        new LinkedHashMap<>();

                byte[] payload =
                        null;

                for (int index = 1;
                     index <= metaData.getColumnCount();
                     index++) {

                    String name =
                            metaData.getColumnName(
                                    index
                            );

                    Object value =
                            resultSet.getObject(
                                    index
                            );

                    columns.put(
                            name,
                            value
                    );

                    byte[] candidate =
                            resultSet.getBytes(
                                    index
                            );

                    if (candidate != null
                            && candidate.length > 0
                            && isLikelyPayload(
                            name,
                            value,
                            candidate,
                            payload
                    )) {

                        payload =
                                candidate;
                    }
                }

                if (payload != null) {
                    records.add(
                            new SaveRecord(
                                    columns,
                                    payload
                            )
                    );
                }
            }
        }

        return records;
    }

    private int countRows(
            Connection connection,
            String tableName
    ) throws SQLException {
        String sql =
                "SELECT COUNT(*) FROM \""
                        + tableName
                        + "\"";

        try (Statement statement =
                     connection.createStatement();

             ResultSet resultSet =
                     statement.executeQuery(
                             sql
                     )) {

            return resultSet.next()
                    ? resultSet.getInt(1)
                    : 0;
        }
    }

    private boolean isLikelyPayload(
            String columnName,
            Object value,
            byte[] candidate,
            byte[] currentPayload
    ) {
        String name =
                columnName.toLowerCase(
                        Locale.ROOT
                );

        if (name.contains("data")
                || name.contains("payload")
                || name.contains("value")
                || name.contains("blob")) {

            return true;
        }

        if (value instanceof byte[]) {
            return currentPayload == null
                    || candidate.length
                    > currentPayload.length;
        }

        return false;
    }

    private boolean withinRadius(
            MapChunkCoordinate coordinate,
            WorldPosition center,
            int radiusBlocks
    ) {
        MapChunkCoordinate centerCoordinate =
                center.mapChunkCoordinate();

        int radiusChunks =
                Math.max(
                        1,
                        (int) Math.ceil(
                                radiusBlocks
                                        / (double)
                                        MapChunkCoordinate.SIZE_BLOCKS
                        )
                );

        return Math.abs(
                coordinate.x()
                        - centerCoordinate.x()
        ) <= radiusChunks
                && Math.abs(
                coordinate.z()
                        - centerCoordinate.z()
        ) <= radiusChunks;
    }

    private boolean withinRadius(
            ChunkCoordinate coordinate,
            WorldPosition center,
            int radiusBlocks
    ) {
        ChunkCoordinate centerCoordinate =
                center.chunkCoordinate();

        int radiusChunks =
                Math.max(
                        1,
                        (int) Math.ceil(
                                radiusBlocks
                                        / (double)
                                        ChunkCoordinate.SIZE_BLOCKS
                        )
                );

        return Math.abs(
                coordinate.x()
                        - centerCoordinate.x()
        ) <= radiusChunks
                && Math.abs(
                coordinate.z()
                        - centerCoordinate.z()
        ) <= radiusChunks;
    }

    private void ensurePlayerDataTable(
            Connection connection
    ) throws SQLException {
        String tableName =
                SaveTable.PLAYERDATA.tableName();

        if (tableMissing(
                connection,
                tableName
        )) {
            throw new CommandException(
                    "Missing required table: "
                            + tableName
            );
        }
    }

    private boolean tableMissing(
            Connection connection,
            String tableName
    ) throws SQLException {
        DatabaseMetaData metaData =
                connection.getMetaData();

        try (ResultSet resultSet =
                     metaData.getTables(
                             null,
                             null,
                             tableName,
                             new String[]{"TABLE"}
                     )) {

            if (resultSet.next()) {
                return false;
            }
        }

        try (ResultSet resultSet =
                     metaData.getTables(
                             null,
                             null,
                             tableName.toUpperCase(
                                     Locale.ROOT
                             ),
                             new String[]{"TABLE"}
                     )) {

            return !resultSet.next();
        }
    }
}
