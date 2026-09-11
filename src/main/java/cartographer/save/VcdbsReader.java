package cartographer.save;

import cartographer.cli.CommandException;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
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

    public VcdbsReader(PlayerDataParser playerDataParser, MapChunkParser mapChunkParser, ChunkParser chunkParser, RegistryParser registryParser) {
        this(playerDataParser, mapChunkParser, chunkParser, registryParser, new SqliteSaveConnection());
    }

    public VcdbsReader(PlayerDataParser playerDataParser, MapChunkParser mapChunkParser, ChunkParser chunkParser, RegistryParser registryParser, SqliteSaveConnection connectionFactory) {
        this.playerDataParser = playerDataParser;
        this.mapChunkParser = mapChunkParser;
        this.chunkParser = chunkParser;
        this.registryParser = registryParser;
        this.connectionFactory = connectionFactory;
    }

    public WorldPosition readPlayerPosition(Path savePath, Optional<String> playerSelector) {
        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            ensureTable(connection, SaveTable.PLAYERDATA);
            List<SaveRecord> records = readRecords(connection, SaveTable.PLAYERDATA.tableName(), 250);
            if (records.isEmpty()) {
                throw new CommandException("Table playerdata exists but contains no rows");
            }

            SaveRecord selected = selectPlayer(records, playerSelector)
                    .orElseThrow(() -> new CommandException("No playerdata row matched selector: " + playerSelector.orElse("")));
            ParseResult<WorldPosition> result = playerDataParser.parse(selected.payload());
            return result.value().orElseThrow(() -> new CommandException(result.error().orElse("Unable to parse player position")));
        } catch (SQLException exception) {
            throw new CommandException("Cannot read playerdata: " + exception.getMessage(), exception);
        }
    }

    public List<MapChunk> readMapChunksAround(Path savePath, WorldPosition center, int radiusBlocks, ReadDiagnostics diagnostics) {
        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            if (!tableExists(connection, SaveTable.MAPCHUNK.tableName())) {
                diagnostics.missingTable(SaveTable.MAPCHUNK.tableName());
                return List.of();
            }

            List<SaveRecord> records = readRecords(connection, SaveTable.MAPCHUNK.tableName(), 100_000);
            List<MapChunk> chunks = new ArrayList<>();
            for (SaveRecord record : records) {
                Optional<MapChunkCoordinate> coordinate = inferMapChunkCoordinate(record);
                if (coordinate.isEmpty()) {
                    diagnostics.recordSkipped("mapchunk row has no readable coordinate");
                    continue;
                }
                if (!withinRadius(coordinate.get(), center, radiusBlocks)) {
                    diagnostics.recordSkipped("mapchunk outside requested radius");
                    continue;
                }
                ParseResult<MapChunk> parsed = mapChunkParser.parse(coordinate.get(), record.payload());
                if (parsed.isSuccess()) {
                    diagnostics.recordParsed();
                    chunks.add(parsed.value().orElseThrow());
                } else {
                    diagnostics.recordFailed(parsed.error().orElse("unknown mapchunk parse error"));
                }
            }
            return chunks;
        } catch (SQLException exception) {
            throw new CommandException("Cannot read mapchunk table: " + exception.getMessage(), exception);
        }
    }

    public List<ParsedChunk> readChunksAround(Path savePath, WorldPosition center, int radiusBlocks, ReadDiagnostics diagnostics) {
        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            if (!tableExists(connection, SaveTable.CHUNK.tableName())) {
                diagnostics.missingTable(SaveTable.CHUNK.tableName());
                return List.of();
            }

            Map<Integer, BlockInfo> registry = readBlockRegistry(connection);
            diagnostics.registryBlocks(registry.size());

            List<SaveRecord> records = readRecords(connection, SaveTable.CHUNK.tableName(), 100_000);
            List<ParsedChunk> chunks = new ArrayList<>();
            for (SaveRecord record : records) {
                Optional<ChunkCoordinate> coordinate = inferChunkCoordinate(record);
                if (coordinate.isEmpty()) {
                    diagnostics.recordSkipped("chunk row has no readable coordinate");
                    continue;
                }
                if (!withinRadius(coordinate.get(), center, radiusBlocks)) {
                    diagnostics.recordSkipped("chunk outside requested radius");
                    continue;
                }
                ParseResult<ParsedChunk> parsed = chunkParser.parse(coordinate.get(), record.payload());
                if (parsed.isSuccess()) {
                    diagnostics.recordParsed();
                    chunks.add(parsed.value().orElseThrow());
                } else {
                    diagnostics.recordFailed(parsed.error().orElse("unknown chunk parse error"));
                }
            }
            return chunks;
        } catch (SQLException exception) {
            throw new CommandException("Cannot read chunk table: " + exception.getMessage(), exception);
        }
    }

    public Map<Integer, BlockInfo> readBlockRegistry(Path savePath) {
        try (Connection connection = connectionFactory.openReadOnly(savePath)) {
            return readBlockRegistry(connection);
        } catch (SQLException exception) {
            throw new CommandException("Cannot read block registry: " + exception.getMessage(), exception);
        }
    }

    private Map<Integer, BlockInfo> readBlockRegistry(Connection connection) throws SQLException {
        if (!tableExists(connection, SaveTable.GAMEDATA.tableName())) {
            return Map.of();
        }
        Map<Integer, BlockInfo> blocks = new HashMap<>();
        for (SaveRecord record : readRecords(connection, SaveTable.GAMEDATA.tableName(), 500)) {
            blocks.putAll(registryParser.parse(record.payload()));
        }
        return blocks;
    }

    private Optional<SaveRecord> selectPlayer(List<SaveRecord> records, Optional<String> selector) {
        if (selector.isEmpty()) {
            return records.stream()
                    .min(Comparator.comparing(record -> String.valueOf(record.columns())));
        }

        String wanted = selector.get().toLowerCase(Locale.ROOT);
        return records.stream()
                .filter(record -> record.columns().values().stream()
                        .map(String::valueOf)
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .anyMatch(value -> value.equals(wanted) || value.contains(wanted)))
                .findFirst();
    }

    private List<SaveRecord> readRecords(Connection connection, String tableName, int limit) throws SQLException {
        List<SaveRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM \"" + tableName + "\" LIMIT " + limit;
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, Object> columns = new LinkedHashMap<>();
                byte[] payload = null;
                for (int index = 1; index <= metaData.getColumnCount(); index++) {
                    String name = metaData.getColumnName(index);
                    Object value = resultSet.getObject(index);
                    columns.put(name, value);
                    byte[] candidate = resultSet.getBytes(index);
                    if (candidate != null && candidate.length > 0 && isLikelyPayload(name, value, candidate, payload)) {
                        payload = candidate;
                    }
                }
                if (payload != null) {
                    records.add(new SaveRecord(columns, payload));
                }
            }
        }
        return records;
    }

    private boolean isLikelyPayload(String columnName, Object value, byte[] candidate, byte[] currentPayload) {
        String name = columnName.toLowerCase(Locale.ROOT);
        if (name.contains("data") || name.contains("payload") || name.contains("value") || name.contains("blob")) {
            return true;
        }
        if (value instanceof byte[]) {
            return currentPayload == null || candidate.length > currentPayload.length;
        }
        return false;
    }

    private Optional<MapChunkCoordinate> inferMapChunkCoordinate(SaveRecord record) {
        OptionalIntPair pair = inferCoordinate(record, "mapchunk");
        return pair.present() ? Optional.of(new MapChunkCoordinate(pair.x(), pair.z())) : Optional.empty();
    }

    private Optional<ChunkCoordinate> inferChunkCoordinate(SaveRecord record) {
        OptionalIntPair pair = inferCoordinate(record, "chunk");
        return pair.present() ? Optional.of(new ChunkCoordinate(pair.x(), pair.z())) : Optional.empty();
    }

    private OptionalIntPair inferCoordinate(SaveRecord record, String prefix) {
        Integer x = null;
        Integer z = null;
        for (Map.Entry<String, Object> entry : record.columns().entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            Integer value = asInteger(entry.getValue());
            if (value == null) {
                continue;
            }
            if (x == null && (name.equals("x") || name.equals(prefix + "x") || name.endsWith("_x") || name.endsWith("xpos"))) {
                x = value;
            } else if (z == null && (name.equals("z") || name.equals(prefix + "z") || name.endsWith("_z") || name.endsWith("zpos"))) {
                z = value;
            }
        }
        return x == null || z == null ? OptionalIntPair.empty() : new OptionalIntPair(x, z, true);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean withinRadius(MapChunkCoordinate coordinate, WorldPosition center, int radiusBlocks) {
        MapChunkCoordinate centerCoordinate = center.mapChunkCoordinate();
        int radiusChunks = Math.max(1, (int) Math.ceil(radiusBlocks / (double) MapChunkCoordinate.SIZE_BLOCKS));
        return Math.abs(coordinate.x() - centerCoordinate.x()) <= radiusChunks
                && Math.abs(coordinate.z() - centerCoordinate.z()) <= radiusChunks;
    }

    private boolean withinRadius(ChunkCoordinate coordinate, WorldPosition center, int radiusBlocks) {
        ChunkCoordinate centerCoordinate = center.chunkCoordinate();
        int radiusChunks = Math.max(1, (int) Math.ceil(radiusBlocks / (double) ChunkCoordinate.SIZE_BLOCKS));
        return Math.abs(coordinate.x() - centerCoordinate.x()) <= radiusChunks
                && Math.abs(coordinate.z() - centerCoordinate.z()) <= radiusChunks;
    }

    private void ensureTable(Connection connection, SaveTable table) throws SQLException {
        if (!tableExists(connection, table.tableName())) {
            throw new CommandException("Missing required table: " + table.tableName());
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(null, null, tableName.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    private record OptionalIntPair(int x, int z, boolean present) {
        static OptionalIntPair empty() {
            return new OptionalIntPair(0, 0, false);
        }
    }
}
