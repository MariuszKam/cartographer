package cartographer.save;

import cartographer.progress.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.parser.ServerMapRegionParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.function.Consumer;

public class VcdbsReader {

    private final PlayerDataParser playerDataParser;
    private final RegistryParser registryParser;
    private final ServerMapRegionParser serverMapRegionParser;
    private final VcdbsChunkStreamReader chunkStreamReader;
    private final VcdbsSelectiveChunkStreamReader selectiveChunkStreamReader;
    private final VcdbsMapChunkStreamReader mapChunkStreamReader;

    ChunkStreamStats forEachChunkByPositionAdaptive(
            SaveSession session,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer
    ) {
        return chunkStreamReader.forEachChunkByPositionAdaptive(
                session,
                positions,
                diagnostics,
                consumer,
                ProgressReporter.NONE
        );
    }

    public ChunkStreamStats forEachSurfaceChunkByPositionAdaptive(
            SaveSession session,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        return chunkStreamReader.forEachSurfaceChunkByPositionAdaptive(
                session, positions, diagnostics, consumer, progress
        );
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        return selectiveChunkStreamReader.forEachChunkByPositionMatchingBlockIdsAdaptive(
                session, positions, wantedBlockIds, diagnostics, consumer, progress
        );
    }

    public MapChunkStreamStats forEachObservedMapChunk(
            SaveSession session,
            ReadDiagnostics diagnostics,
            Consumer<MapChunkCoordinate> observedCoordinateConsumer,
            Consumer<MapChunk> consumer,
            ProgressReporter progress
    ) {
        return mapChunkStreamReader.forEachObservedMapChunk(
                session, diagnostics, observedCoordinateConsumer, consumer, progress
        );
    }

    public MapChunkStreamStats forEachMapChunkByCoordinate(
            SaveSession session,
            Collection<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            Consumer<MapChunk> consumer,
            ProgressReporter progress
    ) {
        return mapChunkStreamReader.forEachMapChunkByCoordinate(
                session, coordinates, diagnostics, consumer, progress
        );
    }

    public MapChunkStreamStats forEachMapChunkByCoordinate(
            SaveSession session,
            Collection<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            Consumer<MapChunk> consumer
    ) {
        return mapChunkStreamReader.forEachMapChunkByCoordinate(
                session, coordinates, diagnostics, consumer
        );
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer,
            ProgressReporter progress
    ) {
        return selectiveChunkStreamReader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                session, positions, wantedBlockIds, diagnostics, consumer, progress
        );
    }

    public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<SelectiveChunkVisit> consumer
    ) {
        return selectiveChunkStreamReader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                session, positions, wantedBlockIds, diagnostics, consumer
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
        return selectiveChunkStreamReader.forEachChunkByPositionMatchingBlockIds(
                session, positions, wantedBlockIds, diagnostics, consumer, progress
        );
    }

    ChunkStreamStats forEachChunkByPositionTableStream(
            SaveSession session,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer
    ) {
        return chunkStreamReader.forEachChunkByPositionTableStream(
                session, positions, diagnostics, consumer
        );
    }

    SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsTableStream(
            SaveSession session,
            Collection<ChunkPosition> positions,
            int[] wantedBlockIds,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer
    ) {
        return selectiveChunkStreamReader.forEachChunkByPositionMatchingBlockIdsTableStream(
                session,
                positions,
                wantedBlockIds,
                diagnostics,
                consumer
        );
    }

    ChunkStreamStats forEachChunkByPosition(
            SaveSession session,
            Collection<ChunkPosition> positions,
            ReadDiagnostics diagnostics,
            Consumer<ParsedChunk> consumer,
            ProgressReporter progress
    ) {
        return chunkStreamReader.forEachChunkByPosition(
                session, positions, diagnostics, consumer, progress
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
                defaultChunkDecodeWorkerCount(),
                defaultChunkDecodeMaxInFlight(defaultChunkDecodeWorkerCount())
        );
    }

    VcdbsReader(
            PlayerDataParser playerDataParser,
            MapChunkParser mapChunkParser,
            ChunkParser chunkParser,
            RegistryParser registryParser,
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
        this.playerDataParser = Objects.requireNonNull(
                playerDataParser,
                "playerDataParser is required"
        );

        this.registryParser = Objects.requireNonNull(
                registryParser,
                "registryParser is required"
        );

        MapChunkParser requiredMapChunkParser = Objects.requireNonNull(
                mapChunkParser,
                "mapChunkParser is required"
        );
        ChunkParser requiredChunkParser = Objects.requireNonNull(
                chunkParser,
                "chunkParser is required"
        );

        this.serverMapRegionParser =
                new ServerMapRegionParser();

        this.chunkStreamReader = new VcdbsChunkStreamReader(
                requiredChunkParser,
                chunkDecodeWorkerCount,
                chunkDecodeMaxInFlight
        );

        this.selectiveChunkStreamReader = new VcdbsSelectiveChunkStreamReader(
                requiredChunkParser,
                chunkDecodeWorkerCount,
                chunkDecodeMaxInFlight
        );

        this.mapChunkStreamReader = new VcdbsMapChunkStreamReader(
                requiredMapChunkParser
        );
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
            SaveSession session,
            ProgressReporter progress
    ) {
        List<SaveRecord> records =
                readPlayerRecords(
                        session,
                        progress
                );

        SaveRecord selected =
                selectDefaultPlayer(
                        records
                )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Table playerdata exists but contains no selectable rows"
                                        )
                        );

        return parsePlayerPosition(
                selected,
                progress
        );
    }

    private List<SaveRecord> readPlayerRecords(
            SaveSession session,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(
                session,
                "session is required"
        );

        try {
            return readPlayerRecords(
                    session.connection(),
                    progress
            );

        } catch (SQLException exception) {
            throw new IllegalStateException(
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
                SaveTable.PLAYERDATA,
                250,
                progress
        );
        if (records.isEmpty()) {
            throw new IllegalStateException("Table playerdata exists but contains no rows");
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
                                        new IllegalStateException(
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

    /** Reads mapregions from a borrowed session-owned connection. */
    public List<ServerMapRegion> readMapRegions(
            SaveSession session,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(progress, "progress is required");
        try {
            return readMapRegions(session.connection(), diagnostics, progress);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot read mapregion table: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<ServerMapRegion> readMapRegions(
            Connection connection,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) throws SQLException {
        if (SqliteSaveTableInspector.tableMissing(connection, SaveTable.MAPREGION.tableName())) {
            diagnostics.missingTable(SaveTable.MAPREGION.tableName());
            return List.of();
        }
        return readMapRegionsFromResultSet(connection, diagnostics, progress);
    }

    /**
     * Streams authoritative mapregion rows from the session-owned read-only
     * connection without retaining source payloads beyond the callback.
     */
    public MapRegionStreamStats forEachObservedMapRegion(
            SaveSession session,
            ReadDiagnostics diagnostics,
            Consumer<ServerMapRegion> consumer,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(consumer, "consumer is required");
        Objects.requireNonNull(progress, "progress is required");

        Connection connection = session.connection();
        progress.start("Indexing mapregions");
        try {
            if (SqliteSaveTableInspector.tableMissing(connection, SaveTable.MAPREGION.tableName())) {
                diagnostics.missingTable(SaveTable.MAPREGION.tableName());
                progress.done("Mapregion table absent");
                return new MapRegionStreamStats(
                        0, 0, 0, 0, 0, 0L
                );
            }

            int expectedRows = SqliteSaveTableInspector.countRows(
                    connection,
                    SaveTable.MAPREGION
            );
            String sql = "SELECT position, data FROM \""
                    + SaveTable.MAPREGION.tableName()
                    + "\" ORDER BY position";
            int rowsFound = 0;
            int parsed = 0;
            int failed = 0;
            int invalid = 0;
            int ignored = 0;
            long payloadBytes = 0L;

            try (PreparedStatement statement =
                         connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rowsFound++;
                    progress.progress(
                            "Indexing mapregions",
                            rowsFound,
                            expectedRows
                    );

                    Optional<ChunkPosition> decodedPosition =
                            SavePackedPositionDecoder.decode(
                                    resultSet.getObject("position")
                            );
                    if (decodedPosition.isEmpty()) {
                        invalid++;
                        diagnostics.recordSkipped(
                                "mapregion row has no readable position"
                        );
                        continue;
                    }
                    ChunkPosition position =
                            decodedPosition.orElseThrow();
                    if (position.dimension() != 0 || position.y() != 0) {
                        ignored++;
                        diagnostics.recordSkipped(
                                "mapregion row is outside the main world"
                        );
                        continue;
                    }
                    MapRegionCoordinate coordinate =
                            new MapRegionCoordinate(
                                    position.x(),
                                    position.z()
                            );

                    byte[] payload = resultSet.getBytes("data");
                    if (payload == null || payload.length == 0) {
                        invalid++;
                        diagnostics.recordSkipped(
                                "mapregion row has no payload"
                        );
                        continue;
                    }
                    payloadBytes = Math.addExact(
                            payloadBytes,
                            payload.length
                    );

                    ParseResult<ServerMapRegion> parsedRegion =
                            serverMapRegionParser.parse(
                                    coordinate,
                                    payload
                            );
                    if (parsedRegion.isSuccess()) {
                        parsed++;
                        diagnostics.recordParsed();
                        consumer.accept(
                                parsedRegion.value().orElseThrow()
                        );
                    } else {
                        failed++;
                        diagnostics.recordFailed(
                                parsedRegion.error().orElse(
                                        "unknown mapregion parse error"
                                )
                        );
                    }
                }
            }

            progress.done("Mapregion indexing source scan complete");
            return new MapRegionStreamStats(
                    rowsFound,
                    parsed,
                    failed,
                    invalid,
                    ignored,
                    payloadBytes
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot stream mapregion table: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    /** Reads registry data from an already-open session-owned read-only connection. */
    protected Map<Integer, BlockInfo> readBlockRegistry(
            Connection connection
    ) throws SQLException {
        if (SqliteSaveTableInspector.tableMissing(
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
                        SaveTable.GAMEDATA,
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
                SqliteSaveTableInspector.countRows(
                        connection,
                        SaveTable.MAPREGION
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
                        SavePackedPositionDecoder.mapRegionCoordinate(
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

    private List<SaveRecord> readRecords(
            Connection connection,
            SaveTable table,
            int limit,
            ProgressReporter progress
    ) throws SQLException {
        List<SaveRecord> records =
                new ArrayList<>();
        String tableName = table.tableName();

        int expectedRows =
                Math.min(
                        SqliteSaveTableInspector.countRows(
                                connection,
                                table
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

    private void ensurePlayerDataTable(
            Connection connection
    ) throws SQLException {
        String tableName =
                SaveTable.PLAYERDATA.tableName();

        if (SqliteSaveTableInspector.tableMissing(
                connection,
                tableName
        )) {
            throw new IllegalStateException(
                    "Missing required table: "
                            + tableName
            );
        }
    }


}
