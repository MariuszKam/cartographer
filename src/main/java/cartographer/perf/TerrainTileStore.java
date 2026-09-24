package cartographer.perf;

import cartographer.model.MapChunkCoordinate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistent cache-local SQLite store for compact terrain tiles.
 *
 * <p>This store is deliberately separate from the read-only game save. It
 * opens a cache database only below the injected PF-1.7 revision directory and
 * never accepts the source save path as a database target.</p>
 */
public final class TerrainTileStore {
    private static final String DATABASE_FILE = "terrain-cache.sqlite";
    private static final int SELECT_BATCH_SIZE = 400;

    @FunctionalInterface
    public interface LookupVisitor {
        /**
         * @return true to continue visiting, false to stop after this lookup
         */
        boolean visit(
                MapChunkCoordinate coordinate,
                TerrainTileLookup lookup
        );
    }

    private final RenderDataCacheStore cacheStore;
    private final RenderDataCacheRevision revision;
    private final Path databasePath;

    public TerrainTileStore(
            RenderDataCacheStore cacheStore,
        RenderDataCacheRevision revision
    ) {
        this.cacheStore = Objects.requireNonNull(cacheStore, "cache store is required");
        this.revision = Objects.requireNonNull(revision, "revision is required");
        this.databasePath = cacheStore.manifestPath(revision)
                .getParent()
                .resolve(DATABASE_FILE);
    }

    public Path databasePath() {
        return databasePath;
    }

    public Map<MapChunkCoordinate, TerrainTileLookup> read(
            Collection<MapChunkCoordinate> coordinates
    ) {
        List<MapChunkCoordinate> requested = uniqueCoordinates(coordinates);
        Map<MapChunkCoordinate, TerrainTileLookup> results = new LinkedHashMap<>();
        requested.forEach(coordinate -> results.put(coordinate, TerrainTileLookup.miss()));
        if (requested.isEmpty()
                || cacheStore.find(revision).isEmpty()
                || !Files.isRegularFile(databasePath)) {
            return results;
        }

        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            for (int start = 0; start < requested.size(); start += SELECT_BATCH_SIZE) {
                int end = Math.min(start + SELECT_BATCH_SIZE, requested.size());
                readBatch(connection, requested.subList(start, end), results);
            }
            return results;
        } catch (SQLException exception) {
            requested.forEach(coordinate -> results.put(coordinate, TerrainTileLookup.corrupt()));
            return results;
        }
    }

    /**
     * Visits requested lookups in first-occurrence request order while keeping
     * at most one SELECT batch of decoded tiles live at a time.
     *
     * @return true when every unique requested coordinate was visited; false
     * when the visitor stopped iteration early
     */
    public boolean forEachLookup(
            Collection<MapChunkCoordinate> coordinates,
            LookupVisitor visitor
    ) {
        List<MapChunkCoordinate> requested = uniqueCoordinates(coordinates);
        Objects.requireNonNull(visitor, "lookup visitor is required");
        if (requested.isEmpty()) {
            return true;
        }
        if (cacheStore.find(revision).isEmpty()
                || !Files.isRegularFile(databasePath)) {
            return emit(
                    requested,
                    0,
                    TerrainTileLookup.miss(),
                    visitor
            );
        }

        int nextUnvisited = 0;
        int failureStart = -1;
        boolean stopped = false;
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            batchLoop:
            for (int start = 0;
                 start < requested.size();
                 start += SELECT_BATCH_SIZE) {
                int end = Math.min(
                        start + SELECT_BATCH_SIZE,
                        requested.size()
                );
                List<MapChunkCoordinate> batch =
                        requested.subList(start, end);
                Map<MapChunkCoordinate, TerrainTileLookup> results =
                        new LinkedHashMap<>();
                batch.forEach(
                        coordinate -> results.put(
                                coordinate,
                                TerrainTileLookup.miss()
                        )
                );
                try {
                    readBatch(connection, batch, results);
                } catch (SQLException exception) {
                    failureStart = start;
                    break;
                }
                for (MapChunkCoordinate coordinate : batch) {
                    if (!visitor.visit(
                            coordinate,
                            results.get(coordinate)
                    )) {
                        stopped = true;
                        break batchLoop;
                    }
                    nextUnvisited++;
                }
            }
        } catch (SQLException exception) {
            if (failureStart < 0) {
                failureStart = nextUnvisited;
            }
        }

        if (stopped) {
            return false;
        }
        if (failureStart >= 0) {
            return emit(
                    requested,
                    failureStart,
                    TerrainTileLookup.corrupt(),
                    visitor
            );
        }
        return true;
    }

    public void publish(Collection<TerrainHeightTile> tiles) {
        Objects.requireNonNull(tiles, "tiles are required");
        if (tiles.isEmpty()) {
            return;
        }
        if (cacheStore.find(revision).isEmpty()) {
            throw new IllegalStateException(
                    "terrain artifacts require a compatible published manifest"
            );
        }
        Path parent = databasePath.getParent();
        try {
            Files.createDirectories(parent);
            try (Connection connection = openDatabase(true)) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(
                            // A deterministic republish heals a corrupt row for this revision.
                            "INSERT INTO terrain_tile "
                                    + "(mapchunk_x, mapchunk_z, payload) VALUES (?, ?, ?) "
                                    + "ON CONFLICT(mapchunk_x, mapchunk_z) DO UPDATE SET payload = excluded.payload"
                    )) {
                        for (TerrainHeightTile tile : tiles) {
                            Objects.requireNonNull(tile, "tiles cannot contain null");
                            statement.setInt(1, tile.coordinate().x());
                            statement.setInt(2, tile.coordinate().z());
                            statement.setBytes(3, TerrainHeightTileCodec.encode(tile));
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException failure) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                    throw failure;
                }
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new IllegalStateException(
                    "Cannot publish terrain cache tiles: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void readBatch(
            Connection connection,
            List<MapChunkCoordinate> coordinates,
            Map<MapChunkCoordinate, TerrainTileLookup> results
    ) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT mapchunk_x, mapchunk_z, payload FROM terrain_tile WHERE "
        );
        for (int index = 0; index < coordinates.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            sql.append("(mapchunk_x = ? AND mapchunk_z = ?)");
        }
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            for (MapChunkCoordinate coordinate : coordinates) {
                statement.setInt(parameter++, coordinate.x());
                statement.setInt(parameter++, coordinate.z());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    MapChunkCoordinate coordinate = new MapChunkCoordinate(
                            resultSet.getInt("mapchunk_x"),
                            resultSet.getInt("mapchunk_z")
                    );
                    byte[] payload = resultSet.getBytes("payload");
                    try {
                        TerrainHeightTile tile = TerrainHeightTileCodec.decode(payload);
                        if (!tile.coordinate().equals(coordinate)) {
                            results.put(coordinate, TerrainTileLookup.corrupt());
                        } else {
                            results.put(coordinate, TerrainTileLookup.hit(tile));
                        }
                    } catch (RuntimeException exception) {
                        results.put(coordinate, TerrainTileLookup.corrupt());
                    }
                }
            }
        }
    }

    private Connection openDatabase(boolean create) throws SQLException {
        if (!create && !Files.isRegularFile(databasePath)) {
            throw new SQLException("terrain cache database is missing");
        }
        return DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath().normalize().toUri()
        );
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS terrain_tile ("
                            + "mapchunk_x INTEGER NOT NULL,"
                            + "mapchunk_z INTEGER NOT NULL,"
                            + "payload BLOB NOT NULL,"
                            + "PRIMARY KEY (mapchunk_x, mapchunk_z)"
                            + ")"
            );
        }
    }

    private static boolean emit(
            List<MapChunkCoordinate> requested,
            int start,
            TerrainTileLookup lookup,
            LookupVisitor visitor
    ) {
        for (int index = start; index < requested.size(); index++) {
            if (!visitor.visit(requested.get(index), lookup)) {
                return false;
            }
        }
        return true;
    }

    private static List<MapChunkCoordinate> uniqueCoordinates(
            Collection<MapChunkCoordinate> coordinates
    ) {
        Objects.requireNonNull(coordinates, "coordinates are required");
        LinkedHashSet<MapChunkCoordinate> unique = new LinkedHashSet<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            unique.add(Objects.requireNonNull(coordinate, "coordinates cannot contain null"));
        }
        return List.copyOf(unique);
    }
}
