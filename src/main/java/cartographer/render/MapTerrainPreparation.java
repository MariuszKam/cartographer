package cartographer.render;

import cartographer.application.ProgressReporter;
import cartographer.model.MapChunk;
import cartographer.model.WorldPosition;

import java.util.Objects;

public final class MapTerrainPreparation {
    private final DenseHeightGrid heights;
    private final int mapChunkCount;

    private MapTerrainPreparation(DenseHeightGrid heights, int mapChunkCount) {
        this.heights = heights;
        this.mapChunkCount = mapChunkCount;
    }

    public int mapChunkCount() {
        return mapChunkCount;
    }

    DenseHeightGrid heights() {
        return heights;
    }

    public static Builder builder(
            WorldPosition center,
            RenderOptions options,
            int expectedMapChunks
    ) {
        return builder(center, options, expectedMapChunks, ProgressReporter.NONE);
    }

    public static Builder builder(
            WorldPosition center,
            RenderOptions options,
            int expectedMapChunks,
            ProgressReporter progress
    ) {
        return new Builder(center, options, expectedMapChunks, progress);
    }

    public static final class Builder {
        private final DenseHeightGrid.Builder heights;
        private int mapChunkCount;

        private Builder(
                WorldPosition center,
                RenderOptions options,
                int expectedMapChunks,
                ProgressReporter progress
        ) {
            Objects.requireNonNull(center, "center is required");
            Objects.requireNonNull(options, "options are required");
            Objects.requireNonNull(progress, "progress is required");
            if (expectedMapChunks < 0) {
                throw new IllegalArgumentException(
                        "expected map chunks cannot be negative"
                );
            }

            boolean collectHeights = options.layers().contains(RenderLayer.TERRAIN)
                    || options.layers().contains(RenderLayer.SURFACE);
            if (!collectHeights) {
                heights = null;
                return;
            }

            int width = Math.multiplyExact(options.radiusBlocks(), 2);
            int minX = (int) Math.floor(center.x()) - options.radiusBlocks();
            int minZ = (int) Math.floor(center.z()) - options.radiusBlocks();
            heights = DenseHeightGrid.builder(
                    minX,
                    minZ,
                    width,
                    width,
                    progress,
                    expectedMapChunks
            );
        }

        public void accept(MapChunk mapChunk) {
            Objects.requireNonNull(mapChunk, "map chunk is required");
            mapChunkCount++;
            if (heights != null) {
                heights.accept(mapChunk);
            }
        }

        public MapTerrainPreparation finish() {
            return new MapTerrainPreparation(
                    heights == null ? DenseHeightGrid.empty() : heights.finish(),
                    mapChunkCount
            );
        }
    }
}
