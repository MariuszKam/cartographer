package cartographer.render;

import cartographer.application.ProgressReporter;
import cartographer.model.MapChunkHeightView;
import cartographer.model.WorldPosition;

import java.util.Objects;

public final class MapTerrainPreparation {
    private final TerrainHeightField heights;
    private final DenseHeightGrid surfaceHeights;
    private final int mapChunkCount;

    private MapTerrainPreparation(
            TerrainHeightField heights,
            DenseHeightGrid surfaceHeights,
            int mapChunkCount
    ) {
        this.heights = heights;
        this.surfaceHeights = surfaceHeights;
        this.mapChunkCount = mapChunkCount;
    }

    public int mapChunkCount() {
        return mapChunkCount;
    }

    TerrainHeightField heights() {
        return heights;
    }

    DenseHeightGrid surfaceHeights() {
        return surfaceHeights;
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
        private final DenseHeightGrid.Builder exactHeights;
        private final SampledTerrainHeightField.Builder sampledHeights;
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

            boolean collectExactHeights =
                    options.layers().contains(RenderLayer.SURFACE);
            boolean collectTerrain =
                    options.layers().contains(RenderLayer.TERRAIN);

            if (collectExactHeights) {
                int width = Math.multiplyExact(
                        options.radiusBlocks(),
                        2
                );
                int minX = (int) Math.floor(center.x())
                        - options.radiusBlocks();
                int minZ = (int) Math.floor(center.z())
                        - options.radiusBlocks();
                exactHeights = DenseHeightGrid.builder(
                        minX,
                        minZ,
                        width,
                        width,
                        progress,
                        expectedMapChunks
                );
            } else {
                exactHeights = null;
            }

            if (collectTerrain && !collectExactHeights) {
                sampledHeights = SampledTerrainHeightField.builder(
                        RenderSamplingPlan.from(center, options),
                        progress,
                        expectedMapChunks
                );
            } else {
                sampledHeights = null;
            }
        }

        public void accept(MapChunkHeightView mapChunk) {
            Objects.requireNonNull(mapChunk, "map chunk height view is required");
            mapChunkCount++;
            if (exactHeights != null) {
                exactHeights.accept(mapChunk);
            }
            if (sampledHeights != null) {
                sampledHeights.accept(mapChunk);
            }
        }

        public MapTerrainPreparation finish() {
            DenseHeightGrid exact = exactHeights == null
                    ? DenseHeightGrid.empty()
                    : exactHeights.finish();
            TerrainHeightField terrain = sampledHeights == null
                    ? exact
                    : sampledHeights.finish();
            return new MapTerrainPreparation(
                    terrain,
                    exact,
                    mapChunkCount
            );
        }
    }
}
