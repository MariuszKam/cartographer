package cartographer.geology.rock;

import cartographer.model.WorldPosition;

import java.util.List;
import java.util.Objects;

/** Package-private mutable owner used to transfer compact map storage once. */
final class RockMapBuilder {
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
    private long observedCount;
    private long noRockCount;
    private long unavailableCount;
    private boolean transferred;

    RockMapBuilder(WorldPosition center, int radius, int minY, int maxYExclusive,
                   RockMapMode mode, RockCatalog catalog) {
        this.center = Objects.requireNonNull(center, "center is required");
        this.radius = radius;
        this.geometry = RockCircleGeometry.from(center, radius);
        if (minY >= maxYExclusive) throw new IllegalArgumentException("Y range must not be empty");
        this.minY = minY;
        this.maxYExclusive = maxYExclusive;
        this.mode = Objects.requireNonNull(mode, "mode is required");
        this.ordinalTable = RockMap.buildOrdinalTable(catalog);
        this.layout = RockCellLayout.forCatalog(ordinalTable.size(), (long) maxYExclusive - minY);
        int cellCount = Math.toIntExact(geometry.cellCount());
        this.intCells = layout.intBacked() ? new int[cellCount] : null;
        this.longCells = layout.intBacked() ? null : new long[cellCount];
        this.presentWords = new long[Math.toIntExact((geometry.cellCount() + 63L) / 64L)];
        this.countsByOrdinal = new long[ordinalTable.size() + 1];
    }

    void accept(RockColumnSample sample) {
        checkOpen();
        Objects.requireNonNull(sample, "sample is required");
        int index = geometry.cellIndex(sample.worldX(), sample.worldZ());
        int word = index >>> 6;
        long bit = 1L << (index & 63);
        if ((presentWords[word] & bit) != 0) throw new IllegalArgumentException("duplicate ROCK map cell");
        int ordinal = 0;
        long yOffset = 0;
        if (sample.state() == RockColumnState.OBSERVED) {
            ordinal = ordinalFor(sample.rock().orElseThrow());
            yOffset = (long) sample.rockY().orElseThrow() - minY;
        }
        long packed = layout.pack(sample.state(), ordinal, yOffset);
        if (layout.intBacked()) intCells[index] = (int) packed;
        else longCells[index] = packed;
        presentWords[word] |= bit;
        if (sample.state() == RockColumnState.OBSERVED) {
            observedCount++;
            countsByOrdinal[ordinal]++;
        } else if (sample.state() == RockColumnState.NO_ROCK) noRockCount++;
        else unavailableCount++;
    }

    RockMap finish() {
        checkOpen();
        transferred = true;
        return new RockMap(center, radius, geometry, minY, maxYExclusive, mode, layout,
                ordinalTable, intCells, longCells, presentWords, countsByOrdinal,
                observedCount, noRockCount, unavailableCount);
    }

    int packedStorageIdentityForTest() {
        checkOpen();
        return System.identityHashCode(layout.intBacked() ? intCells : longCells);
    }

    private int ordinalFor(RockIdentity identity) {
        for (int i = 0; i < ordinalTable.size(); i++) {
            if (ordinalTable.get(i).code().equals(identity.code())) return i + 1;
        }
        throw new IllegalArgumentException("observed rock is absent from catalog: " + identity.code());
    }

    private void checkOpen() {
        if (transferred) throw new IllegalStateException("ROCK map builder ownership was transferred");
    }
}
