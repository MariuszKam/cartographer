package cartographer.perf;

import cartographer.cli.CommandException;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.save.ChunkPosDecoder;
import cartographer.save.ChunkPosEncoder;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Revision-scoped PF-2.5 resource membership/occurrence index.
 *
 * <p>The database is derived data below the external cache namespace. One
 * coverage row is stored for every indexed source chunk position, including
 * authoritative MISSING and FAILED states. AVAILABLE rows may own compact ore
 * membership and exact local-Y occurrence masks.</p>
 */
public final class ResourceIndexStore {
    private static final String DATABASE_FILE = "resource-index-v1.sqlite";
    private static final String SCAN_COMPLETE = "resource-scan-complete";
    private static final int POSITION_BATCH_SIZE = 300;
    private static final int BLOCK_ID_BATCH_SIZE = 300;

    private final RenderDataCacheStore cacheStore;
    private final RenderDataCacheRevision revision;
    private final Path databasePath;

    public ResourceIndexStore(
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

    public void publishBlockCatalog(Collection<BlockInfo> blocks) {
        List<BlockInfo> input = List.copyOf(
                Objects.requireNonNull(blocks, "blocks are required")
        );
        LinkedHashMap<Integer, BlockInfo> uniqueById =
                new LinkedHashMap<>();
        for (BlockInfo block : input) {
            Objects.requireNonNull(
                    block,
                    "blocks cannot contain null"
            );
            if (block.id() < 0
                    || block.code() == null
                    || block.code().isBlank()) {
                throw new IllegalArgumentException(
                        "resource block ID/code must be valid"
                );
            }
            BlockInfo previous = uniqueById.putIfAbsent(
                    block.id(),
                    block
            );
            if (previous != null
                    && !previous.code().equals(block.code())) {
                throw new IllegalArgumentException(
                        "resource block ID has conflicting codes: "
                                + block.id()
                );
            }
        }
        List<BlockInfo> safe = uniqueById.values().stream()
                .sorted(
                        Comparator.comparingInt(BlockInfo::id)
                                .thenComparing(BlockInfo::code)
                )
                .toList();
        requirePublishedRevision();
        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = openDatabase(true)) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try {
                    try (var clear = connection.createStatement()) {
                        clear.executeUpdate("DELETE FROM resource_block");
                    }
                    try (PreparedStatement statement =
                                 connection.prepareStatement(
                                         "INSERT INTO resource_block "
                                                 + "(block_id, code) VALUES (?, ?)"
                                 )) {
                        for (BlockInfo block : safe) {
                            if (block.code() == null
                                    || block.code().isBlank()) {
                                throw new IllegalArgumentException(
                                        "resource block code must not be blank"
                                );
                            }
                            statement.setInt(1, block.id());
                            statement.setString(2, block.code());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException failure) {
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new CommandException(
                    "Cannot publish PF-2.5 resource block catalog: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public Map<Integer, String> blockCatalog() {
        if (!compatibleStoreAvailable()) {
            return Map.of();
        }
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            LinkedHashMap<Integer, String> result = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT block_id, code FROM resource_block ORDER BY block_id"
            ); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int blockId = rows.getInt("block_id");
                    String code = rows.getString("code");
                    if (blockId < 0 || code == null || code.isBlank()) {
                        throw new SQLException(
                                "invalid resource block catalog row"
                        );
                    }
                    result.put(blockId, code);
                }
            }
            return Map.copyOf(result);
        } catch (SQLException exception) {
            return Map.of();
        }
    }

    public Map<ChunkPosition, ResourceChunkIndexLookup> readCoverage(
            Collection<ChunkPosition> positions
    ) {
        List<ChunkPosition> requested = uniquePositions(positions);
        LinkedHashMap<ChunkPosition, ResourceChunkIndexLookup> result =
                new LinkedHashMap<>();
        requested.forEach(position ->
                result.put(position, ResourceChunkIndexLookup.miss())
        );
        if (requested.isEmpty()
                || !compatibleStoreAvailable()) {
            return Map.copyOf(result);
        }

        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            for (int start = 0;
                 start < requested.size();
                 start += POSITION_BATCH_SIZE) {
                List<ChunkPosition> batch = requested.subList(
                        start,
                        Math.min(
                                start + POSITION_BATCH_SIZE,
                                requested.size()
                        )
                );
                readCoverageBatch(connection, batch, result);
            }
            return Map.copyOf(result);
        } catch (SQLException exception) {
            requested.forEach(position ->
                    result.put(position, ResourceChunkIndexLookup.corrupt())
            );
            return Map.copyOf(result);
        }
    }

    public Set<ChunkPosition> positionsContainingAny(
            Collection<ChunkPosition> positions,
            Collection<Integer> blockIds
    ) {
        List<ChunkPosition> requested = uniquePositions(positions);
        List<Integer> ids = uniqueBlockIds(blockIds);
        if (requested.isEmpty()
                || ids.isEmpty()
                || !compatibleStoreAvailable()) {
            return Set.of();
        }

        LinkedHashSet<ChunkPosition> result = new LinkedHashSet<>();
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            Map<Long, ChunkPosition> requestedByPacked =
                    byPackedPosition(requested);
            for (int positionStart = 0;
                 positionStart < requested.size();
                 positionStart += POSITION_BATCH_SIZE) {
                List<ChunkPosition> positionBatch = requested.subList(
                        positionStart,
                        Math.min(
                                positionStart + POSITION_BATCH_SIZE,
                                requested.size()
                        )
                );
                for (int idStart = 0;
                     idStart < ids.size();
                     idStart += BLOCK_ID_BATCH_SIZE) {
                    List<Integer> idBatch = ids.subList(
                            idStart,
                            Math.min(
                                    idStart + BLOCK_ID_BATCH_SIZE,
                                    ids.size()
                            )
                    );
                    readMembershipBatch(
                            connection,
                            positionBatch,
                            idBatch,
                            requestedByPacked,
                            result
                    );
                }
            }
            return Set.copyOf(result);
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot query PF-2.5 resource membership: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public List<ResourceOccurrence> readOccurrences(
            Collection<ChunkPosition> positions,
            Collection<Integer> blockIds
    ) {
        List<ChunkPosition> requested = uniquePositions(positions);
        List<Integer> ids = uniqueBlockIds(blockIds);
        if (requested.isEmpty()
                || ids.isEmpty()
                || !compatibleStoreAvailable()) {
            return List.of();
        }

        List<ResourceOccurrence> result = new ArrayList<>();
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            Map<Long, ChunkPosition> requestedByPacked =
                    byPackedPosition(requested);
            for (int positionStart = 0;
                 positionStart < requested.size();
                 positionStart += POSITION_BATCH_SIZE) {
                List<ChunkPosition> positionBatch = requested.subList(
                        positionStart,
                        Math.min(
                                positionStart + POSITION_BATCH_SIZE,
                                requested.size()
                        )
                );
                for (int idStart = 0;
                     idStart < ids.size();
                     idStart += BLOCK_ID_BATCH_SIZE) {
                    List<Integer> idBatch = ids.subList(
                            idStart,
                            Math.min(
                                    idStart + BLOCK_ID_BATCH_SIZE,
                                    ids.size()
                            )
                    );
                    readOccurrenceBatch(
                            connection,
                            positionBatch,
                            idBatch,
                            requestedByPacked,
                            result
                    );
                }
            }
        } catch (SQLException exception) {
            throw new CommandException(
                    "Cannot query PF-2.5 resource occurrences: "
                            + exception.getMessage(),
                    exception
            );
        }

        result.sort(
                Comparator.comparingInt(
                                (ResourceOccurrence occurrence) ->
                                        occurrence.position().y()
                        )
                        .thenComparingInt(
                                occurrence -> occurrence.position().z()
                        )
                        .thenComparingInt(
                                occurrence -> occurrence.position().x()
                        )
                        .thenComparingInt(ResourceOccurrence::blockId)
                        .thenComparingInt(ResourceOccurrence::localZ)
                        .thenComparingInt(ResourceOccurrence::localX)
        );
        return List.copyOf(result);
    }

    public void publish(Collection<ResourceChunkIndexEntry> entries) {
        List<ResourceChunkIndexEntry> safe = List.copyOf(
                Objects.requireNonNull(entries, "entries are required")
        );
        if (safe.isEmpty()) {
            return;
        }
        LinkedHashSet<ChunkPosition> uniquePositions =
                new LinkedHashSet<>();
        for (ResourceChunkIndexEntry entry : safe) {
            Objects.requireNonNull(
                    entry,
                    "entries cannot contain null"
            );
            if (!uniquePositions.add(entry.position())) {
                throw new IllegalArgumentException(
                        "duplicate resource chunk entry: "
                                + entry.position()
                );
            }
        }
        requirePublishedRevision();

        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = openDatabase(true)) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try (PreparedStatement deleteMembership =
                             connection.prepareStatement(
                                     "DELETE FROM resource_membership "
                                             + "WHERE packed_position = ?"
                             );
                     PreparedStatement deleteOccurrence =
                             connection.prepareStatement(
                                     "DELETE FROM resource_occurrence "
                                             + "WHERE packed_position = ?"
                             );
                     PreparedStatement upsertCoverage =
                             connection.prepareStatement(
                                     "INSERT INTO resource_chunk "
                                             + "(packed_position, status) "
                                             + "VALUES (?, ?) "
                                             + "ON CONFLICT(packed_position) "
                                             + "DO UPDATE SET status = excluded.status"
                             );
                     PreparedStatement insertMembership =
                             connection.prepareStatement(
                                     "INSERT OR REPLACE INTO resource_membership "
                                             + "(packed_position, block_id) "
                                             + "VALUES (?, ?)"
                             );
                     PreparedStatement insertOccurrence =
                             connection.prepareStatement(
                                     "INSERT OR REPLACE INTO resource_occurrence "
                                             + "(packed_position, block_id, "
                                             + "local_x, local_z, y_mask) "
                                             + "VALUES (?, ?, ?, ?, ?)"
                             )) {
                    for (ResourceChunkIndexEntry entry : safe) {
                        long packed = ChunkPosEncoder.encode(entry.position());

                        deleteMembership.setLong(1, packed);
                        deleteMembership.addBatch();
                        deleteOccurrence.setLong(1, packed);
                        deleteOccurrence.addBatch();

                        upsertCoverage.setLong(1, packed);
                        upsertCoverage.setString(
                                2,
                                entry.coverageStatus().name()
                        );
                        upsertCoverage.addBatch();

                        if (entry.coverageStatus()
                                == ResourceChunkCoverageStatus.AVAILABLE) {
                            for (int blockId : entry.blockIdsPresent()) {
                                insertMembership.setLong(1, packed);
                                insertMembership.setInt(2, blockId);
                                insertMembership.addBatch();
                            }
                            for (ResourceOccurrence occurrence :
                                    entry.occurrences()) {
                                insertOccurrence.setLong(1, packed);
                                insertOccurrence.setInt(
                                        2,
                                        occurrence.blockId()
                                );
                                insertOccurrence.setInt(
                                        3,
                                        occurrence.localX()
                                );
                                insertOccurrence.setInt(
                                        4,
                                        occurrence.localZ()
                                );
                                insertOccurrence.setLong(
                                        5,
                                        occurrence.localYMask()
                                );
                                insertOccurrence.addBatch();
                            }
                        }
                    }

                    deleteMembership.executeBatch();
                    deleteOccurrence.executeBatch();
                    upsertCoverage.executeBatch();
                    insertMembership.executeBatch();
                    insertOccurrence.executeBatch();
                    connection.commit();
                } catch (SQLException | RuntimeException failure) {
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new CommandException(
                    "Cannot publish PF-2.5 resource index: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public boolean scanComplete() {
        if (!compatibleStoreAvailable()) {
            return false;
        }
        try (Connection connection = openDatabase(false)) {
            ensureSchema(connection);
            return metaFlag(connection, SCAN_COMPLETE);
        } catch (SQLException exception) {
            return false;
        }
    }

    public void markScanIncomplete() {
        writeMeta(SCAN_COMPLETE, "false");
    }

    public void markScanComplete() {
        writeMeta(SCAN_COMPLETE, "true");
    }

    private void readCoverageBatch(
            Connection connection,
            List<ChunkPosition> positions,
            Map<ChunkPosition, ResourceChunkIndexLookup> result
    ) throws SQLException {
        Map<Long, ChunkPosition> byPacked = byPackedPosition(positions);
        String sql = "SELECT packed_position, status "
                + "FROM resource_chunk WHERE packed_position IN ("
                + placeholders(positions.size()) + ")";
        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            bindPositions(statement, positions, 1);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long packed = rows.getLong("packed_position");
                    ChunkPosition requested = byPacked.get(packed);
                    if (requested == null) {
                        continue;
                    }
                    try {
                        ResourceChunkCoverageStatus status =
                                ResourceChunkCoverageStatus.valueOf(
                                        rows.getString("status")
                                );
                        if (!ChunkPosDecoder.decode(packed)
                                .equals(requested)) {
                            result.put(
                                    requested,
                                    ResourceChunkIndexLookup.corrupt()
                            );
                        } else {
                            result.put(
                                    requested,
                                    ResourceChunkIndexLookup.hit(status)
                            );
                        }
                    } catch (RuntimeException exception) {
                        result.put(
                                requested,
                                ResourceChunkIndexLookup.corrupt()
                        );
                    }
                }
            }
        }
    }

    private void readMembershipBatch(
            Connection connection,
            List<ChunkPosition> positions,
            List<Integer> blockIds,
            Map<Long, ChunkPosition> requestedByPacked,
            Set<ChunkPosition> result
    ) throws SQLException {
        String sql = "SELECT DISTINCT packed_position "
                + "FROM resource_membership WHERE packed_position IN ("
                + placeholders(positions.size())
                + ") AND block_id IN ("
                + placeholders(blockIds.size())
                + ")";
        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            int parameter = bindPositions(statement, positions, 1);
            bindBlockIds(statement, blockIds, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ChunkPosition position = requestedByPacked.get(
                            rows.getLong("packed_position")
                    );
                    if (position != null) {
                        result.add(position);
                    }
                }
            }
        }
    }

    private void readOccurrenceBatch(
            Connection connection,
            List<ChunkPosition> positions,
            List<Integer> blockIds,
            Map<Long, ChunkPosition> requestedByPacked,
            List<ResourceOccurrence> result
    ) throws SQLException {
        String sql = "SELECT packed_position, block_id, local_x, local_z, y_mask "
                + "FROM resource_occurrence WHERE packed_position IN ("
                + placeholders(positions.size())
                + ") AND block_id IN ("
                + placeholders(blockIds.size())
                + ")";
        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            int parameter = bindPositions(statement, positions, 1);
            bindBlockIds(statement, blockIds, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ChunkPosition position = requestedByPacked.get(
                            rows.getLong("packed_position")
                    );
                    if (position == null) {
                        continue;
                    }
                    try {
                        result.add(
                                new ResourceOccurrence(
                                        position,
                                        rows.getInt("block_id"),
                                        rows.getInt("local_x"),
                                        rows.getInt("local_z"),
                                        rows.getLong("y_mask")
                                )
                        );
                    } catch (RuntimeException exception) {
                        throw new SQLException(
                                "corrupt PF-2.5 resource occurrence row",
                                exception
                        );
                    }
                }
            }
        }
    }

    private void writeMeta(String key, String value) {
        requirePublishedRevision();
        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = openDatabase(true)) {
                ensureSchema(connection);
                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     "INSERT INTO resource_index_meta "
                                             + "(key, value) VALUES (?, ?) "
                                             + "ON CONFLICT(key) DO UPDATE "
                                             + "SET value = excluded.value"
                             )) {
                    statement.setString(1, key);
                    statement.setString(2, value);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new CommandException(
                    "Cannot update PF-2.5 resource-index metadata: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private boolean metaFlag(Connection connection, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM resource_index_meta WHERE key = ?"
        )) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        && Boolean.parseBoolean(rows.getString("value"));
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
                    "resource index requires a compatible published manifest"
            );
        }
    }

    private Connection openDatabase(boolean create) throws SQLException {
        if (!create && !Files.isRegularFile(databasePath)) {
            throw new SQLException("resource index database is missing");
        }
        return DriverManager.getConnection(
                "jdbc:sqlite:"
                        + databasePath.toAbsolutePath().normalize().toUri()
        );
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS resource_block ("
                            + "block_id INTEGER PRIMARY KEY,"
                            + "code TEXT NOT NULL"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS resource_chunk ("
                            + "packed_position INTEGER PRIMARY KEY,"
                            + "status TEXT NOT NULL"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS resource_membership ("
                            + "packed_position INTEGER NOT NULL,"
                            + "block_id INTEGER NOT NULL,"
                            + "PRIMARY KEY (packed_position, block_id)"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS resource_membership_block_idx "
                            + "ON resource_membership(block_id, packed_position)"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS resource_occurrence ("
                            + "packed_position INTEGER NOT NULL,"
                            + "block_id INTEGER NOT NULL,"
                            + "local_x INTEGER NOT NULL,"
                            + "local_z INTEGER NOT NULL,"
                            + "y_mask INTEGER NOT NULL,"
                            + "PRIMARY KEY (packed_position, block_id, local_x, local_z)"
                            + ")"
            );
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS resource_occurrence_block_idx "
                            + "ON resource_occurrence(block_id, packed_position)"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS resource_index_meta ("
                            + "key TEXT PRIMARY KEY,"
                            + "value TEXT NOT NULL"
                            + ")"
            );
        }
    }

    private static List<ChunkPosition> uniquePositions(
            Collection<ChunkPosition> positions
    ) {
        Objects.requireNonNull(positions, "positions are required");
        LinkedHashSet<ChunkPosition> unique = new LinkedHashSet<>();
        for (ChunkPosition position : positions) {
            Objects.requireNonNull(
                    position,
                    "positions cannot contain null"
            );
            if (position.dimension() != 0) {
                throw new IllegalArgumentException(
                        "resource index only supports main-world dimension 0"
                );
            }
            unique.add(position);
        }
        return unique.stream()
                .sorted(
                        Comparator.comparingInt(ChunkPosition::y)
                                .thenComparingInt(ChunkPosition::z)
                                .thenComparingInt(ChunkPosition::x)
                )
                .toList();
    }

    private static List<Integer> uniqueBlockIds(
            Collection<Integer> blockIds
    ) {
        Objects.requireNonNull(blockIds, "blockIds are required");
        return blockIds.stream()
                .map(blockId -> Objects.requireNonNull(
                        blockId,
                        "blockIds cannot contain null"
                ))
                .peek(blockId -> {
                    if (blockId < 0) {
                        throw new IllegalArgumentException(
                                "blockIds cannot contain negative values"
                        );
                    }
                })
                .distinct()
                .sorted()
                .toList();
    }

    private static Map<Long, ChunkPosition> byPackedPosition(
            Collection<ChunkPosition> positions
    ) {
        LinkedHashMap<Long, ChunkPosition> result = new LinkedHashMap<>();
        for (ChunkPosition position : positions) {
            result.put(ChunkPosEncoder.encode(position), position);
        }
        return result;
    }

    private static int bindPositions(
            PreparedStatement statement,
            List<ChunkPosition> positions,
            int parameter
    ) throws SQLException {
        int current = parameter;
        for (ChunkPosition position : positions) {
            statement.setLong(
                    current++,
                    ChunkPosEncoder.encode(position)
            );
        }
        return current;
    }

    private static int bindBlockIds(
            PreparedStatement statement,
            List<Integer> blockIds,
            int parameter
    ) throws SQLException {
        int current = parameter;
        for (int blockId : blockIds) {
            statement.setInt(current++, blockId);
        }
        return current;
    }

    private static String placeholders(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "placeholder count must be positive"
            );
        }
        return "?, ".repeat(count - 1) + "?";
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
