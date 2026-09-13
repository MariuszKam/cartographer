package cartographer.scanner;

import cartographer.model.ChunkPosition;

import java.util.List;
import java.util.Objects;

public record SurfaceObjectPlan(
        List<SurfaceObjectTarget> targets,
        List<ChunkPosition> chunkPositions
) {
    public SurfaceObjectPlan {
        targets = copyRequired(targets);
        chunkPositions = copyRequired(chunkPositions);
    }

    private static <T> List<T> copyRequired(List<T> values) {
        Objects.requireNonNull(values, "plan values are required");
        return List.copyOf(values);
    }
}
