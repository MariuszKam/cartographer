package cartographer.save;

import cartographer.cli.CommandException;
import cartographer.cli.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class VcdbsReader {

    private final PlayerDataParser playerDataParser;
    private final MapChunkParser mapChunkParser;
    private final ChunkParser chunkParser;
    private final RegistryParser registryParser;
    private final ServerMapRegionParser serverMapRegionParser;
    private final SqliteSaveConnection connectionFactory;

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
        this.playerDataParser = playerDataParser;
        this.mapChunkParser = mapChunkParser;
        this.chunkParser = chunkParser;
        this.registryParser = registryParser;
        this.serverMapRegionParser =
                new ServerMapRegionParser();
        this.connectionFactory =
                connectionFactory;
    }

    public WorldPosition readPlayerPosition(
            Path savePath,
            Optional<String> playerSelector
    ) {
        return readPlayerPosition(
                savePath,
                playerSelector,
                ProgressReporter.NONE
        );
    }

    public WorldPosition readPlayerPosition(
            Path savePath,
            Optional<String> playerSelector,
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

            ensurePlayerDataTable(
                    connection
            );

            List<SaveRecord> records =
                    readRecords(
                            connection,
                            SaveTable.PLAYERDATA.tableName(),
                            250,
                            progress
                    );

            if (records.isEmpty()) {
                throw new CommandException(
                        "Table playerdata exists but contains no rows"
                );
            }

            progress.start(
                    "Parsing player position"
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
                                                            + playerSelector.orElse("")
                                            )
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

        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot read playerdata: "
                            + exception.getMessage(),
                    exception
            );
        }
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
            Path savePath
    ) {
        return readBlockRegistry(
                savePath,
                ProgressReporter.NONE
        );
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
            ReadDiagnostics diagnostics
    ) {
        return readMapRegions(
                savePath,
                diagnostics,
                ProgressReporter.NONE
        );
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

    private Map<Integer, BlockInfo> readBlockRegistry(
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

            int row = 0;

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

            int row = 0;

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

            int row = 0;

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
                                payload
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
        Optional<ChunkPosition> decoded =
                decodePackedPosition(
                        rawValue
                );

        return decoded.map(
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
        Optional<ChunkPosition> decoded =
                decodePackedPosition(
                        rawValue
                );

        return decoded.map(
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
        Optional<ChunkPosition> decoded =
                decodePackedPosition(
                        rawValue
                );

        return decoded.map(
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

    private Optional<SaveRecord> selectPlayer(
            List<SaveRecord> records,
            Optional<String> selector
    ) {
        if (selector.isEmpty()) {
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

        String wanted =
                selector.orElseThrow()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return records.stream()
                .filter(
                        record ->
                                record.columns()
                                        .values()
                                        .stream()
                                        .map(String::valueOf)
                                        .map(
                                                value ->
                                                        value.toLowerCase(
                                                                Locale.ROOT
                                                        )
                                        )
                                        .anyMatch(
                                                value ->
                                                        value.equals(wanted)
                                                                || value.contains(wanted)
                                        )
                )
                .findFirst();
    }

    private List<SaveRecord> readRecords(
            Connection connection,
            String tableName,
            int limit
    ) throws SQLException {

        return readRecords(
                connection,
                tableName,
                limit,
                ProgressReporter.NONE
        );
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

            int row = 0;

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

                byte[] payload = null;

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