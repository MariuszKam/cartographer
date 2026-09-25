package cartographer.snapshot;

import cartographer.model.MapRegionCoordinate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Revision-scoped cache-local store for interpreted/static mapregion state.
 *
 * <p>The completion marker is removed before a source repair scan and restored
 * only after every authoritative row was visited without parse/coordinate
 * failures. Partial rows remain reusable after interruption.</p>
 */
public final class MapRegionSnapshotStore {
    private static final String DATABASE_FILE = "mapregion-index.sqlite";
    private static final String SCAN_COMPLETE = "mapregion-scan-complete";

    private final RenderDataCacheStore cacheStore;
    private final RenderDataCacheRevision revision;
    private final Path databasePath;

    public MapRegionSnapshotStore(
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

    public MapRegionSnapshotRead readAll() {
        if (!compatibleStoreAvailable()) {
            return new MapRegionSnapshotRead(false, List.of(), 0);
        }
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            boolean complete = metaFlag(connection, SCAN_COMPLETE);
            List<MapRegionSnapshotEntry> entries = new ArrayList<>();
            int corrupt = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT region_x, region_z, payload "
                            + "FROM mapregion_snapshot ORDER BY region_z, region_x"
            ); ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    MapRegionCoordinate coordinate = new MapRegionCoordinate(
                            resultSet.getInt("region_x"),
                            resultSet.getInt("region_z")
                    );
                    try {
                        MapRegionSnapshotEntry entry =
                                MapRegionSnapshotEntryCodec.decode(
                                        resultSet.getBytes("payload")
                                );
                        if (!entry.coordinate().equals(coordinate)) {
                            corrupt++;
                        } else {
                            entries.add(entry);
                        }
                    } catch (RuntimeException exception) {
                        corrupt++;
                    }
                }
            }
            entries.sort(
                    Comparator.comparingInt(
                                    (MapRegionSnapshotEntry entry) ->
                                            entry.coordinate().z()
                            )
                            .thenComparingInt(
                                    entry -> entry.coordinate().x()
                            )
            );
            return new MapRegionSnapshotRead(
                    complete,
                    entries,
                    corrupt
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot read mapregion snapshot: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public void publish(Collection<MapRegionSnapshotEntry> entries) {
        List<MapRegionSnapshotEntry> safe = List.copyOf(
                Objects.requireNonNull(entries, "entries are required")
        );
        if (safe.isEmpty()) return;
        requirePublishedRevision();
        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = openDatabase(true)) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mapregion_snapshot "
                                + "(region_x, region_z, payload) VALUES (?, ?, ?) "
                                + "ON CONFLICT(region_x,region_z) DO UPDATE SET "
                                + "payload = excluded.payload"
                )) {
                    for (MapRegionSnapshotEntry entry : safe) {
                        Objects.requireNonNull(
                                entry,
                                "entries cannot contain null"
                        );
                        statement.setInt(1, entry.coordinate().x());
                        statement.setInt(2, entry.coordinate().z());
                        statement.setBytes(
                                3,
                                MapRegionSnapshotEntryCodec.encode(entry)
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
                    "Cannot publish mapregion snapshot: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public boolean scanComplete() {
        if (!compatibleStoreAvailable()) return false;
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            return metaFlag(connection, SCAN_COMPLETE);
        } catch (SQLException exception) {
            return false;
        }
    }

    public void clearEntries() {
        if (!compatibleStoreAvailable()) {
            return;
        }
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM mapregion_snapshot");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot clear mapregion snapshot entries: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public void markScanIncomplete() {
        writeMeta(SCAN_COMPLETE, "false");
    }

    public void markScanComplete() {
        writeMeta(SCAN_COMPLETE, "true");
    }

    private void writeMeta(String key, String value) {
        requirePublishedRevision();
        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = openDatabase(true)) {
                ensureSchema(connection);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mapregion_snapshot_meta (key, value) "
                                + "VALUES (?, ?) "
                                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value"
                )) {
                    statement.setString(1, key);
                    statement.setString(2, value);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new IllegalStateException(
                    "Cannot update mapregion snapshot metadata: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private boolean metaFlag(Connection connection, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM mapregion_snapshot_meta WHERE key = ?"
        )) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        && Boolean.parseBoolean(resultSet.getString("value"));
            }
        }
    }

    private boolean compatibleStoreAvailable() {
        return cacheStore.find(revision).isPresent()
                && Files.isRegularFile(databasePath);
    }

    private void requirePublishedRevision() {
        if (cacheStore.find(revision).isEmpty()) {
            throw new IllegalStateException(
                    "mapregion snapshot requires a compatible published manifest"
            );
        }
    }

    private Connection openDatabase(boolean create) throws SQLException {
        if (!create && !Files.isRegularFile(databasePath)) {
            throw new SQLException("mapregion snapshot database is missing");
        }
        return DriverManager.getConnection(
                "jdbc:sqlite:"
                        + databasePath.toAbsolutePath().normalize().toUri()
        );
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS mapregion_snapshot ("
                            + "region_x INTEGER NOT NULL,"
                            + "region_z INTEGER NOT NULL,"
                            + "payload BLOB NOT NULL,"
                            + "PRIMARY KEY (region_x, region_z)"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS mapregion_snapshot_meta ("
                            + "key TEXT PRIMARY KEY,"
                            + "value TEXT NOT NULL"
                            + ")"
            );
        }
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
