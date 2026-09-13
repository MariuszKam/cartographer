package cartographer.application;

import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MapChunkPositionPlanner {

    /**
     * Plans mapchunk columns in deterministic chunk-Z then chunk-X order.
     */
    public List<MapChunkCoordinate> plan(
            WorldMetadata metadata,
            int centerWorldX,
            int centerWorldZ,
            int radius
    ) {
        Objects.requireNonNull(metadata, "metadata is required");

        if (radius <= 0
                || metadata.mapSizeX() <= 0
                || metadata.mapSizeZ() <= 0) {
            if (radius <= 0) {
                throw new IllegalArgumentException("radius must be positive");
            }
            return List.of();
        }

        long worldMinX = 0L;
        long worldMaxX = (long) metadata.mapSizeX() - 1L;
        long worldMinZ = 0L;
        long worldMaxZ = (long) metadata.mapSizeZ() - 1L;
        long searchMinX = (long) centerWorldX - radius;
        long searchMaxX = (long) centerWorldX + radius;
        long searchMinZ = (long) centerWorldZ - radius;
        long searchMaxZ = (long) centerWorldZ + radius;

        if (searchMaxX < worldMinX
                || searchMinX > worldMaxX
                || searchMaxZ < worldMinZ
                || searchMinZ > worldMaxZ) {
            return List.of();
        }

        int size = MapChunkCoordinate.SIZE_BLOCKS;
        int minChunkX = Math.floorDiv(searchMinX, (long) size) < 0
                ? 0
                : (int) Math.floorDiv(searchMinX, (long) size);
        int maxChunkX = (int) Math.floorDiv(searchMaxX, (long) size);
        int minChunkZ = Math.floorDiv(searchMinZ, (long) size) < 0
                ? 0
                : (int) Math.floorDiv(searchMinZ, (long) size);
        int maxChunkZ = (int) Math.floorDiv(searchMaxZ, (long) size);

        int worldMaxChunkX = (int) Math.floorDiv(worldMaxX, (long) size);
        int worldMaxChunkZ = (int) Math.floorDiv(worldMaxZ, (long) size);
        maxChunkX = Math.min(maxChunkX, worldMaxChunkX);
        maxChunkZ = Math.min(maxChunkZ, worldMaxChunkZ);

        long radiusSquared = (long) radius * radius;
        List<MapChunkCoordinate> planned = new ArrayList<>();

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                long minBlockX = (long) chunkX * size;
                long maxBlockX = Math.min(
                        minBlockX + size - 1L,
                        worldMaxX
                );
                long minBlockZ = (long) chunkZ * size;
                long maxBlockZ = Math.min(
                        minBlockZ + size - 1L,
                        worldMaxZ
                );

                long nearestX = Math.clamp(centerWorldX, minBlockX, maxBlockX);
                long nearestZ = Math.clamp(centerWorldZ, minBlockZ, maxBlockZ);
                long dx = nearestX - centerWorldX;
                long dz = nearestZ - centerWorldZ;

                if (dx * dx + dz * dz <= radiusSquared) {
                    planned.add(new MapChunkCoordinate(chunkX, chunkZ));
                }
            }
        }

        return List.copyOf(planned);
    }
}
