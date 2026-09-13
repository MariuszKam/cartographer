package cartographer.scanner;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;

import java.util.List;

public record SurfaceObjectTarget(
        int worldX,
        int worldZ,
        List<Integer> candidateWorldYs
) {
    public SurfaceObjectTarget {
        candidateWorldYs = List.copyOf(candidateWorldYs);
        if (candidateWorldYs.isEmpty()) {
            throw new IllegalArgumentException("surface object target requires candidate Y values");
        }
    }

    public ChunkPosition chunkPosition(int worldY) {
        return new ChunkPosition(
                Math.floorDiv(worldX, ChunkCoordinate.SIZE_BLOCKS),
                Math.floorDiv(worldY, ChunkCoordinate.SIZE_BLOCKS),
                Math.floorDiv(worldZ, ChunkCoordinate.SIZE_BLOCKS),
                0
        );
    }
}
