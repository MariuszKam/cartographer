package cartographer.render;

import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Half-open absolute world-block bounds used by one render-tile operation.
 */
public record RenderTileBounds(
        long worldMinX,
        long worldMinZ,
        long worldMaxXExclusive,
        long worldMaxZExclusive
) {

    public RenderTileBounds {
        if (worldMaxXExclusive <= worldMinX
                || worldMaxZExclusive <= worldMinZ) {
            throw new IllegalArgumentException("render-tile bounds must be non-empty");
        }
    }

    public long widthBlocks() {
        return worldMaxXExclusive - worldMinX;
    }

    public long heightBlocks() {
        return worldMaxZExclusive - worldMinZ;
    }

    public boolean containsWorldBlock(long worldX, long worldZ) {
        return worldX >= worldMinX
                && worldX < worldMaxXExclusive
                && worldZ >= worldMinZ
                && worldZ < worldMaxZExclusive;
    }

    /**
     * Clips these bounds to authoritative absolute world bounds.
     *
     * <p>Vintage Story world metadata uses the half-open absolute rectangle
     * {@code [0, mapSizeX) x [0, mapSizeZ)}.</p>
     */
    public Optional<RenderTileBounds> clipToWorld(WorldMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata is required");
        if (metadata.mapSizeX() <= 0 || metadata.mapSizeZ() <= 0) {
            throw new IllegalArgumentException("world X/Z dimensions must be positive");
        }

        long clippedMinX = Math.max(worldMinX, 0L);
        long clippedMinZ = Math.max(worldMinZ, 0L);
        long clippedMaxX = Math.min(worldMaxXExclusive, metadata.mapSizeX());
        long clippedMaxZ = Math.min(worldMaxZExclusive, metadata.mapSizeZ());
        if (clippedMaxX <= clippedMinX || clippedMaxZ <= clippedMinZ) {
            return Optional.empty();
        }

        return Optional.of(new RenderTileBounds(
                clippedMinX,
                clippedMinZ,
                clippedMaxX,
                clippedMaxZ
        ));
    }

    /**
     * Returns every source mapchunk intersecting these bounds in deterministic
     * Z-major, then X-major order.
     */
    public List<MapChunkCoordinate> intersectingMapChunks() {
        long size = MapChunkCoordinate.SIZE_BLOCKS;
        long firstChunkX = Math.floorDiv(worldMinX, size);
        long firstChunkZ = Math.floorDiv(worldMinZ, size);
        long lastChunkX = Math.floorDiv(worldMaxXExclusive - 1L, size);
        long lastChunkZ = Math.floorDiv(worldMaxZExclusive - 1L, size);

        validateMapChunkRange(firstChunkX, lastChunkX);
        validateMapChunkRange(firstChunkZ, lastChunkZ);

        long width = lastChunkX - firstChunkX + 1L;
        long height = lastChunkZ - firstChunkZ + 1L;
        int expectedSize = Math.toIntExact(Math.multiplyExact(width, height));
        List<MapChunkCoordinate> coordinates = new ArrayList<>(expectedSize);

        for (long chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
            for (long chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
                coordinates.add(new MapChunkCoordinate(
                        (int) chunkX,
                        (int) chunkZ
                ));
            }
        }
        return List.copyOf(coordinates);
    }

    private static void validateMapChunkRange(long first, long last) {
        if (first < Integer.MIN_VALUE || last > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "render-tile bounds exceed mapchunk coordinate range"
            );
        }
    }
}
