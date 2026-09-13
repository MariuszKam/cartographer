package cartographer.scanner;

import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;

import java.util.List;
import java.util.Objects;

public record RainHeightSurfacePlan(
        List<RainHeightSurfaceTarget> targets,
        List<ChunkPosition> chunkPositions,
        List<MapChunkCoordinate> fallbackMapChunks
) {
    public RainHeightSurfacePlan {
        targets = copyRequired("targets", targets);
        chunkPositions = copyRequired("chunkPositions", chunkPositions);
        fallbackMapChunks = copyRequired("fallbackMapChunks", fallbackMapChunks);
    }

    private static <T> List<T> copyRequired(
            String name,
            List<T> values
    ) {
        Objects.requireNonNull(values, name + " is required");
        for (T value : values) {
            Objects.requireNonNull(value, name + " cannot contain null");
        }
        return List.copyOf(values);
    }
}
