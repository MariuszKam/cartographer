package cartographer.geology.rock;

import cartographer.model.BlockInfo;
import cartographer.model.WorldPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable compact analytical ROCK result. */
public final class RockMap {
    private final WorldPosition center;
    private final int radius;
    private final RockCircleGeometry geometry;
    private final int minY;
    private final int maxYExclusive;
    private final RockMapMode mode;
    private final RockCellLayout layout;
    private final List<RockIdentity> ordinalTable;
    private final int[] intCells;
    private final long[] longCells;
    private final long[] presentWords;
    private final long[] countsByOrdinal;
    private final long observedCount;
    private final long noRockCount;
    private final long unavailableCount;

    /** Transitional constructor for small legacy/test callers; samples are not retained. */
    public RockMap(WorldPosition center, int radius, List<RockColumnSample> columns) {
        Objects.requireNonNull(columns, "Rock map columns are required");
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (RockColumnSample sample : columns) {
            Objects.requireNonNull(sample, "columns must not contain null");
            if (sample.state() == RockColumnState.OBSERVED) {
                int y = sample.rockY().orElseThrow();
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        int inferredMin = min == Integer.MAX_VALUE ? 0 : min;
        int inferredMax = max == Integer.MIN_VALUE ? Math.addExact(inferredMin, 1) : Math.addExact(max, 1);
        RockMapBuilder builder = new RockMapBuilder(center, radius, inferredMin, inferredMax,
                RockMapMode.UPPER_ROCK, compatibilityCatalog(columns));
        for (RockColumnSample sample : columns) builder.accept(sample);
        RockMap built = builder.finish();
        this.center = built.center;
        this.radius = built.radius;
        this.geometry = built.geometry;
        this.minY = built.minY;
        this.maxYExclusive = built.maxYExclusive;
        this.mode = built.mode;
        this.layout = built.layout;
        this.ordinalTable = built.ordinalTable;
        this.intCells = built.intCells;
        this.longCells = built.longCells;
        this.presentWords = built.presentWords;
        this.countsByOrdinal = built.countsByOrdinal;
        this.observedCount = built.observedCount;
        this.noRockCount = built.noRockCount;
        this.unavailableCount = built.unavailableCount;
    }

    static RockMap fromLegacySamples(WorldPosition center, int radius, int minY, int maxYExclusive,
                                     RockMapMode mode, RockCatalog catalog, List<RockColumnSample> columns) {
        RockMapBuilder builder = new RockMapBuilder(center, radius, minY, maxYExclusive, mode, catalog);
        for (RockColumnSample sample : columns) builder.accept(sample);
        return builder.finish();
    }

    RockMap(WorldPosition center, int radius, RockCircleGeometry geometry, int minY, int maxYExclusive,
            RockMapMode mode, RockCellLayout layout, List<RockIdentity> ordinalTable, int[] intCells, long[] longCells,
            long[] presentWords, long[] countsByOrdinal, long observedCount, long noRockCount,
            long unavailableCount) {
        if (radius <= 0 || minY >= maxYExclusive) throw new IllegalArgumentException("invalid ROCK map bounds");
        this.center = Objects.requireNonNull(center, "center is required");
        this.radius = radius;
        this.geometry = Objects.requireNonNull(geometry, "geometry is required");
        this.minY = minY;
        this.maxYExclusive = maxYExclusive;
        this.mode = Objects.requireNonNull(mode, "mode is required");
        this.layout = Objects.requireNonNull(layout, "layout is required");
        this.ordinalTable = List.copyOf(ordinalTable);
        this.intCells = intCells;
        this.longCells = longCells;
        this.presentWords = presentWords;
        this.countsByOrdinal = countsByOrdinal;
        this.observedCount = observedCount;
        this.noRockCount = noRockCount;
        this.unavailableCount = unavailableCount;
    }

    static List<RockIdentity> buildOrdinalTable(RockCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog is required");
        List<Integer> blockIds = new ArrayList<>(catalog.rockBlockIds());
        blockIds.sort(Integer::compareTo);
        List<RockIdentity> result = new ArrayList<>();
        for (Integer blockId : blockIds) {
            RockIdentity identity = catalog.findByBlockId(blockId).orElseThrow();
            if (result.stream().noneMatch(existing -> existing.code().equals(identity.code()))) result.add(identity);
        }
        return List.copyOf(result);
    }

    private static RockCatalog compatibilityCatalog(List<RockColumnSample> columns) {
        Map<Integer, BlockInfo> registry = new HashMap<>();
        for (RockColumnSample sample : columns) {
            if (sample.state() == RockColumnState.OBSERVED) {
                RockIdentity identity = sample.rock().orElseThrow();
                registry.put(identity.blockId(), new BlockInfo(identity.blockId(), identity.code()));
            }
        }
        return RockCatalog.from(registry);
    }

    public WorldPosition center() { return center; }
    public int radius() { return radius; }
    public RockCircleGeometry geometry() { return geometry; }
    public int minY() { return minY; }
    public int maxYExclusive() { return maxYExclusive; }
    public RockMapMode mode() { return mode; }
    public long observedCount() { return observedCount; }
    public long noRockCount() { return noRockCount; }
    public long unavailableCount() { return unavailableCount; }
    public List<RockIdentity> ordinalTable() { return ordinalTable; }
    public long[] countsByOrdinal() { return countsByOrdinal.clone(); }
    /** Returns whether the indexed cell was populated by the compatibility builder. */
    public boolean isPopulatedAtIndex(int index) {
        checkCellIndex(index);
        return present(index);
    }
    /** Reads a cell state without exposing the packed backing storage. */
    public RockColumnState stateAtIndex(int index) {
        return layout.state(packedAtIndex(index));
    }
    /** Reads the observed ordinal without exposing the packed backing storage. */
    public int rockOrdinalAtIndex(int index) {
        return layout.rockOrdinal(packedAtIndex(index));
    }
    public RockColumnState stateAt(int worldX, int worldZ) { return layout.state(packedAt(worldX, worldZ)); }
    public int rockOrdinalAt(int worldX, int worldZ) { return layout.rockOrdinal(packedAt(worldX, worldZ)); }
    public OptionalInt rockYAt(int worldX, int worldZ) {
        long packed = packedAt(worldX, worldZ);
        if (layout.state(packed) != RockColumnState.OBSERVED) return OptionalInt.empty();
        return OptionalInt.of(Math.toIntExact((long) minY + layout.yOffset(packed)));
    }

    public Optional<RockColumnSample> sampleAt(int worldX, int worldZ) {
        if (!geometry.contains(worldX, worldZ)) return Optional.empty();
        int index = geometry.cellIndex(worldX, worldZ);
        if (!present(index)) return Optional.empty();
        return Optional.of(materialize(index, worldX, worldZ));
    }

    /**
     * Test-oracle adapter: materializes samples on demand and never caches them.
     * Production consumers must use compact indexed access instead.
     */
    List<RockColumnSample> columns() {
        List<RockColumnSample> result = new ArrayList<>();
        for (int row = 0; row < geometry.rowCount(); row++) {
            int z = geometry.worldZForRow(row);
            int start = geometry.rowStartX(row);
            for (int i = 0; i < geometry.rowLength(row); i++) {
                int x = Math.addExact(start, i);
                sampleAt(x, z).ifPresent(result::add);
            }
        }
        return List.copyOf(result);
    }

    int packedStorageIdentityForTest() {
        return System.identityHashCode(layout.intBacked() ? intCells : longCells);
    }

    private boolean present(int index) { return (presentWords[index >>> 6] & (1L << (index & 63))) != 0; }
    private long packedAtIndex(int index) {
        checkCellIndex(index);
        if (!present(index)) throw new IllegalStateException("ROCK cell has not been populated");
        return layout.intBacked() ? intCells[index] & 0xFFFF_FFFFL : longCells[index];
    }
    private void checkCellIndex(int index) {
        if (index < 0 || (long) index >= geometry.cellCount()) {
            throw new IndexOutOfBoundsException("invalid rock circle cell index: " + index);
        }
    }
    private long packedAt(int worldX, int worldZ) {
        if (!geometry.contains(worldX, worldZ)) throw new IllegalArgumentException("coordinate is outside the rock circle");
        int index = geometry.cellIndex(worldX, worldZ);
        if (!present(index)) throw new IllegalStateException("ROCK cell has not been populated");
        return layout.intBacked() ? intCells[index] & 0xFFFF_FFFFL : longCells[index];
    }

    private RockColumnSample materialize(int index, int x, int z) {
        long packed = layout.intBacked() ? intCells[index] & 0xFFFF_FFFFL : longCells[index];
        RockColumnState state = layout.state(packed);
        if (state == RockColumnState.OBSERVED) {
            RockIdentity identity = ordinalTable.get(layout.rockOrdinal(packed) - 1);
            int y = Math.toIntExact((long) minY + layout.yOffset(packed));
            return RockColumnSample.observed(x, z, identity, y);
        }
        return state == RockColumnState.NO_ROCK ? RockColumnSample.noRock(x, z) : RockColumnSample.unavailable(x, z);
    }
}
