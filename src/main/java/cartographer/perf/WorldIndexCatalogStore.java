package cartographer.perf;

import cartographer.cli.CommandException;
import cartographer.model.MapChunkCoordinate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Revision-scoped catalog of observed main-world mapchunks.
 *
 * <p>The catalog lives only in the external derived-cache namespace. A
 * complete marker is published only after the authoritative mapchunk table
 * scan finishes. Partial rows may survive cancellation and are safe to
 * upsert/reuse when the same revision resumes.</p>
 */
public final class WorldIndexCatalogStore {
    private static final String DATABASE_FILE = "world-index.sqlite";
    private static final String MAPCHUNK_SCAN_COMPLETE = "mapchunk-scan-complete";

    private final RenderDataCacheStore cacheStore;
    private final RenderDataCacheRevision revision;
    private final Path databasePath;

    public WorldIndexCatalogStore(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision
    ) {
        this.cacheStore = Objects.requireNonNull(cacheStore, "cacheStore is required");
        this.revision = Objects.requireNonNull(revision, "revision is required");
        this.databasePath = cacheStore.manifestPath(revision)
                .getParent()
                .resolve(DATABASE_FILE);
    }

    public Path databasePath() {
        return databasePath;
    }

    public void recordObserved(Collection<MapChunkCoordinate> coordinates) {
        List<MapChunkCoordinate> unique = uniqueCoordinates(coordinates);
        if (unique.isEmpty()) {
            return;
        }
        requirePublishedRevision();
        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = openDatabase(true)) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO observed_mapchunk (mapchunk_x, mapchunk_z) "
                                + "VALUES (?, ?) "
                                + "ON CONFLICT(mapchunk_x, mapchunk_z) DO NOTHING"
                )) {
                    for (MapChunkCoordinate coordinate : unique) {
                        statement.setInt(1, coordinate.x());
                        statement.setInt(2, coordinate.z());
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
            throw new CommandException(
                    "Cannot record observed world-index mapchunks: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public void markMapChunkScanComplete() {
        requirePublishedRevision();
        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = openDatabase(true)) {
                ensureSchema(connection);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO world_index_meta (key, value) VALUES (?, ?) "
                                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value"
                )) {
                    statement.setString(1, MAPCHUNK_SCAN_COMPLETE);
                    statement.setString(2, "true");
                    statement.executeUpdate();
                }
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new CommandException(
                    "Cannot mark world-index mapchunk discovery complete: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public boolean mapChunkScanComplete() {
        if (!compatibleStoreAvailable()) {
            return false;
        }
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT value FROM world_index_meta WHERE key = ?"
            )) {
                statement.setString(1, MAPCHUNK_SCAN_COMPLETE);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next()
                            && Boolean.parseBoolean(resultSet.getString("value"));
                }
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    public List<MapChunkCoordinate> observedMapChunks() {
        if (!compatibleStoreAvailable()) {
            return List.of();
        }
        List<MapChunkCoordinate> result = new ArrayList<>();
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT mapchunk_x, mapchunk_z FROM observed_mapchunk "
                            + "ORDER BY mapchunk_z, mapchunk_x"
            ); ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new MapChunkCoordinate(
                            resultSet.getInt("mapchunk_x"),
                            resultSet.getInt("mapchunk_z")
                    ));
                }
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            return List.of();
        }
    }

    private boolean compatibleStoreAvailable() {
        return cacheStore.find(revision).isPresent()
                && Files.isRegularFile(databasePath);
    }

    private void requirePublishedRevision() {
        if (cacheStore.find(revision).isEmpty()) {
            throw new IllegalStateException(
                    "world-index catalog requires a compatible published manifest"
            );
        }
    }

    private Connection openDatabase(boolean create) throws SQLException {
        if (!create && !Files.isRegularFile(databasePath)) {
            throw new SQLException("world-index database is missing");
        }
        return DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath().normalize().toUri()
        );
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS observed_mapchunk ("
                            + "mapchunk_x INTEGER NOT NULL,"
                            + "mapchunk_z INTEGER NOT NULL,"
                            + "PRIMARY KEY (mapchunk_x, mapchunk_z)"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS world_index_meta ("
                            + "key TEXT PRIMARY KEY,"
                            + "value TEXT NOT NULL"
                            + ")"
            );
        }
    }

    private static List<MapChunkCoordinate> uniqueCoordinates(
            Collection<MapChunkCoordinate> coordinates
    ) {
        Objects.requireNonNull(coordinates, "coordinates are required");
        LinkedHashSet<MapChunkCoordinate> unique = new LinkedHashSet<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            unique.add(Objects.requireNonNull(
                    coordinate,
                    "coordinates cannot contain null"
            ));
        }
        return unique.stream()
                .sorted(Comparator.comparingInt(MapChunkCoordinate::z)
                        .thenComparingInt(MapChunkCoordinate::x))
                .toList();
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
