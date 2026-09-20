package cartographer.render;

import cartographer.application.ProgressReporter;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.MapChunkHeightView;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Render-sized Terrain height state.
 *
 * <p>Only world columns that the raster can observe, or that are immediate
 * north/south/east/west hillshade neighbours of an observable column, retain a
 * height value. Palette min/max are still derived from every effective height
 * in the requested world window so rendering semantics remain unchanged.</p>
 */
final class SampledTerrainHeightField implements TerrainHeightField {
    private final int minWorldX;
    private final int minWorldZ;
    private final int worldDiameter;
    private final int[] sampleXIndexByOffset;
    private final int[] horizontalXIndexByOffset;
    private final Row[] rows;
    private final int minHeight;
    private final int maxHeight;
    private final int sampleCount;

    private SampledTerrainHeightField(
            int minWorldX,
            int minWorldZ,
            int worldDiameter,
            int[] sampleXIndexByOffset,
            int[] horizontalXIndexByOffset,
            Row[] rows,
            int minHeight,
            int maxHeight,
            int sampleCount
    ) {
        this.minWorldX = minWorldX;
        this.minWorldZ = minWorldZ;
        this.worldDiameter = worldDiameter;
        this.sampleXIndexByOffset = sampleXIndexByOffset;
        this.horizontalXIndexByOffset = horizontalXIndexByOffset;
        this.rows = rows;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.sampleCount = sampleCount;
    }

    static Builder builder(
            RenderSamplingPlan sampling,
            ProgressReporter progress,
            int expectedChunks
    ) {
        return new Builder(sampling, progress, expectedChunks);
    }

    @Override
    public boolean hasHeightAt(int worldX, int worldZ) {
        Cell cell = cell(worldX, worldZ);
        return cell != null && cell.row.present.get(cell.index);
    }

    @Override
    public int heightAt(int worldX, int worldZ) {
        Cell cell = cell(worldX, worldZ);
        if (cell == null || !cell.row.present.get(cell.index)) {
            throw new IllegalArgumentException(
                    "height sample is absent at " + worldX + "," + worldZ
            );
        }
        return cell.row.values[cell.index];
    }

    @Override
    public int minHeight() {
        return minHeight;
    }

    @Override
    public int maxHeight() {
        return maxHeight;
    }

    @Override
    public int sampleCount() {
        return sampleCount;
    }

    private Cell cell(int worldX, int worldZ) {
        long xOffsetLong = (long) worldX - minWorldX;
        long zOffsetLong = (long) worldZ - minWorldZ;
        if (xOffsetLong < 0 || xOffsetLong >= worldDiameter
                || zOffsetLong < 0 || zOffsetLong >= worldDiameter) {
            return null;
        }
        int xOffset = (int) xOffsetLong;
        Row row = rows[(int) zOffsetLong];
        if (row == null) {
            return null;
        }
        int index = row.horizontal
                ? horizontalXIndexByOffset[xOffset]
                : sampleXIndexByOffset[xOffset];
        return index < 0 ? null : new Cell(row, index);
    }

    static final class Builder {
        private final int minWorldX;
        private final int minWorldZ;
        private final int worldDiameter;
        private final int[] sampleXIndexByOffset;
        private final int[] horizontalXIndexByOffset;
        private final Row[] rows;
        private final ProgressReporter progress;
        private final int expectedChunks;
        private final Map<MapChunkCoordinate, HeightRange> finalRanges =
                new HashMap<>();
        private int acceptedChunks;

        private Builder(
                RenderSamplingPlan sampling,
                ProgressReporter progress,
                int expectedChunks
        ) {
            Objects.requireNonNull(sampling, "sampling plan is required");
            this.progress = Objects.requireNonNull(
                    progress,
                    "progress is required"
            );
            if (expectedChunks < 0) {
                throw new IllegalArgumentException(
                        "expected chunks cannot be negative"
                );
            }
            this.expectedChunks = expectedChunks;
            this.minWorldX = sampling.worldMinX();
            this.minWorldZ = sampling.worldMinZ();
            this.worldDiameter = sampling.worldDiameterBlocks();

            boolean[] sampledX = new boolean[worldDiameter];
            boolean[] sampledZ = new boolean[worldDiameter];
            for (int image = 0; image < sampling.rasterSize(); image++) {
                sampledX[Math.toIntExact(
                        (long) sampling.worldXForImageColumn(image)
                                - minWorldX
                )] = true;
                sampledZ[Math.toIntExact(
                        (long) sampling.worldZForImageRow(image)
                                - minWorldZ
                )] = true;
            }

            boolean[] horizontalX = sampledX.clone();
            for (int offset = 0; offset < sampledX.length; offset++) {
                if (!sampledX[offset]) {
                    continue;
                }
                if (offset > 0) {
                    horizontalX[offset - 1] = true;
                }
                if (offset + 1 < horizontalX.length) {
                    horizontalX[offset + 1] = true;
                }
            }

            IndexMap sampleX = IndexMap.from(sampledX);
            IndexMap horizontal = IndexMap.from(horizontalX);
            this.sampleXIndexByOffset = sampleX.indexByOffset;
            this.horizontalXIndexByOffset = horizontal.indexByOffset;
            this.rows = new Row[worldDiameter];

            for (int zOffset = 0; zOffset < worldDiameter; zOffset++) {
                if (sampledZ[zOffset]) {
                    rows[zOffset] = new Row(true, horizontal.count);
                    continue;
                }
                boolean adjacent = (zOffset > 0 && sampledZ[zOffset - 1])
                        || (zOffset + 1 < sampledZ.length
                        && sampledZ[zOffset + 1]);
                if (adjacent) {
                    rows[zOffset] = new Row(false, sampleX.count);
                }
            }
        }

        void accept(MapChunkHeightView chunk) {
            Objects.requireNonNull(chunk, "chunks cannot contain null");
            acceptedChunks++;
            progress.progress(
                    "Indexing render-sized mapchunk heights",
                    acceptedChunks,
                    expectedChunks
            );
            if (!chunk.hasEffectiveHeight()) {
                return;
            }

            long originX = (long) chunk.coordinate().x()
                    * MapChunkCoordinate.SIZE_BLOCKS;
            long originZ = (long) chunk.coordinate().z()
                    * MapChunkCoordinate.SIZE_BLOCKS;
            boolean foundRange = false;
            int tileMin = 0;
            int tileMax = 0;

            for (int localZ = 0;
                 localZ < MapChunkCoordinate.SIZE_BLOCKS;
                 localZ++) {
                long zOffsetLong = originZ + localZ - minWorldZ;
                if (zOffsetLong < 0 || zOffsetLong >= worldDiameter) {
                    continue;
                }
                int zOffset = (int) zOffsetLong;
                Row row = rows[zOffset];

                for (int localX = 0;
                     localX < MapChunkCoordinate.SIZE_BLOCKS;
                     localX++) {
                    long xOffsetLong = originX + localX - minWorldX;
                    if (xOffsetLong < 0 || xOffsetLong >= worldDiameter) {
                        continue;
                    }
                    int height = chunk.effectiveHeightAt(localX, localZ);
                    if (!foundRange) {
                        tileMin = height;
                        tileMax = height;
                        foundRange = true;
                    } else {
                        tileMin = Math.min(tileMin, height);
                        tileMax = Math.max(tileMax, height);
                    }

                    if (row == null) {
                        continue;
                    }
                    int xOffset = (int) xOffsetLong;
                    int index = row.horizontal
                            ? horizontalXIndexByOffset[xOffset]
                            : sampleXIndexByOffset[xOffset];
                    if (index >= 0) {
                        row.values[index] = height;
                        row.present.set(index);
                    }
                }
            }

            if (foundRange) {
                finalRanges.put(
                        chunk.coordinate(),
                        new HeightRange(tileMin, tileMax)
                );
            }
        }

        SampledTerrainHeightField finish() {
            int minHeight = 0;
            int maxHeight = 0;
            boolean found = false;
            for (HeightRange range : finalRanges.values()) {
                if (!found) {
                    minHeight = range.min;
                    maxHeight = range.max;
                    found = true;
                } else {
                    minHeight = Math.min(minHeight, range.min);
                    maxHeight = Math.max(maxHeight, range.max);
                }
            }

            int samples = 0;
            for (Row row : rows) {
                if (row != null) {
                    samples = Math.addExact(
                            samples,
                            row.present.cardinality()
                    );
                }
            }

            return new SampledTerrainHeightField(
                    minWorldX,
                    minWorldZ,
                    worldDiameter,
                    sampleXIndexByOffset,
                    horizontalXIndexByOffset,
                    rows,
                    minHeight,
                    maxHeight,
                    samples
            );
        }
    }

    private static final class Row {
        private final boolean horizontal;
        private final int[] values;
        private final BitSet present;

        private Row(boolean horizontal, int size) {
            this.horizontal = horizontal;
            this.values = new int[size];
            this.present = new BitSet(size);
        }
    }

    private record Cell(Row row, int index) {
    }

    private record HeightRange(int min, int max) {
    }

    private static final class IndexMap {
        private final int[] indexByOffset;
        private final int count;

        private IndexMap(int[] indexByOffset, int count) {
            this.indexByOffset = indexByOffset;
            this.count = count;
        }

        private static IndexMap from(boolean[] required) {
            int[] indexByOffset = new int[required.length];
            Arrays.fill(indexByOffset, -1);
            int count = 0;
            for (int offset = 0; offset < required.length; offset++) {
                if (required[offset]) {
                    indexByOffset[offset] = count++;
                }
            }
            return new IndexMap(indexByOffset, count);
        }
    }
}
