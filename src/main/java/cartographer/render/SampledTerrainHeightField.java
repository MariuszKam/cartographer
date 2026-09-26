package cartographer.render;

import cartographer.progress.ProgressReporter;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.MapChunkHeightView;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Render-sized Terrain height state.
 *
 * <p>The field retains only height cells that can influence the final raster:
 * regular Terrain samples plus their hillshade neighbors, and optional final
 * Surface source cells plus their hillshade neighbors. Palette min/max still
 * come from every effective height in the requested world window so visual
 * semantics remain unchanged.</p>
 */
final class SampledTerrainHeightField implements TerrainHeightField {
    private final int minWorldX;
    private final int minWorldZ;
    private final int worldDiameter;
    private final Row[] rows;
    private final int minHeight;
    private final int maxHeight;
    private final int sampleCount;

    private SampledTerrainHeightField(
            int minWorldX,
            int minWorldZ,
            int worldDiameter,
            Row[] rows,
            int minHeight,
            int maxHeight,
            int sampleCount
    ) {
        this.minWorldX = minWorldX;
        this.minWorldZ = minWorldZ;
        this.worldDiameter = worldDiameter;
        this.rows = rows;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.sampleCount = sampleCount;
    }

    static Builder builder(
            RenderSamplingPlan sampling,
            int expectedChunks
    ) {
        return builder(
                sampling,
                true,
                SurfaceRenderData.empty(sampling),
                ProgressReporter.NONE,
                expectedChunks
        );
    }

    static Builder builder(
            RenderSamplingPlan sampling,
            boolean includeTerrainSamples,
            SurfaceRenderData surfaceData,
            ProgressReporter progress,
            int expectedChunks
    ) {
        return new Builder(
                sampling,
                includeTerrainSamples,
                surfaceData,
                progress,
                expectedChunks
        );
    }

    @Override
    public boolean hasHeightAt(int worldX, int worldZ) {
        Row row = rowAt(worldZ);
        if (row == null) {
            return false;
        }
        int index = row.indexAt((long) worldX - minWorldX);
        return index >= 0 && row.present.get(index);
    }

    @Override
    public int heightAt(int worldX, int worldZ) {
        Row row = rowAt(worldZ);
        int index = row == null
                ? -1
                : row.indexAt((long) worldX - minWorldX);
        if (index < 0 || !row.present.get(index)) {
            throw new IllegalArgumentException(
                    "height sample is absent at " + worldX + "," + worldZ
            );
        }
        return row.values[index];
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

    private Row rowAt(int worldZ) {
        long zOffset = (long) worldZ - minWorldZ;
        if (zOffset < 0 || zOffset >= worldDiameter) {
            return null;
        }
        return rows[(int) zOffset];
    }

    static final class Builder {
        private final int minWorldX;
        private final int minWorldZ;
        private final int worldDiameter;
        private final Row[] rows;
        private final ProgressReporter progress;
        private final int expectedChunks;
        private final Map<MapChunkCoordinate, HeightRange> finalRanges =
                new HashMap<>();
        private int acceptedChunks;

        private Builder(
                RenderSamplingPlan sampling,
                boolean includeTerrainSamples,
                SurfaceRenderData surfaceData,
                ProgressReporter progress,
                int expectedChunks
        ) {
            Objects.requireNonNull(sampling, "sampling plan is required");
            Objects.requireNonNull(surfaceData, "Surface render data is required");
            this.progress = Objects.requireNonNull(
                    progress,
                    "progress is required"
            );
            if (expectedChunks < 0) {
                throw new IllegalArgumentException(
                        "expected chunks cannot be negative"
                );
            }
            if (surfaceData.rasterSize() != sampling.rasterSize()) {
                throw new IllegalArgumentException(
                        "Surface render data does not match sampling raster"
                );
            }

            this.expectedChunks = expectedChunks;
            this.minWorldX = sampling.worldMinX();
            this.minWorldZ = sampling.worldMinZ();
            this.worldDiameter = sampling.worldDiameterBlocks();

            BitSet[] requiredByRow = new BitSet[worldDiameter];
            if (includeTerrainSamples) {
                addTerrainRequirements(sampling, requiredByRow);
            }
            addSurfaceRequirements(surfaceData, requiredByRow);
            this.rows = materializeRows(requiredByRow);
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
                Row row = rows[(int) zOffsetLong];

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
                    int index = row.indexAt(xOffsetLong);
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
                    rows,
                    minHeight,
                    maxHeight,
                    samples
            );
        }

        private void addTerrainRequirements(
                RenderSamplingPlan sampling,
                BitSet[] requiredByRow
        ) {
            BitSet sampledX = new BitSet(worldDiameter);
            BitSet sampledZ = new BitSet(worldDiameter);
            for (int image = 0; image < sampling.rasterSize(); image++) {
                sampledX.set(Math.toIntExact(
                        (long) sampling.worldXForImageColumn(image)
                                - minWorldX
                ));
                sampledZ.set(Math.toIntExact(
                        (long) sampling.worldZForImageRow(image)
                                - minWorldZ
                ));
            }

            BitSet horizontal = (BitSet) sampledX.clone();
            for (int x = sampledX.nextSetBit(0);
                 x >= 0;
                 x = sampledX.nextSetBit(x + 1)) {
                if (x > 0) {
                    horizontal.set(x - 1);
                }
                if (x + 1 < worldDiameter) {
                    horizontal.set(x + 1);
                }
            }

            for (int z = sampledZ.nextSetBit(0);
                 z >= 0;
                 z = sampledZ.nextSetBit(z + 1)) {
                row(requiredByRow, z).or(horizontal);
                if (z > 0) {
                    row(requiredByRow, z - 1).or(sampledX);
                }
                if (z + 1 < worldDiameter) {
                    row(requiredByRow, z + 1).or(sampledX);
                }
            }
        }

        private void addSurfaceRequirements(
                SurfaceRenderData surfaceData,
                BitSet[] requiredByRow
        ) {
            if (surfaceData.isEmpty()) {
                return;
            }

            long[] sources = surfaceData.surfaceSourceByPixelView();
            BitSet present = surfaceData.surfacePresentView();
            for (int index = present.nextSetBit(0);
                 index >= 0;
                 index = present.nextSetBit(index + 1)) {
                long packed = sources[index];
                int worldX = (int) (packed >> 32);
                int worldZ = (int) packed;
                requireWithHillshadeNeighbours(
                        requiredByRow,
                        worldX,
                        worldZ
                );
            }
        }

        private void requireWithHillshadeNeighbours(
                BitSet[] requiredByRow,
                int worldX,
                int worldZ
        ) {
            require(requiredByRow, worldX, worldZ);
            require(requiredByRow, worldX - 1, worldZ);
            require(requiredByRow, worldX + 1, worldZ);
            require(requiredByRow, worldX, worldZ - 1);
            require(requiredByRow, worldX, worldZ + 1);
        }

        private void require(
                BitSet[] requiredByRow,
                int worldX,
                int worldZ
        ) {
            long xOffset = (long) worldX - minWorldX;
            long zOffset = (long) worldZ - minWorldZ;
            if (xOffset < 0
                    || xOffset >= worldDiameter
                    || zOffset < 0
                    || zOffset >= worldDiameter) {
                return;
            }
            row(requiredByRow, (int) zOffset).set((int) xOffset);
        }

        private static BitSet row(BitSet[] rows, int zOffset) {
            BitSet row = rows[zOffset];
            if (row == null) {
                row = new BitSet();
                rows[zOffset] = row;
            }
            return row;
        }

        private static Row[] materializeRows(BitSet[] requiredByRow) {
            Row[] rows = new Row[requiredByRow.length];
            for (int z = 0; z < requiredByRow.length; z++) {
                BitSet required = requiredByRow[z];
                if (required != null && !required.isEmpty()) {
                    rows[z] = Row.from(required);
                }
            }
            return rows;
        }
    }

    private static final class Row {
        private final long[] requiredWords;
        private final int[] prefixCounts;
        private final int[] values;
        private final BitSet present;

        private Row(
                long[] requiredWords,
                int[] prefixCounts,
                int valueCount
        ) {
            this.requiredWords = requiredWords;
            this.prefixCounts = prefixCounts;
            this.values = new int[valueCount];
            this.present = new BitSet(valueCount);
        }

        private static Row from(BitSet required) {
            long[] words = required.toLongArray();
            int[] prefix = new int[words.length + 1];
            for (int word = 0; word < words.length; word++) {
                prefix[word + 1] = Math.addExact(
                        prefix[word],
                        Long.bitCount(words[word])
                );
            }
            return new Row(
                    words,
                    prefix,
                    prefix[words.length]
            );
        }

        private int indexAt(long xOffset) {
            if (xOffset < 0 || xOffset > Integer.MAX_VALUE) {
                return -1;
            }
            int offset = (int) xOffset;
            int wordIndex = offset >>> 6;
            if (wordIndex >= requiredWords.length) {
                return -1;
            }

            long word = requiredWords[wordIndex];
            long bit = 1L << (offset & 63);
            if ((word & bit) == 0L) {
                return -1;
            }
            return prefixCounts[wordIndex]
                    + Long.bitCount(word & (bit - 1L));
        }
    }

    private record HeightRange(int min, int max) {
    }
}
