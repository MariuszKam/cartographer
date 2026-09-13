package cartographer.application;

import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MapChunkRenderWindowPlanner {

    public List<MapChunkCoordinate> plan(
            WorldMetadata metadata,
            WorldPosition center,
            int radiusBlocks
    ) {
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(center, "center is required");
        if (radiusBlocks <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (metadata.mapSizeX() <= 0 || metadata.mapSizeZ() <= 0) {
            return List.of();
        }

        MapChunkCoordinate centerChunk = center.mapChunkCoordinate();
        int radiusChunks = Math.max(
                1,
                (int) Math.ceil(
                        radiusBlocks
                                / (double) MapChunkCoordinate.SIZE_BLOCKS
                )
        );
        int minWorldChunkX = 0;
        int maxWorldChunkX =
                (metadata.mapSizeX() - 1) / MapChunkCoordinate.SIZE_BLOCKS;
        int minWorldChunkZ = 0;
        int maxWorldChunkZ =
                (metadata.mapSizeZ() - 1) / MapChunkCoordinate.SIZE_BLOCKS;

        int minChunkX = Math.max(
                minWorldChunkX,
                centerChunk.x() - radiusChunks
        );
        int maxChunkX = Math.min(
                maxWorldChunkX,
                centerChunk.x() + radiusChunks
        );
        int minChunkZ = Math.max(
                minWorldChunkZ,
                centerChunk.z() - radiusChunks
        );
        int maxChunkZ = Math.min(
                maxWorldChunkZ,
                centerChunk.z() + radiusChunks
        );

        List<MapChunkCoordinate> result = new ArrayList<>();
        for (int z = minChunkZ; z <= maxChunkZ; z++) {
            for (int x = minChunkX; x <= maxChunkX; x++) {
                result.add(new MapChunkCoordinate(x, z));
            }
        }
        return List.copyOf(result);
    }
}
