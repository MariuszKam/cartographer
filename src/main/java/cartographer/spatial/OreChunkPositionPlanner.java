package cartographer.spatial;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;
import cartographer.scanner.ActualBlockYFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OreChunkPositionPlanner {

    public List<ChunkPosition> plan(
            WorldMetadata metadata,
            int centerWorldX,
            int centerWorldZ,
            int radius,
            ActualBlockYFilter yFilter
    ) {
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(yFilter, "yFilter is required");

        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (metadata.mapSizeX() <= 0
                || metadata.mapSizeY() <= 0
                || metadata.mapSizeZ() <= 0) {
            return List.of();
        }

        long minWorldX = Math.max(0L, (long) centerWorldX - (long) radius);
        long maxWorldX = Math.min(
                (long) metadata.mapSizeX() - 1,
                (long) centerWorldX + (long) radius
        );
        long minWorldZ = Math.max(0L, (long) centerWorldZ - (long) radius);
        long maxWorldZ = Math.min(
                (long) metadata.mapSizeZ() - 1,
                (long) centerWorldZ + (long) radius
        );

        if (minWorldX > maxWorldX || minWorldZ > maxWorldZ) {
            return List.of();
        }

        int minChunkX = (int) Math.floorDiv(
                minWorldX,
                ChunkCoordinate.SIZE_BLOCKS
        );
        int maxChunkX = (int) Math.floorDiv(
                maxWorldX,
                ChunkCoordinate.SIZE_BLOCKS
        );
        int minChunkZ = (int) Math.floorDiv(
                minWorldZ,
                ChunkCoordinate.SIZE_BLOCKS
        );
        int maxChunkZ = (int) Math.floorDiv(
                maxWorldZ,
                ChunkCoordinate.SIZE_BLOCKS
        );

        int minY = yFilter.minInclusive() == null
                ? 0
                : Math.max(0, yFilter.minInclusive());
        int maxY = yFilter.maxInclusive() == null
                ? metadata.mapSizeY() - 1
                : Math.min(metadata.mapSizeY() - 1, yFilter.maxInclusive());

        if (minY > maxY) {
            return List.of();
        }

        int minChunkY = minY / ChunkCoordinate.SIZE_BLOCKS;
        int maxChunkY = maxY / ChunkCoordinate.SIZE_BLOCKS;
        long radiusSquared = (long) radius * radius;
        List<ChunkPosition> result = new ArrayList<>();

        for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunkMinZ = Math.max(
                        0L,
                        (long) chunkZ * ChunkCoordinate.SIZE_BLOCKS
                );
                long chunkMaxZ = Math.min(
                        (long) metadata.mapSizeZ() - 1,
                        chunkMinZ + ChunkCoordinate.SIZE_BLOCKS - 1
                );
                long nearestZ = clamp(centerWorldZ, chunkMinZ, chunkMaxZ);
                long dz = nearestZ - centerWorldZ;
                long dzSquared = dz * dz;

                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    long chunkMinX = Math.max(
                            0L,
                            (long) chunkX * ChunkCoordinate.SIZE_BLOCKS
                    );
                    long chunkMaxX = Math.min(
                            (long) metadata.mapSizeX() - 1,
                            chunkMinX + ChunkCoordinate.SIZE_BLOCKS - 1
                    );
                    long nearestX = clamp(centerWorldX, chunkMinX, chunkMaxX);
                    long dx = nearestX - centerWorldX;

                    if (dx * dx + dzSquared <= radiusSquared) {
                        result.add(
                                new ChunkPosition(
                                        chunkX,
                                        chunkY,
                                        chunkZ,
                                        0
                                )
                        );
                    }
                }
            }
        }

        return List.copyOf(result);
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
