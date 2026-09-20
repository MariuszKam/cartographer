package cartographer.render;

import cartographer.application.ProgressReporter;
import cartographer.model.MapChunkHeightView;
import cartographer.model.WorldPosition;

import java.util.Objects;

public final class MapTerrainPreparation {
    private final TerrainHeightField heights;
    private final int mapChunkCount;

    private MapTerrainPreparation(
            TerrainHeightField heights,
            int mapChunkCount
    ) {
        this.heights = heights;
        this.mapChunkCount = mapChunkCount;
    }

    public int mapChunkCount() {
        return mapChunkCount;
    }

    TerrainHeightField heights() {
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
        return new Builder(
                center,
                options,
                expectedMapChunks,
                progress,
                null
        );
    }

    public static Builder builder(
            WorldPosition center,
            RenderOptions options,
            int expectedMapChunks,
            ProgressReporter progress,
            SurfaceRenderData surfaceRenderData
    ) {
        Objects.requireNonNull(
                surfaceRenderData,
                "Surface render data is required"
        );
        return new Builder(
                center,
                options,
                expectedMapChunks,
                progress,
                surfaceRenderData
        );
    }

    public static final class Builder {
        private final DenseHeightGrid.Builder exactHeights;
        private final SampledTerrainHeightField.Builder sampledHeights;
        private int mapChunkCount;

        private Builder(
                WorldPosition center,
                RenderOptions options,
                int expectedMapChunks,
                ProgressReporter progress,
                SurfaceRenderData surfaceRenderData
        ) {
            Objects.requireNonNull(center, "center is required");
            Objects.requireNonNull(options, "options are required");
            Objects.requireNonNull(progress, "progress is required");
            if (expectedMapChunks < 0) {
                throw new IllegalArgumentException(
                        "expected map chunks cannot be negative"
                );
            }

            boolean collectSurface =
                    options.layers().contains(RenderLayer.SURFACE);
            boolean collectTerrain =
                    options.layers().contains(RenderLayer.TERRAIN);
            boolean collectExactHeights =
                    collectSurface && surfaceRenderData == null;

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

            boolean collectSampledHeights =
                    !collectExactHeights
                            && (collectTerrain
                            || (collectSurface
                            && surfaceRenderData != null
                            && !surfaceRenderData.isEmpty()));
            if (collectSampledHeights) {
                RenderSamplingPlan sampling =
                        RenderSamplingPlan.from(center, options);
                sampledHeights = SampledTerrainHeightField.builder(
                        sampling,
                        collectTerrain,
                        surfaceRenderData == null
                                ? SurfaceRenderData.empty(sampling)
                                : surfaceRenderData,
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
            TerrainHeightField sampled = sampledHeights == null
                    ? null
                    : sampledHeights.finish();
            TerrainHeightField terrain = sampled == null
                    ? exact
                    : sampled;
            return new MapTerrainPreparation(
                    terrain,
                    mapChunkCount
            );
        }
    }
}
