package cartographer.scanner;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;

public record RainHeightSurfaceTarget(
        int worldX,
        int worldY,
        int worldZ
) {
    public ChunkPosition chunkPosition() {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        return new ChunkPosition(
                Math.floorDiv(worldX, size),
                Math.floorDiv(worldY, size),
                Math.floorDiv(worldZ, size),
                0
        );
    }

    public int localX() {
        return Math.floorMod(worldX, ChunkCoordinate.SIZE_BLOCKS);
    }

    public int localY() {
        return Math.floorMod(worldY, ChunkCoordinate.SIZE_BLOCKS);
    }

    public int localZ() {
        return Math.floorMod(worldZ, ChunkCoordinate.SIZE_BLOCKS);
    }
}
