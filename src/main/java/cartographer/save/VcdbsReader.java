package cartographer.save;

import cartographer.cli.CommandException;
import cartographer.cli.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
        this.connectionFactory = connectionFactory;
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
        progress.start("Opening save read-only");

        try (Connection connection =
                     connectionFactory.openReadOnly(savePath)) {

            progress.done("Save opened read-only");

            ensureTable(
                    connection,
                    SaveTable.PLAYERDATA
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
                     connectionFactory.openReadOnly(savePath)) {

            progress.done(
                    "Save opened read-only"
            );

            if (!tableExists(
                    connection,
                    SaveTable.MAPCHUNK.tableName()
            )) {
                diagnostics.missingTable(
                        SaveTable.MAPCHUNK.tableName()
                );

                return List.of();
            }

            List<SaveRecord> records =
                    readRecords(
                            connection,
                            SaveTable.MAPCHUNK.tableName(),
                            100_000,
                            progress
                    );

            List<MapChunk> chunks =
                    new ArrayList<>();

            for (int index = 0;
                 index < records.size();
                 index++) {

                SaveRecord record =
                        records.get(index);

                progress.progress(
                        "Parsing mapchunks",
                        index + 1,
                        records.size()
                );

                Optional<MapChunkCoordinate> coordinate =
                        inferMapChunkCoordinate(
                                record
                        );

                if (coordinate.isEmpty()) {
                    diagnostics.recordSkipped(
                            "mapchunk row has no readable coordinate"
                    );

                    continue;
                }

                if (!withinRadius(
                        coordinate.get(),
                        center,
                        radiusBlocks
                )) {
                    diagnostics.recordSkipped(
                            "mapchunk outside requested radius"
                    );

                    continue;
                }

                ParseResult<MapChunk> parsed =
                        mapChunkParser.parse(
                                coordinate.get(),
                                record.payload()
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

            return chunks;

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
                     connectionFactory.openReadOnly(savePath)) {

            progress.done(
                    "Save opened read-only"
            );

            if (!tableExists(
                    connection,
                    SaveTable.CHUNK.tableName()
            )) {
                diagnostics.missingTable(
                        SaveTable.CHUNK.tableName()
                );

                return List.of();
            }

            progress.start(
                    "Reading block registry"
            );

            Map<Integer, BlockInfo> registry =
                    readBlockRegistry(
                            connection
                    );

            diagnostics.registryBlocks(
                    registry.size()
            );

            progress.done(
                    "Block registry read"
            );

            List<SaveRecord> records =
                    readRecords(
                            connection,
                            SaveTable.CHUNK.tableName(),
                            100_000,
                            progress
                    );

            List<ParsedChunk> chunks =
                    new ArrayList<>();

            for (int index = 0;
                 index < records.size();
                 index++) {

                SaveRecord record =
                        records.get(index);

                progress.progress(
                        "Parsing chunks",
                        index + 1,
                        records.size()
                );

                Optional<ChunkCoordinate> coordinate =
                        inferChunkCoordinate(
                                record
                        );

                if (coordinate.isEmpty()) {
                    diagnostics.recordSkipped(
                            "chunk row has no readable coordinate"
                    );

                    continue;
                }

                if (!withinRadius(
                        coordinate.get(),
                        center,
                        radiusBlocks
                )) {
                    diagnostics.recordSkipped(
                            "chunk outside requested radius"
                    );

                    continue;
                }

                ParseResult<ParsedChunk> parsed =
                        chunkParser.parse(
                                coordinate.get(),
                                record.payload()
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

            return chunks;

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
                     connectionFactory.openReadOnly(savePath)) {

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

    private Map<Integer, BlockInfo> readBlockRegistry(
            Connection connection
    ) throws SQLException {

        if (!tableExists(
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
                selector.get()
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
                     statement.executeQuery(sql)) {

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
                     statement.executeQuery(sql)) {

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

    private Optional<MapChunkCoordinate> inferMapChunkCoordinate(
            SaveRecord record
    ) {
        OptionalChunkPositionParts pair =
                inferCoordinate(
                        record,
                        "mapchunk"
                );

        if (!pair.present()) {
            return Optional.empty();
        }

        return Optional.of(
                new MapChunkCoordinate(
                        pair.x(),
                        pair.z()
                )
        );
    }

    private Optional<ChunkCoordinate> inferChunkCoordinate(
            SaveRecord record
    ) {
        OptionalChunkPositionParts pair =
                inferCoordinate(
                        record,
                        "chunk"
                );

        if (!pair.present()) {
            return Optional.empty();
        }

        return Optional.of(
                new ChunkCoordinate(
                        pair.x(),
                        pair.y(),
                        pair.z()
                )
        );
    }

    private OptionalChunkPositionParts inferCoordinate(
            SaveRecord record,
            String prefix
    ) {
        Integer x = null;
        Integer y = null;
        Integer z = null;

        Long packedPosition = null;

        for (Map.Entry<String, Object> entry :
                record.columns().entrySet()) {

            String name =
                    entry.getKey()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            Object rawValue =
                    entry.getValue();

            /*
             * chunk/mapchunk/mapregion store their coordinate
             * in the packed VCDBS ChunkPos "position" column.
             */
            if ("position".equals(name)
                    && rawValue instanceof Number number) {

                packedPosition =
                        number.longValue();
            }

            Integer value =
                    asInteger(
                            rawValue
                    );

            if (value == null) {
                continue;
            }

            if (x == null
                    && (
                    name.equals("x")
                            || name.equals(
                            prefix + "x"
                    )
                            || name.endsWith("_x")
                            || name.endsWith("xpos")
            )) {

                x = value;

            } else if (z == null
                    && (
                    name.equals("z")
                            || name.equals(
                            prefix + "z"
                    )
                            || name.endsWith("_z")
                            || name.endsWith("zpos")
            )) {

                z = value;
            }
        }

        /*
         * Real Vintage Story VCDBS tables normally only give us
         * the packed "position" long.
         *
         * Decode it using the actual ChunkPos bit layout.
         *
         * This works for chunk, mapchunk and mapregion.
         */
        if ((x == null || z == null)
                && packedPosition != null) {

            try {
                ChunkPosition decoded =
                        ChunkPosDecoder.decode(
                                packedPosition
                        );

                if (x == null) {
                    x = decoded.x();
                }

                if (y == null) {
                    y = decoded.y();
                }

                if (z == null) {
                    z = decoded.z();
                }

            } catch (IllegalArgumentException exception) {
                return OptionalChunkPositionParts.empty();
            }
        }

        if (x == null || z == null) {
            return OptionalChunkPositionParts.empty();
        }

        return new OptionalChunkPositionParts(
                x,
                y == null ? 0 : y,
                z,
                true
        );
    }

    private Integer asInteger(
            Object value
    ) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String text) {
            try {
                return Integer.parseInt(
                        text
                );

            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
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

    private void ensureTable(
            Connection connection,
            SaveTable table
    ) throws SQLException {

        if (!tableExists(
                connection,
                table.tableName()
        )) {

            throw new CommandException(
                    "Missing required table: "
                            + table.tableName()
            );
        }
    }

    private boolean tableExists(
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
                return true;
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

            return resultSet.next();
        }
    }

    private record OptionalChunkPositionParts(
            int x,
            int y,
            int z,
            boolean present
    ) {

        static OptionalChunkPositionParts empty() {
            return new OptionalChunkPositionParts(
                    0,
                    0,
                    0,
                    false
            );
        }
    }
}
