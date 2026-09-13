package cartographer.scanner;

import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceBlock;

public final class SurfaceBlockCoordinates {

    private SurfaceBlockCoordinates() {
    }

    public static MapChunkCoordinate mapChunkOf(SurfaceBlock block) {
        if (block == null) {
            throw new NullPointerException("block is required");
        }
        return new MapChunkCoordinate(
                Math.floorDiv(block.worldX(), MapChunkCoordinate.SIZE_BLOCKS),
                Math.floorDiv(block.worldZ(), MapChunkCoordinate.SIZE_BLOCKS)
        );
    }

    public static boolean withinCircle(
            SurfaceBlock block,
            int centerWorldX,
            int centerWorldZ,
            int radius
    ) {
        if (block == null) {
            throw new NullPointerException("block is required");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        long dx = (long) block.worldX() - centerWorldX;
        long dz = (long) block.worldZ() - centerWorldZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }
}
