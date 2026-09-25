package cartographer.snapshot;

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

/** Revision-scoped cache-local store for PF-2.4 UPPER_ROCK tiles. */
public final class UpperRockTileStore {
    private static final String DATABASE_FILE = "upper-rock-cache.sqlite";
    private static final int SELECT_BATCH_SIZE = 400;

    @FunctionalInterface
    public interface LookupVisitor {
        /**
         * @return true to continue visiting, false to stop after this lookup
         */
        boolean visit(
                MapChunkCoordinate coordinate,
                UpperRockTileLookup lookup
        );
    }

    private final RenderDataCacheStore cacheStore;
    private final RenderDataCacheRevision revision;
    private final Path databasePath;

    public UpperRockTileStore(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision
    ) {
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
        this.revision = Objects.requireNonNull(
                revision,
                "revision is required"
        );
        this.databasePath = cacheStore.manifestPath(revision)
                .getParent()
                .resolve(DATABASE_FILE);
    }

    public Path databasePath() {
        return databasePath;
    }

    public Map<MapChunkCoordinate, UpperRockTileLookup> read(
            Collection<MapChunkCoordinate> coordinates
    ) {
        List<MapChunkCoordinate> requested = uniqueCoordinates(coordinates);
        Map<MapChunkCoordinate, UpperRockTileLookup> result =
                new LinkedHashMap<>();
        requested.forEach(
                coordinate -> result.put(
                        coordinate,
                        UpperRockTileLookup.miss()
                )
        );
        if (requested.isEmpty()
                || cacheStore.find(revision).isEmpty()
                || !Files.isRegularFile(databasePath)) {
            return result;
        }

        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            for (int start = 0;
                 start < requested.size();
                 start += SELECT_BATCH_SIZE) {
                readBatch(
                        connection,
                        requested.subList(
                                start,
                                Math.min(
                                        start + SELECT_BATCH_SIZE,
                                        requested.size()
                                )
                        ),
                        result
                );
            }
            return result;
        } catch (SQLException exception) {
            requested.forEach(
                    coordinate -> result.put(
                            coordinate,
                            UpperRockTileLookup.corrupt()
                    )
            );
            return result;
        }
    }

    /**
     * Visits requested UPPER_ROCK lookups in first-occurrence request order
     * while retaining at most one decoded SELECT batch.
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
                    UpperRockTileLookup.miss(),
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
                Map<MapChunkCoordinate, UpperRockTileLookup> results =
                        new LinkedHashMap<>();
                batch.forEach(
                        coordinate -> results.put(
                                coordinate,
                                UpperRockTileLookup.miss()
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
                    UpperRockTileLookup.corrupt(),
                    visitor
            );
        }
        return true;
    }

    public void publish(Collection<UpperRockTile> tiles) {
        List<UpperRockTile> safe = List.copyOf(
                Objects.requireNonNull(tiles, "tiles are required")
        );
        if (safe.isEmpty()) return;
        requirePublishedRevision();

        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = openDatabase(true)) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO upper_rock_tile "
                                + "(mapchunk_x, mapchunk_z, payload) "
                                + "VALUES (?, ?, ?) "
                                + "ON CONFLICT(mapchunk_x,mapchunk_z) "
                                + "DO UPDATE SET payload = excluded.payload"
                )) {
                    for (UpperRockTile tile : safe) {
                        Objects.requireNonNull(
                                tile,
                                "tiles cannot contain null"
                        );
                        statement.setInt(1, tile.coordinate().x());
                        statement.setInt(2, tile.coordinate().z());
                        statement.setBytes(
                                3,
                                UpperRockTileCodec.encode(tile)
                        );
                        statement.addBatch();
                    }
                    statement.executeBatch();
                    connection.commit();
                } catch (SQLException | RuntimeException failure) {
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new IllegalStateException(
                    "Cannot publish UPPER_ROCK tiles: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private void readBatch(
            Connection connection,
            List<MapChunkCoordinate> coordinates,
            Map<MapChunkCoordinate, UpperRockTileLookup> result
    ) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT mapchunk_x, mapchunk_z, payload "
                        + "FROM upper_rock_tile WHERE "
        );
        for (int index = 0; index < coordinates.size(); index++) {
            if (index > 0) sql.append(" OR ");
            sql.append("(mapchunk_x = ? AND mapchunk_z = ?)");
        }
        try (PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            for (MapChunkCoordinate coordinate : coordinates) {
                statement.setInt(parameter++, coordinate.x());
                statement.setInt(parameter++, coordinate.z());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    MapChunkCoordinate coordinate =
                            new MapChunkCoordinate(
                                    resultSet.getInt("mapchunk_x"),
                                    resultSet.getInt("mapchunk_z")
                            );
                    try {
                        UpperRockTile tile = UpperRockTileCodec.decode(
                                resultSet.getBytes("payload")
                        );
                        result.put(
                                coordinate,
                                tile.coordinate().equals(coordinate)
                                        ? UpperRockTileLookup.hit(tile)
                                        : UpperRockTileLookup.corrupt()
                        );
                    } catch (RuntimeException exception) {
                        result.put(
                                coordinate,
                                UpperRockTileLookup.corrupt()
                        );
                    }
                }
            }
        }
    }

    private void requirePublishedRevision() {
        if (cacheStore.find(revision).isEmpty()) {
            throw new IllegalStateException(
                    "UPPER_ROCK tiles require a compatible published manifest"
            );
        }
    }

    private Connection openDatabase(boolean create) throws SQLException {
        if (!create && !Files.isRegularFile(databasePath)) {
            throw new SQLException("UPPER_ROCK database is missing");
        }
        return DriverManager.getConnection(
                "jdbc:sqlite:"
                        + databasePath.toAbsolutePath().normalize().toUri()
        );
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS upper_rock_tile ("
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
            UpperRockTileLookup lookup,
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
            unique.add(
                    Objects.requireNonNull(
                            coordinate,
                            "coordinates cannot contain null"
                    )
            );
        }
        return List.copyOf(unique);
    }

    private static void rollback(
            Connection connection,
            Throwable failure
    ) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
