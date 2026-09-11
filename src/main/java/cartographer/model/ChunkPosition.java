package cartographer.model;

public record ChunkPosition(
        int x,
        int y,
        int z,
        int dimension
) {
}