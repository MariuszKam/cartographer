package cartographer.render;

import cartographer.cli.ProgressReporter;
import cartographer.model.MapChunk;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

final class DenseHeightGrid {

    private final int minWorldX;
    private final int minWorldZ;
    private final int width;
    private final int height;
    private final int[] values;
    private final BitSet present;
    private final int minHeight;
    private final int maxHeight;

    private DenseHeightGrid(
            int minWorldX,
            int minWorldZ,
            int width,
            int height,
            int[] values,
            BitSet present,
            int minHeight,
            int maxHeight
    ) {
        this.minWorldX = minWorldX;
        this.minWorldZ = minWorldZ;
        this.width = width;
        this.height = height;
        this.values = values;
        this.present = present;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    static DenseHeightGrid fromMapChunks(
            List<MapChunk> chunks,
            int minWorldX,
            int minWorldZ,
            int width,
            int height
    ) {
        Objects.requireNonNull(chunks, "chunks are required");
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(
                    "grid dimensions cannot be negative"
            );
        }

        Builder builder = builder(
                minWorldX,
                minWorldZ,
                width,
                height,
                ProgressReporter.NONE,
                chunks.size()
        );
        for (MapChunk chunk : chunks) {
            builder.accept(chunk);
        }
        return builder.finish();
    }

    static Builder builder(
            int minWorldX,
            int minWorldZ,
            int width,
            int height,
            ProgressReporter progress,
            int expectedChunks
    ) {
        return new Builder(
                minWorldX,
                minWorldZ,
                width,
                height,
                progress,
                expectedChunks
        );
    }

    static final class Builder {
        private final int minWorldX;
        private final int minWorldZ;
        private final int width;
        private final int height;
        private final int[] values;
        private final BitSet present;
        private final ProgressReporter progress;
        private final int expectedChunks;
        private int acceptedChunks;

        private Builder(
                int minWorldX,
                int minWorldZ,
                int width,
                int height,
                ProgressReporter progress,
                int expectedChunks
        ) {
            if (width < 0 || height < 0 || expectedChunks < 0) {
                throw new IllegalArgumentException(
                        "grid dimensions and expected chunks cannot be negative"
                );
            }
            this.minWorldX = minWorldX;
            this.minWorldZ = minWorldZ;
            this.width = width;
            this.height = height;
            int cellCount = Math.multiplyExact(width, height);
            this.values = new int[cellCount];
            this.present = new BitSet(cellCount);
            this.progress = Objects.requireNonNull(progress, "progress is required");
            this.expectedChunks = expectedChunks;
        }

        void accept(MapChunk chunk) {
            Objects.requireNonNull(chunk, "chunks cannot contain null");
            acceptedChunks++;
            progress.progress(
                    "Indexing mapchunk heights",
                    acceptedChunks,
                    expectedChunks
            );

            long originX = (long) chunk.coordinate().x() * MapChunk.SIZE;
            long originZ = (long) chunk.coordinate().z() * MapChunk.SIZE;
            for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
                for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                    long relativeX = originX + localX - minWorldX;
                    long relativeZ = originZ + localZ - minWorldZ;
                    if (relativeX < 0 || relativeX >= width
                            || relativeZ < 0 || relativeZ >= height) {
                        continue;
                    }
                    int index = Math.toIntExact(relativeZ * width + relativeX);
                    values[index] = chunk.heightAt(localX, localZ);
                    present.set(index);
                }
            }
        }

        DenseHeightGrid finish() {
            int minHeight = 0;
            int maxHeight = 0;
            boolean found = false;
            for (int index = present.nextSetBit(0);
                 index >= 0;
                 index = present.nextSetBit(index + 1)) {
                int value = values[index];
                if (!found) {
                    minHeight = value;
                    maxHeight = value;
                    found = true;
                } else {
                    minHeight = Math.min(minHeight, value);
                    maxHeight = Math.max(maxHeight, value);
                }
            }

            return new DenseHeightGrid(
                    minWorldX,
                    minWorldZ,
                    width,
                    height,
                    values,
                    present,
                    minHeight,
                    maxHeight
            );
        }
    }

    static DenseHeightGrid empty() {
        return new DenseHeightGrid(
                0,
                0,
                0,
                0,
                new int[0],
                new BitSet(),
                0,
                0
        );
    }

    boolean hasHeightAt(int worldX, int worldZ) {
        int index = indexOf(worldX, worldZ);
        return index >= 0 && present.get(index);
    }

    int heightAt(int worldX, int worldZ) {
        int index = indexOf(worldX, worldZ);
        if (index < 0 || !present.get(index)) {
            throw new IllegalArgumentException(
                    "height sample is absent at " + worldX + "," + worldZ
            );
        }
        return values[index];
    }

    int minHeight() {
        return minHeight;
    }

    int maxHeight() {
        return maxHeight;
    }

    int sampleCount() {
        return present.cardinality();
    }

    private int indexOf(int worldX, int worldZ) {
        long relativeX = (long) worldX - minWorldX;
        long relativeZ = (long) worldZ - minWorldZ;
        if (relativeX < 0 || relativeX >= width
                || relativeZ < 0 || relativeZ >= height) {
            return -1;
        }
        return Math.toIntExact(relativeZ * width + relativeX);
    }
}
